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
        if owner not in ('all','Amus','skyler'):raise ValueError('Unsupported legacy owner')
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
