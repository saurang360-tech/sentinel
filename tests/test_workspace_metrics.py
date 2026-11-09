import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('workspace_helper',Path(__file__).parents[1]/'app/src/main/assets/sentinel-monitor.py')
h=importlib.util.module_from_spec(spec);spec.loader.exec_module(h)

class WorkspaceMetricsTest(unittest.TestCase):
    def test_ram_breakdown_is_raw_linux_fields_and_missing_stays_missing(self):
        result=h.memory_values('MemTotal: 1000 kB\nMemAvailable: 600 kB\nMemFree: 100 kB\nBuffers: 20 kB\nCached: 300 kB\n')
        self.assertEqual(400*1024,result['usedBytes'])
        self.assertEqual(100*1024,result['freeBytes'])
        self.assertEqual(20*1024,result['buffersBytes'])
        self.assertEqual(300*1024,result['cachedBytes'])
        self.assertIsNone(h.memory_values('')['cachedBytes'])

    def test_service_request_is_allowlisted_and_never_uses_shell(self):
        with self.assertRaises(ValueError): h.service_detail('sentinel-service-v1 system x.service; id')
        with patch.object(h.subprocess,'run',return_value=subprocess.CompletedProcess([],0,'trace line\n', '')) as run:
            result=h.service_detail('sentinel-service-v1 user example.service')
        self.assertEqual('service',result['kind'])
        self.assertEqual('trace line\n',result['detail'])
        args=run.call_args.args[0]
        self.assertIn('--user',args);self.assertEqual('example.service',args[args.index('-u')+1])
        self.assertNotIn('shell',run.call_args.kwargs)
    def test_core_frames_are_collected_from_both_samples(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            sys=root/'sys'
            freq=sys/'devices/system/cpu/cpu0/cpufreq'
            freq.mkdir(parents=True)
            (freq/'scaling_cur_freq').write_text('2400000')
            (root/'stat').write_text('cpu 10 0 10 80 0 0 0 0\ncpu0 5 0 5 40 0 0 0 0\n')
            (root/'meminfo').write_text('MemTotal: 1000 kB\nMemAvailable: 500 kB\n')
            (root/'uptime').write_text('100 0')
            (root/'cpuinfo').write_text('model name: Example')
            def tick(_): (root/'stat').write_text('cpu 30 0 20 140 10 0 0 0\ncpu0 15 0 10 70 5 0 0 0\n')
            with patch.object(h,'PROC',root),patch.object(h,'SYS',sys),patch.object(h.time,'sleep',side_effect=tick),patch.object(h,'net_frame',return_value={}),patch.object(h,'disk_frame',return_value={}),patch.object(h,'volumes',return_value=[]),patch.object(h,'sensors',return_value=([],[],[],None,None,None)),patch.object(h,'gpus',return_value=[]),patch.object(h,'failed_units',return_value=None):
                result=h.collect_health(.01)
            self.assertEqual('cpu0',result['cpu']['cores'][0]['name'])
            self.assertEqual(2400,result['cpu']['cores'][0]['frequencyMHz'])
            self.assertAlmostEqual(30.0,result['cpu']['cores'][0]['utilisationPercent'])
    def test_timeout_keeps_actual_exit_result(self):
        with tempfile.TemporaryDirectory() as directory,patch.object(h.Path,'home',return_value=Path(directory)):
            code=h.run_job('Timeout test',['/bin/sleep','10'],timeout=1)
            self.assertNotEqual(0,code)
            records=list((Path(directory)/'.local/state/sentinel/jobs').glob('*.json'))
            self.assertEqual(1,len(records))
            record=json.loads(records[0].read_text())
            self.assertEqual('timed_out',record['state'])
            self.assertLess(record['exitCode'],0)
            self.assertGreaterEqual(record['endedMillis'],record['startedMillis'])
