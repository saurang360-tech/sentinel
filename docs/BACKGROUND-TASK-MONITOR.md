# Optional background task monitor — 7 November 2026

This extends the earlier foreground-only task-watching pass with an explicitly
started Android foreground service. It uses the existing monitoring SSH key and
pinned host. No relay, remote software installation, shell privilege, wake/sleep
policy, or network configuration is added.

## Use

After accepting privacy notice version 5 and pairing/updating the Linux helper,
open Overview and choose **Start background monitor**. Android notification
permission is requested at that action. An ongoing notification shows the session
and provides **Stop**. **Test notification** tests a local Android handoff only.

The service polls exact watched process identities and runner jobs approximately
every 30 seconds. It continues independently of the activity. It holds the selected
configuration in memory and uses the monitoring key; it does not decrypt/read the
locked phone's saved vault or relax the vault's unlocked-device requirement.
Background observations are merged into existing phone watches when the app returns.
New watches, profile changes/settings saves, watch deletion or local app erasure
stop an existing session. Restart explicitly after those changes.

A session is not automatically restored after process death, user stop, force stop,
phone restart or an Android service restriction. Android idle/battery behavior and
network availability can delay or prevent polls. No battery-optimization exemption,
boot receiver or always-held wake lock is used. This is best-effort direct polling,
not guaranteed overnight push delivery. PC runner results remain durable on the PC
and can be fetched after reconnection.

## Outcome and alert behavior

- The first successful job sample establishes a baseline. Existing completed jobs
  do not trigger historical notification floods. A running selected process that
  disappears on the first check may trigger an ended-unknown notification.
- Direct runner exits produce succeeded/failed results. A disappeared arbitrary
  process still produces ended unknown, never fabricated success.
- New outcomes discovered in one poll are grouped into one Android notification.
- Up to 256 hashed event receipts are saved in private preferences. No host/login,
  task label, command/output or credential is stored in those receipts. Stable
  notification tags and `onlyAlertOnce` further limit crash-window repetition.
- Receipts are saved after Android accepts a notification call. This is not proof
  of display, sound, user attention or end-to-end exactly-once delivery. A crash
  between handoff and receipt persistence can still cause a repeat attempt.
- Permission/channel blocking is surfaced. Retry delays grow on failures, up to
  a few minutes. Host identity/authentication failures stop automatic retries.
- Diagnostics show loop heartbeat, last confirmed PC observation, current session
  state and last outcome handoff. A loop heartbeat does not prove the PC responded.
- A later pass adds per-task mute, quiet hours, severity, cooldowns, sustained health
  warnings and expected-success checks. See [current alert behavior](ALERTS-HEALTH-ASSURANCE.md).
  Optional digests remain unimplemented.

## Android implementation rationale

The service uses `connectedDevice` for interaction with an external PC over a
network connection. Its declared prerequisites are `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_CONNECTED_DEVICE` and `CHANGE_NETWORK_STATE`; no network-change
API is invoked. Notification permission and both notification channels must be
available before a session starts. The service is not exported and returns
`START_NOT_STICKY`. Sensitive session contents are not passed in Intents.

Primary references consulted:

- [Android foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Android foreground service timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout)

Play foreground-service declarations and real overnight/background/idle testing
remain release gates; this pass does not submit or claim store approval.

## Tests

`TaskMonitorServiceTest` uses debug-only injected synthetic sources and a synthetic
activity. It exercises continued polling after the activity is backgrounded,
completion notification handoff, repeated-result deduplication, the notification
Stop action, historical baseline suppression and authentication/host-error retry
termination. It never opens real SSH connections or changes pairing settings.
Synthetic receipts use a separate namespace. Notification permission is temporarily
granted for the run and restored afterward. This validates Android mechanics,
not real PC/network/Doze reliability. Results are in background-validation.txt.
