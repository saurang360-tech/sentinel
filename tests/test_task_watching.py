"""Identity, disconnected outcomes, durable verified results and SSH boundaries."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

HELPER = Path(__file__).resolve().parents[1]/'app/src/main/assets/sentinel-monitor.py'
spec = importlib.util.spec_from_file_location('watch_helper', HELPER)
helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper)

class TaskWatchingTest(unittest.TestCase):
    def test_pid_reuse_reboot_and_missing_never_mean_success(self):
        boot = Path('/proc/sys/kernel/random/boot_id').read_text().strip()
        pid = os.getpid()
        start = helper.parse_stat(Path('/proc/self/stat').read_text())['startTicks']
        identities = [f'{pid}:{start}:{boot}', f'{pid}:{start+1}:{boot}',
                      f'{pid}:{start}:00000000-0000-0000-0000-000000000000', f'2147483647:1:{boot}']
        rows = helper.collect_watches('sentinel-watches-v1 '+' '.join(identities))['watches']
        self.assertEqual([r['state'] for r in rows], ['running', 'ended_unknown', 'ended_unknown', 'ended_unknown'])

    def test_requests_bounded_and_cannot_inject_commands(self):
        for suffix in ['', '1:1:bad', '1:1:'+'a'*36+';id', ' '.join(['1:1:'+'a'*36]*33)]:
            with self.assertRaises(ValueError): helper.collect_watches('sentinel-watches-v1 '+suffix)
        result = subprocess.run([sys.executable, '-I', str(HELPER), 'run', 'bad', '--', '/usr/bin/true'],
            env={'SSH_ORIGINAL_COMMAND':'run ignored -- /usr/bin/true'},capture_output=True)
        self.assertEqual(result.returncode,64)

    def run_cli(self, home, *args):
        env = dict(os.environ, HOME=home)
        env.pop('SSH_ORIGINAL_COMMAND',None)
        return subprocess.run([sys.executable,'-I',str(HELPER),*args],env=env,capture_output=True,text=True)

    def test_success_failure_and_launch_failure_persist_without_output(self):
        with tempfile.TemporaryDirectory() as home:
            self.assertEqual(self.run_cli(home,'run','Success','--',sys.executable,'-c','print("PRIVATE OUTPUT")').returncode,0)
            self.assertEqual(self.run_cli(home,'run','Failure','--',sys.executable,'-c','raise SystemExit(7)').returncode,7)
            self.assertEqual(self.run_cli(home,'run','Cannot launch','--','/nonexistent/sentinel-command').returncode,127)
            result = self.run_cli(home,'jobs')
            self.assertEqual(result.returncode,0,result.stderr)
            jobs = {r['label']:r for r in json.loads(result.stdout)['jobs']}
            self.assertEqual((jobs['Success']['state'],jobs['Success']['exitCode']),('succeeded',0))
            self.assertEqual((jobs['Failure']['state'],jobs['Failure']['exitCode']),('failed',7))
            self.assertEqual((jobs['Cannot launch']['state'],jobs['Cannot launch']['exitCode']),('failed',None))
            directory=Path(home)/'.local/state/sentinel/jobs'
            self.assertEqual(directory.stat().st_mode & 0o777,0o700)
            for path in directory.glob('*.json'):
                self.assertNotIn('PRIVATE OUTPUT',path.read_text())
                self.assertNotIn('raise SystemExit',path.read_text())
                self.assertEqual(path.stat().st_mode & 0o777,0o600)
            self.assertEqual(self.run_cli(home,'delete-history').returncode,0)
            self.assertEqual(json.loads(self.run_cli(home,'jobs').stdout)['jobs'],[])

    def test_killed_runner_is_unknown_and_reconnect_retains_result(self):
        with tempfile.TemporaryDirectory() as home:
            env = dict(os.environ,HOME=home);env.pop('SSH_ORIGINAL_COMMAND',None)
            process = subprocess.Popen([sys.executable,'-I',str(HELPER),'run','Interrupted','--',sys.executable,'-c','import time;time.sleep(1)'],env=env,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
            try:
                self.assertIn(b'Sentinel job',process.stderr.readline())
                process.kill();process.wait(timeout=5)
                jobs=json.loads(self.run_cli(home,'jobs').stdout)['jobs']
                self.assertEqual(jobs[0]['state'],'ended_unknown')
                self.assertIsNone(jobs[0]['exitCode'])
            finally:
                if process.poll() is None: process.kill();process.wait()
                process.stderr.close()

    def test_preflight_reports_unknown_sleep_and_available_disk(self):
        with tempfile.TemporaryDirectory() as home:
            result=self.run_cli(home,'preflight',home)
            self.assertEqual(result.returncode,0,result.stderr)
            info=json.loads(result.stdout)
            self.assertEqual(info['sleepPolicy'],'unknown')
            self.assertGreater(info['availableBytes'],0)
            self.assertTrue(info['writable'])

    def test_expected_success_deadlines_do_not_infer_failure_from_missing_runs(self):
        rule={'id':'00000000-0000-0000-0000-000000000001','label':'Backup','intervalSeconds':60,
              'graceSeconds':30,'anchorMillis':1000,'lastSuccessMillis':None}
        within=helper.evaluate_expectations([rule],[],91000)[0]
        overdue=helper.evaluate_expectations([rule],[],91001)[0]
        self.assertEqual(within['state'],'within_window')
        self.assertEqual(overdue['state'],'overdue_unknown')
        self.assertIsNone(overdue['lastSuccessMillis'])
        failure={'label':'Backup','state':'failed','exitCode':7,'endedMillis':90000}
        self.assertEqual(helper.evaluate_expectations([rule],[failure],92000)[0]['state'],'overdue_unknown')
        success=dict(failure,state='succeeded',exitCode=0)
        observed=helper.evaluate_expectations([rule],[success],92000)[0]
        self.assertEqual(observed['state'],'within_window')
        self.assertEqual(observed['lastSuccessMillis'],90000)
        self.assertEqual(observed['deadlineMillis'],180000)

    def test_cli_expectations_are_opt_in_and_preserve_success_after_history_deletion(self):
        with tempfile.TemporaryDirectory() as home:
            configured=self.run_cli(home,'expect','Backup','60','5')
            self.assertEqual(configured.returncode,0,configured.stderr)
            self.assertEqual(self.run_cli(home,'run','Backup','--','/usr/bin/true').returncode,0)
            before=json.loads(self.run_cli(home,'jobs').stdout)['expectations'][0]
            self.assertIsNotNone(before['lastSuccessMillis'])
            self.assertEqual(before['state'],'within_window')
            self.assertEqual(self.run_cli(home,'delete-history').returncode,0)
            after=json.loads(self.run_cli(home,'jobs').stdout)['expectations'][0]
            self.assertEqual(after['lastSuccessMillis'],before['lastSuccessMillis'])
            sidecar=Path(home)/'.local/state/sentinel/jobs/.expectations'
            self.assertEqual(sidecar.stat().st_mode & 0o777,0o600)
            self.assertEqual(self.run_cli(home,'forget-expectations').returncode,0)
            self.assertEqual(json.loads(self.run_cli(home,'jobs').stdout)['expectations'],[])
            self.assertNotEqual(self.run_cli(home,'expect','Backup','0','0').returncode,0)

if __name__=='__main__': unittest.main()
