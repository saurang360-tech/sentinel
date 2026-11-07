# Sentinel

Android companion for monitoring your Linux PC over an existing SSH route,
built with Kotlin/Jetpack Compose through Android Studio and its MCP server.

Version 0.10.0 provides foreground system status, health readings and processes
with CPU/RAM sorting and local-user filters. Optional basic SSH terminal access
uses a separately authorised key. Hardware/permission limitations remain visible.
Shutdown uses existing Terminal SSH access and asks for the PC sudo password for
each action; it is never stored. No additional PC software or permissions are
installed. Existing sudo rights are required. Continuous background alerts and
task-completion tracking remain proposed future work.

Connections go directly to the configured PC. There is no developer backend,
analytics, advertising or account system. Settings remain on the phone; live
readings and terminal output are memory-only. Android backup is disabled.
Saved profiles are encrypted and protected by phone fingerprint/screen-lock
verification; shutdown separately asks for PC authentication.
First use shows privacy/terms information before connecting; Android 17+ requests
local-network permission. Demo and legal text work without a PC connection.

The PC requires SSH, Python 3 and administrator-reviewed installation/pairing of
the helper bundled at `app/src/main/assets/sentinel-monitor.py`. Settings can
export that helper through Android's file picker and explains setup/revocation.
Private keys are generated in Android Keystore and never imported from another
app. Strict server fingerprint checking remains required.

Open this folder in Android Studio and use Studio's bundled JDK; the system
runtime lacks javac. Source targets API 37 and supports Android 10+.
Run `:app:assembleDebug :app:assembleRelease :app:bundleRelease
:app:testDebugUnitTest :app:lintDebug` through Studio/MCP.
No new runtime dependencies were added for 0.9.

Production uses `dev.titus.sentinel`; debug keeps `dev.titus.sentinel.preview`
to preserve the paired phone installation. Production is separate and needs
its own pairing. Release signing reads ignored `release-signing.properties`;
it never falls back to the debug certificate. Back up the newly generated
upload key/configuration securely before publishing.

- [0.10 protected connections and SSH shutdown](docs/SSH-SHUTDOWN-0.10.md)
- [0.9 changes and verification](docs/PUBLIC-RELEASE-0.9.md)
- [Public release gate](release/store/RELEASE-CHECKLIST.md)
- [Data safety worksheet](release/store/DATA-SAFETY.md)
- [Store listing/reviewer instructions](release/store/STORE-LISTING.txt)
- [Publisher metadata still to supply](release/store/publisher.json)
- [Proposed future improvements](docs/PROPOSED-IMPROVEMENTS.txt)
- [Original portability audit](docs/PLAY-STORE-READINESS-REVIEW.md)
- [Android Studio MCP setup](docs/ANDROID-STUDIO-MCP.md)

Run `python tools/prepare_store_documents.py` to regenerate static legal HTML;
`python tools/check_store_readiness.py` reports offline submission blockers.
Neither uploads anything. Publisher contact/policy hosting, independent
compatibility testing and actual Play Console requirements remain release gates.
No app has been submitted to Play from this workspace.

Earlier deployment scripts, manifests, receipts and historical docs describe
the owner's personal setup. They are not universal installers and must not be
distributed as onboarding defaults. The public bundled helper removes personal
legacy usernames, discovers local accounts and sorts before the result cap.
Older installed helpers offer sample-limited compatibility until the PC
administrator reviews an upgrade. This update does not modify PC keys,
services or networking. Explicit phone settings are preserved; old implicit
personal defaults must be replaced with explicit configuration.
