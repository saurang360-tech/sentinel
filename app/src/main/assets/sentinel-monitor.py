#!/usr/bin/python3
"""Unprivileged, allowlisted status and process metadata helper."""
import datetime
import json
import os
from pathlib import Path
import subprocess
import sys

ALLOWED_STATES = {"running", "degraded", "starting", "initializing", "maintenance", "stopping", "offline", "unknown"}

def collect():
    try:
        state = subprocess.run(["/usr/bin/systemctl", "is-system-running"],
                               capture_output=True, text=True, timeout=2,
                               env={"PATH": "/usr/bin", "LANG": "C"}).stdout.strip()
    except (OSError, subprocess.TimeoutExpired):
        state = "unknown"
    return {"protocol": 1, "host": os.uname().nodename,
            "bootId": Path("/proc/sys/kernel/random/boot_id").read_text().strip(),
            "uptimeSeconds": float(Path("/proc/uptime").read_text().split()[0]),
            "systemState": state if state in ALLOWED_STATES else "unknown",
            "users": discover_users(), "capabilities": {"processes": 2, "users": 1, "watches": 1, "jobs": 1},
            "sampledAt": datetime.datetime.now(datetime.timezone.utc).isoformat().replace("+00:00", "Z")}

# PROCESS_COLLECTOR_SOURCE
"""Process-metadata collector: never opens cmdline, environ, fd, cwd, or documents."""
import os
import re
from pathlib import Path
import pwd
import time

MAX_ROWS = 200
MAX_SCAN = 8192

def parse_stat(raw):
    left, right = raw.find('('), raw.rfind(')')
    if left < 0 or right <= left:
        raise ValueError('Invalid process stat')
    fields = raw[right+2:].split()
    if len(fields) < 22:
        raise ValueError('Incomplete process stat')
    name = ''.join(c for c in raw[left+1:right] if c.isprintable())[:32] or '[unnamed]'
    return {'name': name, 'state': fields[0], 'ticks': int(fields[11])+int(fields[12]),
            'startTicks': int(fields[19]), 'rssPages': int(fields[21])}

def cpu_percent(previous, current, total_delta):
    if previous is None or previous['startTicks'] != current['startTicks'] or total_delta <= 0:
        return None
    delta = current['ticks'] - previous['ticks']
    if delta < 0:
        return None
    return round(min(100.0, 100.0 * delta / total_delta), 2)

def discover_users(passwd_path=Path('/etc/passwd'), defs_path=Path('/etc/login.defs')):
    """Local human login accounts only; no shadow, NSS network lookup or home contents."""
    uid_min,uid_max=1000,60000
    try:
        for line in defs_path.read_text().splitlines():
            parts=line.split()
            if len(parts)>=2 and parts[1].isdecimal():
                if parts[0]=='UID_MIN':uid_min=int(parts[1])
                elif parts[0]=='UID_MAX':uid_max=int(parts[1])
    except OSError:pass
    result=[]
    for line in passwd_path.read_text().splitlines():
        fields=line.split(':')
        if len(fields)!=7:continue
        name,_,uid,_,_,_,shell=fields
        if not uid.isdecimal() or not re.fullmatch(r'[A-Za-z_][A-Za-z0-9_.-]{0,63}[$]?',name):continue
        uid=int(uid)
        if uid_min<=uid<=uid_max and shell and Path(shell).name not in {'nologin','false','sync','shutdown','halt'}:
            result.append({'uid':uid,'name':name})
    unique={u['uid']:u for u in sorted(result,key=lambda u:u['name'])}
    return sorted(unique.values(),key=lambda u:u['name'].casefold())[:256]

def parse_process_request(command):
    match=re.fullmatch(r'sentinel-processes-v2 (all|0|[1-9][0-9]{0,9}) (cpu|ram) (user|all)',command)
    if not match:raise ValueError('Unsupported process request')
    owner,sort,scope=match.groups()
    uid=None if owner=='all' else int(owner)
    if uid is not None and uid>4294967294:raise ValueError('Invalid UID')
    return uid,sort,scope

