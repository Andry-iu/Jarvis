"""Permanent signing adapter, executed only inside the existing Jarvis cloud."""
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import time
import zipfile

from runner import BASE, JobError, child_environment

CERT = 'a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a'
BACKUP = '155b8afc72265dae8bfe5e8d4a09a49929375d88e9972bd9b2777723d2502e58'


def run(args, cwd):
    result = subprocess.run(list(map(str, args)), cwd=cwd, env=child_environment(), capture_output=True)
    if result.returncode:
        raise JobError('signing_failed')
    return result.stdout


def sign(work, repo, out):
    sys.path.insert(0, str(BASE/'signing-python'))
    import pyrage
    out.mkdir(parents=True, exist_ok=True, mode=0o700)
    meta = json.loads((work/'app/build/outputs/apk/debug/output-metadata.json').read_text())
    if meta['applicationId'] != 'it.jarviswake' or len(meta['elements']) != 1:
        raise JobError('signing_failed')
    entry = meta['elements'][0]
    version, code = entry['versionName'], entry['versionCode']
    if not re.fullmatch(r'\d+\.\d+\.\d+', version) or Path(entry['outputFile']).name != entry['outputFile']:
        raise JobError('signing_failed')
    source = work/'app/build/outputs/apk/debug'/entry['outputFile']
    target = out/f'JarvisWake-{version}-debug-arm64-permanent.apk'
    bt = BASE/'android-sdk/build-tools/36.0.0'
    archive = run(['git', 'show', 'origin/signing-handoff-2026-10-19:signing-transfer/jarvis-signing-backup.tar.age'], repo)
    if hashlib.sha256(archive).hexdigest() != BACKUP or not os.environ.get('Passphrase'):
        raise JobError('signing_failed')
    with tempfile.TemporaryDirectory(prefix='jarvis-cloud-sign-') as directory:
        private = Path(directory)
        os.chmod(private, 0o700)
        plain = pyrage.passphrase.decrypt(archive, os.environ['Passphrase'])
        with tarfile.open(fileobj=io.BytesIO(plain)) as tar:
            for name in ('jarvis-signing.jks', 'store-password.txt', 'key-password.txt'):
                (private/name).write_bytes(tar.extractfile(name).read())
                os.chmod(private/name, 0o600)
        certificate = private/'certificate.der'
        run([BASE/'jdk-17.0.21+9/bin/keytool', '-exportcert', '-alias', 'jarvis', '-keystore',
             private/'jarvis-signing.jks', '-storepass:file', private/'store-password.txt', '-file', certificate], work)
        if hashlib.sha256(certificate.read_bytes()).hexdigest() != CERT:
            raise JobError('signing_failed')
        aligned = private/'aligned.apk'
        run([bt/'zipalign', '-P', '16', '-f', '4', source, aligned], work)
        run([bt/'apksigner', 'sign', '--ks', private/'jarvis-signing.jks', '--ks-key-alias', 'jarvis',
             '--ks-pass', 'file:'+str(private/'store-password.txt'), '--key-pass', 'file:'+str(private/'key-password.txt'),
             '--v1-signing-enabled', 'false', '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'true', '--out', target, aligned], work)
    report = run([bt/'apksigner', 'verify', '--verbose', '--print-certs', '--min-sdk-version', '31', target], work).decode()
    v2 = run([bt/'apksigner', 'verify', '--verbose', '--min-sdk-version', '24', '--max-sdk-version', '27', target], work).decode()
    if ('Signer #1 certificate SHA-256 digest: '+CERT not in report or
        'Verified using v3 scheme (APK Signature Scheme v3): true' not in report or
        'Verified using v2 scheme (APK Signature Scheme v2): true' not in v2):
        raise JobError('signing_failed')
    run([bt/'zipalign', '-c', '-P', '16', '-v', '4', target], work)
    package = run([bt/'aapt2', 'dump', 'badging', target], work).decode()
    if not all(text in package for text in (f"package: name='it.jarviswake' versionCode='{code}' versionName='{version}'",
                                             "minSdkVersion:'31'", "targetSdkVersion:'36'", "native-code: 'arm64-v8a'")):
        raise JobError('signing_failed')
    with zipfile.ZipFile(target) as z:
        libs = [n for n in z.namelist() if n.startswith('lib/') and n.endswith('.so')]
        if z.testzip() or len(libs) != 3 or not all(n.startswith('lib/arm64-v8a/') for n in libs):
            raise JobError('signing_failed')
        with tempfile.TemporaryDirectory(prefix='jarvis-cloud-elf-') as directory:
            elf = Path(directory)/'library.so'
            for name in libs:
                elf.write_bytes(z.read(name))
                lines = [s.split() for s in run(['readelf', '-lW', elf], work).decode().splitlines() if s.strip().startswith('LOAD ')]
                if not lines or not all(int(x[-1], 16) >= 16384 and int(x[1], 16) % 16384 == int(x[2], 16) % 16384 for x in lines):
                    raise JobError('signing_failed')
    with target.open('rb') as f:
        sha = hashlib.file_digest(f, 'sha256').hexdigest()
    return target, {'version': version, 'version_code': code, 'sha256': sha, 'bytes': target.stat().st_size,
                    'certificate_sha256': CERT, 'expires_at': int(time.time()*1000)+7*86400000-60000}
