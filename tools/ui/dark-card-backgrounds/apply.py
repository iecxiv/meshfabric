from pathlib import Path
import json, re, subprocess

HERE = Path(__file__).resolve().parent
HELPER = (HERE / 'AppCardAppearance.kt').read_text()
UNIT = (HERE / 'AppCardAppearanceTest.kt').read_text()
UI = (HERE / 'AppCardAppearanceUiTest.kt').read_text()
BASE = 'cec944f94ecebe61713371bef0f88e236170aebc'
PREFIX = 'iecxiv.meshfabric.ui.theme.'
REPORT = Path('validation/dark-card-inventory.json')

def mask(text):
    out = list(text); i = 0; n = len(text)
    def blank(a, b):
        for p in range(a, b):
            if out[p] != '\n': out[p] = ' '
    while i < n:
        if text.startswith('//', i):
            end = text.find('\n', i)
            if end < 0: end = n
            blank(i, end); i = end
        elif text.startswith('/*', i):
            start = i; i += 2; depth = 1
            while i < n and depth:
                if text.startswith('/*', i): depth += 1; i += 2
                elif text.startswith('*/', i): depth -= 1; i += 2
                else: i += 1
            if depth: raise ValueError('Unclosed comment')
            blank(start, i)
        elif text.startswith(chr(34)*3, i):
            end = text.find(chr(34)*3, i + 3)
            if end < 0: raise ValueError('Unclosed raw string')
            end += 3; blank(i, end); i = end
        elif text[i] in (chr(34), chr(39)):
            start = i; quote = text[i]; i += 1
            while i < n:
                if text[i] == chr(92): i += 2
                elif text[i] == quote: i += 1; break
                else: i += 1
            else: raise ValueError('Unclosed string')
            blank(start, min(i, n))
        else: i += 1
    return ''.join(out)

def close(code, start):
    pairs = {'(': ')', '{': '}', '[': ']'}; stack = [pairs[code[start]]]
    for i in range(start + 1, len(code)):
        char = code[i]
        if char in pairs: stack.append(pairs[char])
        elif char in ')}]':
            if not stack or char != stack.pop(): raise ValueError('Mismatched delimiters')
            if not stack: return i
    raise ValueError('Unclosed delimiter')

def parts(text):
    code = mask(text); spans = []; start = 0; i = 0
    while i < len(code):
        if code[i] in '({[': i = close(code, i) + 1; continue
        if code[i] == ',': spans.append((start, i)); start = i + 1
        i += 1
    spans.append((start, len(text)))
    return [(a, b) for a, b in spans if mask(text[a:b]).strip()]

def arguments(text):
    named = {}; positional = []
    for a, b in parts(text):
        hit = re.match(r'\s*(\w+)\s*=(?!=)', mask(text[a:b]))
        if hit: named[hit.group(1)] = (a + hit.end(), b)
        else: positional.append((a, b))
    return named, positional

def set_argument(text, name, expression, position=None):
    named, positional = arguments(text)
    span = named.get(name)
    if span is None and position is not None and len(positional) > position:
        span = positional[position]
    if span is not None:
        a, b = span
        return text[:a] + ' ' + expression + text[b:]
    code = mask(text).rstrip()
    separator = '' if not code or code.endswith(',') else ','
    return text + separator + '\n    ' + name + ' = ' + expression + '\n'

def argument(text, name, default, position=None):
    named, positional = arguments(text)
    span = named.get(name)
    if span is None and position is not None and len(positional) > position:
        span = positional[position]
    return text[slice(*span)].strip() if span else default

def function_scopes(code):
    scopes = []
    for match in re.finditer(r'\bfun\s+(?:\w+\.)?(\w+)\s*\(', code):
        end = close(code, match.end() - 1)
        body = re.match(r'\s*(?::[^={]+)?\s*\{', code[end + 1:])
        if body:
            start = end + 1 + body.end() - 1
            scopes.append((start, close(code, start), match.group(1)))
    return scopes

def enclosing(scopes, index):
    matches = [(a, name) for a, b, name in scopes if a < index < b]
    return max(matches)[1] if matches else '<top-level>'

