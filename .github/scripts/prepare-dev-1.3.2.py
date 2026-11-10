from pathlib import Path
import json,re,subprocess
checks={'README.md':'20d5e0f15328841aca5ee9384af3c6d7cc6a546f','README_ES.md':'01fba5c223b0f00737ede5be2cd2158084c7ddf7','app/build.gradle.kts':'25d49d1e44561813ae4b28cd7f2af8031e8f55c5'}
for path,sha in checks.items(): assert subprocess.check_output(['git','hash-object',path],text=True).strip()==sha,path
p=Path('app/build.gradle.kts');s=p.read_text();assert s.count('versionName = "1.3.1"')==1
p.write_text(s.replace('versionName = "1.3.1"','versionName = "1.3.2"',1))
sections={
'README.md':r"""### Events (1.3.2)

- Log has separate Activity and Events tabs. Activity remains the local session journal; Events queries the historical records visible to the current server account. It is not a complete audit archive.
- Server-side type and quantity selectors offer 60, 120, 250, 500, 1000 or Unlimited. Unlimited omits the requested maximum and imposes no artificial app record/frame/message ceiling; server permissions, response availability, timeouts and device resources still apply. Results are not silently truncated.
- Refresh and search are available in the Events header. Visible results have themed date bands; events without a valid time have a separate unknown-date group.
- Type/quantity controls use a capped 2:1 layout with matching anchor/menu widths and wrapping labels. Event-type icons preserve unknown event types.
- Cards show device (group), when resolvable, without node/mesh identifier fields. Read-only details show the device name, group and available identifiers separately; command events retain the literal command, command type and run-as value supplied by the server.
- Event-provided names take precedence. Missing names may be completed from the inventory of the same connection; current inventory is not historical proof. Account/server changes and stale query replies are isolated.
- Previous-login histories share outcome colors and the user-editor presentation; only the My Account history dialog adopts its reference dimensions. Management directory tabs and Server Statistics provide header refresh actions.
- Trace source state accepts an explicit null as no active sources, distinguishes malformed/missing data, and bounds the initial wait. Opening or reconnecting does not send traceinfo writes that could change global sources. Administrator/domain restrictions remain authoritative.

Guide: [Log and Events](docs/log.md). Protocol reference: [MeshCentral 1.3.1 trace sources](docs/reference/meshcentral-1.3.1-trace-sources.md). The app version 1.3.2 is distinct from the MeshCentral 1.3.1 protocol reference.

""",
'README_ES.md':r"""### Eventos (1.3.2)

- Registro separa las pestañas Actividad y Eventos. Actividad conserva el diario local de la sesión; Eventos consulta el historial visible para la cuenta del servidor actual. No es un archivo completo de auditoría.
- Los selectores de tipo y cantidad consultan al servidor: 60, 120, 250, 500, 1000 o Sin límite. Sin límite omite el máximo solicitado y no impone topes artificiales de registros, trama o descripción en la app; siguen aplicándose permisos, disponibilidad de respuesta, plazos de espera y recursos del dispositivo. No hay recorte silencioso.
- Refresco y búsqueda están en la cabecera de Eventos. Los resultados visibles se agrupan con bandas de fecha del tema; los eventos sin hora válida tienen su grupo de fecha no disponible.
- Los controles usan reparto 2:1 con anchura máxima conjunta, menús del ancho de sus cabeceros y texto ajustable. Los iconos conservan también los tipos de evento desconocidos.
- Las tarjetas muestran dispositivo (grupo), cuando se puede resolver, sin los campos de identificador de nodo o grupo. Los detalles de solo lectura separan nombre, grupo e identificadores disponibles; los eventos de comandos conservan el comando literal, tipo y modo de ejecución enviados por el servidor.
- Se priorizan los nombres incluidos en el evento. Los que faltan pueden completarse con el inventario de la misma conexión, que no acredita el estado histórico. Se aíslan cambios de cuenta/servidor y respuestas de consultas anteriores.
- Los historiales de acceso comparten colores de resultado y presentación de Edición de usuario; solo el historial de Mi cuenta adopta sus dimensiones de referencia. Los listados de Gestión y Estadísticas del servidor ofrecen refresco en cabecera.
- Las fuentes de rastreo aceptan un null explícito como ninguna fuente activa, distinguen datos ausentes o incompatibles y limitan la espera inicial. Abrir o reconectar no envía escrituras traceinfo que puedan cambiar las fuentes globales. Siguen vigentes las restricciones de administrador y dominio.

Guía técnica: [Registro y Eventos](docs/log.md) (en inglés). Referencia de protocolo: [Fuentes de MeshCentral 1.3.1](docs/reference/meshcentral-1.3.1-trace-sources.md) (en inglés). La versión 1.3.2 de la app no es la versión 1.3.1 de MeshCentral usada como referencia de protocolo.

"""}
for path,section in sections.items():
    p=Path(path);s=p.read_text();original=s
    if path=='README.md':
        s=s.replace('and an activity view without','and a Log view with Activity and Events tabs without',1)
        s=s.replace('Management, Server, Devices, Activity and Notices','Management, Server, Devices, Log and Notices',1)
        s=s.replace('### Native management and account security (1.3.1)','### Native management and account security (1.3.2)',1)
        marker='### Background sessions and notification\n';assert s.count(marker)==1;s=s.replace(marker,section+marker,1)
        marker='English | [Español](README_ES.md)\n';assert s.count(marker)==1;s=s.replace(marker,marker+'\nCurrent development version: `1.3.2`.\n',1)
        roadmap='## Roadmap and known gaps\n'
        verification='Before integration into dev, run the unfiltered JVM suite, the complete Android instrumentation suite, all standalone JavaScript test scripts and lint. Feature-scoped workflows do not replace that integration check. Build and install the APKs once; preserve actual per-method execution evidence.\n\n'
        marker='## Verification\n\n';assert s.count(marker)==1;s=s.replace(marker,marker+verification,1)
    else:
        s=s.replace('y una vista de actividad sin','y Registro con pestañas Actividad y Eventos sin',1)
        s=s.replace('Gestión, Servidor, Dispositivos, Actividad y Avisos','Gestión, Servidor, Dispositivos, Registro y Avisos',1)
        s=s.replace('### Gestión nativa y seguridad de cuenta (1.3.1)','### Gestión nativa y seguridad de cuenta (1.3.2)',1)
        marker='### Sesiones en segundo plano y notificación\n';assert s.count(marker)==1;s=s.replace(marker,section+marker,1)
        marker='[English](README.md) | Español\n';assert s.count(marker)==1;s=s.replace(marker,marker+'\nVersión actual de desarrollo: `1.3.2`.\n',1)
        roadmap='## Hoja de ruta y carencias conocidas\n'
        verification='Antes de integrar en dev se ejecutan la suite JVM sin filtros, toda la suite instrumentada Android, los scripts de pruebas JavaScript y lint. Las validaciones acotadas por funcionalidad no sustituyen esta comprobación de integración. Los APK se compilan e instalan una sola vez y se conserva evidencia de los métodos realmente ejecutados.\n\n'
        marker='## Verificación\n\n';assert s.count(marker)==1;s=s.replace(marker,marker+verification,1)
    assert original[original.index(roadmap):]==s[s.index(roadmap):]
    for target in re.findall(r'\[[^\]]*\]\(([^)]+)\)',s):
        if not re.match(r'^(?:https?://|mailto:|#)',target): assert (p.parent/target.split('#',1)[0]).exists(),(path,target)
    assert s!=original;p.write_text(s)
subprocess.run(['git','add','--',*checks],check=True)
assert set(subprocess.check_output(['git','diff','--cached','--name-only'],text=True).splitlines())==set(checks)
Path('validation/release-expected.json').write_text(json.dumps(sorted(checks),indent=2)+'\n')
