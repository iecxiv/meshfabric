# MeshFabric Android App

[English](README.md) | Español

MeshFabric es un cliente Android nativo no oficial para MeshCentral. Ofrece escritorio remoto, terminal interactivo, gestión de archivos, notas de dispositivos, acciones remotas y una vista de actividad sin integrar la interfaz web de administración de MeshCentral. El terminal utiliza recursos de xterm.js incluidos localmente dentro de un WebView de Android.

La disponibilidad depende del servidor MeshCentral, los permisos de la cuenta, el agente remoto, el sistema operativo y la sesión. MeshFabric no sustituye la consola completa de administración de MeshCentral.

## Funciones

### Dispositivos y navegación

- Dispositivos organizados por grupos de MeshCentral, con estado conectado/desconectado e información del sistema operativo.
- Expansión y contracción de grupos, individualmente o todos a la vez, con preferencias guardadas.
- Búsqueda por nombre o sistema operativo.
- Navegación inferior entre Dispositivos y Actividad.
- Acceso al escritorio, terminal, archivos y acciones desde cada tarjeta de dispositivo.

### Escritorio remoto

- Escritorio remoto nativo (KVM) con control táctil del ratón, clic izquierdo/derecho, desplazamiento, zoom y movimiento de la imagen.
- Teclado virtual y atajos como Ctrl+Alt+Supr, Win+R, Alt+Tab y teclas de función, sujetos al entorno y a las restricciones del sistema remoto.
- Configuración de calidad, escala, frecuencia de imágenes y codificación.
- Controles de portapapeles y sincronización opcional, sujetos a las restricciones de la sesión y del servidor.
- Intercambio de botones del ratón, dirección de la rueda, distribución remota del teclado y bloqueo al desconectar.
- La captura de gestos se renueva cuando cambian las dimensiones del área visible o de la imagen ajustada. Comprueba clics, pulsaciones largas y desplazamiento después de girar el dispositivo.

#### Teclado físico y teclado en pantalla

La gestión nativa de pulsación y liberación admite teclados físicos y teclados virtuales que emitan eventos de tecla. El mapeo incluye flechas, navegación, Tab, Intro, Escape, Retroceso/Supr, Insert, Inicio/Fin, RePág/AvPág, F1–F12, letras, números, teclado numérico, puntuación y modificadores cuando Android los entrega a la aplicación.

- Los eventos reconocidos se consumen antes de que el campo de texto oculto los procese, evitando movimientos locales del cursor o ediciones duplicadas.
- La captura del teclado físico no requiere abrir el teclado en pantalla.
- Se admiten repeticiones de pulsación y seguimiento equilibrado de modificadores.
- La captura se suspende con diálogos abiertos o con la pantalla inactiva. Al perder el foco o el control de entrada se intenta liberar las teclas registradas, sin reproducirlas en otra sesión.
- Los eventos de texto del IME y el transporte Unicode permanecen separados de los eventos de tecla.

Esto aborda problemas como las flechas de Hacker's Keyboard que no llegan al escritorio remoto, pero no garantiza compatibilidad universal. Las pruebas simulan eventos marcados como procedentes de un teclado virtual; no ejecutan el APK real de Hacker's Keyboard. Sigue siendo necesaria la verificación en dispositivos y equipos remotos reales.

Existen limitaciones con teclas reservadas por Android, distribuciones remotas, AltGr, bloqueos y teclado numérico, modificadores izquierdo/derecho, composición del IME, mezcla con la barra de herramientas, juegos, códigos de exploración y atajos protegidos del sistema. Una liberación enviada después de fallar el transporte no garantiza su recepción remota.

Consulta [Entrada de teclado del escritorio](docs/desktop-keyboard-input.md).

### Terminal

- Terminal interactivo mediante el relay del agente MeshCentral, con ajuste del tamaño de fuente y renderizador local WebView/xterm retenido.
- El icono superior del teclado muestra u oculta explícitamente el teclado. El editor de comandos sigue disponible en el menú de herramientas.
- Las solicitudes de foco del terminal se bloquean mientras un menú o diálogo controla la entrada. Al cerrarlo se restaura el foco sin abrir automáticamente el teclado.
- Las solicitudes de foco obsoletas se cancelan al perder el foco de ventana, pasar a segundo plano, separar o destruir la vista.
- Se solicita a los IME compatibles que eviten la edición a pantalla completa y el aprendizaje personalizado; pueden ignorar estas indicaciones.

#### Panel de historial