def collect_processes(owner, proc=Path('/proc'), sample_interval=0.4, sort='cpu', scope='all', users=None):
    # Retain the exact legacy requests for installed older clients.
    if isinstance(owner,str):
        if owner != 'all':raise ValueError('Unsupported legacy owner')
        try:requested_uid=None if owner=='all' else pwd.getpwnam(owner).pw_uid
        except KeyError:requested_uid=-1
    else:
        requested_uid=owner
        if owner is not None and (not isinstance(owner,int) or owner<0 or owner>4294967294):raise ValueError('Invalid UID')
    if sort not in {'cpu','ram'} or scope not in {'user','all'}:raise ValueError('Invalid process options')
    users=discover_users() if users is None else users
    human_uids={u['uid'] for u in users}
    hz, page_size = os.sysconf('SC_CLK_TCK'), os.sysconf('SC_PAGE_SIZE')
    denied, vanished = set(), set()
    scanned_limit = False
    def total_ticks():
        fields = (proc/'stat').read_text().splitlines()[0].split()
        if fields[0] != 'cpu': raise ValueError('Missing aggregate CPU counters')
        return sum(map(int, fields[1:9]))  # guest counters already included in user/nice
    def frame():
        nonlocal scanned_limit
        rows = {}
        pids = sorted(int(p.name) for p in proc.iterdir() if p.name.isdecimal())
        if len(pids) > MAX_SCAN: scanned_limit = True
        for pid in pids[:MAX_SCAN]:
            path = proc/str(pid)
            try:
                # Effective UID determines the reported process owner.
                uid_line = next(line for line in (path/'status').read_text().splitlines() if line.startswith('Uid:'))
                uid = int(uid_line.split()[2])
                if requested_uid is not None and uid != requested_uid: continue
                if scope=='user' and uid not in human_uids:continue
                row = parse_stat((path/'stat').read_text())
                row.update(pid=pid, uid=uid)
                rows[pid] = row
            except PermissionError: denied.add(pid)
            except (FileNotFoundError, ProcessLookupError): vanished.add(pid)
            except (OSError, ValueError, StopIteration): vanished.add(pid)
        return rows
    first_total = total_ticks(); before = frame(); began = time.monotonic()
    time.sleep(sample_interval)
    after = frame(); total_delta = total_ticks() - first_total
    sample_seconds = time.monotonic() - began
    uptime = float((proc/'uptime').read_text().split()[0])
    owners = {}
    result = []
    for pid, row in after.items():
        uid = row['uid']
        if uid not in owners:
            try: owners[uid] = pwd.getpwuid(uid).pw_name
            except KeyError: owners[uid] = str(uid)
        result.append({'pid': pid, 'name': row['name'], 'uid': uid, 'owner': owners[uid],
                       'state': row['state'], 'startTicks': row['startTicks'],
                       'cpuPercent': cpu_percent(before.get(pid), row, total_delta),
                       'rssBytes': row['rssPages']*page_size if row['rssPages'] >= 0 else None,
                       'runtimeSeconds': round(max(0, uptime-row['startTicks']/hz), 1)})
    def order(row):
        cpu=row['cpuPercent'] if row['cpuPercent'] is not None else -1
        ram=row['rssBytes'] if row['rssBytes'] is not None else -1
        return (-cpu,-ram,row['pid']) if sort=='cpu' else (-ram,-cpu,row['pid'])
    result.sort(key=order)
    return {'ownerFilter':owner if isinstance(owner,str) else ('all' if owner is None else str(owner)),
            'sort':sort,'scope':scope,'users':users,'cpuBasis':'total-machine','sampleSeconds':round(sample_seconds,3),
            'totalMatching':len(result),'limited':len(result)>MAX_ROWS,'scanLimited':scanned_limit,
            'denied':len(denied),'vanished':len(vanished),'processes':result[:MAX_ROWS]}

# HEALTH_COLLECTOR_SOURCE
"""Unprivileged Linux health counters and exposed sensors; no diagnostics or writes."""
import re
import os
import time
import json
import datetime
import subprocess
from pathlib import Path

PROC = Path('/proc')
SYS = Path('/sys')

def text_value(path, default=None):
    try: return Path(path).read_text().strip()
    except (OSError, UnicodeError): return default

def number_value(path, divisor=1):
    try: return int(text_value(path))/divisor
    except (ValueError, TypeError): return None

def cpu_frame(raw):
    values = list(map(int, raw.splitlines()[0].split()[1:9]))
    if len(values) != 8: raise ValueError('Incomplete CPU counters')
    return values

def cpu_usage(before, after):
    delta = [b-a for a,b in zip(before,after)]
    # Linux documents that iowait may decrease. Never turn a reset into utilisation.
    if len(delta)!=8 or any(v<0 for v in delta) or sum(delta)<=0: return None,None
    total=sum(delta)
    return round(100*(total-delta[3]-delta[4])/total,2),round(100*delta[4]/total,2)

