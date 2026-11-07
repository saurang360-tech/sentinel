"""Generate scalable Sentinel branding and matching Android vector resources."""
from pathlib import Path
import xml.etree.ElementTree as E
paths = [
    # Angular shield, framing the helmet without ornamental detail.
    ('#F4F4F1', 'M26,30 L33,27 L33,31 L30,33 L33,63 Q37,75 54,85 Q71,75 75,63 L78,33 L75,31 L75,27 L82,30 L79,64 Q74,80 54,90 Q34,80 29,64 Z'),
    # Broad centurion crest, simplified into a single architectural arc.
    ('#F4F4F1', 'M35,39 C35,15 73,15 73,39 L66,39 C66,25 42,25 42,39 Z'),
    ('#080808', 'M52.5,19 L55.5,19 L55.5,28 L52.5,28 Z M40,23 L42.5,21.5 L46,29 L43.5,30.5 Z M65.5,21.5 L68,23 L64.5,30.5 L62,29 Z'),
    # Helmet dome, brow and cheek guards; a central nose guard forms the T visor.
    ('#F4F4F1', 'M38,48 C38,32 70,32 70,48 L70,57 L64,54 L63,69 L57,76 L57,52 L51,52 L51,76 L45,69 L44,54 L38,57 Z'),
    ('#080808', 'M42,46 L51,48 L51,51 L42,49 Z M57,48 L66,46 L66,49 L57,51 Z'),
]
attrs='xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108"'
foreground='\n'.join(f'    <path android:fillColor="{color}" android:pathData="{p}" />' for color,p in paths)
res=Path('app/src/main/res')
(res/'drawable/ic_sentinel_foreground.xml').write_text(f'<vector {attrs}>\n<group android:pivotX="54" android:pivotY="54" android:scaleX="0.86" android:scaleY="0.86">\n{foreground}\n</group>\n</vector>\n')
(res/'drawable/ic_sentinel.xml').write_text(f'<vector {attrs}>\n    <path android:fillColor="#080808" android:pathData="M0,0h108v108h-108z" />\n{foreground}\n</vector>\n')
svg='\n'.join(f'<path fill="{c}" d="{p}"/>' for c,p in paths)
Path('design/sentinel-mark.svg').write_text(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"><title>Sentinel — modern Roman helmet and shield</title><rect width="108" height="108" rx="24" fill="#080808"/>{svg}</svg>\n')
Path('design/sentinel-logo.svg').write_text(f'<svg xmlns="http://www.w3.org/2000/svg" width="960" height="640" viewBox="0 0 960 640"><title>Sentinel brand preview</title><rect width="960" height="640" fill="#080808"/><g transform="translate(318 48) scale(3)">{svg}</g><text x="480" y="454" text-anchor="middle" font-family="sans-serif" font-size="55" font-weight="700" letter-spacing="12" fill="#F4F4F1">SENTINEL</text></svg>\n')
for p in res.rglob('*.xml'):E.parse(p)

# Alpha-only mask: transparent eye cutouts, suitable for Android themed icons.
mono = [paths[0][1], paths[1][1], paths[3][1] + ' ' + paths[4][1]]
content='\n'.join(f'<path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="{path}" />' for path in mono)
(res/'drawable/ic_sentinel_monochrome.xml').write_text(f'<vector {attrs}><group android:pivotX="54" android:pivotY="54" android:scaleX="0.86" android:scaleY="0.86">{content}</group></vector>\n')
# Review image of the icon under a circular launcher mask.
Path('design/sentinel-icon-round.svg').write_text(f'<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 108 108"><defs><clipPath id="circle"><circle cx="54" cy="54" r="53"/></clipPath></defs><g clip-path="url(#circle)"><circle cx="54" cy="54" r="54" fill="#080808"/><g transform="translate(7.56 7.56) scale(.86)">{svg}</g></g></svg>\n')
