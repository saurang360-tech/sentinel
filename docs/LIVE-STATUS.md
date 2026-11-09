Historical record/planning document. Feature descriptions, test counts and
setup details below apply to that pass and may be superseded. Current behavior:
README.md and docs/WIDGET-WORKSPACE.md. Current release status and reusable checks:
docs/RELEASE-READINESS.md. Historical personal deployment data are not public
onboarding defaults.

# First-page live status — Sentinel 0.3-status

The Power page now uses real connection results. Health, Details and Activity still show labelled demo data. Shutdown is disabled and has no callable backend.

## Settings and identity

The header's Settings button opens host address, SSH port, Linux username and pinned server fingerprint. Titus's known details are prefilled. Settings accept a Tailscale IPv4 endpoint, valid port/user and SHA256 pin. The existing port 2222 is key-only, so there is no misleading password field.

Sentinel generates a separate ECDSA P-256 signing key through Android Keystore. SSH signatures use the Keystore private-key handle; the private key is not exported, imported from Termius or stored as PEM. Only its public key is displayed/copied/exported for pairing. App data backups/transfers are excluded. Settings and cached status contain no private key/password/token.

## Status meaning

- **Status unknown**: no authenticated sample has been received.
- **Checking Titus**: a connection/status request is in progress.
- **SSH responding**: the pinned host key verified, but app-key authentication was not completed. Pairing instructions are shown; repeated authentication attempts stop.
- **System responding**: an authenticated, validated status response arrived. OS state (running/degraded/starting/etc.) and sampled uptime are shown separately.
- **Unreachable**: no verified status contact could be obtained. The phone may have lost Tailscale; Titus could be asleep/off/disconnected. This does not prove power-off.
- **Stale data**: an authenticated sample is older than 20 seconds or restored from a previous app process. Cached values are labelled.
- **Host key mismatch**: fail closed; no bypass/accept-new-key button. Retry requires owner action.
- **Status unavailable**: SSH authenticated but the fixed status command did not return a valid response. A missing helper is not shown as online-with-zero-metrics.

The existing host fingerprint is pinned exactly. The SSH client permits public-key authentication only, no password/keyboard-interactive fallback, no PTY/forwarding, and requests only `sentinel-status-v1`. The server's new key is separately source/command restricted as described in deployment/REVIEW.md.

## Lifecycle and failures

Reuse one authenticated SSH session while the Power page is visible. Poll every 10 seconds, one request in flight. Stop polling, freshness ticking and the SSH connection on leaving the page, backgrounding, changing settings or clearing the ViewModel. Manual settings tests are one-shot. Connection failures back off with jitter up to roughly 61 seconds. Pairing, security or invalid-response failures require explicit retry. Each operation has bounded connect/read/channel/overall deadlines; cancellation closes the session. Host receipt freshness uses monotonic time, not the server's wall clock. Last successful contact survives restart as stale cached data and is cleared on endpoint/pin changes.

## Verification

- Android Studio MCP assembly, Android-test APK assembly, six Kotlin/JVM tests and lint: PASS, including the final lifecycle refinement.
- Seven Python helper/pairing tests: passed. They cover disallowed commands, unavailable systemd, restricted key options, checksum/collision checks and refusal to overwrite later edits during rollback.
- New public key generated on the physical Pixel 9a, exported as public data only, and validated by the pairing plan.
- Owner-approved pairing: APPLIED. Backup is /var/lib/sentinel-status-pairing/20261027T045457390466Z. Existing authorised-key bytes were preserved exactly, file ownership/mode preserved, and SSH 22/2222 plus localhost VNC 5902 listeners remain intact. Two real Pixel instrumentation tests PASS: authenticated samples with SSH session reuse and rejection of a wrong host pin. The temporary test app was uninstalled.
- Home Wi-Fi and a different network remain separate user-coordinated tests.

## Dependencies and primary references

- Maintained JSch fork, pinned 2.28.8, BSD licences: https://github.com/mwiede/jsch/tree/jsch-2.28.8
- Bouncy Castle bcprov-jdk18on, pinned 1.86, used for Android-compatible Ed25519 host-signature verification: https://www.bouncycastle.org/licence.html
- Android Keystore: https://developer.android.com/privacy-and-security/keystore
- Fixed-command/key restrictions: https://man.openbsd.org/sshd.8#AUTHORIZED_KEYS_FILE_FORMAT

Physical UI inspection after this update remains unverified because the Pixel keyguard was visible during the check. Sentinel was reopened successfully behind it; unlock the phone to view the live page. No attempt was made to bypass the lock screen. Second-network and long-duration/background UI tests remain pending.
