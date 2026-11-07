# Design and review plan

## Visual system

The reference establishes black page backgrounds, charcoal rounded cards, restrained white/grey type, circular usage gauges and an oversized power control. Use the reference as visual direction; do not copy its hardware claims, the literal “16px” annotations or browser-tab data.

This native Compose prototype uses #080808 background, #191919 cards, #F4F4F1 foreground and #A5A5A5 secondary text. A muted amber label identifies design/demo data and uncertain states. Four persistent bottom tabs provide Power, Health, Details and Activity. Each screen scrolls; system status/navigation insets are respected. Titles are 28sp; metrics 25sp; supporting content 11–13sp. Font scaling and 48dp minimum touch targets need device verification.

Power has a status/last-contact card, large circular confirmation entry, explicit interruption warning and demo contact-state selection. Health has stacked metric cards, unavailable GPU readings and network/swap detail. Details groups system, connection and secondary sensor capabilities. Activity has owner filters and labelled illustrative rows, with a clear permission explanation for windows/tabs.

The review board at design/pc-sentinel-design.png is a separately rendered mockup, not an emulator screenshot. Regenerate it with python3 tools/render_design.py (Pillow required). The prototype contains four Android Studio @Preview functions at 412 × 915dp. Preview dimensions are a design viewport, not a verified Pixel 9a display measurement. All readings/processes are demo data; no network permission is declared. The power dialog's final action is disabled.

## Concrete current change and rollback

Only this new app workspace and Android Studio's explicitly requested MCP plugin/configuration have changed. No Titus backend has been deployed. Source is in app/src/main/java/dev/titus/sentinel/MainActivity.kt; dependency versions and Gradle distribution checksum are pinned. local.properties is excluded from version control.

Before any future deployment: record checksums/ownership/modes of the exact files to change, create scoped backups without secrets, record newly created paths, and acquire applicable ownership locks. Abort on unexpected existing changes. Rollback restores only those exact files if they still match the deployed checksums and removes only newly created files. Never reset whole SSH/firewall/desktop configuration. Existing laptop, Termius and Skyler paths remain acceptance tests.

Removing/uninstalling dev.titus.sentinel.preview reverses the later preview device install; none has been performed yet. Removing the new source project reverses the current prototype, but retain reviewed work as needed. See ANDROID-STUDIO-MCP.md for integration rollback.

## Acceptance tests

Before production pairing:

1. Real host fingerprint is pinned and mismatch rejects without override. App key is separate from Termius; no secret is in source/logs/backups.
2. CPU/rate counter deltas, first sample, reset/wrap, boot change, missing sensors, permission denials and malformed/oversized JSON are tested.
3. Unknown, fresh, stale, unreachable, reconnect, cancellation and timeout are tested with an injected clock/transport. Never show cached live gauges after reconnect failure.
4. Backgrounding stops polling; only one request is in flight; repeated taps do not duplicate connection attempts.
5. Owner filters preserve correct UID names; absent cross-user permissions display restricted, not empty-as-success. No raw command args or document/browser content is collected.
6. On Pixel: check all four screens, rotation, large font, TalkBack, navigation, scrolling, small targets, confirmation cancellation and stale timestamps.
7. User-coordinated home Wi-Fi and cellular/other-network tests: reachability, authenticated samples, disconnect/reconnect and unchanged Termius fallback. Mark unperformed tests NOT TESTED.
8. Shutdown remains disabled until mechanism and policy review. Then test denial, inhibitors, multiple users, expiry/replay and loss-of-ack with a stub. A real shutdown is scheduled only after the owner has saved work and coordinated Skyler. No automatic real shutdown test.

No new service, key authorisation, polkit rule, firewall rule, driver change or real shutdown is proposed for immediate approval by this design stage.
