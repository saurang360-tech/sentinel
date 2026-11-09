# Sentinel phone testing prompt

Test the installed Sentinel Android app on my connected phone as a real user would. Use the actual UI, not just source inspection or simulated screens. The repository is `/home/Amus/Android/PC_moniter_app`; the paired app is `dev.titus.sentinel.preview`. Identify the connected phone before starting. Do not run these tests against my PC's power controls.

## Absolute exclusion: Power

- Never open, select, test, edit, remove, or otherwise interact with the Power tab or any power widget, button, preset, dialog, controller, or API.
- Never shut down, reboot, restart, suspend, hibernate, log out, or run related commands on the phone or PC; never run service-control macros or enter a sudo/administrator password.
- Do not swipe horizontally between pages: navigate by explicitly named non-Power tabs so you cannot land on Power in between.
- Do not use random tapping, monkey tests, blind gestures, or a broad test suite that could reach a power action.
- If Power opens accidentally, immediately leave using Android Back without touching its contents, report the exclusion breach, and stop the run.
- Mark every Power-related test **EXCLUDED BY USER**, never passed.

## Preserve my setup

Keep my paired installation, connection profiles, keys, existing widgets, tab order, macros, alert settings, and task history. Record the starting layout/theme so you can restore them. Never clear app data, uninstall the paired app, overwrite credentials, bypass authentication, install a PC helper, change SSH/systemd/sudo/network settings, or terminate a real task. If fresh onboarding needs a clean install, use a separate application ID without pairing it to my PC. Only remove test items you created. Wait for phone authentication when necessary or mark that step blocked; do not bypass it.

## Test the non-Power experience

1. **Launch and Home:** launch, foreground/background, refresh and reopen; check connection state, sample age, uptime, CPU, temperature, RAM, storage, network and disk graphs, unavailable readings and stale readings. Confirm the visible headings stay short and useful. Observe real polling and graph history; distinguish a valid zero from missing data. Compare available readings with harmless, read-only host commands over comparable sampling windows; do not invent readings or claim instantaneous equality.
2. **Users:** inspect per-account sampled CPU/RSS totals, partial-sample labels, process account filters, system-process toggle, CPU/RAM sorting, search, scrolling and process details. Verify filter changes update the data; do not describe sampled RSS as unique whole-system memory.
3. **Terminal:** use the existing authorized shell only after the normal unlock. Try harmless commands such as `pwd`, `whoami`, `uname -s` and `printf`. Check keyboard visibility, scrolling, selection, private-input masking, clear, disconnect/reconnect, and returning from the background. Create only temporary harmless macros; test parameter quoting, insert versus reviewed run, cancellation, and no command replay on reconnect. Never use power, sudo, service-control, destructive, or installation commands.
4. **Settings and themes:** check Nord, Amber and Monochrome, accent options, supported polling intervals, persistence after reopening, readable contrast and larger system text where safely reversible. Restore the original settings. Inspect saved connection/setup screens without changing existing profiles or keys. Test first-use and invalid-field flows only in an isolated install/profile.
5. **Presets and tabs:** use Settings → Add preset to add a temporary non-Power preset such as Hardware or Tasks. Check that it selects the new tab and preserves other tabs. Add a duplicate non-Power preset and check independent widget identities. Add a blank custom tab; rename, reorder and delete only test tabs. Check persistence after reopening. Do not select or add the Power preset.
6. **Widgets:** on temporary non-Power tabs, add and remove widgets, change width/height, drag to reorder, and move between temporary tabs. Confirm other tabs are unchanged and nested scrolling, tab navigation and large text remain usable. Never add, move, remove or interact with a power widget.
7. **Optional non-Power widgets:** through a temporary Hardware or Tasks preset, inspect per-core metrics, RAM breakdown, sensors, interfaces/drives, system details, failed services and expandable journal details, watches, job outcomes/durations, monitoring, alerts, quiet hours, mute and notification handoff. Do not restart a service or terminate a task. Use only existing safe observations or isolated test fixtures for job/watch outcomes; missing fixtures are blocked coverage, not a pass. Start/stop only Sentinel's Android background monitor if needed, then restore its original state; do not control a PC daemon. Test local notifications only through an available explicit test action. Distinguish an OS handoff from a visibly observed notification and from confirmed user acknowledgement.
8. **Resilience and clarity:** check Back navigation, empty states, naturally occurring disconnected/unavailable states, orientation if supported, keyboard overlap, clipping, font scaling and recovery after a normal relaunch. Do not interrupt my VPN/network or change the PC to manufacture failures. Use an isolated profile for invalid-connection tests.

## Evidence and result

For every check, record PASS, FAIL, BLOCKED, or EXCLUDED with the observed behavior. Do not infer a pass from code alone, synthetic data, a test result from an older build, or an unobserved action. Record device, package/version, build/commit if identifiable, and test time. Do not capture credentials, private terminal output, keys, or sensitive connection details; respect screenshot protection rather than disabling it.

Save a concise report with reproducible steps, expected versus actual results, severity, and safe screenshots where available. Report missing helper/sensor permissions as limitations, not fake successful tests. Restore temporary changes and explicitly list anything you could not restore. Finish with the highest-impact UX bugs and coverage gaps. Do not fix code, commit, push, or perform any Power-related action during this testing task.