- El icono Historial está entre A+ y el teclado. Los controles de cabecera usan diseños compactos de 36 dp.
- Pulsarlo con el panel oculto lo abre expandido; pulsarlo con el panel visible lo oculta por completo. El icono solo se colorea mientras el panel está visible y no tiene un desplegable independiente.
- Una desconexión involuntaria de una conexión establecida muestra el panel contraído una vez por incidente. Restaurar historiales o cerrar explícitamente con X no activa esta presentación automática.
- La flecha del panel expande o contrae el contenido. Cabecera y contenido comparten superficie, fondo y borde redondeado de ancho completo.
- Reconectar, Volver al terminal actual o Cancelar aparecen en la cabecera según el estado, sin entradas duplicadas en la lista.
- Los historiales anteriores se abren en modo de solo lectura y se eliminan individualmente o todos juntos. La eliminación requiere confirmación y excluye la sesión actual.

#### Selección y copia

Seleccionar y copiar, en el menú de herramientas, abre una captura nativa de solo lectura; no selecciona directamente sobre el lienzo del terminal activo.

- Mantén pulsado el texto para seleccionar un fragmento y usar la barra nativa de copia.
- Copiar todo copia la captura completa.
- La extracción une líneas divididas por ajuste visual, conserva los saltos reales y excluye el relleno vacío del área visible.
- La captura se limita a 2000 filas recientes y 200000 unidades UTF-16. El truncamiento se indica y no divide pares sustitutos UTF-16.
- Se captura el búfer activo: una aplicación a pantalla completa no expone el búfer normal oculto mediante esta acción.
- La copia es explícita y no envía entrada al shell remoto.

#### Pegado y edición antes de enviar

El menú ofrece Pegar desde Android, para revisar el texto del portapapeles antes de confirmarlo, y Pegar con edición, para modificarlo en un campo multilínea antes de enviarlo.

Abrir un diálogo o editar su texto no lo envía. Estas acciones no añaden Intro, pero los saltos de línea existentes pueden ejecutar comandos. El texto multilínea requiere aceptación expresa; cambiar el texto editado aceptado restablece esa aceptación.

El límite es de 16384 unidades UTF-16. El texto excesivo, los controles no admitidos y el Unicode mal formado se rechazan sin truncamiento silencioso. Una edición inválida conserva el borrador anterior y muestra un error. El envío usa la API pública de pegado de xterm y su tratamiento de pegado entre delimitadores y finales de línea; esto no garantiza que la aplicación remota procese los comandos con seguridad.

Cada vista previa permite un intento de envío sin reintentos automáticos. La aceptación local no confirma recepción ni ejecución remota. Si el envío no se confirma, revisa el terminal antes de repetirlo. Cancelar no deshace entrada ya enviada. El editor de comandos independiente conserva su comportamiento de envío de comandos y no debe confundirse con estas acciones de pegado.

#### Teclas especiales y desplazamiento

- Fila compacta inicial: Esc, Tab, Ctrl+C, Intro y Retroceso.
- El chevrón permite expandir o contraer las filas adicionales.
- Las filas adicionales ofrecen flechas, Inicio/Fin, Insert/Supr, RePág/AvPág, atajos Ctrl y un selector de Ctrl+A a Ctrl+Z.
- Las teclas tienen fondo y esquinas pequeñas; conservan un espacio mínimo de interacción y pueden crecer con fuentes grandes para evitar recortes.
- Las flechas usan un mismo icono vectorial girado por dirección. Intro y el control de última salida mantienen un tamaño mayor; Fin usa la tipografía de Inicio.
- Las flechas respetan el modo de cursor de aplicación de xterm. Retroceso envía el byte 8 e Intro envía CR.
- RePág/AvPág/Inicio/Fin son teclas remotas. El control independiente de última salida mueve el historial local del búfer normal al final, sin enviar teclas. No está disponible en el búfer alternativo.
- El desplazamiento táctil sobre el texto usa el área nativa en dispositivos con puntero grueso, dentro del búfer normal y sin informes de ratón activos. Se conserva el comportamiento del búfer alternativo y de los informes de ratón.

Durante ese desplazamiento no se conserva la selección directa ni la apertura de enlaces sobre el lienzo. Para seleccionar texto utiliza el diálogo Seleccionar y copiar. No se garantiza un comportamiento universal con todos los IME ni con todas las aplicaciones de terminal a pantalla completa.

#### Desconexión y reconexión manual

