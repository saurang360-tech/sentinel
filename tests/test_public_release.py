"""Public helper is reviewed separately from historical personal deployment scripts."""
import importlib.util
from pathlib import Path
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
HELPER = ROOT / 'app/src/main/assets/sentinel-monitor.py'
spec = importlib.util.spec_from_file_location('public_helper', HELPER)
helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper)


class PublicReleaseTest(unittest.TestCase):
    def test_public_helper_rejects_legacy_personal_and_shell_requests(self):
        for command in ['', 'bash', 'sentinel-processes-v1 Amus',
                        'sentinel-processes-v1 skyler', 'sentinel-status-v1; id',
                        'sentinel-processes-v2 all ram user; id', 'sudo poweroff']:
            result = subprocess.run([sys.executable, '-I', str(HELPER)],
                                    env={'SSH_ORIGINAL_COMMAND': command}, capture_output=True)
            self.assertEqual(result.returncode, 64)
            self.assertEqual(result.stdout, b'')

    def test_public_helper_advertises_generic_discovery_and_sorting(self):
        status = helper.collect()
        self.assertEqual(status['capabilities'], {'processes': 2, 'users': 1})
        self.assertEqual(helper.parse_process_request('sentinel-processes-v2 1001 ram user'),
                         (1001, 'ram', 'user'))
        source = HELPER.read_text()
        for personal in ['Amus', 'skyler', '/mnt/titus-hdd', '100.75.247.115', '100.70.104.44']:
            self.assertNotIn(personal, source)

    def test_public_android_sources_have_no_personal_connection_fallback(self):
        source = '\n'.join(p.read_text() for p in (ROOT / 'app/src/main/java').rglob('*.kt'))
        for personal in ['TITUS_PIN', '100.75.247.115', '100.70.104.44', '"Amus"', '"skyler"']:
            self.assertNotIn(personal, source)


if __name__ == '__main__':
    unittest.main()
