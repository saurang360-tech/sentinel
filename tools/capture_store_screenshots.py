"""Capture synthetic release UI as JPEGs; never capture a configured real PC.

Requires an unlocked USB-debugging phone and a CLEAN production installation.
Temporarily changes its logical viewport to 1080x1920 and restores it in finally.
The paired .preview application is never cleared or configured by this tool.
"""
from pathlib import Path
import os
import re
import struct
import subprocess
import xml.etree.ElementTree as ET
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ADB = os.environ.get('ADB_BIN', '/home/Amus/Android/Sdk/platform-tools/adb')
PACKAGE = 'dev.titus.sentinel'


def adb(*args):
    return subprocess.check_output([ADB, *args])


def nodes():
    adb('shell', 'uiautomator', 'dump', '/sdcard/sentinel-store-ui.xml')
    root = ET.fromstring(adb('shell', 'cat', '/sdcard/sentinel-store-ui.xml'))
    return [n for n in root.iter('node') if n.get('package') == PACKAGE]


def tap(label):
    for _ in range(5):
        for n in nodes():
            if n.get('text') == label:
                x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
                adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
                return
        adb('shell', 'input', 'swipe', '540', '1600', '540', '500', '300')
    raise RuntimeError('Expected release UI control missing: ' + label)


def capture(filename):
    visible = [n.get('text', '') for n in nodes()]
    if 'DEMO · Synthetic examples, no PC connection' not in visible:
        raise RuntimeError('Refusing to capture an unlabelled or real PC screen')
    # Encode the raw Android display buffer directly as RGB JPEG. No pixel edits,
    # crops, retouching or fabricated UI; only the capture's storage format changes.
    raw = adb('exec-out', 'screencap')
    width, height, fmt, _colorspace = struct.unpack_from('<IIII', raw)
    if fmt != 1 or len(raw) != 16 + width*height*4:
        raise RuntimeError('Unsupported raw screen format; no screenshot written')
    image = Image.frombytes('RGBA', (width, height), raw[16:])
    if (width, height) != (1080, 1920):
        raise RuntimeError('Unexpected display size; no screenshot written')
    output = ROOT / 'release/store/screenshots' / filename
    output.parent.mkdir(parents=True, exist_ok=True)
    image.convert('RGB').save(output, quality=95, subsampling=0)


def main():
    size = adb('shell', 'wm', 'size').decode()
    match = re.search(r'Override size: (\d+x\d+)', size)
    previous = match.group(1) if match else 'reset'
    try:
        adb('shell', 'wm', 'size', '1080x1920')
        adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        tap('Explore demo — no connection')
        tap('Health')
        capture('01-health-demo.jpg')
        tap('Details')
        capture('02-details-demo.jpg')
        tap('Processes')
        tap('RAM')
        capture('03-processes-ram-demo.jpg')
        tap('Terminal')
        capture('04-terminal-info-demo.jpg')
        print('Captured four genuine, labelled 1080x1920 demo screens; no PC connection.')
    finally:
        adb('shell', 'wm', 'size', previous)
        adb('shell', 'rm', '-f', '/sdcard/sentinel-store-ui.xml')


if __name__ == '__main__':
    main()
