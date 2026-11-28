#!/usr/bin/env python3
"""Bump versionName and refresh both READMEs for the desktop and terminal key changes (no version references)."""
import subprocess
from pathlib import Path

EXPECTED = {
    'README.md': 'f7dcd5f3fcfaf78478a4a482e6bf72bcddfca916',
    'README_ES.md': '50e0ea986c42ee0692fa3edb893913ff04e7bde7',
    'app/build.gradle.kts': '9e57f2a18c5aa869bc7b46388fea83b660dd770d',
}


def fail(message):
    raise SystemExit(message)


def rep(text, old, new):
    found = text.count(old)
    if found != 1:
        fail('anchor count %d (expected 1): %r' % (found, old[:90]))
    return text.replace(old, new)


for name, digest in EXPECTED.items():
    actual = subprocess.check_output(['git', 'hash-object', name], text=True).strip()
    if actual != digest:
        fail('%s changed; review required' % name)

gradle = Path('app/build.gradle.kts')
text = gradle.read_text(encoding='utf-8')
text = rep(text, 'versionName = "1.4.5"', 'versionName = "1.4.6"')
gradle.write_text(text, encoding='utf-8')

readme = Path('README.md')
text = readme.read_text(encoding='utf-8')
text = rep(text, '#### Displays and floating controls\n', '#### Displays and controls\n')
text = rep(text, '- Virtual keyboard and shortcuts such as Ctrl+Alt+Del, Win+R, Alt+Tab, and function keys, subject to the remote environment and OS restrictions.\n',
    '- Virtual keyboard and shortcuts such as Ctrl+Alt+Del, Win+R, Alt+Tab, and function keys, subject to the remote environment and OS restrictions. Custom shortcuts are shown as buttons; drag their handle to reorder them and tap the cross to remove one.\n')
text = rep(text, '- One persistent toggle alternates left/right mouse mode and highlights the active button in blue. Scroll has its own icon and restores the remembered click mode when leaving it.\n',
    '- One persistent toggle alternates left and right click, keeps the same width in both states and highlights the active mouse button in the theme blue. Scroll has its own button and restores the remembered click mode when leaving it. In Scroll mode, a short tap with two fingers anywhere on the canvas, over the desktop image or the black area, leaves Scroll and switches to left click without sending a click.\n')
text = rep(text, '- The separate one-shot right-click assistant is toggled after the toolbar collapse control, without a frame or visible text. Information remains last.\n',
    '- A right-click toggle with a numeric badge sits after the toolbar collapse control. While the badge is on, each switch to right click lasts for one click and then returns to left click; with the badge off, right click stays selected until you change it. The setting is remembered until you turn it off. Information remains last.\n')
text = rep(text, '- Compact default row: Esc, Tab, Ctrl+C, Enter, and Backspace.\n- Expand with the downward chevron; collapse with the same icon inverted.\n- Additional rows provide arrows, Home/End, Insert/Delete, PgUp/PgDn, Ctrl shortcuts, and a Ctrl+A through Ctrl+Z picker.\n',
    '- The key bar is a single horizontally scrollable row of keys that you choose. Tap the wrench at its end to edit it: drag the handle to reorder, tap the cross to remove a key, add keys from the catalog (Esc, Tab, arrows, Home/End, Insert/Delete, PgUp/PgDn, Enter, Backspace and Ctrl+A through Ctrl+Z), reset to the defaults, then save or cancel.\n- The chosen keys and the bar scroll position are remembered.\n')
readme.write_text(text, encoding='utf-8')

readme_es = Path('README_ES.md')
text = readme_es.read_text(encoding='utf-8')
text = rep(text, '#### Pantallas y controles flotantes\n', '#### Pantallas y controles\n')
text = rep(text, '- Teclado virtual y atajos como Ctrl+Alt+Supr, Win+R, Alt+Tab y teclas de funci\u00f3n, sujetos al entorno y a las restricciones del sistema remoto.\n',
    '- Teclado virtual y atajos como Ctrl+Alt+Supr, Win+R, Alt+Tab y teclas de funci\u00f3n, sujetos al entorno y a las restricciones del sistema remoto. Los atajos personalizados se muestran como botones; arrastra su asa para reordenarlos y pulsa la cruz para quitar uno.\n')
text = rep(text, '- Un selector persistente alterna clic izquierdo/derecho y resalta en azul el bot\u00f3n activo. Scroll tiene icono propio y permite recuperar el modo de clic recordado al abandonarlo.\n',
    '- Un selector persistente alterna clic izquierdo y derecho, mantiene la misma anchura en ambos estados y resalta en el azul del tema el bot\u00f3n del rat\u00f3n activo. Scroll tiene bot\u00f3n propio y permite recuperar el modo de clic recordado al abandonarlo. En modo Scroll, un toque corto con dos dedos en cualquier parte del lienzo, sobre la imagen del escritorio o la zona negra, abandona Scroll y activa el clic izquierdo sin enviar ning\u00fan clic.\n')
text = rep(text, '- El asistente independiente de clic derecho de un solo uso se muestra u oculta despu\u00e9s del colapsador de la barra, sin recuadro ni texto visible. Informaci\u00f3n permanece al final.\n',
    '- Un bot\u00f3n de clic derecho con insignia num\u00e9rica se sit\u00faa despu\u00e9s del colapsador de la barra. Con la insignia activa, cada cambio a clic derecho dura un solo clic y despu\u00e9s vuelve a clic izquierdo; con la insignia apagada, el clic derecho permanece seleccionado hasta que lo cambies. El ajuste se recuerda hasta que lo desactives. Informaci\u00f3n permanece al final.\n')
text = rep(text, '- Fila compacta inicial: Esc, Tab, Ctrl+C, Intro y Retroceso.\n- El chevr\u00f3n permite expandir o contraer las filas adicionales.\n- Las filas adicionales ofrecen flechas, Inicio/Fin, Insert/Supr, RePág/AvPág, atajos Ctrl y un selector de Ctrl+A a Ctrl+Z.\n',
    '- La barra de teclas es una \u00fanica fila desplazable horizontalmente con las teclas que elijas. Pulsa la llave inglesa del final para editarla: arrastra el asa para ordenar, pulsa la cruz para quitar una tecla, a\u00f1ade teclas del cat\u00e1logo (Esc, Tab, flechas, Inicio/Fin, Insert/Supr, RePág/AvPág, Intro, Retroceso y de Ctrl+A a Ctrl+Z), restablece los valores predeterminados y guarda o cancela.\n- Las teclas elegidas y la posici\u00f3n de desplazamiento de la barra se recuerdan.\n')
readme_es.write_text(text, encoding='utf-8')
print('prepared OK')
