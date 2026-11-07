#!/usr/bin/python3
"""Scoped pairing transaction. Default is a read-only plan. --apply needs review/root."""
import argparse
import base64
import datetime
import fcntl
import hashlib
import json
import os
from pathlib import Path
import stat
import struct
import tempfile

ROOT = Path(__file__).resolve().parent
AUTH = Path('/etc/ssh/compute-authorized-keys/Amus')
HELPER = Path('/usr/local/libexec/sentinel-status-v1.py')
BACKUPS = Path('/var/lib/sentinel-status-pairing')
OWNERSHIP = Path('/home/Amus/Documents/ChatGPT/ricing_linux/runner/ownership.lock')
SOURCE = ROOT / 'sentinel-status-v1.py'
PUBLIC_KEY = ROOT / 'phone-public-key.pub'
MANIFEST = ROOT / 'pairing-manifest.json'
EXPECTED_AUTH = 'c2afd8e49f43f2acc5837178b38170b864130879dfa53d4d7797eb7f3949a3c9'

def digest(data): return hashlib.sha256(data).hexdigest()
def plain(path):
    if path.is_symlink() or not path.is_file(): raise RuntimeError(f'Expected a regular non-symlink file: {path}')
    return path.read_bytes()
def public_blob(text):
    fields = text.strip().split()
    if len(fields) != 3 or fields[0] != 'ecdsa-sha2-nistp256' or fields[2] != 'sentinel-pixel-status':
        raise ValueError('Expected only the Sentinel public key exported by the app')
    blob = base64.b64decode(fields[1],validate=True)
    offset=0; parts=[]
    while offset < len(blob):
        if offset+4 > len(blob): raise ValueError('Invalid public key')
        length=struct.unpack('>I',blob[offset:offset+4])[0]; offset+=4
        if length > 1024 or offset+length > len(blob): raise ValueError('Invalid public key')
        parts.append(blob[offset:offset+length]); offset+=length
    if len(parts)!=3 or parts[:2]!=[b'ecdsa-sha2-nistp256',b'nistp256'] or len(parts[2])!=65 or parts[2][0]!=4:
        raise ValueError('Expected a P-256 SSH public key')
    return fields[1], blob

def authorised_line(key):
    encoded,_=public_blob(key)
    return (f'restrict,from="100.70.104.44",command="/usr/bin/python3 -I {HELPER}" '
            f'ecdsa-sha2-nistp256 {encoded} sentinel-pixel-status-v1\n').encode()

def checked_plan():
    manifest=json.loads(plain(MANIFEST))
    key=plain(PUBLIC_KEY).decode('ascii'); _,blob=public_blob(key)
    source=plain(SOURCE)
    if digest(source)!=manifest['helperSha256'] or digest(blob)!=manifest['publicBlobSha256']:
        raise RuntimeError('Staged helper/public key differs from the review manifest')
    before=plain(AUTH)
    if digest(before)!=EXPECTED_AUTH: raise RuntimeError('Existing authorised keys changed; review again before pairing')
    if HELPER.exists() or HELPER.is_symlink(): raise RuntimeError('Helper path already exists; refusing to overwrite it')
    if key.split()[1].encode() in before: raise RuntimeError('This public key is already present; review existing options')
    return source,before,authorised_line(key),manifest

def atomic_replace(path,data,mode,uid=0,gid=0,selinux=None):
    fd,temporary=tempfile.mkstemp(prefix='.sentinel-',dir=path.parent)
    try:
        with os.fdopen(fd,'wb') as out:
            out.write(data);out.flush();os.fsync(out.fileno())
            os.fchmod(out.fileno(),mode);os.fchown(out.fileno(),uid,gid)
        if selinux is not None: os.setxattr(temporary,'security.selinux',selinux)
        os.replace(temporary,path)
    finally:
        if os.path.exists(temporary):os.unlink(temporary)

