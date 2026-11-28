#!/usr/bin/env python3
"""The left/right click mode button keeps the same width in both states."""
import re
from pathlib import Path

screen_path = Path('app/src/main/java/iecxiv/meshfabric/ui/desktop/RemoteDesktopScreen.kt')
screen = screen_path.read_text(encoding='utf-8')

import_anchor = 'import androidx.compose.ui.semantics.semantics\n'
if screen.count(import_anchor) != 1:
    raise SystemExit('import anchor count %d' % screen.count(import_anchor))
screen = screen.replace(import_anchor, 'import androidx.compose.ui.semantics.clearAndSetSemantics\n' + import_anchor)

pattern = r'Text\(stringResource\(if \(clickMode == MouseMode\.RIGHT_CLICK\) R\.string\.desktop_mouse_mode_right_short\s*else R\.string\.desktop_mouse_mode_left_short\), fontSize = 10\.sp\)'
replacement = '''val leftModeText = stringResource(R.string.desktop_mouse_mode_left_short)
                                val rightModeText = stringResource(R.string.desktop_mouse_mode_right_short)
                                Box(contentAlignment = Alignment.Center) {
                                    Text(leftModeText, fontSize = 10.sp, modifier = Modifier.alpha(0f).clearAndSetSemantics {})
                                    Text(rightModeText, fontSize = 10.sp, modifier = Modifier.alpha(0f).clearAndSetSemantics {})
                                    Text(if (clickMode == MouseMode.RIGHT_CLICK) rightModeText else leftModeText, fontSize = 10.sp)
                                }'''
screen, count = re.subn(pattern, lambda m: replacement, screen, count=1, flags=re.S)
if count != 1:
    raise SystemExit('mode label pattern not found')
screen_path.write_text(screen, encoding='utf-8')
print('prepared OK')
