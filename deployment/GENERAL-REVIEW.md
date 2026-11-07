# General settings, local accounts and process sorting

Replace only `/usr/local/libexec/sentinel-status-v1.py`. Existing SSH keys, forced-command path, source-IP restrictions, services, ownership, mode, SELinux label and networking are preserved. The desktop runner is stopped; the updater takes its existing ownership lock and rejects unexpected baseline/key-file changes.

New functionality:

- Status and process responses discover local human login accounts from `/etc/passwd`, using UID_MIN/UID_MAX from `/etc/login.defs` and excluding non-login/service shells. Only UID and username are returned, with a 256-account bound. No shadow/password, home-directory, browser or document contents are read. Every process refresh re-discovers accounts so additions/deletions update automatically.
- A strictly parsed new request: `sentinel-processes-v2 <numeric UID|all> <cpu|ram> <user|all>`. No shell expansion, names, paths or arbitrary arguments. UID filtering and exclusion of system accounts happen before server-side sorting and the existing 200-row response limit. Existing status, health and exact legacy process requests remain compatible with the currently installed app.
- Health discovers mounted local block filesystems from `/proc/self/mountinfo`, deduplicates shared devices, and reports capacity at their actual mount points instead of assuming a particular HDD path. Network/pseudo filesystems are excluded to avoid slow remote capacity reads. Prior slow firmware-sensor exclusions remain.

The app supports hostnames/MagicDNS, IPv4/IPv6, SSH ports/logins, a user-supplied verified host fingerprint and optional device name. Fresh installs have empty connection settings. An upgrade preserves saved settings and the previous implicit connection only if a prior cached sample exists. SSH remains public-key-only, pinned, without trust-on-first-use; supported modern SSH defaults replace one-server-only algorithm restrictions. No server SSH configuration is changed.

The process page defaults to user processes, provides discovered-user and CPU/RAM filters, and shows compact name/owner/CPU/RAM rows. PID, state and runtime expand on tap; system processes can be included explicitly. Search applies to the capped sample.

Apply after review:
```
pkexec /usr/bin/python3 -I /home/Amus/Android/PC_moniter_app/deployment/update-general-helper.py --apply
```

The updater backs up the helper under `/var/lib/sentinel-status-pairing/general-TIMESTAMP`. Rollback restores only that file, refuses later edits and does not touch SSH authorisations/services:
```
pkexec /usr/bin/python3 -I /home/Amus/Android/PC_moniter_app/deployment/update-general-helper.py --rollback /var/lib/sentinel-status-pairing/RECORDED_GENERAL_DIRECTORY
```

Verification includes account addition/removal fixtures, malicious request rejection, filtering/sorting before the row cap, local mount discovery and remote-mount exclusion, connection validation, typed queries and retained health timeout regression tests. Physical phone checks cover both sort orders for all discovered users, system-process inclusion, normal status and repeated health refreshes after deployment.
