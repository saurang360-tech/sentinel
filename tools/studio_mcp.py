"""Small MCP transport for this IDE; uses the endpoint configured by Android Studio."""
import json, sys, tomllib, urllib.request, threading
from pathlib import Path
cfg = tomllib.loads((Path.home()/'.codex/config.toml').read_text())
url = cfg['mcp_servers']['studio']['url']
if not url.startswith('http://127.0.0.1:'):
    raise SystemExit('Expected a loopback Android Studio server')
headers = {'Content-Type': 'application/json', 'Accept': 'application/json, text/event-stream'}
def request(data):
    with urllib.request.urlopen(urllib.request.Request(url, data=json.dumps(data).encode(), headers=headers), timeout=180) as response:
        if response.headers.get('Mcp-Session-Id'):
            headers['Mcp-Session-Id'] = response.headers['Mcp-Session-Id']
        raw = response.read().decode()
    if not raw.strip(): return {}
    if raw.startswith('data:'): raw = raw.split('data: ', 1)[1].split('\n')[0]
    return json.loads(raw)
request({'jsonrpc':'2.0','id':1,'method':'initialize','params':{'protocolVersion':'2025-03-26','capabilities':{},'clientInfo':{'name':'Codex Studio project tools','version':'1'}}})
# JetBrains requires an SSE listener even when POST replies are JSON.
def listen():
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=180) as stream:
            for _ in stream: pass
    except Exception: pass
threading.Thread(target=listen, daemon=True).start()
request({'jsonrpc':'2.0','method':'notifications/initialized'})
result = request({'jsonrpc':'2.0','id':2,'method':'tools/call','params':{'name':'execute_tool','arguments':{'command':sys.argv[1], 'projectPath':str(Path(__file__).resolve().parents[1])}}})
print(json.dumps(result, indent=2))
if 'error' in result or result.get('result', {}).get('isError'): sys.exit(1)