def calls(text):
    code = mask(text); names = {}
    for kind in ('Card', 'ElevatedCard', 'OutlinedCard', 'Surface'):
        qualified = 'androidx.compose.material3.' + kind
        names[qualified] = kind
        if re.search(r'^\s*import\s+androidx\.compose\.material3\.(?:\*|' + kind + r')\s*$', text, re.M):
            names[kind] = kind
        for match in re.finditer(r'^\s*import\s+' + re.escape(qualified) + r'\s+as\s+(\w+)\s*$', text, re.M):
            names[match.group(1)] = kind
    pattern = re.compile(r'(?<![\w.])(' + '|'.join(re.escape(x) for x in sorted(names, key=len, reverse=True)) + r')\s*([({])')
    scopes = function_scopes(code); result = []
    for match in pattern.finditer(code):
        if re.search(r'\bfun\s*$', code[max(0, match.start() - 30):match.start()]):
            raise ValueError('Local declaration shadows Material Card/Surface')
        start = match.end() - 1
        if code[start] == '(':
            end = close(code, start); values = text[start + 1:end]
            body = end + 1
            while body < len(code) and code[body].isspace(): body += 1
            body = body if body < len(code) and code[body] == '{' else None
        else:
            end = start; values = ''; body = start
        result.append(dict(start=match.start(), opening=start, ending=end, values=values,
            bare=code[start] == '{', body=body, body_end=close(code, body) if body is not None else None,
            kind=names[match.group(1)], function=enclosing(scopes, match.start()),
            line=code.count('\n', 0, match.start()) + 1))
    return result

def rewrite(text, file):
    found = calls(text); code = mask(text); edits = []; inventory = []
    for site in found:
        kind = site['kind']; values = site['values']; named, positional = arguments(values)
        decision = 'change'; reason = 'Material card'
        if kind == 'Surface':
            shape = 'shape' in named or len(positional) > 1
            modifier = argument(values, 'modifier', '', 0)
            width = bool(re.search(r'\bfillMaxWidth\s*\(', mask(modifier)))
            in_card = any(x['kind'] != 'Surface' and x['body'] is not None and x['body'] < site['start'] < x['body_end'] for x in found)
            card_name = 'Card' in site['function'] and not in_card
            body = code[site['body'] + 1:site['body_end']].lstrip() if site['body'] is not None else ''
            if any(x in named for x in ('selected', 'checked')) or ('onClick' in named and not card_name):
                decision = 'preserve'; reason = 'Interactive Surface control (not a card)'
            elif not shape and not card_name:
                decision = 'preserve'; reason = 'Unshaped screen/bar/layout surface'
            elif re.match(r'(?:androidx\.compose\.material3\.)?Text\s*\(', body) and not card_name:
                decision = 'preserve'; reason = 'Text-only heading/date/status band'
            elif 'TerminalBackground' in values or re.search(r'\b(?:AndroidView|WebView)\s*\(', body):
                decision = 'preserve'; reason = 'Terminal/embedded working viewport, not card chrome'
            elif not (width or card_name):
                decision = 'preserve'; reason = 'Icon/badge/control or non-card surface'
            else:
                reason = 'Rounded full-width content container or named card component'
        if file.endswith('/ui/components/AppDialogAppearance.kt'):
            decision = 'preserve'; reason = 'Existing outer dialog appearance contract'
        inventory.append({'file': file, 'line': site['line'], 'component': kind,
            'function': site['function'], 'decision': decision, 'reason': reason})
        if decision != 'change': continue
        if kind == 'Surface':
            if 'onClick' in named and len(positional) > 1:
                raise ValueError('Positional clickable Surface parameters require review')
            original = argument(values, 'color', 'androidx.compose.material3.MaterialTheme.colorScheme.surface', 2)
            content = argument(values, 'contentColor', None, 3)
            tonal = argument(values, 'tonalElevation', 'androidx.compose.ui.unit.Dp(0f)', 4)
            if content is None:
                values = set_argument(values, 'contentColor', 'androidx.compose.material3.contentColorFor(\n' + original + '\n)')
            values = set_argument(values, 'color', PREFIX + 'appCardBackground(\n' + original + '\n)', 2)
            values = set_argument(values, 'tonalElevation', PREFIX + 'appCardTonalElevation(\n' + tonal + '\n)', 4)
        else:
            getter = {'Card': 'cardColors', 'ElevatedCard': 'elevatedCardColors', 'OutlinedCard': 'outlinedCardColors'}[kind]
            if len(positional) >= 3 and (mask(values[slice(*positional[0])]).lstrip().startswith('{') or 'onClick' in named):
                raise ValueError('Ambiguous positional clickable Card parameters require review')
            original = argument(values, 'colors', 'androidx.compose.material3.CardDefaults.' + getter + '()', 2)
            values = set_argument(values, 'colors', PREFIX + 'appCardColors(\n' + original + '\n)', 2)
        if site['bare']:
            edits.append((site['opening'], site['opening'], '(' + values + ') '))
        else:
            edits.append((site['opening'] + 1, site['ending'], values))
    for a, b, replacement in sorted(edits, reverse=True):
        if any(c < a < d or c < b < d for c, d, _ in edits if (c, d) != (a, b)):
            raise ValueError('Nested edited parameter expressions require review')
        text = text[:a] + replacement + text[b:]
    return text, inventory

