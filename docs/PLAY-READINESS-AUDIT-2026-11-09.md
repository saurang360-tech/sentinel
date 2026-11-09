# Sentinel — Google Play readiness audit

Date: 9 November 2026 (America/Toronto)
Source revision: 0651752, reviewed local working tree.
Verdict: **Not ready for public Play submission.** Signed release compilation passes;
publisher setup, reviewer access, store accuracy and device validation remain.
This audit changes no app behaviour, PC configuration or Play Console settings.

## Follow-up: documentation and checker cleanup

The original findings below are retained as a dated audit. The subsequent
cleanup corrects the listing, terms and Data safety version references, marks
historical screenshots/docs, and updates the permission/service gate with
regression tests. The merged-manifest wording below is corrected to include
the AndroidX profile-install receiver guarded by android.permission.DUMP. See [current readiness](RELEASE-READINESS.md) for remaining
blockers and reusable commands. It does not fix the silent no-screen-lock UX,
provide new screenshots/reviewer resources, invent publisher contacts or
complete Play declarations/independent tests. The separate partial connected-
phone report adds search-summary/loading findings without establishing
installed APK provenance. Generated readiness JSON is local and ignored.

## Original submission blockers and release risks

| Priority | Finding | Evidence and required completion |
| --- | --- | --- |
| P1 | Publisher and public privacy contact are missing | `release/store/publisher.json:3` has blank publisherName, supportEmail and privacyPolicyUrl. The bundled policy still defers contact to the eventual listing. Choose publisher identity and public support contact, define support retention, insert contact in the policy, host an accessible public HTML policy, regenerate and rebuild. A dedicated support address can keep personal email private. |
| P1 | Reviewer access is not concrete | `release/store/STORE-LISTING.txt:33` asks reviewers to configure an independent Linux SSH test host. No demo remains. Prepare complete English App access instructions and a practical isolated review setup for restricted features, including helper/key pairing, phone screen-lock requirements and power controls. Do not expose the owner's PC or real sudo credentials. Whether the proposed setup meets review requirements must be established in Play Console. |
| P1 | Foreground-service declaration is outstanding | `app/src/main/AndroidManifest.xml:5` adds notifications, foreground-service, connected-device and CHANGE_NETWORK_STATE permissions. The service is non-exported, explicitly started, uses an ongoing notification/Stop and START_NOT_STICKY. Android's connected-device prerequisites permit CHANGE_NETWORK_STATE, but this alone does not establish Play policy eligibility. Complete the connectedDevice use-case justification, interruption/defer impact and demonstration video in Play Console. |
| P1 | Current listing and screenshots describe a different product | `release/store/STORE-LISTING.txt:25` says background alerts/job tracking are unsupported; current code implements opt-in task/background monitoring, health alerts and expected-success rules. Its publishing notes also still call implemented shutdown a future feature. Existing screenshots are historical demo screens, expressly marked stale at line 38. Replace with actual current workspace screens from a non-personal test configuration and consolidate the listing into final customer-facing copy. |
| P1 | Setup silently fails on phones without a secure lock | `DeviceSecurity.kt:28-32` returns false when isDeviceSecure is false; `WorkspaceScreen.kt:98` handles success only. Protected connection settings cannot be opened, so a new user cannot configure a PC and receives no explanation. Add an explicit requirement/explanation and route to Android screen-lock setup, retaining protected settings. Phone authentication must remain separate from sudo-only power authentication. This is a source-confirmed control-flow finding, not a newly reproduced device test. |
| P1 | Release validation is incomplete across real configurations | Existing `docs/alerts-validation.txt` explicitly excludes actual helper integration, unrelated sensors and overnight/Doze/locked-device SSH. Verify clean production install and upgrade, notification/local-network grant/deny/revoke, loss of network, changed host keys, stopped/restarted app, supported Android versions and at least one unrelated Linux setup. Test power authentication safely and reserve real reboot/shutdown for an explicitly planned disposable host. |
| P2 | Terms and Data safety notes need reconciliation | Privacy policy is version 7 and describes current background/watch/runner retention. Terms remain version 3 and say there is no job-completion detection (`legal/terms.txt:19`), while the worksheet still refers to notice version 6. Explain optional verified job results and their limits consistently, including reboot and current retention. Finalize actual Play Data safety answers from the final artifact; the repository worksheet is not a submitted declaration. |
| P2 | Offline release gate has not been updated for reviewed permissions | `tools/check_store_readiness.py:37` still expects the old permission set. The fresh audit correctly fails its permission re-review safeguard. Review the new permissions and foreground-service declaration, then update the explicit expectation; do not merely remove the check. It currently reports the three missing publisher fields plus changed permissions. |
| Conditional | Play account requirements and signing continuity are unverified | Confirm developer identity verification, account eligibility, Play App Signing enrollment, upload-key backup, ads/audience/content-rating declarations and production access in the actual Console. For personal accounts created after 13 November 2023, Google's current requirement is at least 12 opted-in closed testers for 14 consecutive days before applying for production access. VersionCode remains 13; if that code was previously uploaded to Play, increment it for the next upload. |

