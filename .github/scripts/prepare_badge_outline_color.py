#!/usr/bin/env python3
"""The active one-shot right-click icon uses the same color as its badge (theme primary)."""
import subprocess
from pathlib import Path

MAIN = Path('app/src/main/java/iecxiv/meshfabric/ui/desktop')
glyph_path = MAIN / 'DesktopRightClickHeaderGlyph.kt'
once_path = MAIN / 'DesktopRightClickOnce.kt'


def rep(text, old, new):
    found = text.count(old)
    if found != 1:
        raise SystemExit('anchor count %d (expected 1): %r' % (found, old[:90]))
    return text.replace(old, new)


digest = subprocess.check_output(['git', 'hash-object', str(glyph_path)], text=True).strip()
if digest != 'b80b1e552baa5a0d48ffd4390e3060bf17c91ef6':
    raise SystemExit('DesktopRightClickHeaderGlyph.kt changed; review required')

glyph = glyph_path.read_text(encoding='utf-8')
glyph = rep(glyph, 'iconSize: Dp = 24.dp, leftButtonBlue: Boolean = false) {', 'iconSize: Dp = 24.dp, leftButtonBlue: Boolean = false, activeColor: Color = Color(0xFF2196F3)) {')
glyph = rep(glyph, '    val blue = Color(0xFF2196F3)\n', '    val blue = activeColor\n')
glyph_path.write_text(glyph, encoding='utf-8')

once = once_path.read_text(encoding='utf-8')
once = rep(once, 'DesktopRightClickHeaderGlyph(on, label, iconSize = 20.dp)', 'DesktopRightClickHeaderGlyph(on, label, iconSize = 20.dp, activeColor = MaterialTheme.colorScheme.primary)')
once_path.write_text(once, encoding='utf-8')
print('prepared OK')
