# Titus baseline — 27 October 2026

Inspection was read-only. No remote services, user permissions, drivers, firewall rules, desktop settings, or storage were changed. No existing private key, password, cookie, token, or document content was read. Only relevant deployment status/configuration and hardware/system status were inspected.

## Confirmed on the running host

| Item | Observation |
|---|---|
| OS | Fedora Linux 44, KDE Plasma 6.6.4; kernel 6.19.10-300.fc44.x86_64 |
| CPU | Intel Core i5-9400, six cores/six threads |
| RAM | 12,380,176,384 bytes usable, about 11.53 GiB; 8 GiB zram swap |
| Main storage | Kingston SV300S37A120G, 111.8 GiB device; Btrfs system/home share one filesystem |
| Data storage | Seagate ST2000DM001, 1.8 TiB device; ext4 mounted /mnt/titus-hdd |
| Space at inspection | System filesystem: 117.26 GB total, 81.77 GB used, 33.44 GB available; HDD: 1.968 TB total, 157.8 MB used, 1.868 TB available. Used + available need not equal total because of filesystem reserves/accounting. |
| Graphics | Intel UHD 630 on i915; RTX 2070 SUPER on nouveau. Userspace NVK version not independently checked. |
| Temperature sources | coretemp, gigabyte_wmi, acpitz, pch_cannonlake; temperature inputs exposed |
| GPU counters | No generic busy-percent or VRAM counters found on inspected DRM devices; availability remains unconfirmed, not zero |
| Power/fans | No fan or component-wattage input appeared in inspected hwmon entries; whole-PC watts unavailable |
| Titus Tailscale | 100.75.247.115 |
| Owner phone | 100.70.104.44 shown as dev-phone in Tailscale status |
| Owner Windows | 100.122.243.12 shown as saurang; offline at inspection |
| Owner SSH listener | 100.75.247.115:2222; dedicated config is key-only, Amus from 100.70.104.44, forwarding disabled |
| Skyler SSH listener | 100.75.247.115:22 |
| Skyler VNC listener | 127.0.0.1:5902, localhost only |
| Skyler home | /mnt/titus-hdd/skyler-work, owned skyler:skyler, mode 750 |
| Skyler slice | MemoryMax 4294967296; CPUQuotaPerSecUSec 2s; TasksMax 512 |
| Service warnings | One system DrKonqi processor unit failed; user manager lists 79 failed DrKonqi launcher units. These are historical unit states, not proof that those failures are still occurring. No reset/restart attempted. |

The supplied design's i9-13900K, RTX 3080 and 32 GB RAM are illustrative, not Titus hardware. The app uses detected hardware labels.

## Reported working by the owner

Windows controls the existing KDE desktop, Pixel connects through Tailscale/Termius SSH, and Skyler has restricted SSH plus an independent Xfce desktop through a TigerVNC SSH tunnel. These work without Codex. Local listener checks support the configuration but do not independently prove end-to-end operation.

## Uncertainties and verification limits

- Skyler's shared device 100.65.80.124 did not appear in the owner host's brief Tailscale status output. Do not edit sharing or policies to make it appear.
- The phone deployment status records accepted owner authentication and the expected Ed25519 fingerprint: SHA256:5fLmmMkeCf6pR5n99GMHYeVJYIVp2gGZDc0P2XZ4+Yg. Its configured public host-key file is not readable to the unprivileged inspector. Live cryptographic fingerprint verification remains pending; never bypass pinning on that basis.
- GPU rendering permissions and NVK userspace details remain to be verified without driver changes.
- SMART availability, process visibility across users, actual GPU counters and KWin integration need targeted capability tests. No privileged SMART query, command-line/environment scrape, browser inspection or window-title collection was performed.
- Tests from the Pixel over home Wi-Fi and another network are pending; they cannot be replaced by checks on Titus.
- Sleep/offline state cannot be inferred from failed network requests.

## Coordination

The separate desktop project has an ownership lock under its runner directory. This app work writes only to the new Android project and does not mutate or run that project's installers, timer controls or deployment files. Any future shared desktop/service deployment must coordinate its ownership lock and inspect current state before changes.
