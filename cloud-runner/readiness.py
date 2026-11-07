"""Check the existing Jarvis cloud before a queued job is claimed.

No downloads, builds, credential values, or remote mutations in this check.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys

BASE = Path('/workspace/.jarvis-cloud')
REPO = Path('/workspace/Jarvis')
MOBILE_VERSION = 'v0.0.0-20261011034528-197770444867'


def problems(base=BASE, repo=REPO, env=None):
    env = os.environ if env is None else env
    missing = []
    safe_env = {k: v for k, v in env.items()
                if k not in ('Passphrase', 'JARVIS_SITE_ACCESS')
                and not any(part in k.upper() for part in ('TOKEN', 'SECRET', 'PASSWORD', 'API_KEY'))}

    def executable(relative, code):
        path = base/relative
        if not path.is_file() or not os.access(path, os.X_OK):
            missing.append(code)
            return None
        return path

    def output(args):
        try:
            result = subprocess.run(list(map(str, args)), capture_output=True,
                                    text=True, timeout=15, env=safe_env)
            return result.stdout + result.stderr if result.returncode == 0 else ''
        except (OSError, subprocess.TimeoutExpired):
            return ''

    java = executable('jdk-17.0.21+9/bin/java', 'jdk17_missing')
    if java and 'version "17.' not in output([java, '-version']):
        missing.append('jdk17_invalid')
    if 'version "21.' not in output(['/usr/lib/jvm/java-21-openjdk-amd64/bin/java', '-version']):
        missing.append('test_java21_missing')
    go = executable('go-1.27.3/bin/go', 'go_missing')
    if go and 'go1.27.3 ' not in output([go, 'version']):
        missing.append('go_version_invalid')
    for binary in ('gomobile', 'gobind'):
        path = executable('go-bin/'+binary, binary+'_missing')
        if path and go and MOBILE_VERSION not in output([go, 'version', '-m', path]):
            missing.append(binary+'_version_invalid')
    ndk = base/'android-sdk/ndk/28.2.13676358'
    properties = ndk/'source.properties'
    if not properties.is_file() or '28.2.13676358' not in properties.read_text():
        missing.append('ndk_missing')
    executable('android-sdk/ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/bin/clang', 'ndk_clang_missing')
    for name in ('aapt2', 'zipalign', 'apksigner'):
        executable('android-sdk/build-tools/36.0.0/'+name, name+'_missing')
    if not (base/'android-sdk/platforms/android-36/android.jar').is_file():
        missing.append('sdk36_missing')
    if not (repo/'app/src/main/assets/model/.source-verified').is_file():
        missing.append('model_missing')
    if not list((base/'gradle/wrapper/dists/gradle-8.13-bin').glob('*/gradle-8.13/bin/gradle')):
        missing.append('gradle813_missing')
    signing_path = str(base/'signing-python')
    if not output([sys.executable, '-c',
                   'import sys; sys.path.insert(0, sys.argv[1]); import pyrage; print("ready")', signing_path]).strip() == 'ready':
        missing.append('signing_python_missing')
    if not env.get('Passphrase'):
        missing.append('signing_passphrase_unavailable')
    return missing


if __name__ == '__main__':
    argparse.ArgumentParser(description=__doc__).parse_args()
    missing = problems()
    print(json.dumps({'ready': not missing, 'problems': missing}))
    raise SystemExit(bool(missing))
