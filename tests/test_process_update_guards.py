import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
path=Path(__file__).resolve().parents[1]/'deployment/update-process-helper.py'
spec=importlib.util.spec_from_file_location('update',path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class UpdateGuards(unittest.TestCase):
    def test_existing_helper_and_key_file_must_match_review(self):
        with tempfile.TemporaryDirectory() as tmp:
            d=Path(tmp);target=d/'installed';source=d/'source';auth=d/'auth';manifest=d/'manifest'
            target.write_bytes(b'old');source.write_bytes(b'new');auth.write_bytes(b'keys')
            manifest.write_text(json.dumps({'newHelperSha256':m.digest(b'new'),'authorisedKeysSha256':m.digest(b'keys')}))
            with patch.multiple(m,TARGET=target,SOURCE=source,AUTH=auth,MANIFEST=manifest,EXPECTED=m.digest(b'old')):
                self.assertEqual(m.checked()[:2],(b'old',b'new'))
                target.write_bytes(b'other edit')
                with self.assertRaisesRegex(RuntimeError,'baseline'):m.checked()
                target.write_bytes(b'old');auth.write_bytes(b'new key')
                with self.assertRaisesRegex(RuntimeError,'Authorised keys changed'):m.checked()