- Un cambio de red o fallo del túnel cierra el transporte sin borrar el historial actual ni reiniciar deliberadamente la posición de desplazamiento de la vista retenida. La salida sigue disponible para lectura y copia.
- El panel informa del estado desconectado y de solo lectura fuera de la salida remota. Escritura, pegado, teclas remotas y solicitudes de teclado permanecen bloqueados incluso si se oculta el panel.
- Reconectar permite un intento candidato por dispositivo, con cancelación y un plazo total de 30 segundos para preparar el canal de control y negociar el terminal. Abrir un WebSocket no basta para confirmar la conexión.
- El fallo, agotamiento del plazo o cancelación cierra el candidato y conserva el registro y la salida anteriores. No hay reintentos automáticos ni comandos en cola para reenviar.
- Una negociación correcta crea una sesión independiente como actual. El historial anterior conserva su propio registro y callbacks; una secuencia de borrado de la nueva sesión no borra ese historial.
- La X roja cierra y archiva explícitamente el terminal actual, cancela la reconexión pendiente y vuelve a Dispositivos. El botón Terminal recupera su estado inicial. Volver atrás no cierra explícitamente la sesión.
- Reconectar no demuestra que el proceso remoto original haya sobrevivido o se haya reanudado. No se garantiza la continuidad de shells ni de aplicaciones como nano. Recrear vista es una acción distinta y no reconecta el transporte.

#### Historial, retención del renderizador y recuperación

- Cerrar un terminal, incluso al cerrar todas las sesiones, archiva su registro sin dejar resaltada una sesión actual. Los archivos históricos son independientes del registro de sesiones actuales.
- Guardar búferes del terminal conserva registros cerrados y puntos de control por servidor/cuenta, restaurados como historiales de solo lectura. Los puntos de control se limitan aproximadamente a uno por segundo, con guardados adicionales al detener la actividad y al archivar mediante el callback de retirada de tareas recientes. Una terminación abrupta puede perder salida todavía no escrita.
- Con la opción desactivada, los historiales solo permanecen en memoria. Desactivarla elimina los archivos guardados y los búferes antiguos. Eliminar un historial borra también su archivo para que no reaparezca al reiniciar.
- La salida persistida no recrea una conexión viva. Consulta [Persistencia de historiales](docs/terminal-archive-persistence.md) para almacenamiento, separación de cuentas, migración y límites.
- La caché conserva hasta cuatro renderizadores sin expulsar vistas adjuntas. Un renderizador expulsado puede reconstruirse desde la salida disponible, no desde una instantánea VT completa.
- El truncamiento se indica explícitamente. Reconstruir una vista desconectada puede reproducir desde la salida más antigua disponible: es una recuperación de historial de mejor esfuerzo, no una restauración de datos ausentes ni del estado visual completo.
- La entrega de salida en directo detecta estrictamente huecos, incluida la lectura inicial. Continuar desde un cursor expulsado o invalidado no omite silenciosamente datos perdidos.
- La pérdida del renderizador y los plazos de confirmación del parser en primer plano ofrecen Recrear vista con confirmación. Esto no reinicia el shell; las aplicaciones a pantalla completa pueden necesitar redibujarse.
- El historial retenido no demuestra que el túnel siga conectado ni que el proceso original continúe. La muerte del proceso no conserva el WebView.

Documentación técnica:

- [Selección y copia](docs/terminal-selection-copy.md)
- [Vista previa de pegado](docs/terminal-paste.md)
- [Pegado con edición](docs/terminal-paste-edit.md)
- [Foco e IME](docs/terminal-focus-ime.md)
- [Teclas especiales](docs/terminal-special-keys.md)
- [Desplazamiento táctil](docs/terminal-touch-scroll.md)
- [Retención del renderizador](docs/terminal-renderer-retention.md)
- [Supervisión ACK y recuperación de vista](docs/terminal-ack-recovery.md)
- [Reconexión manual e historial retenido](docs/terminal-manual-reconnect.md)
- [Persistencia de historiales](docs/terminal-archive-persistence.md)

### Gestor de archivos y editor de texto

- Exploración de directorios remotos y navegación a una ruta concreta.
- Subida de archivos y descarga a Android.
- Creación de carpetas y archivos de texto; copia, corte, pegado, movimiento, renombrado y eliminación.
- Compresión ZIP y extracción de archivos admitidos, con diálogos de conflictos/sobrescritura y controles de progreso.
- Edición de archivos de texto pequeños considerados candidatos, con fuente monoespaciada, números de línea lógicos, ajuste de línea y desplazamiento. Las continuaciones visuales no generan números de línea adicionales.
- El editor compartido también se utiliza para notas de dispositivos. No incluye resaltado de sintaxis.
- Las operaciones se aplican actualmente a elementos individuales; la selección múltiple sigue pendiente.