def memory_values(raw):
    result={}
    for line in raw.splitlines():
        parts=line.split()
        if len(parts)>=2 and parts[0] in {'MemTotal:','MemAvailable:','SwapTotal:','SwapFree:','MemFree:','Buffers:','Cached:'}:
            result[parts[0][:-1]]=int(parts[1])*1024
    total=result.get('MemTotal');available=result.get('MemAvailable')
    swap_total=result.get('SwapTotal');swap_free=result.get('SwapFree')
    return {'totalBytes':total,'availableBytes':available,
            'freeBytes':result.get('MemFree'),'buffersBytes':result.get('Buffers'),'cachedBytes':result.get('Cached'),
            'usedBytes':total-available if total is not None and available is not None and 0<=available<=total else None,
            'swapTotalBytes':swap_total,'swapUsedBytes':swap_total-swap_free if swap_total is not None and swap_free is not None and 0<=swap_free<=swap_total else None}

def counter_rate(before, after, seconds):
    if before is None or after is None or after<before or seconds<=0: return None
    return round((after-before)/seconds,2)

def net_frame():
    result={}
    for line in (text_value(PROC/'net/dev','')).splitlines()[2:]:
        if ':' not in line: continue
        name,fields=line.split(':',1);name=name.strip();fields=fields.split()
        if name=='lo' or len(fields)<16:continue
        result[name]=(int(fields[0]),int(fields[8]))
    return result

def disk_frame():
    result={}
    for line in text_value(PROC/'diskstats','').splitlines():
        f=line.split()
        if len(f)>=14 and (SYS/'block'/f[2]/'device').exists():
            result[f[2]]=(int(f[5])*512,int(f[9])*512)
    return result

def mount_unescape(value):
    return re.sub(r'\\([0-7]{3})',lambda m:chr(int(m.group(1),8)),value)

def volumes(mountinfo=None):
    raw=text_value(PROC/'self/mountinfo','') if mountinfo is None else mountinfo
    candidates=[('System','/',None,'Storage'),('Home','/home',None,'Storage'),('Variable data','/var',None,'Storage')]
    for line in raw.splitlines():
        fields=line.split()
        try:
            sep=fields.index('-');path=mount_unescape(fields[4]);source=mount_unescape(fields[sep+2]);fstype=fields[sep+1]
            if not source.startswith('/dev/') or fstype not in {'ext2','ext3','ext4','xfs','btrfs','f2fs','vfat','exfat','ntfs','ntfs3','zfs'}:continue
            dev=(SYS/'dev/block'/fields[2]).resolve()
            rotational=None
            for base in [dev,*list(dev.parents)[:2]]:
                rotational=number_value(base/'queue/rotational')
                if rotational is not None:break
            kind='SSD' if rotational==0 else 'HDD' if rotational==1 else 'Storage'
            candidates.append(('System' if path=='/' else Path(path).name or path,path,source,kind))
        except (ValueError,IndexError,OSError):continue
    candidates.sort(key=lambda v:(v[1]!='/',len(v[1]),v[1]))
    seen=set();paths=set();values=[]
    for label,path,source,kind in candidates:
        if path in paths or source is not None and source in seen:continue
        if path=='/' and source is None and any(c[1]=='/' and c[2] is not None for c in candidates):continue
        paths.add(path)
        if source is not None:seen.add(source)
        row={'label':label,'path':path,'kind':kind,'mounted':True,'totalBytes':None,'usedBytes':None,'availableBytes':None}
        try:
            v=os.statvfs(path)
            row.update(totalBytes=v.f_blocks*v.f_frsize,usedBytes=(v.f_blocks-v.f_bfree)*v.f_frsize,availableBytes=v.f_bavail*v.f_frsize)
        except OSError:row['mounted']=False
        values.append(row)
        if len(values)>=16:break
    return values

def sensors():
    temperatures=[];fans=[];power=[];cpu_temp=None;cpu_label=None;cpu_critical=None
    for directory in sorted((SYS/'class/hwmon').glob('hwmon*')):
        driver=text_value(directory/'name',directory.name)
        # Firmware-backed reads can block for seconds per channel on Titus.
        # Keep these out of foreground polling; coretemp/chipset reads remain live.
        if driver in {'gigabyte_wmi', 'acpitz'}: continue
        for path in sorted(directory.glob('temp*_input')):
            stem=path.name[:-6]
            if text_value(directory/(stem+'_fault'),'0')!='0': continue
            c=number_value(path,1000)
            if c is None or not -100<=c<=200:continue
            label=text_value(directory/(stem+'_label'),stem)
            temperatures.append({'label':driver+' / '+label,'value':c})
            if driver in {'coretemp','k10temp','zenpower'} and ('Package' in label or label=='Tdie' or cpu_temp is None):
                cpu_temp=c;cpu_label=label;cpu_critical=number_value(directory/(stem+'_crit'),1000)
        for path in sorted(directory.glob('fan*_input')):
            stem=path.name[:-6]
            if text_value(directory/(stem+'_fault'),'0')!='0':continue
            value=number_value(path)
            if value is not None and value>=0:fans.append({'label':driver+' / '+text_value(directory/(stem+'_label'),stem),'value':value})
        channels={p.name.split('_')[0] for p in directory.glob('power*_*')}
        for channel in sorted(channels):
            if text_value(directory/(channel+'_fault'),'0')!='0':continue
            value=number_value(directory/(channel+'_average'),1e6)
            if value is None:value=number_value(directory/(channel+'_input'),1e6)
            if value is not None and value>=0:power.append({'label':driver+' / '+text_value(directory/(channel+'_label'),channel),'value':value})
    return temperatures[:64],fans[:32],power[:32],cpu_temp,cpu_label,cpu_critical

