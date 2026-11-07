# Live process monitoring: helper-only update

The Processes page replaces all illustrative activity rows with live read-only process metadata. It uses the existing Sentinel key and pinned SSH connection. No browser integration is included.

## Exact workstation change

Replace only `/usr/local/libexec/sentinel-status-v1.py`, preserving its root ownership, mode and SELinux label. The existing key, authorised-keys file, forced-command path, phone-source restriction, SSH service and all network settings remain unchanged.

The updated helper keeps `sentinel-status-v1` and additionally accepts exactly:

- `sentinel-processes-v1 all`
- `sentinel-processes-v1 Amus`
- `sentinel-processes-v1 skyler`

These requests read PID, process name, effective UID/owner, kernel process state, CPU counters, resident pages and process start time from `/proc`. CPU is sampled over approximately 0.4 seconds and expressed as a percentage of total machine capacity. RSS includes shared memory and is not unique memory. PID plus start time prevents reusing old CPU readings after PID recycling.

No `cmdline`, `environ`, `fd`, `cwd`, document, browser or journal content is opened. No process control, signals, script launching or power action is exposed. Sampling remains unprivileged as Amus. Permission-denied/racing processes and capped lists are reported honestly. Owner filtering happens before the 200-row cap; scans cap at 8192 processes.

## Backups and rollback

`update-process-helper.py` checks the old helper and authorised-key checksums, refuses unexpected changes and takes the existing project ownership lock. Default invocation prints a plan and makes no changes. On approval, its administrator invocation is:

```
pkexec /usr/bin/python3 -I /home/Amus/Android/PC_moniter_app/deployment/update-process-helper.py --apply
```

A root-only backup of the previous helper and its metadata is created under `/var/lib/sentinel-status-pairing/processes-TIMESTAMP`. No SSH reload is needed. Rollback uses:

```
pkexec /usr/bin/python3 -I /home/Amus/Android/PC_moniter_app/deployment/update-process-helper.py --rollback /var/lib/sentinel-status-pairing/RECORDED_PROCESS_DIRECTORY
```

Rollback restores only the helper and refuses to overwrite subsequent edits. It does not touch authorised keys or services. Exact reviewed source hashes are recorded in `process-manifest.json`; the installer also guards the existing authorised-keys checksum.

## Verification

Pure collector tests cover process names containing parentheses/spaces, total-machine CPU normalisation, PID reuse, counter resets, forbidden owner arguments and the absence of command/content fields. Android tests validate rows, owner filtering contracts and stale process states. After applying the helper update, real Pixel tests will verify all three owner filters and confirm the original status request still works.

## Applied and verified

Owner approved this helper-only update. Backup: `/var/lib/sentinel-status-pairing/processes-20261027T051359075742Z`. Installed helper and unchanged authorised-key checksums were verified after deployment. Android Studio build, eight JVM tests and lint passed; twelve Python tests passed. Three physical Pixel tests passed, including all process-owner filters, original status/session reuse and wrong-host-pin rejection. Sentinel 0.4-processes is installed; the temporary test package was removed.
