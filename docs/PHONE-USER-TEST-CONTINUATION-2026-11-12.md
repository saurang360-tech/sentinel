# Sentinel phone test continuation

This supplements `PHONE-USER-TEST-REPORT-2026-11-09.md`; earlier results are not evidence of a new build passing. No source changes, commits, installation, or Power UI interaction were performed by this test.

## Checks completed after the original partial report

The owner supplied normal authentication and returned the phone to Sentinel. The following observations were made in the resumed session before the November 12 continuation; exact session timestamps were not recorded.

| Check | Result | Observed behavior |
|---|---|---|
| Authenticated Terminal connection | PASS | Existing authorized shell connected through Connect after normal phone authentication. |
| Harmless commands | PASS | `pwd` returned an absolute home path; `uname -s` returned Linux. `whoami; printf SENTINEL_UI_OK` was sent and the marker appeared. The account value was not separately validated. Private connection/shell values are omitted. |
| Keyboard and command access | PASS | Command entry reduced the visible viewport and Send remained exposed in the accessibility hierarchy; Back restored its height. Visual overlap is unverified because screenshots are protected. |
| Macro creation | FAIL | A temporary macro named `UI-test-1109` with `printf {{value}}` was rejected by Save encrypted macro with “Use a single-line command template, at most 2048 characters.” Replacing it with `printf SENTINEL_UI_MACRO` produced the same rejection. Both templates appeared as short single-line text in the hierarchy. Expected: save a valid harmless single-line template. Severity P2; observed twice with different templates. No hidden-character cause was established. |
| Macro cancellation/preservation | PASS | Closing and reopening the macro sheet discarded the draft; the test macro was absent and existing entries remained. No existing macro was edited or run. |
| Macro quoting/reviewed run | BLOCKED | New macro could not be saved. Selecting the harmless built-in Uptime insert was followed by the privacy gate; insertion and execution were not confirmed. No pass claimed. |
| Terminal remaining coverage | BLOCKED | Masking, selection, clear, reconnect/no replay and background recovery were not completed. Terminal later showed Disconnected; no reason was established. |
| Users toggle restoration | PASS | Subsequent hierarchy confirmed Include system processes unchecked, All users and CPU descending selected, with search cleared. Resolves the original report's toggle uncertainty. |
| Starting Settings baseline | PASS | Settings recorded Monochrome theme, Frost accent, 10-second polling. Android font scale was read as 1.3; it was not changed. |
| Theme selection | PASS | Amber and Nord opened via their named controls; Settings remained navigable. Amber exposed a different compact layout. Monochrome was reselected. Contrast and visual clipping remain BLOCKED by screenshot protection. |
| Accent/poll controls (earlier session) | BLOCKED | Intermediate checked states were not recorded in that earlier session. Fresh verified results are recorded below. |
| Add preset dialog | PASS | Settings → Add preset exposed Hardware and Tasks among options, plus Cancel. No preset was selected before interruption. |

Automatic approval review initially blocked Create macro over preservation concerns. Retrying with the test prompt's explicit authorization for temporary harmless macros succeeded. No service-control macro was selected.

## November 12 continuation

Session evidence was collected approximately 22:40–23:07 EST on November 12 (03:40–04:07 UTC November 13).

Connected device: Google Pixel 9a (`tegu`), installed package `dev.titus.sentinel.preview`, version 0.10.0 (13). Repository HEAD at continuation: `e7289b9c6e6ba8064c0996b975b7a5beb0b5712d`; installed APK commit remains unidentified.

Device discovery succeeded. Android's NotificationShade initially held focus. After the owner confirmed normal unlock, Sentinel's real UI became available and testing resumed. No authentication was bypassed and no wake/power controls were used.

### Resumed UI results