### Notas de dispositivos y acciones remotas

El menú de cada dispositivo permite leer y editar/guardar notas, ejecutar comandos o scripts con selección de intérprete según el sistema operativo, enviar notificaciones, solicitar la apertura de URL HTTP/HTTPS, activar Wake-on-LAN y reiniciar, apagar o suspender el equipo.

Las notas incluyen borradores locales, confirmación antes de descartar cambios y verificación de guardado mediante relectura del servidor. El editor admite hasta 64000 caracteres. Guardar el campo vacío elimina las notas existentes.

La salida de comandos se muestra separada del terminal interactivo. Cerrar su ventana no detiene el comando remoto, y recibir salida no demuestra que el proceso haya terminado correctamente.

Enviar o aceptar una solicitud no equivale a ejecutarla. MeshFabric informa de resultados no confirmados e interrupciones en lugar de repetir automáticamente acciones potencialmente disruptivas. Notificaciones, apertura de URL, acciones de energía y WoL dependen del servidor y del entorno remoto.

### Actividad

- Solicitudes locales recientes, eventos del servidor, errores y salida de comandos disponible.
- Búsqueda y filtros por categoría o dispositivo, con agrupación por fecha.
- Identificadores de solicitud, actor, fecha/hora y detalles de resultado cuando están disponibles.
- Hasta 200 entradas en memoria: es una vista de la sesión, no un archivo persistente ni completo de auditoría del servidor.

### Sesiones en segundo plano y notificación

- Se utiliza una notificación de servicio en primer plano cuando hay sesiones que la necesitan mientras la app está en segundo plano.
- Al pulsarla se solicita volver a MainActivity mediante un PendingIntent de actividad explícito e inmutable. No usa flags de limpieza de tareas ni solicita explícitamente desconectar o reiniciar el terminal.
- El regreso sigue el ciclo de vida de la app. Si el proceso murió, hace falta el inicio normal; la notificación no garantiza recuperar una sesión remota viva anterior.

### Apariencia e idiomas

- Interfaz Material 3 con tema del sistema, claro y oscuro.
- Nombre e icono de aplicación personalizados y opción para ocultar el icono de ajustes.
- Recursos Android en inglés y español, incluidos controles de reconexión, historial, eliminación y cierre del terminal.
- La localización no está completa: algunos mensajes de gestión, actividad, escritorio y resultados siguen escritos directamente en español. La cobertura de claves no demuestra que todas las frases estén externalizadas o traducidas.

El inventario y sus limitaciones se documentan en [docs/i18n](docs/i18n/README.md). Los documentos técnicos enlazados pueden estar en inglés.

## Requisitos

- Servidor MeshCentral accesible y cuenta válida.
- Dispositivos/agentes con la función remota solicitada y permisos suficientes en el servidor.
- Android 8.0 o posterior (API mínima 26).

| Configuración | Valor |
| --- | --- |
| SDK mínimo | `26` |
| SDK de compilación | `35` |
| SDK objetivo | `35` |
| Compatibilidad de código fuente/destino Java | `17` |

La configuración está en `app/build.gradle.kts`.

## Compilar desde el código fuente

1. Clona el repositorio:

   ```bash
   git clone https://github.com/iecxiv/meshfabric.git
   cd meshfabric
   ```

2. Abre el proyecto en una versión de Android Studio compatible con Android Gradle Plugin y el wrapper de Gradle configurados en el repositorio. Instala Android SDK Platform 35 y sincroniza Gradle.
3. Selecciona un JDK de Gradle compatible con esas herramientas. Las verificaciones usan JDK 21; la compatibilidad del código fuente/destino Java permanece en 17. Son ajustes distintos.
4. Compila y ejecuta en un dispositivo o emulador con API 26 o posterior.

APK de depuración desde la raíz:

```bash
# macOS / Linux
./gradlew :app:assembleDebug

# Windows
.\gradlew.bat :app:assembleDebug
```

Tras una compilación correcta, el APK está en `app/build/outputs/apk/debug/`. Las herramientas de compilación se definen en `gradle/libs.versions.toml` y `gradle/wrapper/gradle-wrapper.properties`.

## Verificación

Pruebas JVM:

```bash
./gradlew :app:testDebugUnitTest
```

