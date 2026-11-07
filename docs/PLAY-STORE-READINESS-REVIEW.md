# Sentinel: portability and Play Store readiness review

Reviewed 6 November 2026. Scope: current Android source, staged helper/setup code, installed helper checksum and contents, packaged Sentinel 0.8.0 APK, and current official Play documentation. This is a static portability/release review plus artifact inspection, not a new cross-device test campaign or a complete penetration test. No application code, PC authentication, network settings or installed software was changed.

## Verdict and exact use case

Sentinel is an Android companion for owners/administrators of Linux desktops, home-lab machines and small servers who already have an authorised SSH route and want to check the machine and troubleshoot it without opening a remote desktop.

A typical user opens Sentinel on their phone, connects over a private LAN or an existing VPN such as Tailscale, checks whether Linux is responding, examines CPU/RAM/storage/process activity, sorts or filters processes, then optionally opens a separate basic SSH shell for commands. Monitoring uses a restricted read-only key; Terminal uses a separately authorised key with the SSH account's normal shell permissions.

Suggested product description:

> Monitor your Linux PC from Android over SSH. Check system health and running processes, sort by CPU or memory, and open a basic terminal when you need to troubleshoot. Requires a reachable Linux PC, SSH access and the Sentinel monitoring helper.

Supported scope to claim honestly:

- Android 10+; one configured PC at a time.
- Linux procfs/sysfs monitoring; systemd-dependent OS/service readings where available.
- Existing SSH connectivity; Tailscale is optional, not bundled or configured by Sentinel.
- Foreground polling with stale/error handling, not continuous background alerts.
- Permission-readable process metadata, not desktop windows/browser tabs or complete application inventory.
- Hardware-exposed sensors only; no guarantee of GPU, SMART, fan or power support.
- Basic line-oriented SSH using TERM=dumb, not Kitty mirroring, a full terminal emulator, remote desktop, persistent terminal sessions or an editor-friendly curses terminal.
- Shutdown remains disabled in the GUI. No claim that an unreachable PC is definitely powered off.

It is not ready for public Play release in its current personal/development form.

## Findings, ordered by release importance

### 1. Personal connection fallback remains in Android runtime code

Evidence: `app/src/main/java/dev/titus/sentinel/monitor/MonitorStore.kt:18–21`; `StatusModel.kt:5`.

The presence of any cached sample activates defaults for 100.75.247.115, port 2222, username Amus and TITUS_PIN if individual stored settings are missing. Fresh installs have blank host/login/fingerprint, so this is not a claim that every new user automatically connects to Titus. However, the public binary still contains and can select those personal values in its migration path.

Before release: remove this fallback from the public product. Preserve the personal installation through an explicit private migration/build variant or fully persisted existing configuration. Missing public configuration must lead to setup, never another person's PC. Replace owner-specific pin constants in tests with explicit fixtures. A server's public fingerprint is not a private credential, but should not be the general app's implicit identity.

### 2. Setup/pairing scripts are personal deployment transactions, not general installers

Evidence: `deployment/pair-terminal-key.py:15–20,38`; analogous constants in pair-status-key.py and update-*-helper.py.

They assume `/etc/ssh/compute-authorized-keys/Amus`, this phone's 100.70.104.44 source, a lock inside /home/Amus/Documents/ChatGPT/ricing_linux, personal key/checksum manifests and `/usr/bin/bash -l`. The latter overrides the account's configured shell. Other distributions may use different SSH authorisation paths, shell paths and SELinux arrangements.

Before release: separate owner-specific migration/rollback history from distributable setup tooling. A reviewed general setup flow must obtain the actual account, key-file location, helper/interpreter path, login shell and optional source restriction. Every user must authorise their own app-generated public keys. Never ship this phone's authorisation as a universal default. Existing .pub files are public keys, not leaked private keys, but do not belong in a public onboarding bundle.

### 3. Full account discovery and server-side sorting are staged, not deployed on the current PC

