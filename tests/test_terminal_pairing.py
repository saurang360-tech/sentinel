import importlib.util
import base64
import struct
from pathlib import Path
import tempfile
import json
import unittest
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('pair_terminal',Path(__file__).parents[1]/'deployment/pair-terminal-key.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class TerminalPairingTest(unittest.TestCase):
    def key(self):
        # Synthetic parser fixture; never depend on the owner's paired phone key.
        def field(value): return struct.pack('>I', len(value)) + value
        blob = field(b'ecdsa-sha2-nistp256') + field(b'nistp256') + field(b'\x04' + b'\x01' * 64)
        return 'ecdsa-sha2-nistp256 ' + base64.b64encode(blob).decode() + ' sentinel-terminal'
    def test_separate_phone_restricted_interactive_key_has_no_forwarding_or_sudo(self):
        line=m.line_for(self.key()).decode()
        self.assertTrue(line.startswith('restrict,pty,from="100.70.104.44",command="/usr/bin/bash -l" '))
        self.assertNotIn('sudo',line);self.assertNotIn('port-forwarding',line)
        self.assertTrue(line.endswith(' sentinel-terminal-v1\n'))
    def test_rejects_multiline_options_and_non_terminal_keys(self):
        for value in [self.key()+'\n'+self.key(),'command="id" '+self.key(),self.key().replace('sentinel-terminal','other-key')]:
            with self.assertRaises(ValueError):m.line_for(value)
    def test_plan_preserves_original_keys_and_refuses_changed_baselines(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);auth=root/'auth';helper=root/'helper';pub=root/'pub';manifest=root/'manifest'
            auth.write_bytes(b'existing-key\n');helper.write_bytes(b'existing-helper');pub.write_text(self.key())
            manifest.write_text(json.dumps({'authorisedKeysBeforeSha256':m.digest(auth.read_bytes()),'statusHelperSha256':m.digest(helper.read_bytes()),'publicBlobSha256':m.digest(m.public_blob(self.key())[1])}))
            with patch.multiple(m,AUTH=auth,HELPER=helper,PUBLIC=pub,MANIFEST=manifest):
                before,after,_=m.checked();self.assertTrue(after.startswith(before))
                auth.write_bytes(b'unrelated-key-change')
                with self.assertRaisesRegex(RuntimeError,'Authorised keys changed'):m.checked()
                auth.write_bytes(before);helper.write_bytes(b'changed-helper')
                with self.assertRaisesRegex(RuntimeError,'Monitoring helper changed'):m.checked()
