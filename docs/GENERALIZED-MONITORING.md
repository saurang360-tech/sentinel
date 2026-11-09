Historical record/planning document. Feature descriptions, test counts and
setup details below apply to that pass and may be superseded. Current behavior:
README.md and docs/WIDGET-WORKSPACE.md. Current release status and reusable checks:
docs/RELEASE-READINESS.md. Historical personal deployment data are not public
onboarding defaults.

# Generalized Linux monitoring — 0.7.0

Settings configures one Linux PC at a time: optional display name, hostname/MagicDNS or IPv4/IPv6, SSH port/login and independently verified host fingerprint. Fresh installations start without personal connection defaults. Saved configurations remain; pre-0.7 installations that relied on implicit defaults retain their connection only when a previous sample exists. Device identity remains in Android Keystore. No private keys are imported. Strict pinning and public-key-only authentication remain in place, with supported modern SSH algorithm defaults for different servers.

Local human login accounts are discovered from /etc/passwd and UID_MIN/UID_MAX, excluding non-login/service shells. Only UID and username are returned; no password/shadow/home data is accessed. Accounts are refreshed with every process sample and connection test. Removing a selected account resets the filter to All users after its next response; creating another normal account makes it available automatically. Directory-only users are not enumerated. Discovery is bounded at 256 accounts.

Processes defaults to user accounts, excluding service/root/kernel accounts. Include system processes enables the full permission-readable list. CPU and RAM sorts happen on the PC before the 200-row cap, including when a specific UID is selected. The compact table shows name, owner, CPU and RAM; tapping a row expands PID/state/runtime. Search applies to the returned sample. Full scanning remains capped at 8192 processes and reports partial/permission-limited results.

Health discovers distinct mounted local block filesystems rather than assuming a disk name or path. Network mounts are not probed. The earlier firmware-sensor timeout exclusions and automatic retry/stale-state handling remain. This is Linux monitoring over an existing SSH connection; other operating systems and shutdown are not implemented.

`deployment/GENERAL-REVIEW.md` records the scoped helper update and guarded backup/rollback. `deployment/INSTALL-LINUX.md` describes pairing another PC. The portable helper bundle is `release/sentinel-linux-helper-0.7.0.zip` and includes an installation guide and checksum.

Validation: 21 Python tests pass, including account create/delete fixtures, strict request grammar, UID filtering and RAM sort before the cap, mount discovery/deduplication and avoidance of network capacity probes. Android Studio assembly, unit tests and lint pass. Physical-phone verification is recorded after approved deployment below.

The APK remains the existing debug-signed personal build. Publishing it requires a release-signing/distribution pass and broader compatibility testing; this update does not claim support for every Linux/NSS/hardware configuration.
