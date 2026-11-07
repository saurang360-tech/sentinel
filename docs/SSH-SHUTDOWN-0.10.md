# Sentinel 0.10: protected connections and PC-authenticated SSH shutdown

The final design follows the owner's latest instruction: shutdown asks for the
PC's administrator/sudo password, not a fresh phone fingerprint/PIN. Phone
verification separately protects saved connection settings and terminal details.

## No additional PC installation

Power actions use the existing Terminal SSH key and the existing SSH route.
Nothing is installed/downloaded onto the PC for shutdown. No SSH keys, sudoers,
polkit rules, service/listener, networking or authentication configuration changed.
The earlier proposed dedicated PC power helper was removed before deployment.
Monitoring continues using its already installed read-only helper.

The account must already have sudo permission, with sh, sudo and systemctl
available. Sentinel does not grant administrator rights. If the PC refuses the
password, account permission or shutdown due to blockers, the app reports it.
The ordinary remote session's CanPowerOff check returned administrator
verification required; the final implementation therefore authenticates using
existing sudo/PAM policy instead of trying to bypass that requirement.

## Request protocol

1. The user explicitly chooses shutdown and enters a masked PC sudo password.
   It is never saved with connection profiles or interpolated into command text.
2. A fresh SSH session verifies the pinned host and authenticates using the
   previously authorised Terminal key. There is no new PC key to pair.
3. A non-PTY shell receives a fixed script. Password bytes are sent only after
   the expected sudo prompt arrives on SSH stderr. No terminal echo is enabled.
4. `sudo -k -S -p <fixed-marker> -v` verifies PC administrator authentication.
   No power action has been requested at this point. Cached sudo authentication
   is not accepted as a substitute for the entered credential.
5. For the separate "Verify PC authentication — no shutdown" action, the script
   ends here. It contains no power command or execution branch.
6. For shutdown, after verified authentication and a fresh foreground/selected-
   profile check, the app sends one fixed execution token. Only then can the
   script call `sudo -k -S -p <fixed-marker> -- /usr/bin/systemctl
   --check-inhibitors=yes poweroff`. The same request-only password may answer
   this second sudo challenge. It is never placed in argv, files or shell code.

The password is entered once for each requested action, kept in memory during
that request, and its transmitted byte buffer is overwritten on completion.
UI input is cleared on submit/cancel and is not saved across recreation or
backgrounding. Raw stdout/stderr are not displayed/logged. Sentinel does not
claim control over independent PC/PAM audit policies or the user's keyboard.

Repeated password prompts abort; no automatic password retries or shutdown
retries occur. Missing authentication prompts fail closed before the execution
token. Lost replies after that token are reported as uncertain, because a
connection ending does not prove a PC powered off. No force/ignore-inhibitor,
reboot or logout action exists. Backgrounding, changing profiles and cancelling
invalidate pending requests and disconnect their SSH session. A power request
already delivered cannot be recalled by cancellation.

## Remembered connections and display protection

Host/port/SSH-login combinations are saved separately, up to 64 profiles. Saving
an existing combination updates its fingerprint/display name without duplicating
it. Selection survives reopening. Existing explicit settings migrate to an
AES-GCM encrypted vault only after its replacement ciphertext is prepared; old
plaintext and sample-cache preferences are removed after successful persistence.
The encryption key stays in Android Keystore and requires an unlocked device.
Corrupted ciphertext fails closed and is not silently overwritten.

The existing monitoring/Terminal private keys retain their aliases and are
never exported or replaced. This app does not remember SSH passwords or PC sudo
passwords. Each PC/login must already authorise the appropriate existing SSH key.

Opening the app, monitoring and shutdown require no phone verification. Only
Settings and Terminal require Android verification before revealing saved
connection fields or terminal details; that access resets on backgrounding. Android 11+ uses the
system strong-biometric or device-credential prompt; Android 10 uses the system
screen-lock confirmation. Fingerprints and phone PINs are never received by the
app. FLAG_SECURE blocks screenshots and recent-app previews. Unsubmitted settings
and password fields are memory-only rather than saved instance-state values.

USE_BIOMETRIC is the only added permission and is an install-time permission for
using Android's own verification UI. Local-network permission handling remains.
No new runtime library, developer backend, telemetry or account was added.
Privacy/terms notice version 3 describes the updated flows.

## Verification and release status

Android Studio/MCP builds the preview APK, signed production APK and AAB. Eighteen
JVM tests include fixed-script tests with stub sudo/systemctl, verifying bad
password/cancel paths cannot execute the command, check-only cannot power off,
refused shutdown is not accepted, and command arguments remain fixed. These tests
cannot reach the real systemctl executable.

Device tests cover multi-login persistence, encrypted settings with no plaintext
host/user data, tamper refusal, migration, existing settings/decoders and privacy.
A read-only existing-SSH capability test was performed without sending a power
command. Existing Python collector/security tests remain separate from this new
app-only power path. Actual PC password verification must be performed privately
by the owner in the app; passwords are never requested through chat or tools.
No real shutdown is performed by automated or UI verification.

Public release still requires publisher/contact/policy URL completion and Play
Console testing/declarations/review. Previous 0.9 artifacts remain rollback files.
Do not uninstall or clear the paired preview when upgrading. A new production
package still needs its own existing SSH authorisation; keys cannot be imported
from another app or transferred from the separate preview installation.