Evidence: installed helper SHA256 `06e28760627cd4664e9a8b200c216ca81681fbba8cd0591440e2f08a85fe6801`; `/usr/local/libexec/sentinel-status-v1.py:55,175,296`; staged `deployment/sentinel-status-v1.py:361–369`; Android `ProcessModel.kt:80–102`.

The installed helper still has legacy all/Amus/skyler request choices and the fixed /mnt/titus-hdd volume. The newer staged helper supports local-account discovery, UID filters, sorting before the cap and discovered mounts, but its deployment remains a separately reviewed change. The app currently handles older helpers by asking for all processes and inferring user names from the returned sample; its dropdown is no longer a hard-coded two-user list.

Compatibility mode only sorts/filters the existing CPU-ranked sample of up to 200 rows. It cannot guarantee that the globally largest RAM consumer is present or that every local account appears. The fallback also assumes human UIDs are 1000..59999. Accounts with lower/different UIDs or no sampled process may be absent.

Before release: publish a versioned generic helper and capability handshake, make complete discovery/sorting the normal supported path, and clearly label any remaining compatibility limitations. Do not advertise all-account discovery or global RAM ranking while relying on inferred sample users. Updating the Android GUI alone does not update the Linux helper.

### 4. Legacy personal user names remain in staged helper code

Evidence: `deployment/process_metrics.py:63`; `deployment/sentinel-status-v1.py:369`.

The modern v2 route is numeric-UID based. The legacy compatibility route still explicitly accepts Amus and skyler. Remove these names from the public protocol or isolate their handling in a private migration component. Their presence in test fixtures/historical docs is different from active runtime restrictions and should not be confused with a fixed public dropdown.

### 5. Hardware handling retains workstation-derived assumptions

Evidence: `deployment/health_metrics.py:107,115,132`.

Two GPU PCI IDs have friendly names; other models fall back to generic vendor/device IDs. CPU temperature selection supports a small driver set. gigabyte_wmi and acpitz are globally excluded because they were slow on Titus. These are real workarounds, not fabricated readings, but their effects on other hardware are not established. Storage discovery in staged code is more generic, but remains local-block-filesystem-specific and intentionally excludes network mounts.

Before broad support claims: use capability-driven sensor discovery, bounded collection and device-independent labels; test different Intel/AMD systems and graphics drivers. Preserve the timeout fix until there is a tested replacement. No need to invent unavailable readings.

### 6. Further portability limits must be explicit or expanded

- SSH login validation accepts only a narrow 32-character username pattern (`StatusModel.kt:19`), whereas discovered names allow a broader pattern. Some legitimate logins will be rejected.
- Full discovery uses local /etc/passwd and UID_MIN/UID_MAX; it does not enumerate directory-only/NSS users. Non-login shells are excluded. These are product assumptions, not a universal definition of a person.
- Accounts are capped at 256, scans at 8192 processes, responses at 200 processes, and UID fields use signed Kotlin Int. Do not claim unrestricted enterprise directory/UID support.
- Paths such as /proc, /sys, /etc/passwd and systemctl are Linux platform dependencies, not personal hard-coding. Supporting Windows/macOS is separate work.
- Terminal fixes TERM=dumb and 80 × 24 and closes on background/navigation. Those are intentional current limitations, not Kitty-equivalent behaviour.
- Review clearing or visibly separating terminal scrollback when changing PCs: the ViewModel's in-memory buffer survives a saved-host change, so old-host output can appear alongside a new connection unless cleared manually.

### 7. Current artifact is a personal debug build

Evidence: `app/build.gradle.kts:6–14`; aapt reports `application-debuggable`; apksigner reports `CN=Android Debug` for `release/Sentinel-0.8.0.apk`.

Current application ID is dev.titus.sentinel.preview and namespace dev.titus.sentinel. Choose the final publisher-controlled identity before the first public release. The word preview is not itself a Play policy violation; this is a product/distribution identity decision.

