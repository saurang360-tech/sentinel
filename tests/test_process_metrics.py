import importlib.util
from pathlib import Path
import unittest
import tempfile
from unittest.mock import patch
from types import SimpleNamespace
p=Path(__file__).resolve().parents[1]/'deployment/process_metrics.py'
spec=importlib.util.spec_from_file_location('process_metrics',p);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class ProcessMetricsTest(unittest.TestCase):
    def test_stat_with_spaces_and_parentheses(self):
        fields=['S']+['0']*21
        fields[11]='100';fields[12]='20';fields[19]='12345';fields[21]='512'
        row=m.parse_stat('42 (worker (example)) '+' '.join(fields))
        self.assertEqual(row['name'],'worker (example)');self.assertEqual(row['ticks'],120)
        self.assertEqual(row['startTicks'],12345);self.assertEqual(row['rssPages'],512)
    def test_cpu_normalised_to_total_machine_and_pid_reuse(self):
        a={'ticks':100,'startTicks':3};b={'ticks':150,'startTicks':3}
        self.assertEqual(m.cpu_percent(a,b,500),10.0)
        self.assertIsNone(m.cpu_percent(a,{**b,'startTicks':4},500))
        self.assertIsNone(m.cpu_percent(a,{**b,'ticks':20},500))
        self.assertIsNone(m.cpu_percent(None,b,500));self.assertIsNone(m.cpu_percent(a,b,0))
    def test_live_metadata_has_no_command_or_content_fields(self):
        sample=m.collect_processes('Amus',sample_interval=0.02)
        self.assertGreater(sample['totalMatching'],0)
        self.assertLessEqual(len(sample['processes']),200)
        for row in sample['processes']:
            self.assertEqual(row['owner'],'Amus')
            self.assertEqual(set(row),{'pid','name','uid','owner','state','startTicks','cpuPercent','rssBytes','runtimeSeconds'})
    def test_owner_command_injection_is_rejected(self):
        for owner in ['root','Amus;id','all --anything','skyler\nall']:
            with self.assertRaises(ValueError):m.collect_processes(owner,sample_interval=0)

    def test_local_accounts_appear_and_disappear_without_hardcoded_names(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/'passwd';d=Path(tmp)/'defs';d.write_text('UID_MIN 500\nUID_MAX 60000')
            p.write_text('root:x:0:0::/root:/bin/bash\nalice:x:501:501::/home/alice:/bin/bash\nservice:x:1001:1001::/:/usr/sbin/nologin\nnobody:x:65534:65534::/:/bin/bash\n')
            self.assertEqual([{'uid':501,'name':'alice'}],m.discover_users(p,d))
            p.write_text('bob:x:1002:1002::/home/bob:/bin/zsh\n')
            self.assertEqual([{'uid':1002,'name':'bob'}],m.discover_users(p,d))

    def test_v2_request_grammar_does_not_accept_shell_or_arbitrary_arguments(self):
        self.assertEqual((1001,'ram','user'),m.parse_process_request('sentinel-processes-v2 1001 ram user'))
        self.assertEqual((None,'cpu','all'),m.parse_process_request('sentinel-processes-v2 all cpu all'))
        for command in ['sentinel-processes-v2 all cpu user;id','sentinel-processes-v2 -1 ram user','sentinel-processes-v2 1 cpu user\n','sentinel-processes-v2 all name user','sentinel-processes-v2 4294967295 cpu all']:
            with self.assertRaises(ValueError):m.parse_process_request(command)

    def test_uid_filter_and_ram_sort_happen_before_row_cap(self):
        with tempfile.TemporaryDirectory() as tmp:
            proc=Path(tmp);(proc/'stat').write_text('cpu 100 0 0 0 0 0 0 0');(proc/'uptime').write_text('10000 0')
            def stat(pid,ticks,rss):
                fields=['S']+['0']*21;fields[11]=str(ticks);fields[19]='1';fields[21]=str(rss)
                return f'{pid} (task{pid}) '+' '.join(fields)
            for pid,uid,rss in [(1,0,10000),(2,1001,1),(3,1001,100),(4,1002,500)]:
                d=proc/str(pid);d.mkdir();(d/'status').write_text(f'Uid: {uid} {uid} {uid} {uid}');(d/'stat').write_text(stat(pid,0,rss))
            def advance(_):
                (proc/'stat').write_text('cpu 200 0 0 0 0 0 0 0')
                for pid,rss,cpu in [(1,10000,70),(2,1,20),(3,100,5),(4,500,1)]:
                    (proc/str(pid)/'stat').write_text(stat(pid,cpu,rss))
            users=[{'uid':1001,'name':'alice'},{'uid':1002,'name':'bob'}]
            with patch.object(m.time,'sleep',side_effect=advance),patch.object(m,'MAX_ROWS',1),patch.object(m.pwd,'getpwuid',side_effect=lambda uid:SimpleNamespace(pw_name=str(uid))):
                ram=m.collect_processes(None,proc,0,sort='ram',scope='user',users=users)
                self.assertEqual(4,ram['processes'][0]['pid']);self.assertEqual(3,ram['totalMatching'])
                alice=m.collect_processes(1001,proc,0,sort='ram',scope='user',users=users)
                self.assertEqual(3,alice['processes'][0]['pid']);self.assertEqual(2,alice['totalMatching'])
                removed=m.collect_processes(1001,proc,0,sort='cpu',scope='user',users=[users[1]])
                self.assertEqual([],removed['processes'])