def gpus():
    rows=[]
    names={('0x10de','0x1e84'):'GeForce RTX 2070 SUPER',('0x8086','0x3e92'):'Intel UHD Graphics 630'}
    for card in sorted((SYS/'class/drm').glob('card*')):
        if not re.fullmatch(r'card[0-9]+',card.name):continue
        d=card/'device';vendor=text_value(d/'vendor');device=text_value(d/'device')
        if not vendor:continue
        busy=number_value(d/'gpu_busy_percent')
        if busy is not None and not 0<=busy<=100:busy=None
        temps=[number_value(p,1000) for p in d.glob('hwmon/hwmon*/temp*_input')]
        temps=[t for t in temps if t is not None and -100<=t<=200]
        rows.append({'name':names.get((vendor,device),f'GPU {vendor}:{device}'),'driver':(d/'driver').resolve().name if (d/'driver').exists() else 'unavailable',
                     'utilisationPercent':busy,'temperatureC':max(temps) if temps else None,
                     'vramUsedBytes':number_value(d/'mem_info_vram_used'),'vramTotalBytes':number_value(d/'mem_info_vram_total')})
    return rows[:8]

def failed_units(user=False):
    command=['/usr/bin/systemctl']+(['--user'] if user else [])+['list-units','--failed','--type=service','--no-pager','--output=json']
    env={'PATH':'/usr/bin','LANG':'C','XDG_RUNTIME_DIR':f'/run/user/{os.getuid()}'}
    try:
        result=subprocess.run(command,capture_output=True,text=True,timeout=0.8,env=env)
        if result.returncode!=0 or len(result.stdout)>1_048_576:return None
        units=json.loads(result.stdout)
        names=[u['unit'] for u in units if isinstance(u.get('unit'),str)]
        return {'count':len(names),'names':names[:50]}
    except (OSError,ValueError,subprocess.TimeoutExpired):return None

