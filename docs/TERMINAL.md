Historical record/planning document. Feature descriptions, test counts and
setup details below apply to that pass and may be superseded. Current behavior:
README.md and docs/WIDGET-WORKSPACE.md. Current release status and reusable checks:
docs/RELEASE-READINESS.md. Historical personal deployment data are not public
onboarding defaults.

# Basic SSH Terminal — Sentinel 0.8.0

Terminal opens a separate line-oriented SSH shell over the saved connection. It does not mirror an existing Kitty window or implement Kitty graphics, curses/full-screen applications or persistent screen sessions. No new package/library was added; Android Studio built the app using Gradle offline mode and existing cached dependencies. Bash and SSH were already installed on the PC.

The explicit Connect action uses a dedicated Android Keystore P256 identity, never the read-only monitoring identity or Termius credentials. Host fingerprints are pinned and mismatches fail closed. PTY type is dumb (80 × 24). Connect failures never trigger command replay or automatic reconnect. Leaving the page, opening Settings or backgrounding Sentinel closes the channel/session. Running foreground jobs may stop as with ordinary SSH disconnection.

Use Command or input plus Send/keyboard Send for lines. Ctrl+C, Tab, Esc, history up/down and Ctrl+D send their terminal control sequences. Enable Private input before entering a password; the app masks that field while the remote program controls PTY echo. Terminal scrollback is bounded at 80,000 UTF-16 characters and kept only in app memory; it is not saved by rememberSaveable or logged. Remote shell history behaves normally. OSC clipboard/title escape commands are discarded. Clear only clears the local display.

Authentication review and exact backup/rollback: deployment/TERMINAL-REVIEW.md. It authorises the new terminal key only from the phone's current tailnet address and forces installed /usr/bin/bash -l as Amus. SSH forwarding/user-rc stay disabled. The existing monitoring key and helper remain read-only and unchanged. This grants normal Amus shell access, not new sudo/root permissions.

Verification before authorisation: offline Studio build/lint, 15 JVM tests, 24 Python tests and two physical-phone security tests passed. Visual review confirmed all five tabs and the Terminal input/control layout on the Pixel. The interactive shell verification is intentionally deferred until the owner approves the separate key.

Sources: [OpenSSH authorised-key options](https://man.openbsd.org/sshd), [Kitty remote-control documentation](https://sw.kovidgoyal.net/kitty/remote-control/).

## Approved, enabled and verified

The owner approved the separate terminal-key authorisation. It was appended successfully; backup: `/var/lib/sentinel-status-pairing/terminal-20261030T180116348988Z`. Authorised-key checksum after pairing: `d4aa2a31ddbf19df47c6e543b5d990d914574a97d2a9b50ddbd653f91e0d6534`. All pre-existing key bytes were verified unchanged, and the monitoring helper checksum is unchanged. Existing private SSH/VNC listeners remain unchanged.

Eight physical Pixel tests passed in 90.892 seconds: interactive shell input, interruption of only its own sleep job, clean exit/no reconnect, separate/stable Keystore identities, fail-closed host-pin checks, read-only status/session reuse, process sorting/filtering and repeated health polling/reconnects. The temporary test package was removed. The app UI connected successfully: Disconnect and Connected status were visible and command input was enabled. No commands were sent by the final UI check. The Terminal page is ready on the installed Sentinel 0.8.0.
