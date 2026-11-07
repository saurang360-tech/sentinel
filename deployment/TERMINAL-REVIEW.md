# Interactive terminal access — separate key

The Terminal page is built and installed. It uses the existing JSch library, SSH/Tailscale route and installed Bash. The build ran offline. No extra packages or libraries were downloaded; no Kitty changes or remote-control endpoint are needed. This is a basic line-oriented SSH shell, not an existing Kitty window or a full-screen terminal emulator.

## Exact authentication change

Append only this phone’s new terminal public key to `/etc/ssh/compute-authorized-keys/Amus`. Fingerprint: `SHA256:69UEDl3VkzTh02EkEO8QrJuxvlUWJ6TgOKr1mT3TIcI`. The public key is in terminal-phone-public-key.pub; its private signing key stays in Android Keystore under a separate alias.

The exact appended key options are:

```
restrict,pty,from="100.70.104.44",command="/usr/bin/bash -l"
```

This authorises an interactive shell as Amus. Commands typed there can read/change files and run programs with Amus’s existing permissions. It is deliberately separate from the read-only monitoring key. Port, agent and X11 forwarding and SSH user-rc remain disabled; PTY is explicitly enabled for this key. There is no new root/sudo permission, service, listener, SSH daemon configuration or network change. Existing Termius, monitoring and Skyler keys remain byte-for-byte intact. The monitoring helper is checked and left unchanged. No shutdown/reboot/log-out is performed by verification.

## Backup and rollback

`pair-terminal-key.py` defaults to a read-only plan. It checks the reviewed key/helper/authorised-key checksums, takes the existing desktop ownership lock, preserves key-file ownership/mode/SELinux context and creates a root-only backup under `/var/lib/sentinel-status-pairing/terminal-TIMESTAMP` before appending the one key.

Apply after review:

```
pkexec /usr/bin/python3 -I /home/Amus/Android/PC_moniter_app/deployment/pair-terminal-key.py --apply
```

Rollback restores the recorded key file only if no subsequent key edits occurred:

```
pkexec /usr/bin/python3 -I /home/Amus/Android/PC_moniter_app/deployment/pair-terminal-key.py --rollback /var/lib/sentinel-status-pairing/RECORDED_TERMINAL_DIRECTORY
```

## App behaviour and verification

Connect is explicit. Leaving Terminal or backgrounding the app disconnects; reconnect opens a new shell and never replays commands. Scrollback/input stay in app memory rather than saved app logs/files. Use Private input for password prompts; normal remote shell history rules still apply. TERM=dumb supports commands and line input, Ctrl+C/Tab/Esc/history keys, not Kitty graphics or full-screen tools. Output is bounded, and OSC clipboard/title actions are discarded.

Offline Android Studio build/lint and fifteen JVM tests passed. Twenty-four Python tests cover existing collectors plus terminal-key options, injection rejection and baseline guards. Two physical-phone tests confirm separate stable keys and fail-closed host-pin checking. After approval, the terminal test will send only a printf marker, a sleep in its own shell interrupted with Ctrl+C, and exit; monitoring regression tests will also run.

References: [OpenSSH key options](https://man.openbsd.org/sshd) and [Kitty remote control](https://sw.kovidgoyal.net/kitty/remote-control/).

## Approved, enabled and verified

The owner approved the separate terminal-key authorisation. It was appended successfully; backup: `/var/lib/sentinel-status-pairing/terminal-20261030T180116348988Z`. Authorised-key checksum after pairing: `d4aa2a31ddbf19df47c6e543b5d990d914574a97d2a9b50ddbd653f91e0d6534`. All pre-existing key bytes were verified unchanged, and the monitoring helper checksum is unchanged. Existing private SSH/VNC listeners remain unchanged.

Eight physical Pixel tests passed in 90.892 seconds: interactive shell input, interruption of only its own sleep job, clean exit/no reconnect, separate/stable Keystore identities, fail-closed host-pin checks, read-only status/session reuse, process sorting/filtering and repeated health polling/reconnects. The temporary test package was removed. The app UI connected successfully: Disconnect and Connected status were visible and command input was enabled. No commands were sent by the final UI check. The Terminal page is ready on the installed Sentinel 0.8.0.
