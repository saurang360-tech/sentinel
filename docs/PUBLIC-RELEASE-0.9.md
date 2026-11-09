Historical record/planning document. Feature descriptions, test counts and
setup details below apply to that pass and may be superseded. Current behavior:
README.md and docs/WIDGET-WORKSPACE.md. Current release status and reusable checks:
docs/RELEASE-READINESS.md. Historical personal deployment data are not public
onboarding defaults.

# Sentinel 0.9.0 public-release preparation

Implemented and checked on 6 November 2026. This is a tested release candidate;
Play submission, policy hosting and independent compatibility testing are pending.

## Functional changes

- Removed personal host/port/login/fingerprint defaults from public runtime code.
  Explicit saved settings survive. Older implicit setups need explicit settings;
  this condition was found and corrected on the owner's phone using its already
  verified connection details. Existing SSH keys were retained.
- Removed persisted status snapshots/last-contact cache, with upgrade cleanup.
  Live samples and terminal output are memory-only.
- Added versioned first-use privacy disclosure/affirmative terms acknowledgement,
  stored locally, before connections. Added Android 17 local-network permission
  requests with denial/retry/Settings recovery. View models guard SSH access.
- Added offline privacy policy, terms and bundled third-party licences, available
  before setup and in Settings. Actual JSch/Bouncy Castle notices and Apache
  licence text are included. Publisher contact still needs completion.
- Added confirmed local-data/key erasure and PC-side key revocation guidance;
  failure directs users to Android's own storage-clear controls.
- Added generic Linux helper export through the system file picker, without
  broad storage permissions. Public helper discovers users, sorts before the cap
  and removes personal legacy usernames. Setup explains pinned host verification,
  restricted monitoring authorisation and optional separate terminal privileges.
- Added a labelled offline demo. It does not instantiate SSH view models or
  connect/generate keys. Demo sorting/user filters work; terminal execution is
  disabled. It is available before first-use acknowledgement/network permission.
- Broadened SSH login validation for common dotted/domain/machine-account names;
  retained whitespace/control/shell-metacharacter rejection. Network guidance
  no longer assumes Tailscale is mandatory.
- Disconnect/clear terminal output when PC configuration changes; release
  transport failures no longer write diagnostic logcat entries.
- Split production dev.titus.sentinel from existing debug .preview identity.
  Release is non-debuggable and uses a new local upload key, never a debug-key
  fallback. Signing secrets are permission-restricted/ignored; backup is required.

## Verification

- Android Studio MCP inspected the open Sentinel Gradle modules. APK/AAB builds,
  unit tests and lint ran through Studio's terminal with its bundled compiler.
  Final build log: /tmp/sentinel-store-verified-build.log.
- 15 Kotlin/JVM tests passed. Lint completed with zero errors; existing warnings
  concern dependency updates, resource cleanup and style suggestions. No runtime
  dependency was added or upgraded for this release.
- 27 Python tests passed, including public helper request rejection and guards
  against personal connection defaults in public Android/helper source.
- Six isolated settings/privacy/demo instrumentation tests passed on the Pixel.
- Seven live SSH/security tests passed after explicit settings restoration:
  status/session reuse, real health, eight health polls/reconnects, user/sort
  queries, wrong-host-pin rejection and separate stable keys. Initial live tests
  failed with blank configuration, leading to the restoration above.
- The signed production app was installed separately for clean-install checks:
  unchecked acknowledgement, offline labelled demo, visible RAM sorting, system
  permission prompt, denial/retry/grant and blank setup were exercised.
- Data erasure was confirmed only in that disposable production app; relaunch
  returned to an unchecked welcome screen. The paired preview was not erased.
- The final paired preview was reinstalled without uninstalling and verified in
  its UI with live Overview/CPU/RAM/temperature readings.
- APK signature verified as CN=Sentinel Upload; bundle JAR signature verified.
  Standard self-signed upload-certificate and JAR-layout warnings remain in the
  verifier output; these checks do not replace Play's own bundle validation.
- Release manifest: INTERNET, ACCESS_LOCAL_NETWORK and AndroidX's own protected
  receiver permission. Backup disabled, non-debuggable. APK ZIP alignment and all
  bundled native-library PT_LOAD segments checked for 16 KiB compatibility.
- Genuine labelled demo screenshots captured at 1080x1920 for Play. The capture
  tool restores the prior logical screen size in finally. Temporary phone
  stay-awake settings are restored after testing.

Artifacts: release/Sentinel-0.9.0.aab (upload candidate), Sentinel-0.9.0.apk
(signed production APK), and Sentinel-0.9.0-preview.apk (paired-phone update).
The production package is separate and needs its own PC-key authorisation.

## Outstanding gates

Supply publisher name, public support/privacy contact and hosted policy URL;
update/rebuild bundled legal text to match. Complete independent Android/Linux
compatibility tests and actual Play Console declarations/testing/review. The
offline gate records missing metadata in release/store/readiness.json.

Static legal HTML, listing copy, Data safety worksheet, store graphics and demo
JPEG screenshots are under release/store. Tall raw Pixel PNGs are QA references,
not submission screenshots. No Play upload was performed. Future job tracking,
notifications and power actions are not part of this release. The public helper
was not deployed to the owner's PC; no PC authentication, services or networking
were changed.