def collect_health(sample_interval=0.4):
    raw_before=text_value(PROC/'stat','')
    cpu_before=cpu_frame(raw_before)
    net_before=net_frame();disk_before=disk_frame();began=time.monotonic()
    time.sleep(sample_interval)
    raw_after=text_value(PROC/'stat','')
    cpu_after=cpu_frame(raw_after)
    cores=[]
    before_cores={line.split()[0]:line for line in raw_before.splitlines() if re.match(r'^cpu[0-9]+ ',line)}
    for line in raw_after.splitlines():
        name=line.split()[0] if line.split() else ''
        if name in before_cores and len(cores)<64:
            try: value,_=cpu_usage(cpu_frame(before_cores[name]),cpu_frame(line))
            except (ValueError,IndexError): value=None
            frequency=number_value(SYS/'devices/system/cpu'/name/'cpufreq/scaling_cur_freq',1000)
            cores.append({'name':name,'utilisationPercent':value,'frequencyMHz':frequency if frequency is not None and frequency>0 else None})
    net_after=net_frame();disk_after=disk_frame();elapsed=time.monotonic()-began
    cpu_percent_value,iowait=cpu_usage(cpu_before,cpu_after)
    memory=memory_values(text_value(PROC/'meminfo',''))
    psi=text_value(PROC/'pressure/memory','')
    match=re.search(r'^some\s+avg10=([0-9.]+)',psi,re.M)
    memory['pressureSomeAvg10']=float(match.group(1)) if match else None
    networks=[]
    for name,(rx,tx) in net_after.items():
        prev=net_before.get(name)
        networks.append({'name':name,'state':text_value(SYS/'class/net'/name/'operstate','unknown'),
            'physical':(SYS/'class/net'/name/'device').exists(),
            'rxBytesPerSecond':counter_rate(prev[0] if prev else None,rx,elapsed),
            'txBytesPerSecond':counter_rate(prev[1] if prev else None,tx,elapsed)})
    disks=[]
    for name,(read,write) in disk_after.items():
        prev=disk_before.get(name)
        disks.append({'name':name,'model':text_value(SYS/'block'/name/'device/model',name),
            'readBytesPerSecond':counter_rate(prev[0] if prev else None,read,elapsed),
            'writeBytesPerSecond':counter_rate(prev[1] if prev else None,write,elapsed)})
    temperature,fans,power,cpu_temp,cpu_label,cpu_critical=sensors()
    storage=volumes();warnings=[]
    if memory['availableBytes'] is not None and memory['totalBytes'] and memory['availableBytes']/memory['totalBytes']<0.1:
        warnings.append('Available RAM is below 10%.')
    if memory['pressureSomeAvg10'] is not None and memory['pressureSomeAvg10']>1:
        warnings.append('Memory pressure is causing measurable task stalls.')
    if cpu_temp is not None and cpu_critical is not None and cpu_temp>=cpu_critical-10:warnings.append('CPU temperature is near its hardware critical limit.')
    for volume in storage:
        if not volume['mounted']:warnings.append(volume['label']+' is not mounted.')
        elif volume['availableBytes'] is not None and volume['totalBytes'] and volume['availableBytes']/volume['totalBytes']<0.1:warnings.append(volume['label']+' has less than 10% available capacity.')
    cpu_name=next((l.split(':',1)[1].strip() for l in text_value(PROC/'cpuinfo','').splitlines() if l.startswith('model name')),'CPU model unavailable')
    os_name='Linux'
    for line in text_value('/etc/os-release','').splitlines():
        if line.startswith('PRETTY_NAME='):os_name=line.split('=',1)[1].strip('"')
    return {'capabilities':{'watches':1,'jobs':1},'protocol':1,'kind':'health','host':os.uname().nodename,
        'sampledAt':datetime.datetime.now(datetime.timezone.utc).isoformat().replace('+00:00','Z'),
        'sampleSeconds':round(elapsed,3),'uptimeSeconds':float(text_value(PROC/'uptime','0').split()[0]),
        'osName':os_name,'kernel':os.uname().release,'load':list(os.getloadavg()),
        'cpu':{'cores':cores,'name':cpu_name,'logicalCores':os.cpu_count(),'utilisationPercent':cpu_percent_value,'iowaitPercent':iowait,'temperatureC':cpu_temp,'temperatureCriticalC':cpu_critical,'temperatureLabel':cpu_label},
        'memory':memory,'volumes':storage,'networks':networks[:32],'disks':disks[:32],
        'temperatures':temperature,'fans':fans,'componentPower':power,'gpus':gpus(),
        'failedSystemServices':failed_units(),'failedUserServices':failed_units(True),'warnings':warnings}


def collect_watches(command, proc=Path('/proc')):
    """Exact identities only; PID disappearance never establishes a successful exit."""
    tokens = command.split()[1:]
    if not 1 <= len(tokens) <= 32:
        raise ValueError('Watch limit')
    boot = (proc/'sys/kernel/random/boot_id').read_text().strip()
    rows = []
    for token in tokens:
        match = re.fullmatch(r'([1-9][0-9]{0,9}):([0-9]{1,20}):([0-9a-f-]{36})', token)
        if not match:
            raise ValueError('Invalid identity')
        pid, start, expected_boot = match.groups()
        state = 'ended_unknown'
        if expected_boot == boot:
            try:
                current = parse_stat((proc/pid/'stat').read_text())
                if current['startTicks'] == int(start):
                    state = 'ended_unknown' if current['state'] in {'Z', 'X', 'x'} else 'running'
            except (FileNotFoundError, ProcessLookupError):
                pass
            except (OSError, ValueError):
                state = 'unavailable'
        rows.append({'identity': token, 'state': state})
    return {'protocol': 1, 'kind': 'watches', 'bootId': boot,
            'sampledAt': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'watches': rows}


# JOB_RUNNER_SOURCE
# Local execution is deliberately separate from the read-only SSH protocol.
import uuid
import signal
import fcntl
import stat

def job_directory():
    directory = Path.home()/'.local/state/sentinel/jobs'
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    info = directory.lstat()
    if not stat.S_ISDIR(info.st_mode) or info.st_uid != os.getuid() or info.st_mode & 0o077:
        raise ValueError('Job directory must be owned by this account with mode 0700')
    return directory

