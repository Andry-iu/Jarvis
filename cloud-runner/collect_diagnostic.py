#!/usr/bin/env python3
"""Opt-in, bounded build diagnostics: publish source locations, never raw logs or values."""
import json
import re
import subprocess
import tempfile
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path

BASE = Path('/workspace/.jarvis-cloud')
REPO = Path('/workspace/Jarvis')
SOURCE = Path(__file__).resolve().parent.parent


def identifier(value):
    parsed = uuid.UUID(value)
    if parsed.version != 4 or str(parsed) != value:
        raise ValueError('invalid_job')
    return value


def category(message):
    text = message.lower()
    for words, code in [
        (('expecting',), 'syntax'), (('unresolved reference',), 'unresolved_reference'),
        (('type mismatch', 'argument type mismatch', 'return type mismatch'), 'type_mismatch'),
        (('overload resolution ambiguity',), 'overload_ambiguity'),
        (('no value passed',), 'missing_argument'), (('cannot access',), 'access'),
        (('overrides nothing',), 'override'), (('redeclaration', 'conflicting overloads'), 'redeclaration'),
    ]:
        if any(word in text for word in words):
            return code
    return 'compiler_error'


def collect(job, base=BASE):
    identifier(job)
    work = base/'builds'/job
    log = base/'cloud-runner/logs'/job/'build.log'
    report = {'schema': 1, 'job_id': job, 'compiler': [], 'failed_tasks': [], 'tests': []}
    if log.is_file():
        if log.stat().st_size > 32 * 1024 * 1024:
            raise ValueError('log_too_large')
        for line in log.read_text(errors='replace').splitlines():
            task = re.fullmatch(r'> Task (:[A-Za-z0-9_:.-]{1,120}) FAILED', line.strip())
            if task and task[1] not in report['failed_tasks']:
                report['failed_tasks'].append(task[1])
            match = re.match(r'^e: (?:file://)?(.+?\.kt):(\d+):(\d+)\s+(.+)$', line)
            if not match:
                match = re.match(r'^e: (?:file://)?(.+?\.kt): \((\d+), (\d+)\): (.+)$', line)
            if match and 'app/src/' in match[1] and len(report['compiler']) < 100:
                relative = 'app/src/' + match[1].split('app/src/', 1)[1]
                if not re.fullmatch(r'app/src/[A-Za-z0-9_./-]{1,240}\.kt', relative):
                    continue
                report['compiler'].append({'path': relative, 'line': int(match[2]),
                                           'column': int(match[3]), 'kind': category(match[4])})
    folder = work/'app/build/test-results/testDebugUnitTest'
    for path in sorted(folder.glob('TEST-*.xml'))[:200]:
        if path.stat().st_size > 2 * 1024 * 1024:
            continue
        root = ET.parse(path).getroot()
        for case in root.iter('testcase'):
            failure = case.find('failure')
            if failure is None:
                failure = case.find('error')
            if failure is None or len(report['tests']) >= 100:
                continue
            # Names and frames are source identifiers. Assertion messages/expected values are private.
            clean = lambda s: re.sub(r'[^A-Za-z0-9_.$\[\] -]', '', s)[:180]
            frames = []
            for name, number in re.findall(r'at it\.jarviswake\.[^\n]*\(([A-Za-z0-9_]+\.kt):(\d+)\)', failure.text or ''):
                frame = {'file': name, 'line': int(number)}
                if frame not in frames and len(frames) < 4:
                    frames.append(frame)
            report['tests'].append({'class': clean(case.get('classname', '')),
                                    'name': clean(case.get('name', '')),
                                    'type': clean(failure.get('type', '')), 'frames': frames})
    return report


def git(*args, cwd=REPO):
    result = subprocess.run(['git', *map(str, args)], cwd=cwd, capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError('diagnostic_git_failed')
    return result.stdout.strip()


def publish(report):
    if git('remote', 'get-url', 'origin') not in ('https://github.com/Andry-iu/Jarvis', 'https://github.com/Andry-iu/Jarvis.git'):
        raise ValueError('wrong_repository')
    git('fetch', 'origin', 'main')
    head = git('rev-parse', 'origin/main')
    root = BASE/'cloud-runner/diagnostic-worktrees'
    root.mkdir(parents=True, exist_ok=True, mode=0o700)
    with tempfile.TemporaryDirectory(dir=root, prefix='source-diagnostic-') as directory:
        checkout = Path(directory)/'repo'
        git('worktree', 'add', '--detach', checkout, head)
        try:
            target = checkout/'releases/cloud-diagnostics'/(identifier(report['job_id'])+'.json')
            target.parent.mkdir(parents=True, exist_ok=True)
            content = json.dumps(report, indent=2, sort_keys=True)+'\n'
            if target.exists() and target.read_text() == content:
                return
            target.write_text(content)
            git('add', str(target.relative_to(checkout)), cwd=checkout)
            git('-c', 'user.name=Andrea Fabbri @ AUTECH-SM', '-c', 'user.email=andrea.fabbri@autech-sm.com',
                'commit', '-m', 'Record bounded source diagnostics for cloud job '+report['job_id'], cwd=checkout)
            git('push', 'origin', 'HEAD:main', cwd=checkout)
            if git('ls-remote', 'origin', 'refs/heads/main').split()[0] != git('rev-parse', 'HEAD', cwd=checkout):
                raise RuntimeError('diagnostic_push_unverified')
        finally:
            git('worktree', 'remove', '--force', checkout)


def main():
    request = SOURCE/'cloud-runner/diagnostic-request.json'
    if not request.is_file():
        return
    # Run only inside a managed Jarvis cloud build, never on the phone or another checkout.
    if SOURCE.parent != BASE/'builds' or not REPO.is_dir():
        print('Source diagnostics inactive outside Jarvis cloud build')
        return
    config = json.loads(request.read_text())
    if set(config) != {'job_ids'} or not isinstance(config['job_ids'], list) or not 1 <= len(config['job_ids']) <= 2:
        raise ValueError('invalid_diagnostic_request')
    for job in config['job_ids']:
        report = collect(job)
        publish(report)
        print('Bounded source diagnostic recorded for job '+identifier(job))


if __name__ == '__main__':
    main()
