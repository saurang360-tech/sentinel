# Simple workspace

Current defaults are Home, Users, Terminal, Power and Settings. Home stays focused
on basic readings and plots. Users combines received-process account totals with
the existing filterable process table. The totals describe the received sample,
retain unknown values, flag incomplete sampling and disclose shared RSS memory.
Terminal retains its separate phone unlock; Power retains hold plus password.

Settings > Add preset offers Home, Users, Terminal, Power, Hardware, Tasks and
Settings. Presets are independent editable copies, with distinct tab/widget IDs
and duplicate-name suffixes. Each tab supports Edit > Add widget and widget Edit >
Remove widget, move and resize. Settings > Manage tabs and Edit > Tabs support
adding blank tabs, renaming, reordering and deletion. Limits remain 12 tabs and
32 widgets per tab. No preset executes a command merely by being added.

Existing simple layouts gain missing Users, Terminal and Power tabs once, when
capacity permits; existing tab/widget identities and selection are preserved.
After the upgrade, deleted tabs stay deleted. Preferences, keys and history are
unchanged. The earlier crowded-layout backup remains retained locally.

Preset validation: debug/release builds, Android lint, 35 JVM tests and nine
isolated phone UI tests passed, including preset addition, per-tab widget
removal/addition, upgrade preservation, navigation and persistence.

The sections below include dated implementation/verification records. Test
counts and installation status describe each pass, not the current release gate.
See [current readiness](RELEASE-READINESS.md) and the latest audit for release status.

# Sentinel widget workspace

The fixed page shell has been replaced by a persisted, user-editable workspace.
The original redesign used Dashboard, Operations, Terminal, Control and Settings.
Those are historical defaults; the current defaults are listed at the top. Every
widget can be removed, moved between tabs, reordered and resized. Up to 12 tabs
and 32 widgets per tab are supported. Deleting a tab removes its layout only;
it does not revoke SSH keys, erase connections or delete task history.

## Editing

Choose **Edit layout**. Hold a widget handle and drag over another widget to
reorder it. Drag the bottom-right resize handle vertically to change height;
a horizontal drag switches between half/full width. The **Edit** menu provides
accessible alternatives: width, taller/shorter, move up/down, move to another
tab and remove. **Tabs** adds, renames, reorders and deletes tabs. At least one
tab is retained. Empty tabs offer an explicit add-widget starting point.

Layout is saved locally as versioned, validated JSON. Damaged layouts show a
recoverable message and temporary defaults rather than silently overwriting
stored data. Enlarged text and narrow windows render a single column while
retaining the saved spans. Appearance supports Nord/monochrome and Frost, Ice
or Snow accents. Polling selections are 1, 2, 5 and 10 seconds, in addition to
collection/SSH time; requests remain sequential rather than overlapping.

## Widget and data coverage

| Widget | Binding |
|---|---|
| Connection | Authenticated sample phase/age, actual SSH-session elapsed time, PC uptime, persisted notification audio mute |
| CPU & thermal | Total and per-core `/proc/stat` samples; hwmon temperature and critical limit; bounded CPU/temperature trends |
| Memory & swap | Used/total/available bytes, swap, pressure, session trend |
| Storage | Actual statvfs capacity for reported volumes and `/home`/`/var`; mounts may share capacity and are never summed |
| Network / Disk | Paired, shared-scale Canvas trends aligned to the same sampled timestamps; per-interface/device rates |
| Environment & power | Sysfs temperature/fan/component power readings, ambient explicitly unavailable if no labelled sensor exists; GPU/VRAM readings where exposed |
| Failed services | Counts and unit names; on-demand read-only recent unit journal, maximum 40 entries; stack traces only if present in the journal |
| Processes | Account filters, CPU/RAM sort, search, exact values with cell bars, expandable details and task watching |
| Watched timeline | Timestamped observed transitions, bounded encrypted history, unknown outcomes and per-task mute |
| Verified jobs | Raw runner exit result, running/success/failure/timeout/unknown states, elapsed-duration comparison bars, expected-success windows |
| Background monitor | Explicit Android foreground-service start/stop, heartbeat/PC observation and interruption status; Android notification permission flow |
| Alert policy | Quiet-hour time selectors, severity, cooldown, opt-in sustained health alerts and existing per-task mute rules |
| Notification handoff | Actual Android handoff status, last observation and honest unconfirmed display/delivery state |
| Terminal | Existing separately authorised SSH TTY, line-oriented stream, shortcuts, private input and parameterized macros |
| Power matrix | Hold-to-arm shutdown/reboot, explicit PC password confirmation, pinned SSH and existing sudo policy, inhibitor checks, no retry |
| Preferences | Poll interval and dynamic persisted theme/accent selections |
| Connections & privacy | Existing encrypted profiles, setup/pairing/helper export, phone verification, authentication-only check, privacy/legal text and erasure |
| System details | OS, kernel, core count, load and PC uptime |

