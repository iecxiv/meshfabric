#!/usr/bin/env python3
"""In left-click mode the mode button glyph leaves the right button unfilled, like the left one in right-click mode."""
import subprocess
from pathlib import Path

MAIN = Path('app/src/main/java/iecxiv/meshfabric/ui/desktop')
glyph_path = MAIN / 'DesktopRightClickHeaderGlyph.kt'
screen_path = MAIN / 'RemoteDesktopScreen.kt'


def rep(text, old, new):
    found = text.count(old)
    if found != 1:
        raise SystemExit('anchor count %d (expected 1): %r' % (found, old[:90]))
    return text.replace(old, new)


digest = subprocess.check_output(['git', 'hash-object', str(glyph_path)], text=True).strip()
if digest != 'f83d7bcd650e03390a8485046aaade392ae45adb':
    raise SystemExit('DesktopRightClickHeaderGlyph.kt changed; review required')

glyph = glyph_path.read_text(encoding='utf-8')
glyph = rep(glyph, 'activeColor: Color = Color(0xFF2196F3)) {', 'activeColor: Color = Color(0xFF2196F3), fillIdleRightButton: Boolean = true) {')
glyph = rep(glyph, '            drawPath(rightButton, if (rightButtonBlue) blue else body)\n', '            if (rightButtonBlue || fillIdleRightButton) drawPath(rightButton, if (rightButtonBlue) blue else body)\n')
glyph_path.write_text(glyph, encoding='utf-8')

screen = screen_path.read_text(encoding='utf-8')
screen = rep(screen, 'activeColor = MaterialTheme.colorScheme.primary)', 'activeColor = MaterialTheme.colorScheme.primary,\n                                        fillIdleRightButton = false)')
screen_path.write_text(screen, encoding='utf-8')
print('prepared OK')