def job_write(directory, record, filename=None):
    # Random private temporary file + atomic replace; no command or output retained.
    target = directory/(filename if filename is not None else record['id']+'.json')
    temporary = directory/(str(uuid.uuid4())+'.tmp')
    try:
        with open(temporary, 'x', opener=lambda path, flags: os.open(path, flags, 0o600)) as stream:
            json.dump(record, stream)
            stream.flush()
            os.fsync(stream.fileno())
        temporary.replace(target)
    finally:
        temporary.unlink(missing_ok=True)

def job_alive(record, proc=Path('/proc')):
    try:
        current = parse_stat((proc/str(record['runnerPid'])/'stat').read_text())
        return (proc/'sys/kernel/random/boot_id').read_text().strip() == record['bootId'] and \
            current['startTicks'] == record['runnerStartTicks'] and current['state'] not in {'Z','X','x'}
    except (OSError, ValueError, KeyError):
        return False

def job_records(directory):
    result = []
    for path in sorted(directory.glob('*.json'))[:128]:
        try:
            if path.is_symlink() or path.stat().st_size > 8192:
                continue
            record = json.loads(path.read_text())
            if str(uuid.UUID(record['id'])) != record['id'] or path.stem != record['id']:
                continue
            if record['state'] not in {'running', 'succeeded', 'failed', 'timed_out', 'ended_unknown'}:
                continue
            if record['state'] == 'running' and not job_alive(record):
                record.update(state='ended_unknown', exitCode=None)
            result.append(record)
        except (OSError, ValueError, KeyError, TypeError):
            continue
    return sorted(result, key=lambda r: r['startedMillis'], reverse=True)

def read_expectations(directory):
    path=directory/'.expectations'
    if not path.exists(): return []
    if path.is_symlink() or path.stat().st_size>16384: raise ValueError('Invalid expectation file')
    rules=json.loads(path.read_text())['rules']
    if not isinstance(rules,list) or len(rules)>32: raise ValueError('Too many expectations')
    labels=set()
    for rule in rules:
        if str(uuid.UUID(rule['id']))!=rule['id']: raise ValueError('Invalid expectation identity')
        label=rule['label']
        if not isinstance(label,str) or not 1<=len(label)<=64 or any(not c.isprintable() for c in label) or label in labels:
            raise ValueError('Invalid expectation label')
        labels.add(label)
        if not 60<=rule['intervalSeconds']<=604800 or not 0<=rule['graceSeconds']<=86400 or rule['anchorMillis']<=0:
            raise ValueError('Invalid expectation timing')
        success=rule.get('lastSuccessMillis')
        if success is not None and success<rule['anchorMillis']: raise ValueError('Invalid last success')
    return rules

def evaluate_expectations(rules,records,now):
    values=[]
    for rule in rules:
        successes=[r['endedMillis'] for r in records if r['label']==rule['label'] and r['state']=='succeeded'
                   and r.get('exitCode')==0 and r.get('endedMillis') is not None and r['endedMillis']>=rule['anchorMillis']]
        if rule.get('lastSuccessMillis') is not None: successes.append(rule['lastSuccessMillis'])
        last=max(successes) if successes else None
        deadline=(last or rule['anchorMillis'])+(rule['intervalSeconds']+rule['graceSeconds'])*1000
        values.append({'id':rule['id'],'label':rule['label'],'lastSuccessMillis':last,'deadlineMillis':deadline,
                       'state':'overdue_unknown' if now>deadline else 'within_window'})
    return values

def configure_expectation(label,interval_minutes,grace_minutes):
    if not label or len(label)>64 or any(not c.isprintable() for c in label): raise ValueError('Invalid label')
    interval,grace=int(interval_minutes)*60,int(grace_minutes)*60
    if not 60<=interval<=604800 or not 0<=grace<=86400: raise ValueError('Invalid timing')
    directory=job_directory()
    with open(directory/'.lock','a',opener=lambda path,flags: os.open(path,flags | os.O_NOFOLLOW,0o600)) as lock:
        fcntl.flock(lock,fcntl.LOCK_EX)
        rules=read_expectations(directory)
        previous=next((r for r in rules if r['label']==label),None)
        if previous is None and len(rules)>=32: raise ValueError('32 expectations already configured')
        # Reconfiguration explicitly starts a fresh expectation window.
        rule={'id':previous['id'] if previous else str(uuid.uuid4()),'label':label,'intervalSeconds':interval,
              'graceSeconds':grace,'anchorMillis':int(time.time()*1000),'lastSuccessMillis':None}
        rules=[r for r in rules if r['label']!=label]+[rule]
        job_write(directory,{'rules':rules},filename='.expectations')
    return 0

