#!/usr/bin/python3
"""Guarded helper-only update. Default prints a plan; no SSH configuration is edited."""
import argparse
import base64
import datetime
import fcntl
import hashlib
import json
import os
from pathlib import Path
import stat
import tempfile

ROOT=Path(__file__).resolve().parent
SOURCE=ROOT/'sentinel-status-v1.py'
TARGET=Path('/usr/local/libexec/sentinel-status-v1.py')
AUTH=Path('/etc/ssh/compute-authorized-keys/Amus')
BACKUPS=Path('/var/lib/sentinel-status-pairing')
LOCK=Path('/home/Amus/Documents/ChatGPT/ricing_linux/runner/ownership.lock')
EXPECTED='948e46f1686e9faab3b21da692bcc3b1bb31f8551b9e5867af95eed0769bf911'
MANIFEST=ROOT/'process-manifest.json'
def digest(data):return hashlib.sha256(data).hexdigest()
def plain(path):
    if path.is_symlink() or not path.is_file():raise RuntimeError(f'Expected non-symlink file: {path}')
    return path.read_bytes()
def checked():
    before=plain(TARGET);after=plain(SOURCE)
    manifest=json.loads(plain(MANIFEST))
    if digest(before)!=EXPECTED:raise RuntimeError('Installed helper differs from the reviewed baseline')
    if digest(after)!=manifest['newHelperSha256']:raise RuntimeError('Staged helper changed after review')
    if digest(plain(AUTH))!=manifest['authorisedKeysSha256']:raise RuntimeError('Authorised keys changed; recheck existing access before deployment')
    return before,after,manifest

def replace(data,mode,uid,gid,context):
    fd,temp=tempfile.mkstemp(prefix='.sentinel-process-',dir=TARGET.parent)
    try:
        with os.fdopen(fd,'wb') as out:
            out.write(data);out.flush();os.fsync(out.fileno());os.fchmod(out.fileno(),mode);os.fchown(out.fileno(),uid,gid)
        os.setxattr(temp,'security.selinux',context)
        os.replace(temp,TARGET)
    finally:
        if os.path.exists(temp):os.unlink(temp)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    group=parser.add_mutually_exclusive_group();group.add_argument('--apply',action='store_true');group.add_argument('--rollback')
    args=parser.parse_args()
    if not args.apply and not args.rollback:
        before,after,manifest=checked()
        print(json.dumps({'target':str(TARGET),'oldSha256':digest(before),'newSha256':digest(after),'authorisedKeysUnchanged':True,
            'allowedRequests':['sentinel-status-v1','sentinel-processes-v1 all','sentinel-processes-v1 Amus','sentinel-processes-v1 skyler'],
            'backups':str(BACKUPS),'applied':False},indent=2));return
    if os.geteuid()!=0:raise RuntimeError('Reviewed update/rollback needs administrator authentication')
    fd=os.open(LOCK,os.O_RDWR|os.O_NOFOLLOW)
    try:
        fcntl.flock(fd,fcntl.LOCK_EX|fcntl.LOCK_NB)
        if args.rollback:
            backup=Path(args.rollback)
            if backup.parent!=BACKUPS or backup.is_symlink():raise RuntimeError('Invalid recorded backup directory')
            state=json.loads(plain(backup/'process-state.json'));before=plain(backup/'helper.before')
            if digest(plain(TARGET))!=state['after'] or digest(before)!=state['before']:raise RuntimeError('Later helper edits or changed backup detected; refusing rollback')
            replace(before,state['mode'],state['uid'],state['gid'],base64.b64decode(state['context']))
            print('Restored only the previous status helper. Authorised keys and services untouched.')
        else:
            before,after,manifest=checked();metadata=TARGET.stat();context=os.getxattr(TARGET,'security.selinux')
            if metadata.st_uid!=0 or stat.S_IMODE(metadata.st_mode)&0o022:raise RuntimeError('Expected a root-owned, non-writable helper')
            if BACKUPS.is_symlink() or BACKUPS.stat().st_uid!=0 or stat.S_IMODE(BACKUPS.stat().st_mode)!=0o700:raise RuntimeError('Unsafe backup directory')
            backup=BACKUPS/('processes-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ'))
            backup.mkdir(mode=0o700)
            (backup/'helper.before').write_bytes(before);(backup/'helper.before').chmod(0o600)
            state={'before':digest(before),'after':digest(after),'mode':stat.S_IMODE(metadata.st_mode),'uid':metadata.st_uid,'gid':metadata.st_gid,'context':base64.b64encode(context).decode()}
            (backup/'process-state.json').write_text(json.dumps(state,indent=2));(backup/'process-state.json').chmod(0o600)
            if digest(plain(TARGET))!=state['before']:raise RuntimeError('Helper changed during staging')
            replace(after,state['mode'],state['uid'],state['gid'],context)
            if digest(plain(AUTH))!=manifest['authorisedKeysSha256']:raise RuntimeError('Concurrent unrelated authorised-key change detected; investigate without overwriting it')
            print(json.dumps({'updated':str(TARGET),'backup':str(backup),'helperSha256':digest(after),'authorisedKeysUnchanged':True},indent=2))
    finally:os.close(fd)
if __name__=='__main__':main()
