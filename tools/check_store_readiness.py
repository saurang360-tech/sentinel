"""Offline Play submission checks. Exit 1 on blockers; never upload or read signing secrets.

Run after building through Android Studio. Passing these local checks is not Play
approval: manual release requirements remain in every report. See
 docs/RELEASE-READINESS.md for scope, usage and external checks.
"""
from pathlib import Path
import argparse
import hashlib
import json
import re
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'
ASSETS = ('legal/privacy-policy.txt', 'legal/terms.txt',
          'legal/third-party-notices.txt', 'sentinel-monitor.py')
PERMISSIONS = {
    'android.permission.INTERNET',
    'android.permission.ACCESS_LOCAL_NETWORK',
    'android.permission.USE_BIOMETRIC',
    'android.permission.POST_NOTIFICATIONS',
    'android.permission.FOREGROUND_SERVICE',
    'android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE',
    # Android connectedDevice prerequisite; this app does not change networking.
    'android.permission.CHANGE_NETWORK_STATE',
}
MANUAL_CHECKS = [
    'Publisher identity/contact and public privacy-policy URL accessibility',
    'Reviewer access to an isolated SSH test setup; current truthful screenshots/listing',
    'Play foreground-service use-case declaration, eligibility review and demonstration video',
    'Final Data safety, ads/audience/content-rating and account eligibility declarations',
    'Play App Signing enrollment, upload-key backup and unused versionCode',
    'Required closed testing/production access in the actual developer account',
    'Independent-device/PC, clean-install/upgrade, permission/revocation and idle/overnight tests',
    'Secure-screen-lock setup guidance and open issues in the latest audit/phone test report',
]


