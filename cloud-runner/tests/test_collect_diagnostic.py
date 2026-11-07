import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('collect_diagnostic', Path(__file__).parents[1]/'collect_diagnostic.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class DiagnosticTest(unittest.TestCase):
    def test_only_source_locations_and_categories_survive_compiler_log(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory);job = '63f21d9a-acb8-4cdb-8f9f-7888d7c3bb1d'
            log = root/'cloud-runner/logs'/job/'build.log';log.parent.mkdir(parents=True)
            log.write_text('secret=do-not-publish\ne: file:///cloud/app/src/test/java/it/jarviswake/Test.kt:35:9 Unresolved reference private-value\n> Task :app:compileDebugUnitTestKotlin FAILED\n')
            report = module.collect(job, root)
            self.assertEqual(report['compiler'], [{'path':'app/src/test/java/it/jarviswake/Test.kt','line':35,'column':9,'kind':'unresolved_reference'}])
            self.assertEqual(report['failed_tasks'], [':app:compileDebugUnitTestKotlin'])
            self.assertNotIn('private-value', str(report));self.assertNotIn('secret', str(report))

    def test_failure_messages_and_system_output_are_never_published(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory);job = '63f21d9a-acb8-4cdb-8f9f-7888d7c3bb1d'
            xml = root/'builds'/job/'app/build/test-results/testDebugUnitTest/TEST-example.xml';xml.parent.mkdir(parents=True)
            xml.write_text('<testsuite><testcase classname="it.jarviswake.Example" name="case[34]"><failure type="java.lang.AssertionError" message="private-address">private-number\nat it.jarviswake.Example.case(Example.kt:45)</failure></testcase><system-out>private-token</system-out></testsuite>')
            report = module.collect(job, root)
            self.assertEqual(report['tests'][0]['frames'], [{'file':'Example.kt','line':45}])
            self.assertNotIn('private-', str(report))

    def test_job_id_cannot_select_arbitrary_files(self):
        for value in ['../secrets', 'not-a-job', '63F21D9A-acb8-4cdb-8f9f-7888d7c3bb1d']:
            with self.assertRaises(ValueError):module.identifier(value)