def remember_expected_success(directory,record):
    if record['state']!='succeeded': return
    with open(directory/'.lock','a',opener=lambda path,flags: os.open(path,flags | os.O_NOFOLLOW,0o600)) as lock:
        fcntl.flock(lock,fcntl.LOCK_EX)
        rules=read_expectations(directory)
        changed=False
        for rule in rules:
            if rule['label']==record['label'] and record['endedMillis']>=rule['anchorMillis']:
                rule['lastSuccessMillis']=max(rule.get('lastSuccessMillis') or 0,record['endedMillis'])
                changed=True
        if changed: job_write(directory,{'rules':rules},filename='.expectations')


def collect_jobs():
    directory = Path.home()/'.local/state/sentinel/jobs'
    records = []
    expectations = []
    if directory.exists():
        directory = job_directory()
        now = int(time.time()*1000)
        all_records=job_records(directory)
        expectations=evaluate_expectations(read_expectations(directory),all_records,now)
        # Reads do not modify state. Expiration is applied to returned history too.
        records = [r for r in all_records
                   if r['state']=='running' or now-r['startedMillis'] <= 7*86400000][:64]
    # Return allowlisted metadata only. Never expose argv, stdout or stderr.
    keys = ('id','label','state','exitCode','startedMillis','endedMillis')
    return {'protocol':1,'kind':'jobs','jobs':[{k:r.get(k) for k in keys} for r in records],
            'sampledAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),
            'delivery':'direct-polling','retentionDays':7,'expectations':expectations}

def run_job(label, command, timeout=None):
    if timeout is not None and not 1<=timeout<=86400: raise ValueError("Timeout must be 1–86400 seconds")
    if not label or len(label)>64 or any(not c.isprintable() for c in label) or not command:
        raise ValueError('Use a printable label up to 64 characters and a command after --')
    directory = job_directory()
    lock_path = directory/'.lock'
    with open(lock_path, 'a', opener=lambda path, flags: os.open(path, flags | os.O_NOFOLLOW, 0o600)) as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        records = job_records(directory)
        if sum(r['state']=='running' for r in records) >= 32:
            raise ValueError('32 running jobs already tracked')
        finished = [r for r in records if r['state']!='running']
        keep_finished = 63-sum(r['state']=='running' for r in records)
        for old in finished[keep_finished:]:
            (directory/(old['id']+'.json')).unlink(missing_ok=True)
        now = int(time.time()*1000)
        for old in records:
            if old['state']!='running' and now-old['startedMillis']>7*86400000:
                (directory/(old['id']+'.json')).unlink(missing_ok=True)
        record={'id':str(uuid.uuid4()),'label':label,'state':'running','exitCode':None,
                'startedMillis':now,'endedMillis':None,'runnerPid':os.getpid(),
                'runnerStartTicks':parse_stat(Path('/proc/self/stat').read_text())['startTicks'],
                'bootId':Path('/proc/sys/kernel/random/boot_id').read_text().strip()}
        job_write(directory,record)
    print('Sentinel job '+record['id'], file=sys.stderr, flush=True)
    child = None
    old_handlers = {}
    def forward(signum, frame):
        if child is not None:
            try: os.killpg(child.pid, signum)
            except ProcessLookupError: pass
    try:
        for signum in (signal.SIGTERM, signal.SIGINT, signal.SIGHUP):
            old_handlers[signum] = signal.signal(signum, forward)
        child = subprocess.Popen(command, start_new_session=True)
        timed_out=False
        try: code = child.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            timed_out=True
            try: os.killpg(child.pid,signal.SIGTERM)
            except ProcessLookupError: pass
            try: code=child.wait(timeout=5)
            except subprocess.TimeoutExpired:
                try: os.killpg(child.pid,signal.SIGKILL)
                except ProcessLookupError: pass
                code=child.wait()
        record.update(state='timed_out' if timed_out else 'succeeded' if code==0 else 'failed',exitCode=code,
                      endedMillis=int(time.time()*1000))
    except OSError:
        # Failure to launch is explicit; no fabricated child exit code.
        record.update(state='failed',exitCode=None,endedMillis=int(time.time()*1000))
        code = 127
    finally:
        for signum, handler in old_handlers.items(): signal.signal(signum,handler)
        job_write(directory,record)
        try: remember_expected_success(directory,record)
        except (OSError,ValueError,KeyError,TypeError):
            print('Expected-success metadata could not be updated; the job exit record is retained.',file=sys.stderr)
    return code if code>=0 else 128-code

def job_preflight(path):
    target=Path(path).resolve(strict=True)
    info=os.statvfs(target)
    return {'path':str(target),'availableBytes':info.f_bavail*info.f_frsize,
            'writable':os.access(target,os.W_OK),'sleepPolicy':'unknown',
            'delivery':'Phone must be open; no background delivery is configured.',
            'permissions':'Uses this account only; no sudo or sleep inhibition.'}

