import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import unittest
from unittest.mock import patch

HELPER = Path(__file__).resolve().parents[1] / 'deployment/sentinel-status-v1.py'
spec = importlib.util.spec_from_file_location('helper', HELPER)
helper = importlib.util.module_from_spec(spec); spec.loader.exec_module(helper)
class HelperTest(unittest.TestCase):
    def test_rejects_shell_and_unknown_requests(self):
        for command in ['', 'bash', 'sentinel-status-v1; id', 'sudo poweroff', 'sentinel-status-v1 --anything', 'sentinel-processes-v1 all;id', 'sentinel-processes-v1 root', 'sentinel-processes-v1 Amus extra']:
            result = subprocess.run([sys.executable, '-I', str(HELPER)], env={'SSH_ORIGINAL_COMMAND': command}, capture_output=True)
            self.assertEqual(result.returncode,64)
            self.assertEqual(result.stdout,b'')
    def test_live_read_only_schema(self):
        data=helper.collect()
        self.assertEqual(data['protocol'],1)
        self.assertGreater(data['uptimeSeconds'],0)
        self.assertIn(data['systemState'], helper.ALLOWED_STATES)
        self.assertEqual(set(data),{'protocol','host','bootId','uptimeSeconds','systemState','sampledAt','users','capabilities'})
    def test_systemd_timeout_is_explicit_unknown(self):
        with patch.object(helper.subprocess,'run',side_effect=subprocess.TimeoutExpired('systemctl',2)):
            self.assertEqual(helper.collect()['systemState'],'unknown')
