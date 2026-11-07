import hashlib
import json
import io
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import runner


class FakeClient:
    def __init__(self, replies=None):
        self.calls = []
        self.replies = replies or {}

    def request(self, route, body=None, **kwargs):
        self.calls.append((route, body, kwargs))
        value = self.replies.get(route, {})
        if isinstance(value, Exception):
            raise value
        return value


class RunnerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.state = self.root/'state.json'
        self.identity = {'owner': 'a'*64, 'job_id': str(uuid.uuid4()), 'claim_id': str(uuid.uuid4())}

    def tearDown(self):
        self.temp.cleanup()

    def make(self, client=None, value=None):
        if value:
            runner.private_json(self.state, {**self.identity, **value})
        return runner.Runner(client or FakeClient(), self.state, self.root, self.root)

    def test_private_journal_atomic_and_strict_identity(self):
        r = self.make(value={'status': 'running'})
        self.assertEqual(self.state.stat().st_mode & 0o777, 0o600)
        runner.validate_identity(json.loads(self.state.read_text()))
        for key in ('job_id', 'claim_id', 'owner'):
            with self.assertRaises(ValueError):
                runner.validate_identity({**self.identity, key: '../../private'})
        self.assertFalse(self.state.with_suffix('.tmp').exists())

    def test_restart_running_never_executes_build(self):
        r = self.make(value={'status': 'running'})
        with patch.object(r, 'execute', side_effect=AssertionError('must not execute')):
            r.step()
        self.assertEqual(r.client.calls[0][0], '/cloud/runner/finish')
        self.assertEqual(r.client.calls[0][1]['state'], 'outcome_uncertain')
        self.assertEqual(json.loads(self.state.read_text())['status'], 'done')

    def test_failed_finish_is_durable_and_retryable_without_execute(self):
        client = FakeClient({'/cloud/runner/finish': OSError('offline')})
        r = self.make(client, {'status': 'running'})
        with self.assertRaises(OSError):
            r.step()
        self.assertEqual(json.loads(self.state.read_text())['status'], 'finish_ready')
        client.replies = {}
        with patch.object(r, 'execute', side_effect=AssertionError('must not execute')):
            r.step()
        self.assertEqual(json.loads(self.state.read_text())['status'], 'done')

    def test_missing_local_active_state_does_not_steal_claim(self):
        client = FakeClient({'/cloud/runner/poll': {'active': self.identity}})
        r = self.make(client)
        with self.assertRaisesRegex(runner.JobError, 'runner_state_missing'):
            r.step()
        self.assertEqual([c[0] for c in client.calls], ['/cloud/runner/poll'])

    def test_claim_ack_loss_reuses_previously_persisted_uuid(self):
        next_job = {k: self.identity[k] for k in ('owner', 'job_id')}
        client = FakeClient({'/cloud/runner/poll': {'next': next_job}, '/cloud/runner/claim': OSError('offline')})
        r = self.make(client)
        with self.assertRaises(OSError):
            r.step()
        journal = json.loads(self.state.read_text())
        claim_id = journal['claim_id']
        self.assertEqual(journal['status'], 'claim_ready')
        client.replies = {'/cloud/runner/poll': {'active': {**next_job, 'claim_id': claim_id}},
                          '/cloud/runner/claim': {'job': {'operation': 'verify', 'revision': 'main'}}}
        with patch.object(r, 'execute') as execute:
            r.step()
            execute.assert_called_once()
        self.assertEqual(client.calls[-1][1]['claim_id'], claim_id)

    def test_upload_resume_retries_only_immutable_artifact(self):
        apk = self.root/'sample.apk'
        apk.write_bytes(b'fake APK')
        r = self.make(value={'status': 'upload_ready', 'apk': str(apk),
                             'artifact': {'sha256': hashlib.sha256(apk.read_bytes()).hexdigest()}})
        with patch.object(r, 'execute', side_effect=AssertionError('must not rebuild')):
            r.step()
        self.assertEqual([c[0] for c in r.client.calls], ['/cloud/runner/artifact', '/cloud/runner/finish'])
        self.assertEqual(r.client.calls[-1][1]['state'], 'succeeded')

    def test_corrupt_local_artifact_never_uploaded(self):
        apk = self.root/'sample.apk'; apk.write_bytes(b'bad')
        r = self.make(value={'status': 'upload_ready', 'apk': str(apk), 'artifact': {'sha256': '0'*64}})
        with self.assertRaisesRegex(runner.JobError, 'upload_failed'):
            r.step()
        self.assertEqual(r.client.calls, [])

    def test_child_environment_excludes_bridge_and_signing_secrets(self):
        with patch.dict(os.environ, {'Passphrase': 'test-private', 'CLOUD_RUNNER_TOKEN': 'secret',
                                     'SOME_API_KEY': 'secret', 'JARVIS_SIGNING_PASSWORD': 'secret'}):
            env = runner.child_environment()
        self.assertTrue(all(k not in env for k in ('Passphrase', 'CLOUD_RUNNER_TOKEN', 'SOME_API_KEY', 'JARVIS_SIGNING_PASSWORD')))
        self.assertEqual(env['GRADLE_USER_HOME'], '/workspace/.jarvis-cloud/gradle')

    def test_client_refuses_unexpected_destination(self):
        with self.assertRaises(ValueError):
            runner.Client({'origin': 'https://attacker.example'})

    def test_client_preserves_scoped_auth_and_streams_apk(self):
        client = runner.Client({'origin': runner.ORIGIN, 'runner_token': 'private-runner', 'site_access': 'private-site'})
        seen = []
        def open_request(req, **kwargs):
            seen.append(req)
            if req.method == 'PUT':
                self.assertTrue(hasattr(req.data, 'read'))
                self.assertEqual(req.data.read(), b'fake APK')
                self.assertEqual(req.get_header('Content-length'), '8')
            return io.BytesIO(b'{"state":"ok"}')
        apk = self.root/'test.apk'; apk.write_bytes(b'fake APK')
        with patch.object(client.opener, 'open', side_effect=open_request):
            client.request('/cloud/runner/poll')
            client.request('/cloud/runner/artifact', apk=apk, identity=self.identity)
        self.assertTrue(all(r.full_url.startswith(runner.ORIGIN+'/cloud/runner/') for r in seen))
        self.assertEqual(seen[0].get_header('Authorization'), 'Bearer private-runner')
        self.assertEqual(seen[0].get_header('Oai-sites-authorization'), 'Bearer private-site')
        with self.assertRaises(runner.urllib.error.URLError):
            runner.NoRedirect().redirect_request(None, None, 302, '', {}, 'https://other.example')

    def test_simulated_signed_pipeline_orders_verification_record_upload_finish(self):
        next_job = {k: self.identity[k] for k in ('owner', 'job_id')}
        client = FakeClient({'/cloud/runner/poll': {'next': next_job},
                             '/cloud/runner/claim': {'job': {'operation': 'build_signed', 'revision': 'main'}}})
        r = self.make(client)
        work = self.root/'work'; work.mkdir()
        apk = self.root/'signed.apk'; apk.write_bytes(b'fake APK')
        artifact = {'sha256': hashlib.sha256(apk.read_bytes()).hexdigest()}
        events = []
        def prepare(job):
            events.append('fetch'); r.current['resolved_commit'] = 'b'*40; r.save(); return work
        def record(path):
            events.append('recording'); r.current['release_commit'] = 'c'*40; r.save()
        with patch.object(r, 'prepare', side_effect=prepare), \
             patch.object(r, 'checks', side_effect=lambda _: events.append('checks')), \
             patch.object(r, 'android', side_effect=lambda *_: events.append('android')), \
             patch.object(r, 'record', side_effect=record), \
             patch('signing.sign', return_value=(apk, artifact)):
            r.step()
        self.assertEqual(events, ['fetch', 'checks', 'android', 'recording'])
        stages = [body['stage'] for route, body, _ in client.calls if route.endswith('/progress')]
        self.assertEqual(stages, ['fetch', 'checks', 'android', 'signing', 'recording', 'upload'])
        upload_index = next(i for i, c in enumerate(client.calls) if c[0].endswith('/artifact'))
        self.assertEqual(client.calls[upload_index-1][1]['release_commit'], 'c'*40)
        self.assertEqual(client.calls[-1][1]['state'], 'succeeded')
        self.assertEqual(json.loads(self.state.read_text())['status'], 'done')

    def test_revision_is_pinned_before_worktree_creation(self):
        r = self.make()
        r.current = {**self.identity, 'status': 'running'}
        commands = []
        with patch.object(r, 'git', side_effect=['https://github.com/Andry-iu/Jarvis.git', 'b'*40]), \
             patch.object(r, 'command', side_effect=lambda args, *_: commands.append(args)):
            work = r.prepare({'operation': 'build_signed', 'revision': 'main'})
        self.assertEqual(json.loads(self.state.read_text())['resolved_commit'], 'b'*40)
        self.assertEqual(commands[1][0:3], ['git', 'merge-base', '--is-ancestor'])
        self.assertEqual(commands[2][-1], 'b'*40)
        self.assertEqual(work, self.root/'builds'/self.identity['job_id'])

    def test_unknown_branch_and_outside_main_never_build(self):
        r = self.make(); r.current = self.identity
        for revision in ('main;id', 'other', '-c', '../keys'):
            with self.assertRaisesRegex(runner.JobError, 'revision_not_on_main'):
                r.prepare({'operation': 'verify', 'revision': revision})
        with patch.object(r, 'git', side_effect=['https://github.com/Andry-iu/Jarvis.git', 'b'*40]), \
             patch.object(r, 'command', side_effect=[None, runner.JobError('revision_not_on_main')]):
            with self.assertRaisesRegex(runner.JobError, 'revision_not_on_main'):
                r.prepare({'operation': 'verify', 'revision': 'b'*40})
        self.assertFalse((self.root/'builds').exists())

    def test_fixed_failure_is_reported_without_raw_log(self):
        r = self.make(); r.current = {**self.identity, 'status': 'claim_ready'}
        with patch.object(r, 'progress'), patch.object(r, 'prepare', side_effect=runner.JobError('fetch_failed')):
            r.execute({'operation': 'verify', 'revision': 'main'})
        body = r.client.calls[-1][1]
        self.assertEqual(body['state'], 'failed')
        self.assertEqual(body['error'], 'fetch_failed')
        self.assertEqual(set(body), {'owner', 'job_id', 'claim_id', 'state', 'error'})

    def test_upload_network_loss_preserves_resume_point(self):
        r = self.make(); r.current = {**self.identity, 'status': 'upload_ready'}; r.save()
        with patch.object(r, 'upload', side_effect=OSError('offline')):
            with self.assertRaises(OSError):
                r.step()
        self.assertEqual(json.loads(self.state.read_text())['status'], 'upload_ready')

    def test_xml_counts_are_exposed_only_when_real_reports_exist(self):
        tests = self.root/'app/build/test-results/testDebugUnitTest'
        lint = self.root/'app/build/reports'
        tests.mkdir(parents=True); lint.mkdir(parents=True)
        (tests/'TEST-example.xml').write_text('<testsuite tests="7" failures="1" errors="1" skipped="2"/>')
        (lint/'lint-results-debug.xml').write_text('<issues><issue severity="Error"/><issue severity="Warning"/></issues>')
        self.assertEqual(runner.read_android_counts(self.root), {'android_tests': 7, 'android_failures': 2,
                          'android_skipped': 2, 'lint_errors': 1, 'lint_warnings': 1})


if __name__ == '__main__':
    unittest.main()
