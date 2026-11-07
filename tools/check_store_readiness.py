"""Offline submission gate. Reports missing metadata without reading signing secrets."""
from pathlib import Path
import hashlib
import json
import re
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'


def check():
    failures = []
    metadata = json.loads((ROOT / 'release/store/publisher.json').read_text())
    for field in ('publisherName', 'supportEmail', 'privacyPolicyUrl'):
        if not metadata.get(field):
            failures.append('Publisher must supply ' + field)
    email = metadata.get('supportEmail', '')
    if email and not re.fullmatch(r'[^\s@]+@[^\s@]+\.[^\s@]+', email):
        failures.append('Support email must be a usable public contact')
    if metadata.get('privacyPolicyUrl') and not metadata['privacyPolicyUrl'].startswith('https://'):
        failures.append('Privacy policy needs a public HTTPS URL')
    if email and email not in (ROOT / 'app/src/main/assets/legal/privacy-policy.txt').read_text():
        failures.append('Bundled privacy policy must include the selected contact before rebuilding')
    manifest = ROOT / 'app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml'
    if not manifest.exists():
        failures.append('Build the release manifest through Android Studio first')
    else:
        root = ET.parse(manifest).getroot()
        if root.get('package') != metadata.get('applicationId'):
            failures.append('Release application ID differs from publisher metadata')
        app = root.find('application')
        if app.get(ANDROID + 'debuggable', 'false') != 'false':
            failures.append('Release app must not be debuggable')
        if app.get(ANDROID + 'allowBackup') != 'false':
            failures.append('App backup must remain disabled')
        expected = {'android.permission.INTERNET', 'android.permission.ACCESS_LOCAL_NETWORK', 'android.permission.USE_BIOMETRIC',
                    metadata['applicationId'] + '.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'}
        actual = {p.get(ANDROID + 'name') for p in root.findall('uses-permission')}
        if actual != expected:
            failures.append('Re-review merged permissions: permission set has changed')
    bundle = ROOT / 'app/build/outputs/bundle/release/app-release.aab'
    digest = None
    if not bundle.exists():
        failures.append('Release AAB has not been built')
    else:
        with zipfile.ZipFile(bundle) as archive:
            names = archive.namelist()
            if not any(n.startswith('META-INF/') and n.endswith(('.RSA', '.EC', '.DSA')) for n in names):
                failures.append('Release bundle is unsigned')
            for path in ('legal/privacy-policy.txt', 'legal/terms.txt', 'legal/third-party-notices.txt', 'sentinel-monitor.py'):
                if 'base/assets/' + path not in names:
                    failures.append('Missing bundled resource: ' + path)
                elif archive.read('base/assets/' + path) != (ROOT / 'app/src/main/assets' / path).read_bytes():
                    failures.append('Rebuild bundle after changing: ' + path)
        digest = hashlib.sha256(bundle.read_bytes()).hexdigest()
    report = {'offlineChecksPassed': not failures, 'bundleSha256': digest, 'blockedBy': failures,
              'additionalRequiredReview': ['Public policy URL accessibility', 'Publisher backup of upload signing material',
                                           'Independent-device/PC compatibility', 'Actual Play Console eligibility, declarations and review']}
    (ROOT / 'release/store/readiness.json').write_text(json.dumps(report, indent=2) + '\n')
    for issue in failures:
        print('BLOCKED: ' + issue)
    if not failures:
        print('Offline checks passed. Complete the listed external/release reviews before submission.')
    return 1 if failures else 0


if __name__ == '__main__':
    raise SystemExit(check())
