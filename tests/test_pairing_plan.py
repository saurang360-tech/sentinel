import base64
import importlib.util
from pathlib import Path
import struct
import unittest

path=Path(__file__).resolve().parents[1]/'deployment/pair-status-key.py'
spec=importlib.util.spec_from_file_location('pairing',path);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
def field(x):return struct.pack('>I',len(x))+x
class PairingTest(unittest.TestCase):
    def test_generated_authorisation_restricts_source_and_command(self):
        blob=field(b'ecdsa-sha2-nistp256')+field(b'nistp256')+field(b'\x04'+b'\x01'*64)
        key='ecdsa-sha2-nistp256 '+base64.b64encode(blob).decode()+' sentinel-pixel-status'
        line=module.authorised_line(key).decode()
        self.assertTrue(line.startswith('restrict,from="100.70.104.44",command="/usr/bin/python3 -I /usr/local/libexec/sentinel-status-v1.py" '))
        self.assertEqual(line.count('\n'),1)
    def test_no_multiline_or_supplied_options_or_wrong_algorithms(self):
        for key in ['ssh-rsa AAAA x','command="bash" ecdsa-sha2-nistp256 AAAA sentinel-pixel-status','ecdsa-sha2-nistp256 AAAA sentinel-pixel-status\nssh-rsa AAAA x','ecdsa-sha2-nistp256 !!! sentinel-pixel-status']:
            with self.assertRaises((ValueError,Exception)):module.public_blob(key)