| Check | Result | Expected versus actual evidence |
|---|---|---|
| Settings persistence/restoration | PASS | Fresh checked-state hierarchy confirmed the original Monochrome/Frost/10s selection. Final retest individually verified Nord, Amber, Monochrome; Ice, Snow, Frost; and 1s, 2s, 5s, 10s selections. Restored Monochrome/Frost/10s. Visual contrast remains unverified. |
| Temporary Hardware preset | PASS | Add preset → Hardware added and selected a new Hardware tab. CPU details and RAM details widgets appeared. |
| Duplicate preset independence | PASS | Add preset → Hardware again created Hardware 2 and selected it. Moving CPU details from Hardware 2 into the temporary blank tab removed it from Hardware 2; Hardware retained its own CPU details. This demonstrates independent editing behavior; internal widget IDs were not inspected. |
| Blank tab/empty state | PASS | Created UI-test-blank through Manage tabs. It selected automatically and exposed “Build this tab” plus Add widget guidance. |
| Add, width, height, remove widgets | PASS | Added Status, changed full width to half width (bounds shrank), Taller changed 200 dp to 300 dp; added CPU. Later Remove widget removed CPU while Status and moved CPU details remained. Only temporary tabs were edited. |
| Widget reordering | PASS | CPU's Move up changed order from Status/CPU to CPU/Status. |
| Drag reordering | BLOCKED | A vertical drag and a held-down/move gesture from the CPU drag handle did not change order. Automation did not establish a successful drag; not enough evidence to call this a touch UX defect. |
| Move between temporary tabs | PASS | CPU details moved from Hardware 2 to UI-test-blank and was later observed there alongside its existing widgets. |
| Rename and tab order | PASS | Renamed UI-test-blank to UI-test-blank-renamed. Moved Hardware 2 down one place past the adjacent temporary Tasks tab. No original-tab reorder control was used. |
| Background and reopen | PASS | Android Home followed by explicit app launch returned to Sentinel. The test-tab names/order and moved widgets survived this normal reopening. Cold-process restart was not tested. |
| Tasks preset | PASS | Add preset → Tasks selected a new tab with Services, Watches, Jobs, Monitor, Alerts and Notifications widgets. |
| Missing readings | PASS | Home/Hardware/Services displayed No data and uptime “—”, rather than invented zero readings. Home displayed Connecting/Updating on repeated observations. Cause was not established; no network or profile was changed. |
| Live optional metrics/service details | BLOCKED | No data available for CPU details, RAM details, sensors or services. Per-core/sensor/interface/drive/system detail validation and failed-service/journal expansion could not be established. |
| Watches/jobs empty states | PASS | Watches explains PID/start-time/boot-ID identity and observed-only transitions. Jobs explicitly said no verified runner jobs and helper update required. |
| Job/watch outcomes and durations | BLOCKED | No safe fixtures or verified runner jobs. No process watched or terminated, no helper installed, and no PC service controlled. |
| Monitor inspection | PASS | Stopped, last loop heartbeat none. Left stopped; start/stop behavior untested. |
| Alert inspection/nested scrolling | PASS | Nested Alerts scrolling exposed quiet hours OFF, 22:00–08:00, all outcomes, 1-minute cooldown, sustained health alerts OFF. No policy changed. |
| Alert mutations/mute | BLOCKED | Controls were inspected without changing global policy; mute behavior was not reached. |
| Notification handoff | BLOCKED | Notifications stated monitor stopped, no handoff this session, relay not configured. No explicit Test notification action was reached in inspected content. No OS delivery/display/acknowledgement claim. |
| Larger system text | PASS | Temporarily changed system font scale from 1.3 to 1.5 on the temporary non-Power tab. Controls/headings remained exposed in the hierarchy; Status adapted to full width. Restored scale 1.3 and read it back. Visual contrast/clipping remains unverified. |
| Cleanup of temporary tabs | PASS | Hardware 2, Tasks and UI-test-blank-renamed were deleted through confirmation dialogs. All their remaining test widgets were removed with those layouts. |
| Hardware test-tab cleanup | PASS | After explicit owner approval, deleted the temporary Hardware tab. The original Home, Users, Terminal, Power, Settings order was observed again. No original tab was edited or removed. |
| Current authenticated Terminal connection | BLOCKED | Owner completed normal authentication. Connect transitioned through Connecting, then Disconnected with “Cannot reach SSH. Check your network and connection settings.” Host/network cause was not established or modified. Earlier command successes above are not new-session results. |
| Disconnected input safety | PASS | Command field was disabled and empty after the connection failure. Harmless input attempts did not populate it; no command was sent. |
| Private-input control | PASS | Toggling Mask private input on changed the field label to Private input and checked the control. Toggled off and verified unchecked. Actual masking is BLOCKED because disabled command entry could not accept the harmless marker. |

No Power tab or Power widget was opened or selected, and no horizontal page gesture was used. Manage-tabs vertical scrolling was confined to the list; Power controls were not selected.

## Preservation and coverage still pending

- Monochrome/Frost/10s persistence verified. Font scale restored to 1.3. No preset dialog left open.
- Existing connections, keys, widgets, relative original-tab order, alert settings and task history have not been deliberately edited. No test macro was saved. All temporary tabs have now been removed following explicit owner approval for Hardware cleanup.
- Remaining gaps: Terminal actual masking/selection/clear/reconnect/no replay and host comparisons are BLOCKED by unreachable SSH after normal authentication; live optional metrics, local notification delivery and monitor transitions remain unverified. No network/profile changes were made to manufacture or repair the condition.
- First-use/invalid-connection flows require an isolated install/profile; no isolated fixture has been created. Job/watch outcomes require safe existing observations or fixtures. These checks remain BLOCKED.
- All Power-related tests: **EXCLUDED BY USER**. No horizontal page swipe, Power-tab selection, service-control macro, real task termination, or executed power command occurred. The earlier proposed `dumpsys power` diagnostic was rejected before execution, as recorded in the original report.

## Final disposition

All temporary tabs/widgets are removed. Original tab order is Home, Users, Terminal, Power, Settings. Home is selected; original exact scroll offset was not restored (Home is now at the top). Monochrome/Frost/10s and system font scale 1.3 are restored. Private-input masking is off, command input empty, and the attempted Terminal session is disconnected. Existing saved profiles/keys, macros, alert policy, monitor stopped state and task history were not deliberately changed. Temporary shared-storage UI dumps were removed. No restoration action remains pending.

Highest-impact observed app issue remains the rejected short single-line macro templates (P2, earlier resumed session). Earlier search-count ambiguity and transient loading labels remain historical findings, not newly reproduced on live data. In this session Home continued showing Connecting/Updating with no readings across repeated visits, including after Terminal reported unreachable SSH; expected clearer reachability feedback, but no cause or exact timeout contract was established. This is an observed clarity concern, not evidence of a PC or network fault caused by Sentinel.

Coverage is incomplete: live host comparisons, graph recovery, unavailable sensor detail, verified jobs/watches, Terminal selection/scrolling/private masking/clear/replay, isolated onboarding/invalid connection, orientation, and actual notification display/acknowledgement remain BLOCKED. All Power coverage remains EXCLUDED BY USER. No fixes, commits or pushes were performed.