## Execution and security boundaries

The PC helper adds a restricted `sentinel-service-v1 system|user UNIT.service`
request. Both Android and Python validate the name. The helper passes argument
arrays to journalctl, never a shell, uses existing journal permissions, a timeout
and a bounded response. This does not grant sudo, read arbitrary files or install
a PC service. On older installed helpers, per-core/journal capabilities stay
unavailable until the owner reviews and upgrades the exported helper.

The local PC runner supports `run-timeout LABEL SECONDS -- COMMAND [ARGS...]`.
Timeout sends TERM to the launched process group, waits, then KILL if necessary.
The raw child return code is retained. A timed-out runner does not prove that
independently detached descendants stopped. Normal `run` behavior is retained.

Macros use standalone unquoted `{{parameter}}` tokens and literal shell-quoted
values. Quoted/embedded placeholders and multiline inputs are rejected. Templates
are encrypted in the connection vault; parameter values are not saved. Insert
mode populates the command field; run mode requires a command review and sends
one command. Reconnection does not replay a queue. Built-ins include user-service
status/start/stop/restart using the account's existing rights. Saved macros can
be deleted independently of connections.

Phone verification continues to protect connection and terminal widgets. Tabs
and editing revoke terminal access and disconnect the shell. App backgrounding
clears password confirmations. Release screenshot protection is unchanged.
Power tests use a stub executable and cannot reach real systemctl.

## Explicit external limits

No server-side monitor daemon is automatically installed or enabled. The
background monitor is the existing explicitly started Android foreground
service; existing PC user services can be managed with terminal macros. No
webhook/push relay endpoint or authenticated client-acknowledgment protocol is
configured. The notification widget therefore distinguishes Android acceptance
from unconfirmed display rather than claiming delivery. No socket-listener,
cloud backend, systemd privilege rule or PC networking change is deployed.

This implementation does not turn a missing sensor into a zero, an unobserved
process ending into success, an SSH disconnect into proof of power-off, or a job
elapsed-duration bar into a completion percentage. Foreground history is a
bounded, memory-only ten-minute window. CPU percentages remain total-machine
shares for processes and per-core shares for the per-core widget.

## Verification

Unit tests cover layout identity/preservation/bounds, literal macro arguments,
service request allowlisting and the reboot authentication/cancellation guard.
Python tests cover per-core sampling, journal request validation and real local
runner timeout termination without remote PC access. Synthetic debug UI tests
cover navigation, custom tab/widget persistence, accessible resize/reorder/move/
remove operations, existing setup validation, process scrolling and large text.
The debug-only non-exported gallery creates no SSH transports; phone review uses
a separate application ID, preserving the paired installation.

Validation: debug and release APK builds, Android lint, 32 JVM tests and 37
Python tests passed. Five phone UI tests passed together; a separate sixth
phone test verified direct long-press widget reordering and corner-drag resizing.
Screenshots are saved in `design/workspace/`. These tests did not exercise live
SSH power actions or install/update the helper on the user's PC.

## Amber Terminal and normal-only operation

The app no longer offers demo mode or generates sample graph values. Synthetic
fixtures are restricted to debug UI test code, outside the production source set.
Settings > Preferences offers Nord, Monochrome and Retro Terminal / Amber Mobile;
the selected theme persists without changing widget layout. Amber uses #000000
backgrounds, #FFB000 phosphor-inspired amber, monospace typography, compact spacing,
square widgets, shorter plots and block meters alongside exact numeric readings.
This is an sRGB color inspired by P3 phosphor, not a claim of Display-P3 gamut.
Touch controls retain usable targets and large-text handling remains enabled.

Per-core frequencies come from Linux scaling_cur_freq when exposed. Free, buffers
and cached RAM are raw meminfo fields, not additive categories. The PC helper must
be updated for these fields. ICMP ping is explicitly not measured; SSH collection
time is not misrepresented as network latency. No battery saving is quantified.

Amber validation: debug/release builds, Android lint and the Android UI-test APK
compile passed; 33 JVM tests and 38 Python tests passed. The theme persistence/
black-background phone test is packaged but was not run because ADB no longer
detected the phone. The new build has not been installed on the phone.
