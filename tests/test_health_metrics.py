import importlib.util
import unittest
import tempfile
from pathlib import Path
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('health',Path(__file__).parents[1]/'deployment/health_metrics.py')
h=importlib.util.module_from_spec(spec);spec.loader.exec_module(h)
class HealthTest(unittest.TestCase):
    def test_cpu_counts_exclude_guest_and_iowait(self):
        a=h.cpu_frame('cpu 10 0 10 80 0 0 0 0 50 0')
        b=h.cpu_frame('cpu 30 0 20 140 10 0 0 0 60 0')
        self.assertEqual((30.0,10.0),h.cpu_usage(a,b))
        self.assertEqual((None,None),h.cpu_usage(b,a))
    def test_memory_uses_available_not_free(self):
        m=h.memory_values('MemTotal: 1000 kB\nMemAvailable: 600 kB\nMemFree: 100 kB\nSwapTotal: 200 kB\nSwapFree: 150 kB')
        self.assertEqual(400*1024,m['usedBytes'])
        self.assertEqual(50*1024,m['swapUsedBytes'])
        self.assertIsNone(h.memory_values('')['usedBytes'])
    def test_rates_and_generic_mounts(self):
        self.assertIsNone(h.counter_rate(100,10,1))
        self.assertEqual(20,h.counter_rate(100,110,.5))
        raw='1 0 8:1 / / rw - ext4 /dev/sda1 rw\n2 0 8:2 / /data rw - xfs /dev/sdb1 rw\n3 0 0:1 / /remote rw - nfs host:/share rw\n4 0 8:1 /home /home rw - ext4 /dev/sda1 rw'
        actual=h.os.statvfs
        def stat(path):
            if path=='/remote':raise AssertionError('Remote mounts must not be inspected')
            if path=='/data':raise OSError('Unmounted')
            return actual('/')
        with patch.object(h.os,'statvfs',side_effect=stat):values=h.volumes(raw)
        self.assertEqual(['/', '/data'],[v['path'] for v in values])
        self.assertFalse(values[1]['mounted']);self.assertIsNone(values[1]['totalBytes'])
        self.assertEqual('/media/My Disk',h.mount_unescape('/media/My\\040Disk'))
    def test_sensor_units_and_faults(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);hw=root/'class/hwmon/hwmon0';hw.mkdir(parents=True)
            for name,value in {'name':'coretemp','temp1_input':'42000','temp1_label':'Package id 0','temp1_crit':'100000','temp2_input':'99000','temp2_fault':'1','fan1_input':'1200','power1_input':'15000000'}.items():
                (hw/name).write_text(value)
            with patch.object(h,'SYS',root):
                temperatures,fans,power,cpu,label,critical=h.sensors()
            self.assertEqual(1,len(temperatures));self.assertEqual(42,cpu)
            self.assertEqual(100,critical);self.assertEqual(1200,fans[0]['value']);self.assertEqual(15,power[0]['value'])
    def test_slow_firmware_channels_are_never_polled(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            for index,driver in enumerate(['gigabyte_wmi','acpitz']):
                hw=root/f'class/hwmon/hwmon{index}';hw.mkdir(parents=True)
                (hw/'name').write_text(driver);(hw/'temp1_input').write_text('42000')
            original=h.text_value
            def guarded(path,default=None):
                if Path(path).name=='temp1_input':raise AssertionError('Blocking firmware channel was polled')
                return original(path,default)
            with patch.object(h,'SYS',root),patch.object(h,'text_value',side_effect=guarded):
                self.assertEqual(([],[],[],None,None,None),h.sensors())
    def test_real_health_contains_bounded_real_counters(self):
        j=h.collect_health(.05)
        self.assertEqual('health',j['kind']);self.assertGreater(j['memory']['totalBytes'],0)
        self.assertLessEqual(j['cpu']['utilisationPercent'],100)
        self.assertNotIn('commandLine',str(j))
