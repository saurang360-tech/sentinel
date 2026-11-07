# Live health: helper-only update

Replace `/usr/local/libexec/sentinel-status-v1.py` with the staged, self-contained collector. Keep the existing phone key, source restriction, forced-command path, file ownership, permissions and SELinux label. No service installation/reload, authentication, driver, storage or network changes.

The single additional allowed request is `sentinel-health-v1`. Original status and all three process requests remain supported. It runs unprivileged as Amus and reads CPU counters, MemAvailable-based RAM/swap, memory pressure, mounted filesystem capacity, per-interface and physical-disk transfer counters, hardware-exposed temperatures/fans/component power, GPU metadata and optional exposed counters, OS/kernel/uptime/load. Two counter samples are separated by 0.4 seconds. It also calls `systemctl list-units --failed --type=service --output=json` for system and current user with bounded timeouts, returning only service names/counts. No journal, document, process arguments, environment, browser content, SMART diagnostics, signals or power actions.

Missing sensors remain null. Unmounted data storage is not replaced with root filesystem capacity. Tunnel and physical interface throughput are not summed. No wall-power estimate is produced. Phone polling runs only while a monitoring page is visible, every 10 seconds with reconnect backoff and stale state after 20 seconds.

`update-health-helper.py` verifies the current helper and authorised-key hashes against the approved baseline and health-manifest.json, takes the existing desktop project ownership lock, creates a root-only backup under `/var/lib/sentinel-status-pairing/health-TIMESTAMP`, and atomically replaces only the helper. Default mode is read-only planning. Apply after review:

```
pkexec /usr/bin/python3 -I /home/Amus/Android/PC_moniter_app/deployment/update-health-helper.py --apply
```

Rollback refuses later edits and restores only the helper:

```
pkexec /usr/bin/python3 -I /home/Amus/Android/PC_moniter_app/deployment/update-health-helper.py --rollback /var/lib/sentinel-status-pairing/RECORDED_HEALTH_DIRECTORY
```

Collector tests cover CPU/iowait/guest accounting, counter resets, MemAvailable and swap, unmounted data storage, and real read-only sampling. Physical Pixel verification will cover health, original status/session reuse, processes and host-pin rejection after deployment. Android changes replace all demo Health/Details values with authenticated samples or explicit unavailable states.

## Applied and verified

Owner approved the read-only health helper update. Backup: `/var/lib/sentinel-status-pairing/health-20261027T053512460705Z`. Installed helper SHA256: `c33d3095b425e061ae223fc166b2e7f34d5fd11bcea71bdd932c23952e5c3c26`. Existing authorised-key checksum remains unchanged. Existing private SSH/VNC listeners remain unchanged.

Android Studio MCP build, nine JVM tests and lint completed successfully (no lint errors; existing dependency/style/resource warnings remain). Seventeen Python tests passed. Four physical Pixel tests passed over the existing Tailscale path: real CPU/RAM/storage/network/temperature response and malformed-value rejection, original status/session reuse, all process owner filters, and wrong-host-pin rejection. Sentinel 0.5-health (versionCode 5) was installed successfully; temporary instrumentation package removed. The phone was locked during the final UI inspection, so no claim of final screenshot verification is made. Different-network testing was not repeated in this session.
