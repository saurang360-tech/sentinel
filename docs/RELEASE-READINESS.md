# Current release readiness

Updated 9 November 2026. Sentinel 0.10.0 is **not ready for public Play submission**.
A signed build and passing tests establish technical checks, not store approval.

## Run the reusable offline check

1. Open this checkout in Android Studio and connect its MCP server as described
   in [Android Studio workflow](ANDROID-STUDIO-MCP.md).
2. Build `:app:testDebugUnitTest :app:lintRelease :app:assembleRelease
   :app:bundleRelease` through Studio/MCP with Studio's bundled JDK.
3. From the project root, run:

   ```sh
   python tools/check_store_readiness.py
   ```

Exit **0** means the implemented offline checks passed; exit **1** means blockers
were found. The script prints blockers and writes `release/store/readiness.json`.
That generated file is ignored because it describes local build artifacts and
must not become a stale committed readiness claim. The checker, its regression
tests and the dated audit report are versioned in GitHub. A fresh checkout needs
its own Android SDK/local configuration and private release-signing setup; no
credentials, keystores or built APK/AAB files are committed.

Optional paths:

```sh
python tools/check_store_readiness.py --root /path/to/project --report /tmp/sentinel-readiness.json
python -m unittest discover -s tests -p test_store_readiness.py
python -m unittest discover -s tests
```

The checker uses only Python's standard library. It checks publisher metadata,
bundled contact, production manifest safety, the explicit seven Android permissions
plus the generated receiver permission, connectedDevice/non-exported service
configuration and the AndroidX profile-install receiver permission guard, signing-entry presence, legal/helper asset byte equality and bundle
SHA-256. Unexpected permissions or exported components fail the check.

It **does not** verify signature cryptography, native alignment, compiled-source
freshness, host/device behavior, public URL accessibility or actual Play Console
approval. Rebuild first. Independently run Android `apksigner verify`, JDK
`jarsigner -verify`, 16 KB native/ZIP alignment checks and device tests. An old
bundle can pass the asset comparison if only Kotlin code changed; this checker
must never substitute for the fresh Studio build. `submissionApproval` remains
NOT_ESTABLISHED and manual checks are listed even when offline checks pass.

## Remaining release work

- Fill `release/store/publisher.json`, add the real support/privacy contact to the
  bundled policy, define support retention, regenerate HTML, host the policy at
  its stable public HTTPS URL and rebuild. Do not invent publisher information.
- Provide current screenshots and complete English reviewer instructions/access
  to an isolated practical SSH setup. Production has no demo; do not use the
  owner's PC or private credentials for review.
- Complete connected-device foreground-service justification and demonstration
  video, final Data safety and ads/audience/content-rating forms in Play Console.
  Repository declarations are drafts, not submitted forms or policy approval.
- Fix the silent settings unlock failure on a phone without a secure screen lock.
  Documentation now states the requirement, but the runtime guidance is still
  missing. Keep phone verification separate from sudo-only power authentication.
- Validate independent Linux PCs, clean production install/upgrade, supported
  Android versions, permission denial/revocation, network/identity failures and
  locked/idle/overnight monitoring. Notification handoff is not delivery proof.
- Resolve or assess the current [partial phone test findings](PHONE-USER-TEST-REPORT-2026-11-09.md),
  including misleading process-search counts and loading labels. Untested coverage
  remains unverified; installed preview provenance was not established in that run.
- Confirm Play account eligibility/testing, signing-key backup and App Signing
  enrollment. If versionCode 13 has already been uploaded, increment before a
  subsequent Play upload. Do not erase the owner's paired preview for testing.

## Evidence and history

[9 November readiness audit](PLAY-READINESS-AUDIT-2026-11-09.md) records the original
findings and build evidence: 35 Kotlin tests, 38 Python tests, zero release-lint
errors (15 warnings), valid signed artifacts and 16 KB alignment. Documentation
cleanup subsequently adds eight checker regression tests and updates legal text;
see the cleanup verification in the audit. Old counts remain dated evidence.
Historical docs, roadmap tables and validation logs describe earlier passes.
Use this page, README and WIDGET-WORKSPACE.md for current status/behavior.

Current behavior includes optional background task polling, process watches,
verified direct-command job exits, expected-success rules, sustained health alerts,
custom tabs/themes/macros and sudo-authenticated shutdown/reboot. It does not include
guaranteed unattended delivery, remote desktop/Kitty mirroring, Windows/macOS host
support, backup-content verification, a cloud relay or developer data collection.

## Official references (checked 9 November 2026; recheck before submission)

- [User data/privacy policy](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Foreground-service declaration/video](https://support.google.com/googleplay/android-developer/answer/13392821)
- [Connected-device prerequisites](https://developer.android.com/develop/background-work/services/fgs/service-types#connected-device)
- [Reviewer access](https://support.google.com/googleplay/android-developer/answer/15748846)
- [Personal-account testing](https://support.google.com/googleplay/android-developer/answer/14151465)
- [Target API requirements](https://developer.android.com/google/play/requirements/target-sdk)
- [16 KB compatibility](https://developer.android.com/guide/practices/page-sizes)
