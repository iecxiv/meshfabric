# Mejora de interacción 1: selección y copia

[English](../terminal-selection-copy.md) | Español

Añade un menú de herramientas del terminal y un diálogo nativo de captura de solo lectura. La selección mediante pulsación larga usa SelectionContainer de Compose, no selección DOM sobre el lienzo del terminal. La selección parcial se copia mediante la barra nativa; Copiar todo copia exactamente la captura. La copia siempre la inicia el usuario y nunca envía entrada al shell remoto. Es una captura independiente, no selección directa sobre el terminal activo.

La extracción usa únicamente las API públicas de líneas del búfer activo de xterm. Une los ajustes visuales de línea y conserva los saltos reales. Omite el relleno vacío del área visible. Los límites son 2000 filas recientes y 200000 unidades UTF-16; el truncamiento se indica y no corta pares sustitutos. No se captura el búfer normal oculto mientras nano usa el alternativo.

La lectura nativa se puede cancelar y vence a los tres segundos. Cerrar el diálogo cancela su corrutina: los callbacks tardíos no pueden reabrirlo ni rellenarlo. El texto solo permanece transitoriamente en memoria de interfaz, sin logs ni persistencia. Seleccionar no pausa el terminal ni altera el cursor de entrega, el redimensionado o el protocolo.

Tanto la copia parcial como la completa usan un ClipboardManager local al diálogo que marca el contenido como sensible para ocultar la vista previa de Android. Esto no cifra el portapapeles ni impide leer el texto a otras aplicaciones autorizadas. Android 13 y posteriores aportan su propia confirmación de copia; solo las versiones anteriores muestran un Toast.

Verificación: nuevas pruebas JavaScript puras de capturas y pruebas existentes del runtime. Los casos Android WebView también comprueban el texto capturado; un caso con colores ANSI comprueba que se copian caracteres renderizados y no secuencias de escape. Otra prueba instrumentada verifica el texto de ClipData y sus metadatos sensibles. CI debe compilar app/pruebas y ejecutar JVM, Node y las pruebas Android específicas antes de publicar. Sigue siendo necesaria la verificación física de tiradores de selección, desplazamiento al seleccionar, orientación horizontal, barra del portapapeles y estado retenido del terminal.

Este commit excluye deliberadamente el pegado, el comportamiento IME/foco y el rediseño de teclas especiales/desplazamiento.
