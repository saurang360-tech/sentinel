Historical record/planning document. Feature descriptions, test counts and
setup details below apply to that pass and may be superseded. Current behavior:
README.md and docs/WIDGET-WORKSPACE.md. Current release status and reusable checks:
docs/RELEASE-READINESS.md. Historical personal deployment data are not public
onboarding defaults.

# Architecture proposal and first-version contract

## Recommendation

Use a native Kotlin/Jetpack Compose Android app, developed and checked in Android Studio through its MCP integration. It provides native navigation, accessibility, lifecycle control, Android secure storage and an SSH transport without browser restrictions. The supplied monochrome reference suits a Compose interface well.

| Approach | Benefits | Costs for this use case |
|---|---|---|
| Native Android — recommended | Direct SSH over Tailscale; native credential handling; predictable lifecycle and touch UI | Android build/toolchain and app maintenance |
| Installable web app | Easy updates and cross-device access | Cannot directly use SSH or Termius keys; needs an authenticated HTTPS backend plus certificate/pairing/session management |
| Web UI inside a native shell | Reuses web layouts; native bridge can offer SSH | Adds bridge security and two UI/runtime layers without a clear benefit for four pages |

## Transport proposal: SSH first, no new listener

Reuse the existing private route to Amus@100.75.247.115:2222. A native SSH library can run a fixed, reviewed, unprivileged collector and read versioned JSON. No persistent monitoring service is required initially. A long-lived SSH connection can amortise handshake cost while the screen is visible. Cancel collection when backgrounded and disconnect after a short idle period.

A small HTTPS service would be justified if multiple clients or browser access become requirements. It would require a reviewed binding, authentication, TLS, rate limits and operating lifecycle. Do not deploy one solely for this prototype.

Termius's dedicated private key is not available to this app and must not be copied. Later pairing should generate an app-specific key, keep it in app-private storage encrypted by an Android Keystore key, and authorise only its public key after review. Verify SSH-library and Keystore algorithm compatibility before choosing the key type. Do not assume Ed25519 key handles are supported by Android Keystore or a particular SSH library. Pin the verified host fingerprint; mismatches block connection.

Pairing may require a new restricted authorised-key entry, preferably a fixed-command protocol handler with no forwarding and a deliberately small request vocabulary. The existing Termius entry remains untouched. A phone-side biometric gate can protect access locally but does not replace server authorisation.

## Boundaries

UI -> screen state/ViewModel -> typed repository -> authenticated SSH transport -> fixed collector -> allowlisted data sources.

A transport result must include protocol version, sample timestamp, collection duration, boot ID and per-field status (available, unsupported, permission denied, temporarily unavailable). Values use bytes, seconds, degrees Celsius and explicit rates at the protocol layer; presentation converts to GiB/TiB/MiB/s. Unknown values are nullable, never zero by default. Resource errors are per field, not silent whole-response success.

CPU and interface/disk rates need two samples and monotonic elapsed time. First samples show warming up. Process identity is PID + start time to handle PID reuse. CPU percentages must state whether they are total-machine or per-core-equivalent; the UI should use total-machine percentage consistently. Do not sum system and home Btrfs mounts as separate capacity.

## Sampling and contact state

- Visible Power/Health: proposed 5-second interval, one request in flight, 3-second response deadline. Initial rate estimate needs a second sample.
- Details: 15–30 seconds; drive-health data on explicit request or long cache TTL, never continuous SMART polling.
- Activity: 10 seconds while visible; limited, sorted process list. No raw argv, environment, shell history or file content.
- Screen off/background: stop polling; lifecycle-aware coroutine cancellation. No permanent foreground monitoring service in v1.
- Backoff after failure: 5, 10, 20, 40, then 60 seconds with jitter. Manual retry cancels duplicate scheduled attempts.
- Fresh: valid authenticated response within 15 seconds. Older cached response is stale. An explicit connection failure shows unreachable with previous readings marked stale. Before the first valid response, show unknown.
- Show last successful contact and sample age separately from the most recent failed attempt. A future/distant timestamp does not make a response fresh; use local monotonic receipt age.
- Tailscale reachability and authenticated OS responsiveness are distinct signals. No inference of power-off from silence, no delivery promise when offline/asleep, no automatic retry of a shutdown command.

## Data sources and scope

| Screen | Source proposal | Limitations |
|---|---|---|
| Power | SSH handshake + valid collector response; last local monotonic receipt time | Tailscale alone does not prove OS health |
| Main health | /proc/stat, /proc/meminfo, statvfs for / and /mnt/titus-hdd, /proc/net/dev, coretemp hwmon | GPU utilisation/VRAM only if readable supported counters exist; no forced driver query |
| Additional health | /proc/uptime, /proc/loadavg, /proc/diskstats, hwmon, unprivileged systemd D-Bus failed-unit properties, interface counters, bounded connection timings | Component power/fans unavailable until exposed. Tailscale status only bounded local output; latency is labelled transport-specific. Drive SMART is optional and separately reviewed. Events are bounded unit metadata; no broad journal scraping. |
| Activity | Restricted /proc PID metadata: uid, comm, stat runtime/CPU, status memory; process ancestry where readable | Permission denial is explicit. Classifying shells does not prove an interactive terminal session; ancestry does not prove an application window. No command arguments, document paths or contents. |
| Windows, later opt-in | Reviewed KWin/desktop integration scoped to Amus | Wayland blocks general cross-client enumeration. An owner-session helper cannot imply permission to enumerate Skyler's desktop. |
| Browser tabs, later opt-in | Explicitly approved browser extension + minimal local integration | Titles/URLs are sensitive; default off, exclude private browsing and Skyler, never cookies/history/content. No integration installed in v1 preview. |

## Shutdown design, pending review

The current preview only displays a confirmation dialog; its final action is disabled. No shutdown backend, polkit rule or sudoers change exists.

The proposed mechanism is one fixed operation mediated by a narrowly scoped authenticated handler using systemd-logind's org.freedesktop.login1.Manager PowerOff(false). Before offering it, inspect CanPowerOff, inhibitors and active sessions read-only. Deny or require owner coordination when other users/sessions or inhibitors are present. Do not silently acquire ignore-inhibit or multiple-sessions privileges.

If policy does not authorise the operation, return permission denied. Prepare the exact minimal policy/helper for review rather than granting broad commands. Polkit privileges scoped only to the Amus UID would apply to all its processes, so do not mistake UID scoping for app-specific isolation: a dedicated helper identity and authenticated IPC may be needed. No generic shell or arbitrary executable argument is allowed. Audit only request IDs/results, not secrets. Bind one short-lived confirmation nonce to a recent connection and discard it on disconnect; duplicate requests are rejected. Connection loss after acceptance means delivery unknown, not success. Never queue a shutdown for later reconnection.

No reboot, wake, process termination, window closing or script execution. “pepper” is intentionally undefined. A future allowlisted launcher requires a separate review and fixed typed actions; no generic command field.

## Official references

- Android Keystore: https://developer.android.com/privacy-and-security/keystore
- Systemd login1 API and power authorisation: https://www.freedesktop.org/software/systemd/man/262/org.freedesktop.login1.html
- Browser tab properties/permissions: https://developer.chrome.com/docs/extensions/reference/api/tabs
- Compose setup: https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
- AGP 9.2 compatibility: https://developer.android.com/build/releases/agp-9-2-0-release-notes
