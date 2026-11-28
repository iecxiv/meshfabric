#!/usr/bin/env python3
"""values-en is a full English locale in this project, so lint requires the new strings there too."""
from pathlib import Path

target = Path('app/src/main/res/values-en/desktop_right_click_once.xml')
target.write_text('''<resources>
    <string name="desktop_right_click_once_on">One-shot right click is on. Tap to turn it off.</string>
    <string name="desktop_right_click_once_off">Turn on one-shot right click</string>
</resources>
''', encoding='utf-8')
print('en strings written')
