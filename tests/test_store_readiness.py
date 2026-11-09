"""Release-gate regression tests use synthetic artifacts; no keys, PC or Play access."""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile

spec = importlib.util.spec_from_file_location('readiness', Path(__file__).parents[1] / 'tools/check_store_readiness.py')
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)
A = checker.ANDROID


class StoreReadinessTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        folder = self.root / 'release/store'
        folder.mkdir(parents=True)
        self.metadata = folder / 'publisher.json'
        self.metadata.write_text(json.dumps({'publisherName': 'Test Publisher', 'supportEmail': 'support@example.test',
                                             'privacyPolicyUrl': 'https://example.test/privacy', 'applicationId': 'dev.titus.sentinel'}))
        for path in checker.ASSETS:
            asset = self.root / 'app/src/main/assets' / path
            asset.parent.mkdir(parents=True, exist_ok=True)
            asset.write_text('Synthetic fixture support@example.test\n')
        self.manifest = self.root / 'app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml'
        self.manifest.parent.mkdir(parents=True)
        document = ET.Element('manifest', {'package': 'dev.titus.sentinel'})
        for name in checker.PERMISSIONS | {'dev.titus.sentinel.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'}:
            ET.SubElement(document, 'uses-permission', {A + 'name': name})
        app = ET.SubElement(document, 'application', {A + 'allowBackup': 'false', A + 'fullBackupContent': 'false',
                                                    A + 'debuggable': 'false', A + 'usesCleartextTraffic': 'false'})
        ET.SubElement(app, 'activity', {A + 'name': 'dev.titus.sentinel.MainActivity', A + 'exported': 'true'})
        ET.SubElement(app, 'service', {A + 'name': 'dev.titus.sentinel.monitor.TaskMonitorService',
                                     A + 'exported': 'false', A + 'foregroundServiceType': 'connectedDevice'})
        ET.ElementTree(document).write(self.manifest)
        self.bundle = self.root / 'app/build/outputs/bundle/release/app-release.aab'
        self.bundle.parent.mkdir(parents=True)
        self.bundle_fixture()

    def bundle_fixture(self, signed=True, omit=None, cache=False):
        with zipfile.ZipFile(self.bundle, 'w') as z:
            if signed:
                z.writestr('META-INF/TEST.RSA', b'presence fixture, NOT a valid cryptographic signature')
            for path in checker.ASSETS:
                if path != omit:
                    z.writestr('base/assets/' + path, (self.root / 'app/src/main/assets' / path).read_bytes())
            if cache:
                z.writestr('base/assets/__pycache__/helper.pyc', b'cache')

    def failures(self):
        return '\n'.join(checker.audit(self.root)['blockedBy'])

    def test_valid_local_shape_still_requires_manual_review(self):
        report = checker.audit(self.root)
        self.assertTrue(report['offlineChecksPassed'])
        self.assertIn('NOT_ESTABLISHED', report['submissionApproval'])
        self.assertTrue(report['additionalRequiredReview'])
        self.assertIn('signature cryptography', ' '.join(report['limitations']))
        self.assertEqual(len(report['bundleSha256']), 64)

    def test_incomplete_metadata_and_contact_fail(self):
        self.metadata.write_text('{}')
        self.assertIn('Publisher must supply supportEmail', self.failures())
        self.metadata.write_text(json.dumps({'publisherName': 'Test', 'supportEmail': 'missing@example.test',
                                             'privacyPolicyUrl': 'http://example.test/privacy', 'applicationId': 'dev.titus.sentinel'}))
        self.assertIn('public HTTPS URL', self.failures())
        self.assertIn('include the selected contact', self.failures())

    def test_changed_permissions_and_exported_service_fail(self):
        tree = ET.parse(self.manifest)
        ET.SubElement(tree.getroot(), 'uses-permission', {A + 'name': 'android.permission.ACCESS_FINE_LOCATION'})
        tree.getroot().find('application/service').set(A + 'exported', 'true')
        tree.write(self.manifest)
        failures = self.failures()
        self.assertIn('ACCESS_FINE_LOCATION', failures)
        self.assertIn('TaskMonitorService must be non-exported', failures)
        self.assertIn('unexpected exported component', failures)

    def test_profileinstaller_receiver_requires_its_permission_guard(self):
        tree = ET.parse(self.manifest)
        receiver = ET.SubElement(tree.getroot().find('application'), 'receiver',
                                 {A + 'name': 'androidx.profileinstaller.ProfileInstallReceiver',
                                  A + 'exported': 'true', A + 'permission': 'android.permission.DUMP'})
        tree.write(self.manifest)
        self.assertTrue(checker.audit(self.root)['offlineChecksPassed'])
        receiver.attrib.pop(A + 'permission')
        tree.write(self.manifest)
        self.assertIn('unexpected exported component', self.failures())

    def test_backup_debug_cleartext_and_missing_type_fail(self):
        tree = ET.parse(self.manifest)
        app = tree.getroot().find('application')
        for attribute in ('allowBackup', 'debuggable', 'usesCleartextTraffic'):
            app.set(A + attribute, 'true')
        app.find('service').attrib.pop(A + 'foregroundServiceType')
        tree.write(self.manifest)
        for message in ('backup must remain disabled', 'must not be debuggable', 'disable cleartext', 'connectedDevice type'):
            self.assertIn(message, self.failures())

    def test_stale_missing_unsigned_and_cached_assets_fail(self):
        path = self.root / 'app/src/main/assets/legal/terms.txt'
        path.write_text('Changed terms')
        self.assertIn('Rebuild bundle after changing: legal/terms.txt', self.failures())
        self.bundle_fixture(signed=False, omit='sentinel-monitor.py', cache=True)
        for message in ('no signing entry', 'Missing bundled resource: sentinel-monitor.py', 'bytecode caches'):
            self.assertIn(message, self.failures())

    def test_missing_and_malformed_inputs_report_blockers(self):
        self.metadata.write_text('[]')
        self.manifest.write_text('<bad')
        self.bundle.write_bytes(b'not a zip')
        for message in ('invalid JSON', 'manifest is malformed', 'unreadable or corrupt'):
            self.assertIn(message, self.failures())
        self.manifest.unlink()
        self.bundle.unlink()
        self.assertIn('Build the release manifest', self.failures())
        self.assertIn('Release AAB has not been built', self.failures())

    def test_exit_code_and_json_output(self):
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(checker.check(self.root), 0)
            self.metadata.unlink()
            target = self.root / 'custom/report.json'
            self.assertEqual(checker.check(self.root, target), 1)
        self.assertFalse(json.loads(target.read_text())['offlineChecksPassed'])


if __name__ == '__main__':
    unittest.main()
