# Review: first-page read-only status pairing

Sentinel 0.3-status is installed on the Pixel. The connection settings are prefilled:

- Titus: 100.75.247.115, SSH port 2222, username Amus
- Phone source: 100.70.104.44 over the existing Tailscale path
- Pinned server Ed25519 fingerprint: SHA256:5fLmmMkeCf6pR5n99GMHYeVJYIVp2gGZDc0P2XZ4+Yg
- New Sentinel-only public-key fingerprint: SHA256:UX+bdVK9xpGKJW+sjrf8tqpBUS9ao/8hcl9fuLG57Do

The private key was generated inside Android Keystore and is not exported. No existing Termius or workstation private key was read or copied. There is no password field because the existing SSH endpoint does not accept passwords.

## Exact proposed workstation changes

1. Create `/usr/local/libexec/sentinel-status-v1.py`, root-owned, mode 0644. OpenSSH executes it as **Amus** using `/usr/bin/python3 -I`; it does not run as root.
2. Append one new key to `/etc/ssh/compute-authorized-keys/Amus`, retaining every existing byte and preserving its owner, group, mode and SELinux label. Its options restrict it to the phone's 100.70.104.44 address, disable forwarding/PTY/user-rc via `restrict`, and force only the status helper. The helper accepts exactly `sentinel-status-v1` and refuses shells, SFTP requests and other commands.
3. Create a root-only, timestamped backup below `/var/lib/sentinel-status-pairing/`, containing the previous public authorised-keys file, metadata and rollback hashes. Create `/usr/local/libexec` only if absent.

The helper returns protocol version, hostname, boot ID, uptime, systemd state and sample time. It reads `/proc` and runs only `systemctl is-system-running`. It does not inspect documents, command arguments, browser data or journal content.

No SSH daemon reload/restart is needed. No service/listener, firewall, Tailscale policy, sudoers, driver or desktop change is included. Existing Termius, Windows and Skyler paths remain unchanged. No power action is implemented.

## Reviewable source and preflight

- `sentinel-status-v1.py`: complete fixed-command handler
- `pair-status-key.py`: scoped installer and checksum-guarded rollback
- `phone-public-key.pub`: only Sentinel's new public key
- `pairing-manifest.json`: public-key fingerprint and source checksums

`python3 deployment/pair-status-key.py` prints the exact plan without changing anything. It currently reports `applied: false`.

Existing authorised-keys SHA-256 must remain:
`c2afd8e49f43f2acc5837178b38170b864130879dfa53d4d7797eb7f3949a3c9`

If it changes, or the helper already exists, installation stops for another review. Installation must coordinate the existing desktop project's continuation/ownership lock; the installer takes its exclusive nonblocking ownership lock and refuses to compete with another owner.

After approval, the narrow installation command is:

```
sudo /usr/bin/python3 -I /home/Amus/Android/PC_moniter_app/deployment/pair-status-key.py --apply
```

## Rollback

The installer prints the exact backup directory. With that recorded path:

```
sudo /usr/bin/python3 -I /home/Amus/Android/PC_moniter_app/deployment/pair-status-key.py --rollback /var/lib/sentinel-status-pairing/RECORDED_DIRECTORY
```

Rollback restores only the prior authorised-keys file when it still matches the deployed hash, and removes only the checksum-matching new helper. It refuses to overwrite subsequent changes. No services are restarted. Scoped backups remain available.

## Verification before approval

Android Studio assembly, six Android unit tests, five Python helper/pairing tests and lint completed successfully. The unit tests cover stale clocks, host-key mismatch, invalid settings, malformed samples/signatures and SSH ECDSA signing format. The helper tests reject other requested commands and show explicit unknown state for unavailable systemd. No pairing change has been applied.

## Device checks after approval

Verify authenticated status and uptime from the Pixel; navigate away/background to stop polling; reconnect without cached-online claims. Test wrong-pin rejection and invalid endpoint handling. Confirm the original authorised-key line remains byte-for-byte intact. Home Wi-Fi and a different network are separate user-coordinated tests; never change the phone network or shut down Titus unexpectedly.

## Applied with owner approval

The owner explicitly approved read-only pairing in chat. Fedora’s native administrator prompt authenticated the scoped installer. Backup: `/var/lib/sentinel-status-pairing/20261027T045457390466Z`. The existing authorised keys were verified byte-for-byte unchanged before the appended entry, and the installed helper hash matches the reviewed source. No services were restarted.

On the physical Pixel 9a, two read-only instrumentation tests passed: authenticated status retrieval twice using the Keystore key (including session reuse), and rejection of a deliberately wrong host pin before authentication. The temporary test package was removed afterward. Current-network connectivity is verified; a second network remains untested.