## Verification performed now

- Android Studio MCP connected to this project. Requested `:app:testDebugUnitTest
  :app:lintRelease :app:assembleRelease :app:bundleRelease` through Studio's terminal.
  **BUILD SUCCESSFUL**, 6m 17s; log `/tmp/sentinel-play-readiness-build.log`.
- **35 Kotlin/JVM tests passed**, zero failures/errors, including shutdown/reboot
  fixed-command, authentication/refusal and cancellation tests using stub sudo.
- **38 Python tests passed**, log `/tmp/sentinel-readiness-python.log`.
- Release lint: **0 errors/fatal issues; 15 warnings**, primarily dependency
  freshness, KTX suggestions and unused/obsolete resource configuration.
- Signed production APK verified with apksigner; signed AAB verified with jarsigner.
- APK 16 KB ZIP alignment passed. arm64-v8a and x86_64 native ELF LOAD segment
  alignments are all 16384. No 16 KB runtime-device test was performed.
- Target SDK 37 exceeds the current published phone-app minimum of API 36.
  Minimum supported SDK remains 29.
- Merged production manifest disables debug and backup, disables cleartext,
  exports the launcher Activity and the AndroidX profile-install receiver guarded
  by android.permission.DUMP; TaskMonitorService and the startup provider are non-exported.
- Existing tests check generic production connection settings and helper command
  boundaries. No personal Amus/skyler/address fallback was found in main sources.
- Current dependency declarations contain no advertising/analytics/crash-report SDK;
  architecture remains direct SSH with strict host fingerprint checking and local
  Keystore credentials. This is not a full dependency vulnerability assessment.
- Fresh bundle includes the current privacy policy/helper and no Python bytecode cache.
- New production bundle: `app/build/outputs/bundle/release/app-release.aab`.
  SHA-256: `2637033ae29432710de1f410a14dfcc9a557533e58dece07270795812816c0bd`.
  The pre-existing named copies under `release/` were not refreshed by this audit;
  do not assume those are the newly verified build.
- No app installation, destructive data erasure, real-PC power command or Play
  submission was performed. A connected Pixel/API 37 is not independent coverage.

The old release bundle initially failed asset freshness checks. Rebuilding through
Studio resolved that condition; publisher fields and permission review still fail.
The generated ignored `release/store/readiness.json` records those current results.
No final release should be declared approved solely because this offline gate passes.

## Official requirements checked

- [Privacy and user data](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Data safety definitions and form](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Foreground-service declaration and video](https://support.google.com/googleplay/android-developer/answer/13392821)
- [Android connected-device service prerequisites](https://developer.android.com/develop/background-work/services/fgs/service-types#connected-device)
- [Reviewer access requirements](https://support.google.com/googleplay/android-developer/answer/15748846)
- [New personal-account testing](https://support.google.com/googleplay/android-developer/answer/14151465)
- [Current target API requirement](https://developer.android.com/google/play/requirements/target-sdk)
- [16 KB native compatibility](https://developer.android.com/guide/practices/page-sizes)

Policy eligibility, actual Console declarations and review outcomes remain external
checks. The no-developer-collection Data safety proposal is an architecture-based
interpretation, not Google approval; user-chosen SSH/terminal destinations and
support messages still require accurate disclosure.

## Cleanup verification — 9 November 2026

- Corrected current README/store copy, terms (version 4), privacy wording and
  generated HTML; privacy notice remains version 7 because this corrects the
  description of existing behavior rather than adding new processing.
- Archived earlier descriptions as explicitly historical and retired the obsolete
  demo screenshot command without issuing any phone commands.
- Added reusable readiness usage, an updated manifest/service/asset gate and
  **eight regression tests**. All **46 Python tests** passed.
- Studio/MCP release build, unit tests and release lint verified the corrected
  disclosure/assets: **35 Kotlin tests**, zero failures, **0 release-lint errors**
  (15 warnings). BUILD SUCCESSFUL in 2m 59s.
  Log: `/tmp/sentinel-doc-cleanup-build.log`.
- Rebuilt AAB signature verified with jarsigner; bundled legal/helper assets match
  source. Updated checker now reports only the three missing publisher/contact/URL
  fields as automated blockers. Manual checks remain explicitly outstanding.
- Fresh bundle SHA-256: `64561ac3fb335c8a061972624104bc6b75f45d96b8cf9c36c0d12e48da6ef124`.
  The earlier hash in the original audit identifies the pre-cleanup artifact.
- Phone disconnected: no new phone/live-PC tests, installs, PC configuration or
  power actions. Open runtime findings and release approvals remain unverified.
