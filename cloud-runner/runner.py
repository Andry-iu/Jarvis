#!/usr/bin/env python3
"""Bounded Jarvis cloud executor. Never accepts shell strings or arbitrary repositories."""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import urllib.request
import urllib.error
import uuid
import xml.etree.ElementTree as ET

BASE = Path('/workspace/.jarvis-cloud')
REPO = Path('/workspace/Jarvis')
ORIGIN = 'https://jarviswake-link-andrea.autech-sm267-2695.chatgpt.site'
REF = re.compile(r'^(main|[0-9a-f]{40})$')
HEX = re.compile(r'^[0-9a-f]{64}$')
PHASES = ['fetch', 'checks', 'android', 'signing', 'recording', 'upload']


class JobError(Exception):
    pass


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise urllib.error.URLError('redirect_refused')


def private_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    temp = path.with_suffix('.tmp')
    fd = os.open(temp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, 'w') as f:
        json.dump(value, f, sort_keys=True)
        f.flush()
        os.fsync(f.fileno())
    os.replace(temp, path)
    fd = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def validate_identity(value):
    if not HEX.fullmatch(value['owner']):
        raise ValueError('Invalid owner')
    for key in ('job_id', 'claim_id'):
        item = value[key]
        parsed = uuid.UUID(item)
        if parsed.version != 4 or str(parsed) != item:
            raise ValueError('Invalid job identity')


def child_environment():
    # Repository build scripts never receive signing or bridge credentials in their environment.
    env = {k: v for k, v in os.environ.items()
           if k not in ('Passphrase', 'JARVIS_SIGNING_PASSWORD')
           and not any(x in k.upper() for x in ('TOKEN', 'SECRET', 'PASSWORD', 'API_KEY'))}
    env.update(JAVA_HOME=str(BASE/'jdk-17.0.21+9'),
               ANDROID_HOME=str(BASE/'android-sdk'), ANDROID_SDK_ROOT=str(BASE/'android-sdk'),
               GRADLE_USER_HOME=str(BASE/'gradle'), ANDROID_USER_HOME=str(BASE/'android-user'))
    env['PATH'] = str(BASE/'jdk-17.0.21+9/bin') + ':' + env.get('PATH', '')
    return env


class Client:
    def __init__(self, config):
        if config['origin'] != ORIGIN:
            raise ValueError('Only the configured private Site is supported')
        self.config = config
        self.opener = urllib.request.build_opener(NoRedirect())

    def request(self, route, body=None, apk=None, identity=None):
        headers = {'Authorization': 'Bearer '+self.config['runner_token'],
                   'OAI-Sites-Authorization': 'Bearer '+self.config['site_access']}
        if apk:
            headers.update({'Content-Type': 'application/vnd.android.package-archive',
                            'Content-Length': str(apk.stat().st_size),
                            **{'X-'+k.replace('_', '-'): v for k, v in {
                                'Job-Owner': identity['owner'], 'Job-Id': identity['job_id'],
                                'Claim-Id': identity['claim_id']}.items()}})
            # urllib streams a file object with explicit Content-Length, without loading the APK.
            with apk.open('rb') as source:
                req = urllib.request.Request(ORIGIN+route, data=source, method='PUT', headers=headers)
                with self.opener.open(req, timeout=180) as r:
                    return json.load(r)
        headers['Content-Type'] = 'application/json'
        req = urllib.request.Request(ORIGIN+route, data=json.dumps(body or {}).encode(),
                                     method='POST', headers=headers)
        with self.opener.open(req, timeout=30) as r:
            return json.load(r)


