import base64
import hashlib
import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch

path=Path(__file__).resolve().parents[1]/'deployment/pair-status-key.py'
spec=importlib.util.spec_from_file_location('guarded_pairing',path);p=importlib.util.module_from_spec(spec);spec.loader.exec_module(p)
def field(x):return struct.pack('>I',len(x))+x
class PairingGuardTest(unittest.TestCase):
    def test_plan_refuses_changed_auth_or_helper_collision(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);auth=root/'authorized';helper=root/'installed.py';source=root/'source.py';public=root/'public.pub';manifest=root/'manifest.json'
            original=b'original-owner-public-key\n';auth.write_bytes(original);source.write_bytes(b'# status helper\n')
            blob=field(b'ecdsa-sha2-nistp256')+field(b'nistp256')+field(b'\x04'+b'\x01'*64)
            public.write_text('ecdsa-sha2-nistp256 '+base64.b64encode(blob).decode()+' sentinel-pixel-status')
            manifest.write_text(json.dumps({'helperSha256':p.digest(source.read_bytes()),'publicBlobSha256':p.digest(blob)}))
            with patch.multiple(p,AUTH=auth,HELPER=helper,SOURCE=source,PUBLIC_KEY=public,MANIFEST=manifest,EXPECTED_AUTH=p.digest(original)):
                self.assertEqual(p.checked_plan()[1],original)
                auth.write_bytes(original+b'later-key\n')
                with self.assertRaisesRegex(RuntimeError,'changed'):p.checked_plan()
                auth.write_bytes(original);helper.write_bytes(b'existing helper')
                with self.assertRaisesRegex(RuntimeError,'already exists'):p.checked_plan()
    def test_rollback_refuses_to_overwrite_later_changes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);backups=root/'backups';backups.mkdir();backup=backups/'recorded';backup.mkdir()
            auth=root/'authorized';auth.write_bytes(b'later-edits');lock=root/'lock';lock.touch();helper=root/'helper'
            before=b'original';(backup/'authorized-keys.before').write_bytes(before)
            (backup/'state.json').write_text(json.dumps({'authBefore':p.digest(before),'authAfter':p.digest(b'deployed'),'helperSha256':p.digest(b'helper')}))
            with patch.multiple(p,AUTH=auth,HELPER=helper,BACKUPS=backups,OWNERSHIP=lock),patch.object(p.os,'geteuid',return_value=0):
                with self.assertRaisesRegex(RuntimeError,'Later authorised-key changes'):p.rollback(str(backup))
            self.assertEqual(auth.read_bytes(),b'later-edits')