def local_cli(arguments):
    if len(arguments)>=5 and arguments[0]=="run-timeout" and arguments[3]=="--":
        return run_job(arguments[1],arguments[4:],timeout=int(arguments[2]))
    if len(arguments)>=4 and arguments[0]=='run' and arguments[2]=='--':
        return run_job(arguments[1],arguments[3:])
    if len(arguments)==2 and arguments[0]=='preflight':
        print(json.dumps(job_preflight(arguments[1]))); return 0
    if len(arguments)==4 and arguments[0]=='expect':
        return configure_expectation(*arguments[1:])
    if arguments==['forget-expectations']:
        directory=job_directory()
        with open(directory/'.lock','a',opener=lambda path,flags: os.open(path,flags | os.O_NOFOLLOW,0o600)) as lock:
            fcntl.flock(lock,fcntl.LOCK_EX)
            (directory/'.expectations').unlink(missing_ok=True)
        return 0
    if arguments==['jobs']:
        print(json.dumps(collect_jobs())); return 0
    if arguments==['delete-history']:
        directory=job_directory()
        for record in job_records(directory):
            if record['state']!='running': (directory/(record['id']+'.json')).unlink(missing_ok=True)
        return 0
    print('Usage: sentinel-monitor.py run LABEL -- COMMAND [ARG...] | preflight PATH | jobs | delete-history | expect LABEL INTERVAL_MIN GRACE_MIN | forget-expectations',file=sys.stderr)
    return 64


def service_detail(command):
    match=re.fullmatch(r'sentinel-service-v1 (system|user) ([A-Za-z0-9_.@:-]{1,160}\.service)',command)
    if match is None: raise ValueError('Invalid service request')
    scope,unit=match.groups()
    args=['/usr/bin/journalctl']+(['--user'] if scope=='user' else [])+['-b','-u',unit,'-n','40','--no-pager','-o','short-iso']
    try:
        process=subprocess.run(args,capture_output=True,text=True,timeout=2,env={'PATH':'/usr/bin','LANG':'C','XDG_RUNTIME_DIR':f'/run/user/{os.getuid()}'})
        detail=process.stdout[:8000] if process.returncode==0 else 'Journal permission denied or unit journal unavailable.'
        detail=''.join(c for c in detail if c.isprintable() or c in '\n\t')
    except (OSError,subprocess.TimeoutExpired): detail='Journal query unavailable or timed out.'
    return {'protocol':1,'kind':'service','unit':unit,'scope':scope,'detail':detail or 'No journal entries visible to this account.'}


def main():
    if "SSH_ORIGINAL_COMMAND" not in os.environ and len(sys.argv)>1:
        try: return local_cli(sys.argv[1:])
        except (OSError, ValueError):
            print("Sentinel could not access private job state or validate the request.",file=sys.stderr)
            return 65
    command = os.environ.get("SSH_ORIGINAL_COMMAND", "")
    if command.startswith("sentinel-service-v1 "):
        try: result=service_detail(command)
        except ValueError: return 64
    elif command.startswith("sentinel-watches-v1 "):
        try: result = collect_watches(command)
        except ValueError: return 64
    elif command == "sentinel-jobs-v1":
        result = collect_jobs()
    elif command == "sentinel-status-v1":
        result = collect()
    elif command.startswith("sentinel-processes-v2 "):
        try:uid,sort,scope=parse_process_request(command)
        except ValueError:return 64
        result={"protocol":2,"kind":"processes","host":os.uname().nodename,
                "capabilities":{"watches":1,"jobs":1},
                "bootId": Path("/proc/sys/kernel/random/boot_id").read_text().strip(),
                "sampledAt":datetime.datetime.now(datetime.timezone.utc).isoformat().replace('+00:00','Z'),
                **collect_processes(uid,sort=sort,scope=scope)}
    elif command == "sentinel-health-v1":
        result = collect_health()
    elif command == "sentinel-processes-v1 all":
        result = {"protocol": 1, "kind": "processes", "host": os.uname().nodename,
                  "sampledAt": datetime.datetime.now(datetime.timezone.utc).isoformat().replace("+00:00", "Z"),
                  **collect_processes(command.split()[1])}
    else:
        print("Only the reviewed status/process requests are allowed.", file=sys.stderr)
        return 64
    encoded = json.dumps(result, separators=(",", ":"))
    if len(encoded.encode()) > 131072:
        return 65
    print(encoded, flush=True)
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
