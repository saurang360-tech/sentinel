# Sentinel widget and task-monitoring retest

## Scope and evidence

Fresh connected-phone UI run November 12, 2026, approximately 23:25–23:50 EST (November 13, 04:25–04:50 UTC). Device: Google Pixel 9a (`tegu`), Android 17; installed package `dev.titus.sentinel.preview`, version 0.10.0 (13). Repository HEAD at start: `7a2ff6d36be749557e89931f6e78606ade399557`; installed APK commit is unknown. Pre-existing uncommitted source/test changes were present and left untouched. No build or install was performed.

Actual phone interaction used fresh accessibility hierarchies, named controls and deliberate gestures. Screenshot capture again returned black app content; protection was respected. Consequently visual contrast, precise clipping and appearance are **BLOCKED**, even where accessible labels/bounds work. Source inventory was consulted only to identify widget coverage, never as proof that a feature passed.

Power and all service/task-control actions are **EXCLUDED BY USER**. No horizontal page navigation, Power tab/widget selection, password entry, authentication attempt, PC command, network change or real-task termination was performed. Password/phone-authentication gates are skipped for this run.

## All non-Power widgets

“PASS (state)” means the observed layout/control/empty-state behavior passed; it does not mean unavailable telemetry was validated.

| Widget | Result | Actual observation and usability limit |
|---|---|---|
| Status | PASS (state); BLOCKED (live data) | A dedicated observation changed Connecting/Updating to Unreachable with Refresh by the final read; uptime stayed “—”. Initial short visits were not sufficient to establish a stuck state. |
| CPU | PASS (state); BLOCKED (data) | Heading and No data exposed; no fake 0% or temperature. Live samples/history unavailable. |
| RAM | PASS (state); BLOCKED (data) | No data; totals, breakdown and graph unavailable. |
| Network | PASS (state); BLOCKED (data) | No data confirmed in final Home traversal; no live rates/history. |
| Disk | PASS (state); BLOCKED (data) | No data confirmed; no live read/write rates or history. |
| Storage | PASS (state); BLOCKED (data) | No data confirmed; no capacity totals to validate. |
| CPU details | PASS (state); BLOCKED (data) | Temporary Hardware preset rendered No data; no per-core readings to validate. |
| RAM details | PASS (state); BLOCKED (data) | Heading/empty state reachable by vertical scrolling; no breakdown. |
| Sensors | PASS (state); BLOCKED (data) | No data; sensor permissions/capabilities cannot be determined from this state. |
| Interfaces | PASS (state); BLOCKED (data) | Reachable on Hardware; No data. |
| Drives | PASS (state); BLOCKED (data) | Reachable at bottom of Hardware; No data. |
| System | PASS (layout); BLOCKED (data) | Added on temporary QA112-Widgets tab; No data. Drag-resized from full width/340 dp to half width/424 dp and moved to temporary Tasks tab. |
| Session | PASS (controls) | Not connected; PC uptime Unknown. Mute toggled on and back off. Accessible layout survived font scale 1.5 and landscape. No private connection details exposed. |
| Services | PASS (state); BLOCKED (details) | No data. Failed-service lists and journal expansion unavailable. No service-control action used. |
| Watches | PASS (empty state); BLOCKED (outcomes) | Empty list explains process identity and observed-only transitions. No safe process fixture or live list available; no watch created or history cleared. |
| Jobs | PASS (empty state); BLOCKED (outcomes) | Explicitly reports no verified runner jobs and a helper update requirement; bars described as elapsed duration rather than percentage. No runner/helper installed or job launched. |
| Monitor | PASS (start/background/stop); FAIL (message) | Android monitor started, exposed a loop heartbeat, survived normal background/return, and reported no verified SSH response. Stopped via its UI. Old Starting background session message remained afterward; finding 1. |
| Alerts | PASS (controls); BLOCKED (delivery policy) | Quiet hours OFF→ON→OFF; start-time editor opened/cancelled with 22:00–08:00 retained. Severity cycled all→warnings/errors→errors→all. Cooldown cycled 1→5→15→0→1 min. Health alerts OFF→ON→OFF while monitor stopped. No actual alert outcome fixture. |
| Notifications | PASS (honest state); BLOCKED (display) | Monitor stopped and no task handoff reported. Explicit Test notification reported handed to Android, with display/hearing unconfirmed. No Sentinel notification was found in the inspected shade; acknowledgement untested. |
| Users | PASS (state); BLOCKED (data) | No data; sampled account totals unavailable. Account dropdown exposed only All users. |
| Processes | PASS (controls); BLOCKED (data) | Reached Unreachable/Retry and No process readings yet. RAM sort selection worked. Search accepted QA112_no_match, keyboard and Clear remained accessible, and test text was cleared. No rows for ordering, details or Watch task tests. Restored All users/CPU descending/empty search. |
| Terminal | BLOCKED — authentication | No unlock/password requested; shell, private masking, macros and command execution intentionally not attempted. |
| Settings | PASS (controls) | Individually verified Nord/Amber/Monochrome, Ice/Snow/Frost and 1s/2s/5s/10s selection. Restored Monochrome/Frost/10s; actual telemetry cadence unavailable. |
| Connection | BLOCKED — authentication | Protected saved-connections widget; profiles, keys and credentials untouched. |

