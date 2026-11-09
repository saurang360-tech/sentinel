# Roadmap functionality pass — 7 November 2026

This pass implements a tested Linux task-watching foundation. The supplied roadmap
and priority CSV are planning inputs; their embedded instructions are not treated
as separate authorization. The user's request authorizes functionality work and
simple test controls. Existing uncommitted redesign work was preserved. No remote
PC packages, keys, services, sudo rules, power settings or network settings were
changed. The validation record below describes the original pass. Current builds must
use Android Studio/MCP as requested; see ANDROID-STUDIO-MCP.md.

## Follow-up: background delivery

The next functionality pass adds an optional, explicitly started Android foreground
service with task-outcome notifications and watcher diagnostics. The historical
foreground-only limitations below describe the first pass. See
[background monitor behavior and limits](BACKGROUND-TASK-MONITOR.md) for current
behavior. This does not provide guaranteed overnight delivery or a cloud relay.

## Follow-up: alerts, health and assurance

The 9 November pass adds U06 mute/severity/quiet/cooldown controls, U11 sustained
health warnings and U09 expected-success checks for runner labels. See
[alert and assurance behavior](ALERTS-HEALTH-ASSURANCE.md). The scope table below
records the initial pass, not the cumulative implementation status.

## Available functionality

- Expand a process and use **Watch task (test)**. An updated helper supplies boot
  ID; boot ID + PID + start ticks prevent following a reused PID or rebooted job.
- Up to 32 watches per pinned connection, separated by host/port/login/fingerprint.
  Process names and up to 20 transitions per watch are encrypted using the existing
  phone vault. Running watches reopen as unavailable until checked again.
- Exact watched PIDs are read directly, independently of the 200-process list cap.
  Missing/zombie/replaced processes and changed boots mean **ended unknown**.
  Unreadable processes and failed requests mean **observation unavailable**.
  CPU inactivity, sleep and I/O waiting do not establish success or a stall.
- The Overview has a plain test panel showing watches, last observation, recent
  transitions, job results and the delivery limitation. Delete watches and history
  removes the current connection's phone watch records. It does not delete PC jobs.
- Optional local PC command wrapper records the directly launched command's exit
  code and shows succeeded (0), failed (nonzero or launch failure), running or ended
  unknown after runner loss/reboot. The runner writes private, atomic records even
  while the phone is absent. The monitoring SSH key cannot execute the CLI runner.
- Updated helpers advertise capabilities; older helpers retain existing status,
  process and health behavior. No monitoring privilege becomes shell privilege.
- Preflight reports available space and current-account writability for a chosen
  path. Sleep policy is explicitly unknown; it neither changes nor inhibits sleep.
- Privacy notice version 4 discloses the new storage and requires acknowledgement.

## Try the optional runner on the PC

Review/install the exported updated helper using the existing setup instructions.
Use the same Unix account as monitoring for job metadata visibility. Local CLI:

```sh
python3 /path/to/sentinel-monitor.py preflight /path/to/work
python3 /path/to/sentinel-monitor.py run "Test success" -- /usr/bin/true
python3 /path/to/sentinel-monitor.py run "Test failure" -- /usr/bin/false
python3 /path/to/sentinel-monitor.py run "My job" -- /path/to/program argument
python3 /path/to/sentinel-monitor.py jobs
python3 /path/to/sentinel-monitor.py delete-history
```

The wrapper stays attached to its command. If work must survive closing a shell,
use an existing tmux/session/service arrangement explicitly configured by the PC
owner. Sentinel does not install one or silently keep the PC awake. The outcome
covers the direct command's exit; detached descendants are not tracked. Killing
the wrapper does not establish that the underlying work stopped.

PC records live in `~/.local/state/sentinel/jobs` (directory 0700, records 0600).
Command arguments/output are not recorded by Sentinel. The job itself can write
files/output normally. Results older than 7 days are excluded (running jobs stay).
The next launch prunes old completed files and keeps at most 64 records, including
up to 32 running jobs. `delete-history` immediately removes finished records.
Uninstall after stopping runners: remove this directory explicitly if desired and
revoke the phone key/remove the helper using the existing setup guide.

## Scope against the matrix

| Item | Result in this pass |
|---|---|
| U01 | Simple Overview test panel; final task-first design remains for UI work. |
| U02 | Existing sorting/freshness preserved; exact identity checks added for watched work. |
| U03 | Arbitrary process watching + opt-in direct-command verified exit results. No stalled inference or process-tree integrations. |
| U04 | CLI test success/failure recipes and disk/permissions preflight; guided job presets remain. |
| U05 | Durable PC results survive phone disconnection. **No unattended notification delivery**; phone checks only while visible. |
| U06 | State transitions deduplicated in watch history; mute/quiet hours/digests remain. |
| U07 | Explicit foreground delivery/paused/last-known messages. No watcher heartbeat or push handoff diagnostics. |
| U08 | Bounded encrypted phone process-watch history and private PC job history; no diagnostic log collection. |
| U09 | Not implemented: schedule/backup check-in integration. |
| U10 | Sleep policy unknown in preflight; no awake/restart control. |
| U11 | Existing health warnings preserved; sustained alert engine remains. |
| U12 | Existing secure portable pairing retained; updated helper exported through existing settings. |
| U13 | Repeatable local overhead tool and captured results; representative hardware/network testing remains. |
| U14 | Existing independently pinned connection profiles retained. |
| U15 | Local build/unit/lint, Python lifecycle tests and connected Pixel checks; unrelated hardware/release acceptance remains. |
| U16 | Existing explicit authenticated shutdown unchanged; watched-job checklist remains. |
| U17 | No session/activity inference added. |

## Validation

- `python -m unittest discover -s tests -q`: 32 checks, including real wrapper
  success/failure/launch failure, killed runner, PID reuse/reboot and request limits.
- `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug
  :app:assembleDebugAndroidTest --offline` using Studio's bundled JDK via CLI.
- `TaskWatchTest`: bounded history, unavailable preserves last evidence, terminal
  unknown never becomes a fabricated success.
- Device protocol tests use the isolated test preferences namespace; they do
  not modify the user's connection settings or connect to a real PC.
- `python tools/measure_helper_overhead.py --samples 3` writes per-request local
  elapsed/CPU/response bytes. Captured output: `helper-overhead-local.json`.
  This is one local host and excludes SSH cost, not a general overhead guarantee.

Phone device test results and final build status are recorded in
`functionality-validation.txt`. No production release/store upload is performed.
