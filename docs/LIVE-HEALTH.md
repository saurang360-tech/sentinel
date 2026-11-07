# Live health

Sentinel 0.5-health replaces illustrative Health/Details readings with `sentinel-health-v1` over the existing pinned Tailscale SSH connection and Android Keystore identity. No extra server port or account setup is required. Deployment review is in deployment/HEALTH-REVIEW.md.

CPU utilisation is the delta of Linux aggregate user/nice/system/idle/iowait/irq/softirq/steal counters, excluding idle and iowait from busy time and excluding guest counters already included elsewhere. CPU, network and disk rates average about 0.4 seconds; they will differ from tools using longer sampling windows. Readings refresh every 10 seconds only while the page is visible. Foreground polling stops on background/settings, connection failures back off, and samples become stale after 20 seconds using the phone's monotonic clock.

RAM used is MemTotal minus MemAvailable, not MemTotal minus MemFree. Swap has its own used/total reading and memory-pressure stalls use PSI some avg10. Storage uses statvfs, reports used and user-available capacity separately, and refuses to substitute root filesystem statistics when the data HDD is unmounted. Reserved space means used plus available may not equal total.

Network counters are per interface; tunnel and physical traffic are never added together. Disk transfer rates use physical whole-disk counters (512-byte sectors). Load, OS, kernel, uptime, service failure names/counts and exposed hwmon sensors appear in Details. Service queries time out and report unavailable if inaccessible. No journals are read.

Titus currently exposes coretemp CPU package/core readings and chipset sensors. Its nouveau/i915 sysfs interfaces do not expose usable GPU utilisation/VRAM counters, fan speeds or component power in this setup. These show unavailable. SMART is not queried. No root diagnostics, driver changes or whole-PC power estimates are used.

Warnings cover low available RAM, measurable PSI memory stalls, CPU temperature approaching the hardware critical threshold, low disk availability and an unmounted data volume. These are limited observations, not a complete hardware-health diagnosis.

References: Linux [proc documentation](https://docs.kernel.org/filesystems/proc.html) and [hwmon interface](https://docs.kernel.org/hwmon/sysfs-interface.html).

## Applied and verified

Owner approved the read-only health helper update. Backup: `/var/lib/sentinel-status-pairing/health-20261027T053512460705Z`. Installed helper SHA256: `c33d3095b425e061ae223fc166b2e7f34d5fd11bcea71bdd932c23952e5c3c26`. Existing authorised-key checksum remains unchanged. Existing private SSH/VNC listeners remain unchanged.

Android Studio MCP build, nine JVM tests and lint completed successfully (no lint errors; existing dependency/style/resource warnings remain). Seventeen Python tests passed. Four physical Pixel tests passed over the existing Tailscale path: real CPU/RAM/storage/network/temperature response and malformed-value rejection, original status/session reuse, all process owner filters, and wrong-host-pin rejection. Sentinel 0.5-health (versionCode 5) was installed successfully; temporary instrumentation package removed. The phone was locked during the final UI inspection, so no claim of final screenshot verification is made. Different-network testing was not repeated in this session.

## Health interruption fix — 0.6.2

The initial broad hwmon scan was unsuitable for regular polling: gigabyte_wmi and ACPI firmware temperature reads sometimes blocked the entire response beyond the six-second phone deadline. A measured sensors scan took 6.968 seconds. The phone incorrectly treated NoData as requiring manual intervention, so polling could remain stopped after a transient delay.

Live collection now skips gigabyte_wmi and acpitz, retaining fast coretemp CPU and chipset readings. Eight staged full health requests completed in 0.484–0.510 seconds. Phone polling now retries NoData automatically with bounded backoff; only host-key mismatch and authentication failures stop automatic retries. Previously collected samples remain visible with their connection/freshness state. Safe diagnostics record only stage, exception class, exit status and response byte count, never response content or credentials.

Regression coverage includes firmware-driver exclusion, retry policy, eight real ten-second health polls and an explicit SSH disconnect/reconnect. Review and rollback are in deployment/HEALTH-LATENCY-REVIEW.md.

## Applied and verified

Owner approved and applied the helper-only fix. Backup: `/var/lib/sentinel-status-pairing/health-latency-20261027T060044416163Z`. Installed helper checksum matches health-latency-manifest.json; authorised-key checksum is unchanged. Android Studio build/lint passed, with ten JVM tests and eighteen Python tests passing. All five physical Pixel tests passed in 82.794 seconds, including eight health requests separated by ten-second idle intervals and an explicit SSH disconnect/reconnect. Original status/session reuse, owner-filtered processes, real health response validation and wrong-host-pin rejection also passed. Sentinel 0.6.2 is installed; temporary test package removed.
