#!/usr/bin/python3
"""Reviewed, key-only terminal pairing; default is read-only planning."""
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
ROOT=Path(__file__).resolve().parent
AUTH=Path('/etc/ssh/compute-authorized-keys/Amus')
HELPER=Path('/usr/local/libexec/sentinel-status-v1.py')
PUBLIC=ROOT/'terminal-phone-public-key.pub'
MANIFEST=ROOT/'terminal-pairing-manifest.json'
BACKUPS=Path('/var/lib/sentinel-status-pairing')
LOCK=Path('/home/Amus/Documents/ChatGPT/ricing_linux/runner/ownership.lock')
def digest(data):return hashlib.sha256(data).hexdigest()
def plain(path):
    if path.is_symlink() or not path.is_file():raise RuntimeError('Expected regular non-symlink file: '+str(path))
    return path.read_bytes()
def public_blob(value):
    fields=value.strip().split()
    if len(fields)!=3 or fields[0]!='ecdsa-sha2-nistp256' or fields[2]!='sentinel-terminal':raise ValueError('Expected only the app terminal public key')
    blob=base64.b64decode(fields[1],validate=True);parts=[];offset=0
    while offset<len(blob):
        if offset+4>len(blob):raise ValueError('Invalid public key')
        n=struct.unpack('>I',blob[offset:offset+4])[0];offset+=4
        if n>1024 or offset+n>len(blob):raise ValueError('Invalid public key')
        parts.append(blob[offset:offset+n]);offset+=n
    if len(parts)!=3 or parts[:2]!=[b'ecdsa-sha2-nistp256',b'nistp256'] or len(parts[2])!=65 or parts[2][0]!=4:raise ValueError('Expected P256 public key')
    return fields[1],blob
def line_for(key):
    encoded,_=public_blob(key)
    return ('restrict,pty,from="100.70.104.44",command="/usr/bin/bash -l" '
            'ecdsa-sha2-nistp256 '+encoded+' sentinel-terminal-v1\n').encode()
def checked():
    m=json.loads(plain(MANIFEST));before=plain(AUTH);key=plain(PUBLIC).decode('ascii');encoded,blob=public_blob(key)
    if digest(before)!=m['authorisedKeysBeforeSha256']:raise RuntimeError('Authorised keys changed after review')
    if digest(plain(HELPER))!=m['statusHelperSha256']:raise RuntimeError('Monitoring helper changed after review')
    if digest(blob)!=m['publicBlobSha256']:raise RuntimeError('Terminal public key changed after review')
    if encoded.encode() in before:raise RuntimeError('Terminal key already exists; review current options')
    after=before+(b'' if before.endswith(b'\n') else b'\n')+line_for(key)
    return before,after,m
def replace(data,metadata):
    fd,tmp=tempfile.mkstemp(prefix='.sentinel-terminal-',dir=AUTH.parent)
    try:
        with os.fdopen(fd,'wb') as out:
            out.write(data);out.flush();os.fsync(out.fileno());os.fchmod(out.fileno(),metadata['mode']);os.fchown(out.fileno(),metadata['uid'],metadata['gid'])
        os.setxattr(tmp,'security.selinux',base64.b64decode(metadata['context']))
        os.replace(tmp,AUTH)
    finally:
        if os.path.exists(tmp):os.unlink(tmp)
def main():
    p=argparse.ArgumentParser(description=__doc__);g=p.add_mutually_exclusive_group();g.add_argument('--apply',action='store_true');g.add_argument('--rollback');args=p.parse_args()
    if not args.apply and not args.rollback:
        before,after,m=checked();print(json.dumps({'target':str(AUTH),'publicKeyFingerprint':m['publicKeyFingerprint'],'addedOptions':'restrict,pty,from="100.70.104.44",command="/usr/bin/bash -l"','monitoringHelperUnchanged':True,'existingKeysUnchanged':True,'applied':False},indent=2));return
    if os.geteuid()!=0:raise RuntimeError('Reviewed apply/rollback requires administrator authentication')
    fd=os.open(LOCK,os.O_RDWR|os.O_NOFOLLOW)
    try:
        fcntl.flock(fd,fcntl.LOCK_EX|fcntl.LOCK_NB)
        if args.rollback:
            backup=Path(args.rollback)
            if backup.parent!=BACKUPS or backup.is_symlink():raise RuntimeError('Invalid recorded backup')
            state=json.loads(plain(backup/'terminal-state.json'));before=plain(backup/'authorized-keys.before')
            if digest(plain(AUTH))!=state['after'] or digest(before)!=state['before']:raise RuntimeError('Later key edits detected; refusing rollback')
            replace(before,state);print('Removed only the reviewed terminal authorisation; restored the recorded key file.');return
        before,after,m=checked();st=AUTH.stat()
        if st.st_uid!=0 or stat.S_IMODE(st.st_mode)&0o022:raise RuntimeError('Unsafe authorised-key ownership/mode')
        for path in [AUTH.parent,BACKUPS]:
            s=path.stat()
            if path.is_symlink() or s.st_uid!=0 or stat.S_IMODE(s.st_mode)&0o022:raise RuntimeError('Unsafe directory')
        if stat.S_IMODE(BACKUPS.stat().st_mode)!=0o700:raise RuntimeError('Expected root-only backup directory')
        state={'before':digest(before),'after':digest(after),'mode':stat.S_IMODE(st.st_mode),'uid':st.st_uid,'gid':st.st_gid,'context':base64.b64encode(os.getxattr(AUTH,'security.selinux')).decode()}
        backup=BACKUPS/('terminal-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ'));backup.mkdir(mode=0o700)
        (backup/'authorized-keys.before').write_bytes(before);(backup/'authorized-keys.before').chmod(0o600)
        (backup/'terminal-state.json').write_text(json.dumps(state,indent=2));(backup/'terminal-state.json').chmod(0o600)
        if digest(plain(AUTH))!=state['before']:raise RuntimeError('Concurrent key edit detected')
        replace(after,state)
        print(json.dumps({'authorisedKeys':str(AUTH),'backup':str(backup),'afterSha256':state['after'],'monitoringHelperUnchanged':True},indent=2))
    finally:os.close(fd)
if __name__=='__main__':main()