Prepare a non-debug release Android App Bundle, upload signing and Play App Signing. App signing is distinct from SSH identities. A new package or reinstall produces separate app storage/Keystore identities and may require pairing again; do not break the personal installation or replace live PC keys as an incidental release-cleanup action.

### 8. Public onboarding and disclosure are incomplete

Settings exposes connection fields and key copying, but there is no complete in-app general Linux setup/revocation flow, privacy-policy link/text, third-party notice screen or independent review/demo path in the inspected source. Repository docs contain stale prototype claims, personal paths, network details, public keys and deployment receipts. These are not automatically APK assets—the inspected main source has no bundled deployment/assets directory—but a public source/helper release needs a deliberate clean product package.

Before submission: document what the helper reads, permission limits, two different key scopes, data retention, uninstall/revocation and sensor limitations; prepare privacy policy and accurate Data safety declarations, library-license notices, store screenshots/descriptions, rating/ads/audience declarations and a reviewer-access plan. Reviewers should not require access to the owner's real PC or tailnet. A clearly labelled demo mode may help, but is not a guarantee of Play approval or a substitute for whatever access the review process requires.

## What is already reasonably generic

- Fresh connection settings are blank; hostname/MagicDNS, IPv4/IPv6, SSH port/login and a verified host fingerprint are configurable.
- Display name can be supplied or discovered from the machine hostname.
- PC user filters use data models/UIDs rather than fixed UI buttons for Amus/Skyler.
- Modern staged process requests validate typed UID/sort/scope arguments and sort before limiting.
- Staged mounted-volume discovery no longer assumes just two drives.
- Android Keystore generates separate local monitoring/terminal keys; private keys are not embedded in source or exported as files.
- Pinned SSH host verification remains fail-closed. Monitoring and interactive privileges are separate.
- Foreground polling, response bounds, protocol versions, timeouts and stale-data thresholds are ordinary design constants, not owner-specific configuration that must all be removed.
- No analytics/advertising SDK is declared in the inspected app dependencies. This does not by itself establish the correct Data safety answers for direct SSH traffic.

## Current Play requirements checked

Official documentation checked on 6 November 2026:

1. New apps use Android App Bundles and need release/upload signing and Play App Signing: https://developer.android.com/guide/app-bundle and https://developer.android.com/studio/publish/app-signing .
2. Privacy policy must be available in the app and Play Console; Data safety is required even for apps declaring no collection. SSH/user-device data flows and library behaviour need an accurate assessment, not an automatic "no data collected" claim: https://support.google.com/googleplay/android-developer/answer/10144311 and https://support.google.com/googleplay/android-developer/answer/10787469 .
3. Reviewer-access instructions and app-content declarations must be prepared: https://support.google.com/googleplay/android-developer/answer/9859455 .
4. Personal developer accounts created after 13 November 2023 currently need at least 12 opted-in closed-test testers for 14 continuous days before applying for production access; whether this applies depends on the actual account: https://support.google.com/googleplay/android-developer/answer/14151465 .
5. The published policy currently requires API 36+ for new phone apps/updates. Source targets API 37, so the numeric target requirement is not the present blocker: https://support.google.com/googleplay/android-developer/answer/11926878 .

This review did not inspect a Play Console account, verify its eligibility or submit any release. Meeting this checklist does not guarantee approval.

## Recommended order

1. Freeze the intended first public scope: Linux companion, one PC, foreground monitoring plus basic SSH.
2. Remove personal Android fallback defaults and choose public app identity without disrupting the private installation.
3. Package/version a generic helper and safe parameterised pairing/revocation flow. Finish real-account discovery and full-list sorting.
4. Verify empty setup on a second unrelated Linux PC, different accounts/UIDs/shells, several Android devices/text sizes and lost-network/background scenarios.
5. Complete privacy/disclosures/reviewer access and release signing/AAB, then use the appropriate Play testing track.