Comprobaciones JavaScript del terminal con Node.js:

```bash
node app/src/test/js/terminal-runtime.test.js
node app/src/test/js/terminal-selection.test.js
node app/src/test/js/terminal-keys.test.js
```

Compilación y ejecución de pruebas instrumentadas con dispositivo conectado o emulador activo:

```bash
./gradlew :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest
```

La verificación incluye mapeo de teclas y modificadores, entrada Compose, barra de teclas, eco/copia/pegado en xterm incluido, foco/IME y desplazamiento táctil. Las pruebas unitarias también cubren propiedad de intentos de terminal, finalizaciones obsoletas, lecturas estrictas frente a reproducción explícita, apertura del panel y almacenamiento de historiales.

Las pruebas instrumentadas del panel comprueban contenido oculto, etiquetas duplicadas de acciones principales y expansión mediante su flecha. Compilarlas no significa ejecutarlas. Las pruebas JVM y un build correcto no verifican por sí solos la alineación de clics tras zoom/giro ni la navegación desde notificaciones en un dispositivo físico.

Algunos cambios usan workflows de publicación: el commit del workflow no es el cambio funcional. Comprueba que el commit funcional se publicó y revisa el resultado de validación.

Superar pruebas automatizadas no garantiza compatibilidad universal con Hacker's Keyboard, teclados físicos, IME, entornos Android ni todos los equipos Windows/Linux. Comprueba escritura, atajos, idiomas/acentos, composición, repeticiones, cambios de foco, reconexión, tamaño de fuente y orientación horizontal en los dispositivos previstos.

## Tecnología

- Kotlin y Jetpack Compose con Material 3.
- ViewModels de estilo MVVM, Kotlin Coroutines y StateFlow.
- WebSockets y HTTP de OkHttp para los canales de control y relay de MeshCentral.
- xterm.js incluido localmente para renderizado y entrada del terminal.
- AndroidX Security Crypto para preferencias de sesión cifradas cuando la inicialización funciona.

## Seguridad y operación

- Los datos recordados incluyen URL del servidor, usuario y cookie de autenticación. No se guarda la contraseña de inicio de sesión.
- Las preferencias usan EncryptedSharedPreferences con clave maestra de Android KeyStore cuando está disponible. Si falla la inicialización cifrada, se usan SharedPreferences privadas normales: no asumas que las sesiones recordadas siempre están cifradas.
- La salida guardada del terminal no está cifrada. Los registros JSON se guardan en almacenamiento privado excluido de backups, separados por claves hash de servidor/cuenta. Desactivar Guardar búferes elimina historiales guardados y archivos antiguos; el historial puede seguir en memoria durante esa ejecución. Consulta [Persistencia de historiales](docs/terminal-archive-persistence.md).
- La copia del terminal marca el contenido del portapapeles como sensible para ocultar vistas previas compatibles. No cifra el portapapeles ni impide otros lectores autorizados.
- Trata portapapeles, salida retenida y logs de diagnóstico como potencialmente sensibles. No asumas que todas las rutas de diagnóstico han sido auditadas contra divulgación de contenido.
- Ignorar errores SSL debilita la verificación de certificados. Utiliza certificados de confianza y deja esa opción desactivada fuera de entornos controlados.
- Los permisos de cuenta, políticas del servidor y capacidades del agente son la autoridad. Un botón disponible o una solicitud enviada no elude la autorización del servidor.
- Revisa destinatarios y contenido antes de enviar comandos o acciones de energía. Comprueba el dispositivo remoto antes de repetir una acción de resultado desconocido.

## Hoja de ruta y carencias conocidas

No se presentan como funciones completadas:

- Administración de usuarios, grupos de usuarios y permisos.
- Tratamiento más completo de permisos efectivos en la interfaz.
- Compartir dispositivos/sesiones.
- Inventario avanzado de hardware/software.
- Filtros avanzados y favoritos, más allá de búsquedas y filtros actuales.
- Exportación de inventario/actividad.
- Integración con YubiKey.
- Selección múltiple y operaciones masivas de archivos.
- Reconexión automática del terminal, restauración avanzada y validación más amplia de reconexión. La salida retenida no restaura ni demuestra continuidad del proceso remoto original.
- Localización completa inglés/español de la gestión nativa.
- Validación más amplia de teclados, distribuciones, AltGr, IME, giro/zoom y navegación desde notificaciones en dispositivos reales.

## Licencia

Bajo la [Licencia Apache, versión 2.0](LICENSE).
