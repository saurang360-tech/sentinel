# Sentinel connected-phone test — 2026-11-09

## Scope and evidence

Partial run, approximately 03:42–03:52 EST (08:42–08:52 UTC). Followed `PHONE-USER-TEST-PROMPT.md`; no code changes, installation, commits, or PC commands through Sentinel. Device: connected Google Pixel 9a (`tegu`), Android 17. Installed package `dev.titus.sentinel.preview`, version 0.10.0, versionCode 13. Repository HEAD: `0651752a020c1b5e2cea8927f4a985b1605b8d5a`; installed APK provenance/commit was not established.

Evidence is from actual Android UI accessibility hierarchies, named-tab taps, and vertical scrolling via ADB. App content was black in a screenshot; screenshot protection was respected. No usable app screenshots or visual contrast evidence were collected. An initial screenshot contains only system bars and black app content and is not included as UX evidence. Failed UI dumps were treated as stale and not used to claim results.

The phone subsequently foregrounded another app, confirmed by window focus. Interaction stopped to avoid interfering with the owner. Remaining tests require the phone to be available; Terminal requires normal owner authentication.

## Results

| Check | Result | Expected and observed behavior |
|---|---|---|
| Identify device/package | PASS | ADB identified one connected Pixel 9a; package manager reported the installed version above. |
| Foreground existing app | PASS | Explicit launch delivered to the already running activity. Home was selected. Cold launch was not tested. |
| Starting layout | PASS | Existing tab order: Home, Users, Terminal, excluded tab, Settings. Home contained Status, CPU, RAM, Network, Disk, Storage; Users contained Users and Processes. No tab/widget edits were made. Theme and all below-fold configuration were not established. |
| Home connection and refresh | PASS | Status showed Connected, sample age 5s, uptime 0 h 44 min. Tapping Refresh produced 0s age and uptime 0 h 45 min. |
| CPU, temperature, RAM | PASS | UI exposed live CPU readings (28.5%, later 24.7%), temperature (31°C, later 28°C), RAM (8.9/11.5 GiB, later 8.8/11.5 GiB). This establishes display/update behavior, not accuracy against the host. |
| Network/disk graphs and history | PASS | UI exposed network down/up rates, disk read/write rates, and trends with 23 samples spanning about 599s; later spans changed. Disk write explicitly displayed 0.0 B/s. Trend descriptions state missing samples are gaps. Actual missing/stale samples were not observed. |
| Storage | PASS | UI exposed two mount entries with used/total capacities and progress indicators. Host accuracy was not independently compared. |
| Short headings | PASS | Accessible headings included Status, CPU, RAM, Network, Disk, Storage, Users, Processes. Visual clipping/readability is unverified. |
| Host comparison, unavailable/stale readings, valid-zero accuracy | BLOCKED | No authorized unlocked app terminal was available; no comparable host sampling windows or naturally missing readings were observed. An explicit zero was displayed, but its correctness was not verified. |
| Account totals and sample labels | PASS | Loaded per-account CPU/RSS totals and process counts; UI labels “Sample totals”, “Partial sample”, and “RSS includes shared memory.” RSS was not interpreted as unique whole-system usage. |
| Account filter updates data | PASS | All users → single account changed totals to that account and list to one matching process. Restored All users. |
| RAM sorting | PASS | RAM ↓ selected; visible first entries were 1.6 GiB then 704 MiB. Restored CPU ↓. Only the visible ordering was verified. |
| CPU sorting | PASS | CPU ↓ selected after restoration; visible first rows showed 11.4% then 7.8%. |
| System-process toggle | PASS | Enabling added system accounts and increased results to 200 received. A subsequent tap to restore the initial off state was issued; final state was not independently re-read before leaving Users. |
| Search empty state | PASS | Unique harmless search produced zero shown and “No matching processes in this sample. Try another name or PID.” Clear restored rows. |
| Search count clarity | FAIL | With that no-match search, summary read “0 shown · 145 matching · top 145 received.” The “matching” count conflicts with the empty search result; see issue below. |
| Scrolling and process details | PASS | Vertical outer scroll moved widgets; tapping one process expanded PID, state, runtime and name, and tapping again collapsed it. Nested list scrolling and full details coverage remain unverified. No task-control action was used. |
| Loading-state clarity | FAIL | Changing account/sort temporarily replaced populated totals with “No data” and list status with “Connecting”, “Updating…” and “Loading processes…”, then recovered. “No data” is misleading while an update is in flight; see issue below. |
| Terminal privacy gate | PASS | Terminal initially showed “Verify with your phone to reveal this widget.” Unlock opened Android authentication. The prompt reported face not recognized and offered fingerprint/pattern. No authentication was bypassed or entered by the tester. |
| Terminal commands and behavior | BLOCKED | No completed normal authentication observed. `pwd`, `whoami`, `uname -s`, `printf`, keyboard, selection, scrolling, private-input masking, clear, disconnect/reconnect, background return and command-replay tests were not performed. |
| Temporary macros | BLOCKED | Authentication unavailable; creation, quoting, insert/reviewed run and cancellation untested. No macro created or run. |
| Settings/themes | BLOCKED | Owner phone activity interrupted access before inspection. Nord, Amber, Monochrome, accents, polling intervals and persistence untested. No settings changed. |
| Saved connections/setup | BLOCKED | Not inspected; existing profiles and keys were not edited. Isolated onboarding/invalid-field profile was not created. |
| Presets/custom tabs | BLOCKED | Hardware/Tasks presets, duplicates, independent identities, blank tabs, rename/reorder/delete and persistence untested. No test tab created. |
| Widget editing | BLOCKED | Add/remove, sizing, drag order and moving between temporary tabs untested. No widget edited. |
| Optional widgets | BLOCKED | Per-core metrics, RAM breakdown, sensors, interfaces/drives, system details, failed-service/journal expansion, watches, jobs/outcomes/durations untested. No fixtures created; no tasks terminated or services controlled. |
| Monitoring/alerts/notifications | BLOCKED | Monitor state, alert settings, quiet hours, mute and explicit notification test not inspected or changed. No notification or acknowledgement claimed. |
| Resilience/accessibility | BLOCKED | Background/reopen, disconnected recovery, orientation, font scaling, keyboard overlap, visual clipping and normal relaunch persistence untested. Network/VPN unchanged. Back was issued at authentication, but successful cancellation was not established by a fresh hierarchy. |
| Every Power tab/widget/preset/action/API test | EXCLUDED BY USER | No Power tab was selected; no horizontal swipe was used. No Power widget/preset/controller was interacted with. |