def main():
    if subprocess.check_output(['git', 'rev-parse', 'HEAD^'], text=True).strip() != BASE:
        raise RuntimeError('dev source base changed; review required')
    updates = {}; inventory = []
    for path in sorted(Path('app/src/main').rglob('*.kt')):
        old = path.read_text(); new, entries = rewrite(old, path.as_posix())
        inventory.extend(entries)
        if new != old: updates[path.as_posix()] = new
    theme_path = 'app/src/main/java/iecxiv/meshfabric/ui/theme/Theme.kt'
    theme = updates.get(theme_path, Path(theme_path).read_text())
    changes = {
        'import androidx.compose.runtime.Composable\n': 'import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.CompositionLocalProvider\n',
        'val darkTheme = when (themeMode) {\n        AppThemeMode.LIGHT -> false\n        AppThemeMode.DARK -> true\n        AppThemeMode.SYSTEM -> isSystemInDarkTheme()\n    }': 'val darkTheme = resolveAppDarkTheme(themeMode, isSystemInDarkTheme())',
        'content = content': 'content = { CompositionLocalProvider(LocalAppDarkTheme provides darkTheme, content = content) }'
    }
    for old, new in changes.items():
        if theme.count(old) != 1: raise RuntimeError('Theme anchor changed: ' + old)
        theme = theme.replace(old, new, 1)
    updates[theme_path] = theme
    paths = {
        'app/src/main/java/iecxiv/meshfabric/ui/theme/AppCardAppearance.kt': HELPER,
        'app/src/test/java/iecxiv/meshfabric/ui/theme/AppCardAppearanceTest.kt': UNIT,
        'app/src/androidTest/java/iecxiv/meshfabric/ui/theme/AppCardAppearanceUiTest.kt': UI
    }
    for path, value in paths.items():
        if Path(path).exists(): raise RuntimeError('New file already exists: ' + path)
        updates[path] = value
    required = {'ManagementPresentation.kt', 'AccountUiComponents.kt', 'ServerGeneralPresentation.kt',
        'DeviceListScreen.kt', 'SettingsScreen.kt', 'LogScreen.kt', 'ActivityTimeline.kt'}
    if required - {Path(p).name for p in updates}:
        raise RuntimeError('Missing card area: ' + str(required - {Path(p).name for p in updates}))
    for name in ('NoticeInbox.kt', 'NoticeCenterScreen.kt'):
        path = Path('app/src/main/java/iecxiv/meshfabric/ui/management') / name
        if 'ManagementCard {' not in path.read_text():
            raise RuntimeError('Notice card inheritance changed: ' + name)
    for path in Path('app/src/main').rglob('*'):
        if path.suffix in ('.java', '.xml') and re.search(r'\b(?:MaterialCardView|CardView)\b', path.read_text()):
            raise RuntimeError('Non-Compose cards require separate review: ' + str(path))
    if not any(x['file'].endswith('LogScreen.kt') and x['component'] == 'Card' and x['decision'] == 'change' for x in inventory):
        raise RuntimeError('Event reference card missing')
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps({'source_base': BASE, 'reference': 'CardDefaults.cardColors().containerColor in the active application theme',
        'changed_files': sorted(updates), 'inventory': inventory}, indent=2))
    for name, text in updates.items():
        path = Path(name); path.parent.mkdir(parents=True, exist_ok=True); path.write_text(text)

if __name__ == '__main__':
    main()
