"""Render the bundled legal text as static HTML; never upload anything.

Fill release/store/publisher.json first for final publication. Draft output is
explicitly marked when publisher/contact/URL information is missing.
"""
from pathlib import Path
import html
import json

ROOT = Path(__file__).resolve().parents[1]


def prepare():
    folder = ROOT / 'release/store'
    metadata = json.loads((folder / 'publisher.json').read_text())
    missing = [k for k in ('publisherName', 'supportEmail', 'privacyPolicyUrl') if not metadata.get(k)]
    for name in ('privacy-policy', 'terms', 'third-party-notices'):
        text = (ROOT / f'app/src/main/assets/legal/{name}.txt').read_text()
        details = ''
        if metadata.get('publisherName'):
            details += 'Publisher: ' + metadata['publisherName'] + '\n'
        if metadata.get('supportEmail'):
            details += 'Support/privacy contact: ' + metadata['supportEmail'] + '\n'
        warning = '<p class="draft">PRE-RELEASE DRAFT: publisher details are incomplete.</p>' if missing else ''
        page = ('<!doctype html><html lang="en"><meta charset="utf-8">'
                '<meta name="viewport" content="width=device-width,initial-scale=1">'
                f'<title>Sentinel — {html.escape(name.replace("-", " "))}</title>'
                '<style>body{max-width:800px;margin:40px auto;padding:0 20px;font:16px/1.6 system-ui;'
                'color:#202020;background:#fff}pre{white-space:pre-wrap;font:inherit}.draft{color:#8b3100}</style>'
                f'<body>{warning}<pre>{html.escape(details + text)}</pre></body></html>')
        (folder / f'{name}.html').write_text(page)
    print('Prepared static HTML in release/store; no upload performed.')
    if missing:
        print('Not publishable yet. Missing: ' + ', '.join(missing))


if __name__ == '__main__':
    prepare()
