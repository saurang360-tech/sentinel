"""Create a NEW local upload key. Never inspect/import existing private keys.

No secrets are printed. The key/password/configuration must be backed up securely
by the publisher before submission. Existing files are never overwritten.
"""
from pathlib import Path
import os
import secrets
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def main():
    folder = ROOT / 'release/private'
    config = ROOT / 'release-signing.properties'
    key = folder / 'sentinel-upload.jks'
    password_file = folder / 'upload-password.txt'
    if config.exists() or key.exists() or password_file.exists():
        raise SystemExit('Signing material already exists. Nothing changed.')
    folder.mkdir(mode=0o700, parents=True, exist_ok=True)
    folder.chmod(0o700)
    password = secrets.token_urlsafe(48)
    fd = os.open(password_file, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as stream:
        stream.write(password)
    keytool = Path('/home/Amus/Applications/android-studio/jbr/bin/keytool')
    result = subprocess.run([str(keytool), '-genkeypair', '-noprompt',
        '-keystore', str(key), '-storetype', 'JKS', '-alias', 'sentinel-upload',
        '-keyalg', 'RSA', '-keysize', '3072', '-sigalg', 'SHA256withRSA',
        '-validity', '10000', '-dname', 'CN=Sentinel Upload',
        '-storepass:file', str(password_file), '-keypass:file', str(password_file)],
        capture_output=True, text=True)
    if result.returncode:
        password_file.unlink()
        raise SystemExit('Key generation failed. No passwords were printed. Inspect local keytool installation.')
    key.chmod(0o600)
    fd = os.open(config, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as stream:
        stream.write('storeFile=release/private/sentinel-upload.jks\n'
                     'storePassword=' + password + '\nkeyAlias=sentinel-upload\n'
                     'keyPassword=' + password + '\n')
    print('Created a dedicated Sentinel upload key and local signing configuration.')
    print('No existing keys were read or changed. Secrets are excluded from version control.')
    print('Publisher must securely back up release/private and release-signing.properties.')


if __name__ == '__main__':
    main()