def audit(root=ROOT):
    root = Path(root)
    failures = []
    checks = []
    metadata = {}
    try:
        metadata = json.loads((root / 'release/store/publisher.json').read_text())
        if not isinstance(metadata, dict):
            raise ValueError('expected an object')
    except (OSError, ValueError):
        metadata = {}
        failures.append('Publisher metadata is missing or invalid JSON')
    for field in ('publisherName', 'supportEmail', 'privacyPolicyUrl'):
        if not isinstance(metadata.get(field), str) or not metadata[field].strip():
            failures.append('Publisher must supply ' + field)
    app_id = metadata.get('applicationId')
    if app_id != 'dev.titus.sentinel':
        failures.append('Publisher applicationId must match the current production ID dev.titus.sentinel')
    email = metadata.get('supportEmail', '')
    if email and (not isinstance(email, str) or not re.fullmatch(r'[^\s@]+@[^\s@]+\.[^\s@]+', email)):
        failures.append('Support email must be a usable public contact')
    url = metadata.get('privacyPolicyUrl', '')
    if url and (not isinstance(url, str) or not re.fullmatch(r'https://[^/\s]+(?:/[^\s]*)?', url)):
        failures.append('Privacy policy needs a public HTTPS URL')
    policy = root / 'app/src/main/assets/legal/privacy-policy.txt'
    if isinstance(email, str) and email:
        if not policy.exists() or email not in policy.read_text():
            failures.append('Bundled privacy policy must include the selected contact before rebuilding')
    checks.append('Publisher fields and bundled contact (URL reachability is manual)')

    manifest = root / 'app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml'
    if not manifest.exists():
        failures.append('Build the release manifest through Android Studio first')
    else:
        try:
            document = ET.parse(manifest).getroot()
            if document.get('package') != app_id:
                failures.append('Release application ID differs from publisher metadata')
            app = document.find('application')
            if app is None:
                raise ValueError('missing application')
            if app.get(ANDROID + 'debuggable', 'false') != 'false':
                failures.append('Release app must not be debuggable')
            if app.get(ANDROID + 'allowBackup') != 'false' or app.get(ANDROID + 'fullBackupContent') != 'false':
                failures.append('App backup must remain disabled')
            if app.get(ANDROID + 'usesCleartextTraffic') != 'false':
                failures.append('Release must explicitly disable cleartext traffic')
            expected = PERMISSIONS | {'dev.titus.sentinel.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'}
            actual = {p.get(ANDROID + 'name', '') for p in document.findall('uses-permission')}
            if actual != expected:
                failures.append('Re-review merged permissions: added=' + ','.join(sorted(actual - expected)) +
                                '; missing=' + ','.join(sorted(expected - actual)))
            service = next((s for s in app.findall('service')
                            if s.get(ANDROID + 'name') in ('.monitor.TaskMonitorService',
                                                          'dev.titus.sentinel.monitor.TaskMonitorService')), None)
            if service is None or service.get(ANDROID + 'foregroundServiceType') != 'connectedDevice' or service.get(ANDROID + 'exported') != 'false':
                failures.append('TaskMonitorService must be non-exported with connectedDevice type')
            for component in list(app):
                if component.tag in ('service', 'receiver', 'provider', 'activity', 'activity-alias'):
                    if component.get(ANDROID + 'exported') == 'true' and not (
                        (component.tag == 'activity' and component.get(ANDROID + 'name') in
                         ('.MainActivity', 'dev.titus.sentinel.MainActivity')) or
                        (component.tag == 'receiver' and
                         component.get(ANDROID + 'name') == 'androidx.profileinstaller.ProfileInstallReceiver' and
                         component.get(ANDROID + 'permission') == 'android.permission.DUMP')):
                        failures.append('Review unexpected exported component: ' + str(component.get(ANDROID + 'name')))
            checks.append('Merged production manifest, explicit permissions and service exposure')
        except (OSError, ET.ParseError, ValueError):
            failures.append('Release manifest is malformed or incomplete; rebuild through Android Studio')

    bundle = root / 'app/build/outputs/bundle/release/app-release.aab'
    digest = None
    if not bundle.exists():
        failures.append('Release AAB has not been built')
    else:
        try:
            with zipfile.ZipFile(bundle) as archive:
                names = archive.namelist()
                # Presence is not cryptographic verification; do not claim otherwise.
                if not any(n.startswith('META-INF/') and n.endswith(('.RSA', '.EC', '.DSA')) for n in names):
                    failures.append('Release bundle has no signing entry; build a signed release')
                for path in ASSETS:
                    source = root / 'app/src/main/assets' / path
                    if 'base/assets/' + path not in names:
                        failures.append('Missing bundled resource: ' + path)
                    elif not source.exists() or archive.read('base/assets/' + path) != source.read_bytes():
                        failures.append('Rebuild bundle after changing: ' + path)
                if any(n.endswith('.pyc') or '/__pycache__/' in n for n in names):
                    failures.append('Remove generated Python bytecode caches from release assets')
            digest = hashlib.sha256(bundle.read_bytes()).hexdigest()
            checks.append('AAB signing-entry presence, bundled legal/helper byte equality and SHA-256')
        except (OSError, ValueError, zipfile.BadZipFile, RuntimeError):
            failures.append('Release AAB is unreadable or corrupt; rebuild a signed bundle')
    return {
        'offlineChecksPassed': not failures,
        'submissionApproval': 'NOT_ESTABLISHED: manual release checks are always required',
        'bundleSha256': digest,
        'blockedBy': failures,
        'checksPerformed': checks,
        'limitations': ['Does not verify signature cryptography, native 16 KB alignment, source-code freshness or runtime behavior',
                        'Does not access Play Console, verify the public policy URL or certify legal/policy compliance'],
        'additionalRequiredReview': list(MANUAL_CHECKS),
    }


def check(root=ROOT, report_path=None):
    root = Path(root)
    report = audit(root)
    target = Path(report_path) if report_path else root / 'release/store/readiness.json'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(report, indent=2) + '\n')
    for issue in report['blockedBy']:
        print('BLOCKED: ' + issue)
    if report['offlineChecksPassed']:
        print('Offline checks passed. Manual release checks are still required; this is not Play approval.')
    print('Report: ' + str(target))
    print('Manual requirements remain: see docs/RELEASE-READINESS.md.')
    return 0 if report['offlineChecksPassed'] else 1


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT, help='Project root; defaults to this checkout')
    parser.add_argument('--report', type=Path, help='Optional output JSON path')
    args = parser.parse_args()
    raise SystemExit(check(args.root, args.report))
