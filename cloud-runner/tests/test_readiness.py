import os
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import readiness


class ReadinessTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.base = self.root/'cloud'
        self.repo = self.root/'repo'
        for name in ('jdk-17.0.21+9/bin/java', 'go-1.27.3/bin/go',
                     'go-bin/gomobile', 'go-bin/gobind',
                     'android-sdk/ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/bin/clang',
                     'android-sdk/build-tools/36.0.0/aapt2',
                     'android-sdk/build-tools/36.0.0/zipalign',
                     'android-sdk/build-tools/36.0.0/apksigner',
                     'gradle/wrapper/dists/gradle-8.13-bin/hash/gradle-8.13/bin/gradle'):
            p = self.base/name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text('fixture')
            p.chmod(0o700)
        for p, text in ((self.base/'android-sdk/ndk/28.2.13676358/source.properties', 'Pkg.Revision = 28.2.13676358'),
                        (self.base/'android-sdk/platforms/android-36/android.jar', ''),
                        (self.repo/'app/src/main/assets/model/.source-verified', '')):
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(text)

    def tearDown(self):
        self.temp.cleanup()

    def run_tool(self, args, **kwargs):
        # None of the version/import probes may receive credentials.
        self.assertNotIn('Passphrase', kwargs['env'])
        self.assertNotIn('JARVIS_SITE_ACCESS', kwargs['env'])
        if '-version' in args:
            text = 'openjdk version "21.0.9"' if 'java-21' in args[0] else 'openjdk version "17.0.21"'
        elif args[1:] == ['version']:
            text = 'go version go1.27.3 linux/amd64'
        elif '-m' in args:
            text = readiness.MOBILE_VERSION
        else:
            text = 'ready'
        return SimpleNamespace(returncode=0, stdout=text, stderr='')

    def check(self):
        return readiness.problems(self.base, self.repo,
                                  {'Passphrase': 'fixture-secret', 'JARVIS_SITE_ACCESS': 'fixture-access'})

    def test_complete_pinned_toolchain_is_ready_without_building(self):
        with patch('readiness.subprocess.run', side_effect=self.run_tool) as run:
            self.assertEqual(self.check(), [])
        self.assertFalse(any('assemble' in str(c) for c in run.call_args_list))

    def test_missing_go_never_falls_back_to_system_go(self):
        (self.base/'go-1.27.3/bin/go').unlink()
        with patch('readiness.subprocess.run', side_effect=self.run_tool) as run:
            self.assertIn('go_missing', self.check())
        self.assertFalse(any(Path(c.args[0][0]).name == 'go' for c in run.call_args_list))

    def test_wrong_go_binary_is_not_ready(self):
        def wrong_go(args, **kwargs):
            if args[1:] == ['version']:
                return SimpleNamespace(returncode=0, stdout='Go: Unknown option: version', stderr='')
            return self.run_tool(args, **kwargs)
        with patch('readiness.subprocess.run', side_effect=wrong_go):
            self.assertIn('go_version_invalid', self.check())

    def test_partial_ndk_directory_is_not_ready(self):
        (self.base/'android-sdk/ndk/28.2.13676358/source.properties').unlink()
        with patch('readiness.subprocess.run', side_effect=self.run_tool):
            self.assertIn('ndk_missing', self.check())

    def test_signing_secret_missing_is_reported_by_name_only(self):
        with patch('readiness.subprocess.run', side_effect=self.run_tool):
            self.assertIn('signing_passphrase_unavailable', readiness.problems(self.base, self.repo, {}))

    def test_failed_probe_does_not_claim_readiness(self):
        with patch('readiness.subprocess.run', side_effect=OSError('unavailable')):
            missing = self.check()
        self.assertIn('go_version_invalid', missing)
        self.assertIn('signing_python_missing', missing)


if __name__ == '__main__':
    unittest.main()
