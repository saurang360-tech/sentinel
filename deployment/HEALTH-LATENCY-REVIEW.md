# Health timeout fix — helper-only update

Reproduced on the Pixel: health poll failed at the response-read deadline with zero bytes received. On Titus, `sensors()` took 6.968 seconds; a separate ACPI temperature read took 2.47 seconds. CPU coretemp and chipset reads completed in roughly 1 ms or less. All metrics are sent after collection, so slow optional firmware channels blocked CPU/RAM/storage too. The phone then stopped polling after a NoData result.

The concrete helper change is one exclusion in `sensors()`: do not read `gigabyte_wmi` or `acpitz` channels during live polling. CPU coretemp, chipset temperatures and all existing CPU/RAM/storage/network/process/status requests remain. No new commands, ports, services, authentication, permissions, drivers or network settings. Slow optional motherboard/ACPI temperatures will be omitted. The app separately continues automatic retries after interrupted readings while retaining stale data honestly.

The guarded updater verifies existing helper and authorised-key hashes, takes the existing desktop ownership lock, preserves root ownership/mode/SELinux context, and backs up only the helper under `/var/lib/sentinel-status-pairing/health-latency-TIMESTAMP`.

Apply after review:
```
pkexec /usr/bin/python3 -I /home/Amus/Android/PC_moniter_app/deployment/update-health-latency-helper.py --apply
```
Rollback (refuses subsequent helper edits):
```
pkexec /usr/bin/python3 -I /home/Amus/Android/PC_moniter_app/deployment/update-health-latency-helper.py --rollback /var/lib/sentinel-status-pairing/RECORDED_HEALTH_LATENCY_DIRECTORY
```

Eighteen Python tests pass, including a regression that fails if either excluded firmware driver is read. Phone verification will use eight health polls separated by real 10-second intervals, an explicit SSH disconnect/reconnect, and the existing status/process/pin checks.

## Applied and verified

Owner approved and applied the helper-only fix. Backup: `/var/lib/sentinel-status-pairing/health-latency-20261027T060044416163Z`. Installed helper checksum matches health-latency-manifest.json; authorised-key checksum is unchanged. Android Studio build/lint passed, with ten JVM tests and eighteen Python tests passing. All five physical Pixel tests passed in 82.794 seconds, including eight health requests separated by ten-second idle intervals and an explicit SSH disconnect/reconnect. Original status/session reuse, owner-filtered processes, real health response validation and wrong-host-pin rejection also passed. Sentinel 0.6.2 is installed; temporary test package removed.
