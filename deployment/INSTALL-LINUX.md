# Connect another Linux PC

Sentinel 0.7 monitors one configured Linux PC at a time over an existing SSH route, including Tailscale/MagicDNS or a private LAN. It does not create network access, discover SSH credentials or install itself on the PC. Windows/macOS monitoring is not implemented.

1. Confirm SSH access through your existing private route. Keep the normal SSH login and port; no new listener is required.
2. Review `sentinel-status-v1.py`. An administrator must install this self-contained Python 3 helper as `/usr/local/libexec/sentinel-status-v1.py`, root-owned and not writable by the SSH account. Use the normal SELinux file context for that directory on SELinux systems. The helper itself runs unprivileged as the SSH login and requires Linux procfs/sysfs and systemd for OS/service state; unsupported data stays unavailable.
3. Open Settings in Sentinel. Enter the hostname/IP, port and SSH login. Obtain the server's SHA256 SSH host fingerprint independently from the PC administrator and enter it; Sentinel never silently accepts a new host key.
4. Expand Security & pairing and copy the phone's public key. The private key stays in Android Keystore. An administrator must append that public key to the SSH login's configured authorised-key file, with `restrict,command="/usr/bin/python3 -I /usr/local/libexec/sentinel-status-v1.py"` ahead of it. Restrict its source to the phone's stable tailnet address if appropriate. Preserve existing keys and access; do not replace the file or add sudo rights. Review the actual SSH configuration and key-file ownership/location on that PC first.
5. Save and test the connection. Settings shows discovered local login accounts. Processes refreshes that list automatically and offers UID-based filtering, CPU/RAM sorting and optional system processes.

Regular accounts are local /etc/passwd entries in the configured UID_MIN..UID_MAX range with a login shell. Directory-only/NSS users and service accounts are outside automatic human-account discovery. Account metadata is capped at 256, process scanning at 8192 and each sorted response at 200 rows. Permission-limited process access remains limited; Sentinel does not elevate privileges. Refreshes pause in the background.

Storage discovery lists distinct mounted local block filesystems, including their actual mount paths. Network mounts are not probed. No drive contents are read. Unsupported SMART/GPU/fan/power information remains unavailable. Firmware-backed gigabyte_wmi/acpitz sensors are excluded from live polling because they can block for seconds. Shutdown remains disabled.

For rollback, restore the previous helper from the administrator's recorded backup and remove only the specifically added phone key if disconnecting Sentinel. Do not alter unrelated SSH keys, networking or services.