class Runner:
    def __init__(self, client, state, repo=REPO, base=BASE):
        self.client, self.state, self.repo, self.base = client, Path(state), Path(repo), Path(base)
        self.current = None
        self.last_poll = 0
        self.lock_fd = None

    def save(self):
        private_json(self.state, self.current)

    def heartbeat(self):
        if time.monotonic()-self.last_poll >= 15:
            self.last_poll = time.monotonic()
            try:
                self.client.request('/cloud/runner/poll')
            except Exception:
                # A temporary outage must not start another process or retry an uncertain build.
                pass

    def command(self, args, cwd, error):
        log_path = self.base/'cloud-runner/logs'/self.current['job_id']/ 'build.log'
        log_path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        with log_path.open('ab') as log:
            os.chmod(log_path, 0o600)
            with subprocess.Popen(list(map(str, args)), cwd=cwd, env=child_environment(),
                                  stdout=log, stderr=subprocess.STDOUT,
                                  pass_fds=(() if self.lock_fd is None else (self.lock_fd,))) as proc:
                while proc.poll() is None:
                    self.heartbeat()
                    time.sleep(.5)
                if proc.returncode:
                    raise JobError(error)

    def git(self, *args, cwd=None):
        p = subprocess.run(['git', *args], cwd=cwd or self.repo, env=child_environment(),
                           capture_output=True, text=True)
        if p.returncode:
            raise JobError('fetch_failed')
        return p.stdout.strip()

    def progress(self, stage):
        self.current['stage'] = stage
        self.save()
        self.client.request('/cloud/runner/progress', {
            **{k: self.current[k] for k in ('owner', 'job_id', 'claim_id')},
            **{k: self.current.get(k) for k in ('resolved_commit', 'artifact', 'release_commit')},
            'stage': stage, 'counts': self.current.get('counts', {})})

    def prepare(self, job):
        if job['operation'] not in ('verify', 'build_signed') or not REF.fullmatch(job['revision']):
            raise JobError('revision_not_on_main')
        remote = self.git('remote', 'get-url', 'origin')
        if remote not in ('https://github.com/Andry-iu/Jarvis.git', 'https://github.com/Andry-iu/Jarvis'):
            raise JobError('fetch_failed')
        self.command(['git', 'fetch', 'origin', 'main', 'signing-handoff-2026-10-19'], self.repo, 'fetch_failed')
        commit = self.git('rev-parse', 'origin/main' if job['revision'] == 'main' else job['revision']+'^{commit}')
        self.command(['git', 'merge-base', '--is-ancestor', commit, 'origin/main'], self.repo, 'revision_not_on_main')
        self.current['resolved_commit'] = commit
        self.save()  # main is pinned before any build command.
        work = self.base/'builds'/self.current['job_id']
        if work.exists():
            raise JobError('outcome_uncertain')
        work.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.command(['git', 'worktree', 'add', '--detach', work, commit], self.repo, 'fetch_failed')
        model = self.repo/'app/src/main/assets/model'
        target = work/'app/src/main/assets/model'
        if model.is_dir() and not target.exists():
            target.parent.mkdir(parents=True, exist_ok=True)
            target.symlink_to(model, target_is_directory=True)
        return work

    def checks(self, work):
        for script in ('test-cover-microphone.cjs', 'test-web-voice.cjs', 'test-prepared-chat.cjs',
                       'test-desktop-web.cjs', 'test-google-text.cjs'):
            self.command(['node', 'scripts/'+script], work, 'checks_failed')
        self.command(['node', '--test', '--test-isolation=none', 'test/bridge.test.mjs'], work/'phone-bridge', 'checks_failed')
        cloud_test = work/'phone-bridge/test/cloud.test.mjs'
        if cloud_test.exists():
            self.command(['node', '--test', '--test-isolation=none', 'test/cloud.test.mjs'], work/'phone-bridge', 'checks_failed')
        self.command(['python', '-m', 'unittest', 'discover', '-s', 'scripts/tests'], work, 'checks_failed')
        if (work/'cloud-runner/tests').is_dir():
            self.command(['python', '-m', 'unittest', 'discover', '-s', 'cloud-runner/tests'], work, 'checks_failed')
        self.command(['python', 'scripts/sync-guide.py', '--check'], work, 'checks_failed')
        self.command(['bash', 'scripts/build.sh'], work/'phone-bridge', 'checks_failed')
        self.command(['node', 'scripts/validate-artifact.mjs'], work/'phone-bridge', 'checks_failed')

    def android(self, work, build):
        args = ['bash', 'gradlew', '--offline', '--no-daemon', '-PjarvisUnsigned=true',
                '-Pkotlin.compiler.execution.strategy=in-process',
                '-PjarvisTestJava=/usr/lib/jvm/java-21-openjdk-amd64/bin/java', '-PjarvisTestSdks=34,36',
                ':app:testDebugUnitTest', ':app:lintDebug']
        if build:
            args.append(':app:assembleDebug')
        self.command(args, work, 'android_failed')
        self.current['counts'] = read_android_counts(work)
        if self.current['counts']['android_failures'] or self.current['counts']['lint_errors']:
            raise JobError('android_failed')

    def record(self, work):
        """Commit a fixed technical report only, never artifacts or arbitrary runner logs."""
        report = {k: self.current.get(k) for k in ('job_id', 'operation', 'revision', 'resolved_commit', 'counts', 'artifact')}
        report.update(physical_device_tested=False, google_paid_calls=False,
                      scope='Cloud build verification; existing application behavior unchanged')
        release = self.base/'reports'/self.current['job_id']
        for attempt in range(3):
            self.command(['git', 'fetch', 'origin', 'main'], self.repo, 'recording_failed')
            latest = self.git('rev-parse', 'origin/main')
            if not release.exists():
                self.command(['git', 'worktree', 'add', '--detach', release, latest], self.repo, 'recording_failed')
            elif attempt:
                self.command(['git', 'reset', '--hard', latest], release, 'recording_failed')
            path = release/'releases/cloud-jobs'/ (self.current['job_id']+'.json')
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(report, indent=2, sort_keys=True)+'\n')
            verification = release/'VERIFICATION.md'
            verification.write_text('CLOUD JOB '+self.current['job_id']+' — '+self.current['resolved_commit']+
                                    '\nVerifiche cloud e checksum: releases/cloud-jobs/'+self.current['job_id']+
                                    '.json. Nessun collaudo Galaxy o API a pagamento. Tutte le attività ancora pendenti e i requisiti precedenti restano sotto.\n\n'+verification.read_text())
            self.command(['git', 'add', 'VERIFICATION.md', 'releases/cloud-jobs/'+path.name], release, 'recording_failed')
            self.command(['git', 'commit', '-m', 'Record Jarvis cloud job '+self.current['job_id']], release, 'recording_failed')
            sha = self.git('rev-parse', 'HEAD', cwd=release)
            p = subprocess.run(['git', 'push', 'origin', 'HEAD:main'], cwd=release,
                               env=child_environment(), capture_output=True)
            if p.returncode == 0:
                remote = self.git('ls-remote', 'origin', 'refs/heads/main').split()[0]
                if remote != sha:
                    raise JobError('recording_failed')
                self.current['release_commit'] = sha
                self.save()
                return
        raise JobError('recording_failed')

    def execute(self, job):
        # The durable running marker comes before the first subprocess. Restart never reexecutes it.
        self.current.update(status='running', operation=job['operation'], revision=job['revision'])
        self.save()
        phase = 'fetch'
        try:
            self.progress(phase)
            work = self.prepare(job)
            phase = 'checks'; self.progress(phase); self.checks(work)
            phase = 'android'; self.progress(phase); self.android(work, job['operation'] == 'build_signed')
            if job['operation'] == 'build_signed':
                phase = 'signing'; self.progress(phase)
                from signing import sign
                out = self.base/'cloud-runner/artifacts'/self.current['job_id']
                apk, artifact = sign(work, self.repo, out)
                self.current.update(artifact=artifact, apk=str(apk)); self.save()
            phase = 'recording'; self.progress(phase); self.record(work)
            if job['operation'] == 'build_signed':
                phase = 'upload'; self.progress(phase)
                self.current.update(status='upload_ready'); self.save()
                self.upload()  # restart may repeat only this content-addressed upload, never the build.
            self.current.update(status='finish_ready', result='succeeded', error=None); self.save()
        except JobError as exc:
            self.current.update(status='finish_ready', result='outcome_uncertain' if str(exc)=='outcome_uncertain' else 'failed', error=str(exc)); self.save()
        except Exception:
            if self.current.get('status') == 'upload_ready':
                raise  # Retry the upload only after reconnect; the APK is already verified.
            # Network loss or an unknown exception after dispatch is explicitly uncertain.
            self.current.update(status='finish_ready', result='outcome_uncertain', error='outcome_uncertain'); self.save()
        self.finish()

    def upload(self):
        apk = Path(self.current['apk'])
        with apk.open('rb') as source:
            if hashlib.file_digest(source, 'sha256').hexdigest() != self.current['artifact']['sha256']:
                raise JobError('upload_failed')
        self.client.request('/cloud/runner/artifact', apk=apk, identity=self.current)

    def finish(self):
        self.client.request('/cloud/runner/finish', {**{k: self.current[k] for k in ('owner', 'job_id', 'claim_id')},
            'state': self.current['result'], 'error': self.current['error']})
        self.current['status'] = 'done'; self.save()

    def step(self):
        self.current = json.loads(self.state.read_text()) if self.state.exists() else None
        if self.current and self.current['status'] not in ('done', 'claim_ready'):
            validate_identity(self.current)
            if self.current['status'] == 'upload_ready':
                self.upload()
                self.current.update(status='finish_ready', result='succeeded', error=None); self.save()
            elif self.current['status'] == 'running':
                self.current.update(status='finish_ready', result='outcome_uncertain', error='outcome_uncertain'); self.save()
            self.finish()
            return
        poll = self.client.request('/cloud/runner/poll')
        next_job = poll.get('next')
        if poll.get('active'):
            active = poll['active']
            if not self.current or any(self.current.get(k) != active[k] for k in ('owner', 'job_id', 'claim_id')):
                # Lost local state is never permission to steal or re-run an active job.
                raise JobError('runner_state_missing')
            next_job = active
        if not next_job:
            return
        if not self.current or self.current['job_id'] != next_job['job_id']:
            self.current = {**next_job, 'claim_id': str(uuid.uuid4()), 'status': 'claim_ready'}
            self.save()
        validate_identity(self.current)
        job = self.client.request('/cloud/runner/claim', {k: self.current[k] for k in ('owner', 'job_id', 'claim_id')})['job']
        self.execute(job)


