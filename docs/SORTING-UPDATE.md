Historical record/planning document. Feature descriptions, test counts and
setup details below apply to that pass and may be superseded. Current behavior:
README.md and docs/WIDGET-WORKSPACE.md. Current release status and reusable checks:
docs/RELEASE-READINESS.md. Historical personal deployment data are not public
onboarding defaults.

# GUI process sorting — 0.7.1

Adds CPU and RAM descending sort chips to the compact process page. Existing installed helpers are supported: when the v2 request is rejected with the explicit unsupported-request exit code (64), Sentinel requests the existing allowlisted legacy process sample and sorts it locally. Host-key/authentication/decoding failures never trigger this fallback. No workstation helper, keys, services or networking were changed for this installation.

With a legacy helper, sorting and user filtering cover only its returned CPU-ranked sample of up to 200 processes. The page labels this limitation; account names are inferred from that sample. The prepared v2 helper enables sorting before the cap and complete local account discovery, pending the separate review in deployment/GENERAL-REVIEW.md. Do not describe legacy sample sorting as global RAM ranking or full account discovery.

Twelve JVM tests and lint passed. The three physical-phone settings/compatibility tests passed, including a deterministic fixture whose CPU and RAM rankings differ and whose system rows are filtered independently. The initial five live SSH tests failed before authentication because the phone was reported offline by Titus's Tailscale daemon. The GUI APK was installed successfully; live verification status is recorded below after reconnection.

## Installed and verified

Sentinel 0.7.1 (versionCode 10) was installed on the Pixel. After the owner reconnected Tailscale, all eight physical-phone tests passed in 87.028 seconds. CPU and RAM descending order were validated for each available user filter, alongside health polling/reconnects, status/session reuse and wrong-host-pin rejection. The test package was removed.

The GUI was inspected on the unlocked Pixel. CPU ↓ showed descending CPU values; tapping RAM changed the chip to RAM ↓ and reordered the visible rows to 753, 633, 447, 343 and 295 MiB. The page correctly labels sample-only sorting. No workstation helper/key/network changes were made. Complete account discovery and server-side ranking still require the separately prepared helper update.
