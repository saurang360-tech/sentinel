# Sentinel 0.10 — Data safety worksheet

Updated 9 November 2026 for the current direct-SSH design. This is a submission
worksheet, not a completed Play Console declaration or approval.

There is no developer backend, account, analytics, advertising, automatic crash
reporting, identifier collection or cloud sync. Do not add any without revisiting
the policy, notice and form. Live readings/terminal output are memory-only; the
saved host/login/fingerprint/display name are local configuration. SSH private
keys stay in Android Keystore. Public keys are stable connection identities and
can be saved on the user's PC. Explicit process watches and bounded transition
history are encrypted locally; optional runner labels/exit results are stored on
the user's PC. Up to 256 hashed notification receipts are saved in private phone
preferences. None are sent to a developer service.

Proposed answers, subject to the publisher's final review of the release artifact:

- Data collected by the developer: No.
- Data shared by Sentinel with other companies: No, for the documented direct
  SSH and explicitly user-initiated key-copy/helper-export flows.
- Ads: No. Account creation: None. No account-deletion web endpoint is needed
  for an account system that does not exist. Local erasure and PC-side key
  revocation are explained in the app.
- Encryption: SSH payloads are encrypted directly to the user-configured PC,
  with strict host fingerprint checking. DNS/network routing metadata is not
  concealed by Sentinel; VPN/DNS providers have independent practices.
- No contacts, location, microphone, camera, phone, photos, broad storage,
  or advertising-ID permissions. POST_NOTIFICATIONS is requested only for an
  explicit background-monitor start or local notification test.
- INTERNET is an install-time normal permission. ACCESS_LOCAL_NETWORK is
  requested through Android on API 37+ after the in-app disclosure. Denial
  prevents SSH connections; legal text remains available.

Background-monitor update: optional direct-SSH sessions use a connectedDevice
foreground service, FOREGROUND_SERVICE, FOREGROUND_SERVICE_CONNECTED_DEVICE and
CHANGE_NETWORK_STATE. The app does not change network settings. Sessions hold
connection configuration in memory without weakening the locked vault and show
an ongoing notification with Stop. No boot restart, relay or analytics is added.
Foreground-service Play declarations, purpose eligibility and real idle/overnight
checks are outstanding release requirements. Notification handoff does not prove
display or user receipt. Privacy notice version 7 describes these flows.

Basis: Google's current guidance excludes on-device processing and end-to-end
encrypted data unreadable by intermediaries/developer from collection reporting.
It also excludes transfers reasonably expected from a specific user-initiated
action from sharing. This is our interpretation for this architecture, not a
guarantee of Google's assessment. The PC receives the login, public key and
commands; these flows remain disclosed in the privacy policy even if exempt
from the form's collection categories. If any publisher relay, telemetry,
support upload, notification service or other SDK is added, reassess every answer.

Publisher-provided support email may receive information people voluntarily
send. Decide its retention/handling policy and add it to the final policy before
opening that support channel; never request private SSH keys or passwords.

Official sources checked:
- https://support.google.com/googleplay/android-developer/answer/10787469
- https://support.google.com/googleplay/android-developer/answer/10144311
- https://developer.android.com/privacy-and-security/local-network-permission

The public privacy policy still needs a real inquiry/contact mechanism and a
publicly accessible static non-PDF URL, entered in Play Console. Fill in
publisher.json, update the bundled contact paragraph, regenerate HTML and rebuild
before submission. Terms/privacy acceptance is stored locally with notice version;
it is not sent to the developer and is not permission for developer collection.

0.10 update: USE_BIOMETRIC enables Android's own saved-settings/terminal verification; no
biometric template or phone PIN enters the app. Saved connection profiles are
AES-GCM encrypted locally. Shutdown and reboot ask for a PC sudo password per request,
sends it only over pinned SSH when the PC's sudo prompt arrives, and never saves
it or sends it to the developer. This direct encrypted credential flow must stay
disclosed in the public privacy policy even though no developer collection occurs.
The optional power path installs no additional PC software and changes no PC
permissions. Monitoring still requires the reviewed helper.

9 November alert/assurance update: global quiet hours, severity, cooldown and health
opt-in are private phone settings; at most 256 mute rules are hashed. Deferral
queues are memory-only. PC expected-success labels and last verified timestamps
remain locally until forget-expectations; clearing job history leaves those rules.
No third-party relay, background log upload or new SDK is added. Real sensor,
nighttime notification and independent-device checks remain release requirements.