## Reproducible findings

### P2 — Search summary calls nonmatching processes “matching”

1. Open Users, leave All users selected, and wait for processes to load.
2. Enter `sentinel_test_no_match_914` in Search name or PID and dismiss the keyboard.
3. Observe the empty state and count summary.

Expected: search-matching count agrees with the zero-result state, or clearly distinguishes server/account matches from search matches. Actual: “0 shown · 145 matching · top 145 received” appears above “No matching processes in this sample.” Search filtering itself worked. Reproduced once on the installed build; raw private process output was not retained in the report.

### P3 — Filter/sort refresh briefly reports absence/disconnection

1. In a loaded Users view, change All users to a single account, or switch CPU/RAM sort.
2. Read Users and Processes while the new query loads.

Expected: loading/updating labels distinguish pending data from an established empty result or lost connection. Actual: Users temporarily said “No data”; Processes showed “Connecting”, “Updating…” and “Loading processes…”. Valid populated results returned on subsequent reads. Observed across filter/sort changes. Duration was not precisely measured; no network fault was induced.

## Preservation, exceptions and remaining work

- No connection/profile/key, theme, polling interval, tab order, widget, macro, alert, monitor state or task-history mutation was deliberately made. No app data cleared, app uninstalled, helper installed, network changed, or task terminated.
- Temporary Users changes: account filter restored to All users; search cleared; CPU descending restored; expanded row collapsed. System-process toggle was tapped back toward its initial off state, but final off state could not be verified. This is the only app setting restoration uncertainty.
- Original Home scroll position was near Network/Disk/Storage. Testing scrolled upward and later switched tabs. Original scroll position and selected Home tab were not restored after the phone became occupied; last confirmed Sentinel selection was Terminal with authentication pending. No attempt was made to take the phone away from the owner.
- ADB-generated temporary UI dump paths `/sdcard/window.xml` and `/sdcard/sentinel-test-current.xml` were cleaned up without further UI interaction. Local scratch evidence is under `/tmp/sentinel-phone-test` and contains a protected black screenshot and the hierarchy-reading helper.
- No Power UI exclusion breach was observed. A proposed read-only diagnostic included `dumpsys power`; automatic approval review rejected the entire command before execution because it violated the user's Power exclusion. It was not retried. This was a test-procedure error, not an app finding.
- Highest-impact coverage gaps: authenticated Terminal/macros and host accuracy checks; all Settings, workspace editing, optional widgets and resilience tests. These remain BLOCKED, not passed. No high-severity app failure was established in the completed coverage.
