# Alert hygiene, sustained health and expected-success checks — 9 November 2026

This functionality pass adds U06 controls, opt-in U11 sustained warnings and a
narrow U09 integration using the existing PC runner. The UI consists of simple
buttons for testing. The existing in-progress redesign is preserved.

## Alert policy

Overview has an Alert Policy test card. Settings are global to this phone:

- Per-task mute buttons for watched processes, runner jobs and expected-success
  rules. At most 256 hashed mute scopes are saved; no plaintext task/host identity
  is stored in those mute preferences.
- Minimum severity: all outcomes (default), warnings/errors, or errors only.
  Verified failures are errors; unknown outcomes, overdue expectations and health
  warnings are warnings; success and health recovery are informational.
- Daily quiet hours use the phone's local time. The end hour is exclusive;
  crossing midnight works. Equal start/end means quiet all day. Default: disabled.
- A cooldown groups later alerts until its interval expires (default 1 minute;
  test choices 0, 1, 5, 15 minutes). New outcomes from the same poll remain grouped.
- Muted/below-severity events are suppressed and receipted, so unmuting does not
  replay old outcomes. Quiet/cooldown alerts are deferred in a bounded, 256-event
  session-memory queue. This queue is lost on service/process stop, including
  phone restart. The explicit local Test notification bypasses these filters.
- Health recovery replaces an undelivered health warning for that same sensor;
  a resolved expectation removes its queued obsolete deadline warning. This
  prevents stale quiet-hours alerts claiming a problem is still active.

Optional periodic digests are not implemented. Notification handoff receipts still
cannot prove display, sound or user attention. These controls apply to the optional
background monitor, not the platform's notification channel settings.

## Sustained health (off by default)

Enable Sustained health alerts, then start/keep running the background monitor.
Health polling is added only while opted in; this adds helper/SSH overhead.

| Signal | Trigger | Recovery |
|---|---|---|
| Available RAM | Below 10% | At least 15% |
| Memory PSI some avg10 | At least 1% | Below 0.5% |
| Mounted volume available capacity | Below 10% | At least 15% |
| CPU temperature | Within 10°C of reported hardware critical limit | More than 15°C below critical |

Trigger and recovery each need observations spanning 60 seconds. A threshold spike
resets the pending dwell. Sensor absence, invalid values, an observed connection
interruption or sample gaps over 90 seconds reset pending dwell, and never prove
recovery. A prior active warning remains last-known until recovery is observed.
CPU warning availability requires both the temperature and the hardware critical
limit; there is no invented universal temperature limit. Unmounted volumes are
unavailable, not zero available space. Different disk paths are different causes.

Active health episodes live in session memory, not a long-term sensor log. A new
session starts a new sustained-observation window. No hardware/power settings or
sampling privileges are changed.

## Expected successful jobs

Configure on the PC, using the updated helper and the same account as monitoring:

```sh
python3 /path/to/sentinel-monitor.py expect "Backup" 1440 60
python3 /path/to/sentinel-monitor.py run "Backup" -- /path/to/your-backup-command
python3 /path/to/sentinel-monitor.py jobs
python3 /path/to/sentinel-monitor.py forget-expectations
```

The first command opts in to an expected successful command exit every 1440 minutes
with 60 minutes of grace. It does not schedule or execute the backup. Use the PC's
existing scheduler to run the wrapper if wanted. Reconfiguring the label starts a
fresh expectation window. There are at most 32 rules; interval is 1 minute to 7 days,
and grace is 0 to 1440 minutes.

Rules and last verified success are kept in the private PC jobs directory in a
0600 `.expectations` file, protected by the existing runner lock and atomic writes.
They remain until `forget-expectations` or explicit directory removal; job history
deletion does not erase configured expectations or their last successful timestamp.
Successful runner exits update matching labels. Failures and unobserved runs do not
count as success. The read-only monitoring SSH protocol cannot configure rules.

The phone displays the deadline and rule state; the background monitor alerts once
per overdue deadline, subject to policy. Wording is **no verified success observed;
outcome unknown**. This checks exit records for a user-chosen label. It does not
verify backup contents, external schedulers, unwrapped work, detached children, or
prove that no run occurred. No new live evidence can be obtained while the PC is
unreachable; phone-clock/PC-clock discrepancies are not diagnosed automatically.

## Validation and limits

Pure tests cover midnight/end boundaries, cooldowns, spikes, hysteresis, gaps,
missing sensors, recovery and repeat health episodes. Python tests cover grace
boundaries, failure-vs-success and durable expectation metadata after history
deletion. Device tests cover filtering, deferred delivery, mute persistence,
non-replay after unmute, obsolete-queue resolution, sensor availability and
unknown-outcome expectation wording, plus the existing background/protocol/UI tests.
Final results are recorded in `alerts-validation.txt`.

Real PC integration still requires installing the updated helper through the
existing administrator-reviewed setup. This pass does not modify PC SSH keys,
services, power policy, sudo rules or the installed helper. Real overnight/Doze,
locked-phone signing, unrelated hardware, signed release/store acceptance, digest
scheduling, guided presets, awake mode and shared-session/shutdown checklists
remain work. This is not a completion claim for the entire 17-workstream roadmap.
