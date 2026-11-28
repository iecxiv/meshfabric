#!/usr/bin/env python3
"""The left/right click mode button glyph uses the badge color (theme primary) for its blue parts."""
from pathlib import Path

screen_path = Path('app/src/main/java/iecxiv/meshfabric/ui/desktop/RemoteDesktopScreen.kt')
glyph_path = Path('app/src/main/java/iecxiv/meshfabric/ui/desktop/DesktopRightClickHeaderGlyph.kt')
if 'activeColor: Color' not in glyph_path.read_text(encoding='utf-8'):
    raise SystemExit('glyph has no activeColor parameter yet; publish the badge outline change first')
screen = screen_path.read_text(encoding='utf-8')
old = 'leftButtonBlue = mouseModeActive && clickMode == MouseMode.LEFT_CLICK)'
if screen.count(old) != 1:
    raise SystemExit('anchor count %d (expected 1)' % screen.count(old))
screen = screen.replace(old, 'leftButtonBlue = mouseModeActive && clickMode == MouseMode.LEFT_CLICK,\n                                        activeColor = MaterialTheme.colorScheme.primary)')
screen_path.write_text(screen, encoding='utf-8')
print('prepared OK')