def read_android_counts(work):
    files = list((work/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
    if not files:
        raise JobError('android_failed')
    roots = [ET.parse(p).getroot() for p in files]
    counts = {'android_tests': sum(int(r.attrib.get('tests', 0)) for r in roots),
              'android_failures': sum(int(r.attrib.get('failures', 0))+int(r.attrib.get('errors', 0)) for r in roots),
              'android_skipped': sum(int(r.attrib.get('skipped', 0)) for r in roots)}
    lint = ET.parse(work/'app/build/reports/lint-results-debug.xml').getroot()
    counts.update(lint_errors=sum(i.get('severity') in ('Error', 'Fatal') for i in lint),
                  lint_warnings=sum(i.get('severity') == 'Warning' for i in lint))
    if counts['android_tests'] < 1:
        raise JobError('android_failed')
    return counts


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--config', type=Path, default=BASE/'cloud-runner/config.json')
    parser.add_argument('--once', action='store_true')
    args = parser.parse_args()
    os.umask(0o077)
    if args.config.stat().st_mode & 0o077:
        raise SystemExit('Runner config must be private (0600)')
    args.config.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    with (args.config.parent/'runner.lock').open('w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        runner = Runner(Client(json.loads(args.config.read_text())), args.config.parent/'journal.json')
        runner.lock_fd = lock.fileno()
        while True:
            try:
                runner.step()
                if args.once:
                    break
                time.sleep(15)
            except Exception as exc:
                # Fixed code only: HTTP exception strings may contain privileged URL/header details.
                print(json.dumps({'event': 'runner_waiting', 'code': str(exc) if isinstance(exc, JobError) else 'connection_or_state_error'}), flush=True)
                if args.once:
                    raise SystemExit(1)
                time.sleep(30)


if __name__ == '__main__':
    main()
