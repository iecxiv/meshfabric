# Pegado explícito en el terminal

[English](../terminal-paste.md) | Español

Menú → Pegar desde Android → vista previa completa desplazable → confirmar. El portapapeles se lee únicamente mediante esa acción, como texto plano y sin conversión de URI. No se añade Intro; los saltos de línea existentes pueden ejecutar comandos y requieren aceptación expresa. Límite: 16384 unidades UTF-16, rechazando el exceso en lugar de truncarlo. Se rechazan controles no admitidos, incrustaciones/aislamientos bidireccionales y pares sustitutos malformados. La entrada usa la API pública de pegado de xterm incluido y su comportamiento de pegado entre delimitadores/finales de línea.

Un envío por vista previa, sin reintentos automáticos, logs ni persistencia. El callback de envío no confirma recepción remota; un tiempo de espera deja el resultado desconocido y solicita revisar el terminal antes de otro intento. Cancelar no deshace entrada enviada. No cambian el editor de comandos, foco, IME, teclas, entrega de salida, redimensionado ni recuperación existentes.

CI: pruebas Node/JVM existentes, pruebas de políticas y casos con xterm real en WebView de una línea sin Intro extra, CRLF, texto multilínea entre delimitadores e inyección de escapes; compilar app y APK de pruebas, ejecutar emulador y después publicar. Quedan comprobaciones en dispositivo: vista previa/desplazamiento, aceptación/cancelación, portapapeles vacío y desconexión. No se garantiza que el contenido del portapapeles ni los comandos sean seguros.