def apply():
    if os.geteuid()!=0: raise RuntimeError('--apply requires the reviewed root invocation')
    # This lock coordinates the existing desktop/remote-access project. Do not run
    # its historical installers or restart services as part of this transaction.
    lockfd=os.open(OWNERSHIP,os.O_RDWR|os.O_NOFOLLOW)
    try:
        fcntl.flock(lockfd,fcntl.LOCK_EX|fcntl.LOCK_NB)
        source,before,line,manifest=checked_plan()
        for parent in [AUTH.parent,Path('/usr/local'),HELPER.parent] :
            if parent.exists() and (parent.is_symlink() or parent.stat().st_uid!=0 or stat.S_IMODE(parent.stat().st_mode)&0o022):
                raise RuntimeError(f'Unsafe target parent directory: {parent}')
        metadata=AUTH.stat(); context=os.getxattr(AUTH,'security.selinux')
        after=before+(b'' if before.endswith(b'\n') else b'\n')+line
        if BACKUPS.is_symlink():raise RuntimeError('Unsafe backup directory')
        BACKUPS.mkdir(mode=0o700,exist_ok=True)
        if BACKUPS.stat().st_uid!=0 or stat.S_IMODE(BACKUPS.stat().st_mode)!=0o700:raise RuntimeError('Backup directory must be root:root 0700')
        backup=BACKUPS/datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
        backup.mkdir(mode=0o700)
        (backup/'authorized-keys.before').write_bytes(before);(backup/'authorized-keys.before').chmod(0o600)
        state={'authBefore':digest(before),'authAfter':digest(after),'helperSha256':digest(source),
               'uid':metadata.st_uid,'gid':metadata.st_gid,'mode':stat.S_IMODE(metadata.st_mode),
               'selinux':base64.b64encode(context).decode(),'createdLibexec':not HELPER.parent.exists()}
        (backup/'state.json').write_text(json.dumps(state,indent=2));(backup/'state.json').chmod(0o600)
        HELPER.parent.mkdir(mode=0o755,exist_ok=True)
        # Never follow a colliding helper. O_EXCL protects this new path.
        fd=os.open(HELPER,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o644)
        with os.fdopen(fd,'wb') as out:
            os.fchmod(out.fileno(),0o644);os.fchown(out.fileno(),0,0)
            out.write(source);out.flush();os.fsync(out.fileno())
        try:
            if digest(plain(AUTH))!=state['authBefore']:raise RuntimeError('Authorised keys changed during staging')
            atomic_replace(AUTH,after,state['mode'],state['uid'],state['gid'],context)
        except Exception:
            if HELPER.exists() and digest(plain(HELPER))==state['helperSha256']:HELPER.unlink()
            raise
        print(json.dumps({'installed':str(HELPER),'authorisedKeys':str(AUTH),'backup':str(backup),'publicKeyFingerprint':manifest['publicKeyFingerprint']},indent=2))
    finally:os.close(lockfd)

def rollback(backup):
    if os.geteuid()!=0:raise RuntimeError('Rollback requires root')
    backup=Path(backup)
    if backup.parent!=BACKUPS or backup.is_symlink():raise RuntimeError('Expected one recorded backup directory')
    lockfd=os.open(OWNERSHIP,os.O_RDWR|os.O_NOFOLLOW)
    try:
        fcntl.flock(lockfd,fcntl.LOCK_EX|fcntl.LOCK_NB)
        state=json.loads(plain(backup/'state.json'));before=plain(backup/'authorized-keys.before')
        if digest(before)!=state['authBefore']:raise RuntimeError('Backup checksum mismatch')
        current=digest(plain(AUTH))
        if current not in (state['authBefore'],state['authAfter']):raise RuntimeError('Later authorised-key changes detected; refuse to overwrite')
        if HELPER.exists() and digest(plain(HELPER))!=state['helperSha256']:raise RuntimeError('Later helper changes detected; refuse rollback')
        if current==state['authAfter']:
            atomic_replace(AUTH,before,state['mode'],state['uid'],state['gid'],base64.b64decode(state['selinux']))
        if HELPER.exists():HELPER.unlink()
        if state['createdLibexec']:
            try:HELPER.parent.rmdir()
            except OSError:pass
        print('Scoped rollback completed; no services restarted.')
    finally:os.close(lockfd)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    actions=parser.add_mutually_exclusive_group();actions.add_argument('--apply',action='store_true');actions.add_argument('--rollback',metavar='BACKUP_DIRECTORY')
    args=parser.parse_args()
    if args.apply:apply()
    elif args.rollback:rollback(args.rollback)
    else:
        source,before,line,manifest=checked_plan()
        print(json.dumps({'helper':str(HELPER),'authorisedKeys':str(AUTH),'existingAuthSha256':digest(before),'append':line.decode().strip(),'publicKeyFingerprint':manifest['publicKeyFingerprint'],'backupRoot':str(BACKUPS),'applied':False},indent=2))
