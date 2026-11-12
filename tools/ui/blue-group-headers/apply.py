from pathlib import Path
import subprocess
BASE = '2e357f8457a4c055e6858ddaafbd6c68fb6562a2'
assert subprocess.check_output(['git', 'rev-parse', 'HEAD^'], text=True).strip() == BASE, 'Source base changed'
p = Path('app/src/main/java/iecxiv/meshfabric/ui/devices/DeviceListScreen.kt')
assert subprocess.check_output(['git', 'hash-object', str(p)], text=True).strip() == 'd4eed0ee26bf1d31ba394a7b7d5bdae2a11f2dc5', 'Device source changed'
s = p.read_text()
old = """        colors= iecxiv.meshfabric.ui.theme.appCardColors(
CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)
)) {"""
new = """        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {"""
assert s.count(old) == 1, 'Expected one group header anchor'
before, header = s.split('@Composable fun GroupHeaderCard(', 1)
header, after = header.split('@Composable fun DeviceCard(', 1)
assert header.count(old) == 1
changed = before + '@Composable fun GroupHeaderCard(' + header.replace(old, new, 1) + '@Composable fun DeviceCard(' + after
assert changed.split('@Composable fun DeviceCard(', 1)[1] == after
assert changed.split('@Composable fun GroupHeaderCard(', 1)[0] == before
p.write_text(changed)
target = Path('app/src/androidTest/java/iecxiv/meshfabric/ui/theme/GroupHeaderAppearanceUiTest.kt')
assert not target.exists()
target.write_text(Path(__file__).with_name('GroupHeaderAppearanceUiTest.kt').read_text())
Path('validation').mkdir(exist_ok=True)
subprocess.run(['git', 'add', '--', str(p), str(target)], check=True)
