Historical record/planning document. Feature descriptions, test counts and
setup details below apply to that pass and may be superseded. Current behavior:
README.md and docs/WIDGET-WORKSPACE.md. Current release status and reusable checks:
docs/RELEASE-READINESS.md. Historical personal deployment data are not public
onboarding defaults.

# Live Processes — Sentinel 0.4-processes

The fourth tab is now Processes. It replaces all illustrative application rows with authenticated process metadata from Titus over the existing Tailscale SSH connection. Browser tabs are excluded. Health and Details remain explicitly labelled demo pages; Power remains live.

## Measurements and limits

Rows show process name, PID, effective user, kernel state, CPU percentage, resident memory (MiB) and elapsed lifetime. Process names are not document/window titles and cannot identify every script. Raw arguments, environment, open files and content are never collected.

CPU uses two samples about 0.4 seconds apart, divided by the aggregate machine CPU-counter delta. One fully busy core on a six-core CPU is about 16.7% of total capacity. PID and process start ticks must match before a delta is accepted; new/reused PIDs show warming up. RSS includes shared pages and must not be summed as unique system memory.

Owner filters run on the workstation before ranking/capping. Up to 200 rows are returned, ordered by CPU then RSS. The UI displays 40 initially, with Show more and a search over the returned rows. Permission denials, disappearing processes, result limits and the 8192-PID scan cap are disclosed. No returned processes does not mean a user is offline.

## Connection/lifecycle

Use the existing Sentinel key, pinned host fingerprint and Amus@100.75.247.115:2222 over Tailscale. No new listener or Termius credential import is required. The existing forced-command handler gains three exact, read-only process requests. SSH authorised keys and restrictions remain unchanged.

Power and Processes share a single lifecycle-aware transport/ViewModel, never poll both pages at once, reuse SSH sessions, stop on background/settings/navigation, and back off failures. Process snapshots are held only in memory and labelled stale after 20 seconds. Changing owner clears mismatched cached rows. No process killing or launching is implemented.

## Deployment

Owner approved the helper-only update described in deployment/PROCESS-REVIEW.md. Installed helper SHA-256: 5c08ec1719a313dafe4f46c98e1b403beb045f9ec8ba82ba85de7778c393eb07.

Backup: /var/lib/sentinel-status-pairing/processes-20261027T051359075742Z.

Rollback restores only the previous helper via update-process-helper.py --rollback with that exact directory and administrator authentication. Checksum guards refuse to overwrite later edits. No SSH reload/restart, authorisation, firewall, VPN, driver or desktop configuration change was made.

## Verification status

- Twelve Python collector/security/deployment-guard tests: PASS.
- Live installed helper, as Amus, for all three filters: PASS; output contained real processes, filtering was correct and samples took about 0.42 seconds.
- Android Studio assembly, eight Android unit tests and lint: PASS. Three physical Pixel tests: PASS (all three process-owner filters, authenticated status/session reuse, wrong-pin rejection). The app update was installed successfully; the temporary test package was removed.

Final APK installed on Pixel 9a. Automated phone tests verified real data; a stable visual capture of the live list was not obtained during the UI check. A second-network test remains pending.