## Workspace, resilience and task-monitoring checks

- **PASS:** Hardware and Tasks presets selected their newly created tabs. Blank QA112-Widgets selected automatically. Added System and Session through the widget picker.
- **PASS:** Hold-and-drag moved Session above System. A resize gesture changed System width/height; Move to tab moved it into the temporary Tasks tab. Remove widget removed Session and returned the Build this tab empty state.
- **PASS:** Normal Home/background → relaunch retained the Tasks page and Android monitoring session until explicitly stopped. No process death/reboot test.
- **PASS:** Renamed Hardware and Tasks to QA112-Hardware/QA112-Tasks, reordered the two adjacent test tabs, and removed all test tabs through confirmation dialogs. Original tabs were not renamed/reordered/deleted.
- **BLOCKED:** Invalid/onboarding flows require an isolated installation; live process sort/filter correctness requires readings. Duplicate-preset independence was not rerun in this pass and remains a coverage gap; no historical result is counted as a fresh pass.
- **PASS:** Nested Monitor scrolling reached Start/Stop and Test notification; nested Alerts scrolling reached policy controls. Important actions require inner scrolling at the original 1.3 font scale.
- **PASS:** Session remained accessible at font scale 1.5 and actual landscape hierarchy rotation 1. Restored font scale 1.3, accelerometer rotation 0 and user rotation 0 in a finally block, with values read back.
- **BLOCKED:** Host telemetry, verified process/job outcomes, durations, per-task mute, notification suppression/recovery and comparable host sampling. The monitor explicitly reported no verified SSH response and no confirmed PC observation. Missing data was not treated as task success.
- **EXCLUDED BY USER:** Every Power tab/widget/preset/controller/API/action. Power was never opened or selected.

## Findings

### 1. P2 — Monitor retains “Starting” after it has stopped

Steps: Tasks → scroll inside Monitor → Start background monitor; background/return normally; wait for its no-SSH-response state; Stop background monitor.

Expected: the transient start message clears or changes to a completed stop message. Actual: “Starting background session…” remained simultaneously with “Stopping background monitoring; results are last known. Session stopped; restart from Sentinel to resume.” and the Start background monitor button. The stop button had changed back to Start. This was still present during subsequent Alerts tests. This is a contradictory status-message failure; it does not prove the service remained running.

### 2. Home connection-feedback concern not reproduced — PASS

A dedicated final observation left Home in the foreground. At 23:49:03 EST it was Connecting/Updating; at 23:49:59 it showed Unreachable and Refresh. The transition occurred within that observation window; its exact timing was not measured. The earlier short visits do **not** establish a persistent-Connecting bug. No fix is proposed from those short visits. Live telemetry and host comparisons remain BLOCKED by reachability.

## Cleanup and final verification

All temporary tabs/widgets have been removed, including the final Tasks verification preset. Original Home, Users, Terminal, Power, Settings order is restored, Home selected, with original Home widgets. Monochrome/Frost/10s restored; Android font scale 1.3, accelerometer rotation 0, user rotation 0 read back. Users is back to All users/CPU descending/empty search; system-process inclusion was not changed. Final UI verification confirmed Mute OFF, quiet OFF with 22:00–08:00, all-outcomes severity, 1-minute cooldown, health alerts OFF and monitor stopped.

The monitoring diagnostic heartbeat/last-known message now reflects the test session; it was not erased or presented as a live PC observation. A test notification was requested, but no Sentinel notification was found in the inspected shade; no unrelated notifications were dismissed. Screenshot protection prevented usable app screenshots. Temporary UI dumps are cleaned up at completion.

Existing paired installation, profiles, keys, original widgets, task history and macros were preserved. Unrelated source/test edits and another report appearing during the run were left untouched. No code fixes, builds, installs, commits or pushes are part of this run.

## Highest-impact result

The confirmed new UX failure is the stale Starting background session message after stopping the Android monitor. Widget drag reordering, resizing, moving/removing, nested scrolling, themes, policy controls, larger text and landscape worked in the observed UI. Task-monitor start/background/stop was exercised, but verified host outcomes, duration correctness, per-task alerts and notification display/acknowledgement could not be established. No repair was attempted.
