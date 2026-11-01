# Recepción tipada del terminal y pruebas de eco con WebView real

[English](../terminal-typed-receive.md) | Español

La salida binaria WebSocket se entrega directamente a handleTerminalOutput y nunca se interpreta como negociación c/cr/1. Se conserva la compatibilidad de negociación mediante tramas de texto antes de conectar; el texto ya conectado usa el mismo publicador de salida. No cambian paquetes de tamaño, retención del renderizador, codificación de entrada ni eco local.

Cuatro casos instrumentados Android ejercitan los manejadores de recepción TerminalSession de producción, el registro y la entrega con ACK del parser; después inspeccionan el búfer xterm incluido real en un WebView Android. Cubren ac1b y cc11 como caracteres binarios/de texto aislados. El socket es simulado y no se usan comandos remotos ni credenciales. La página de pruebas envuelve el constructor Terminal incluido únicamente en su HTML sintético; no se añade un punto de inspección a los recursos de producción. No reproducen una conexión de red MeshCentral completa ni entrada de teclado Android.

Los builds debug registran como máximo 64 metadatos de tramas por sesión y sus longitudes/revisiones de salida bajo TerminalEcho. Los logs contienen tipo, longitudes e indicadores de conexión, nunca texto del terminal ni pulsaciones. Si persiste el síntoma en dispositivo, hacen falta estas trazas para localizar la pérdida restante; este cambio no establece por sí solo la causa anterior en el runtime.

La Activity auxiliar y su entrada de manifiesto no exportada solo existen en debug. CI ejecuta JavaScript/unitarias existentes, compila app y APK instrumentado y ejecuta los cuatro casos WebView en un emulador API 35 antes de publicar. Sigue siendo necesaria la validación física.
