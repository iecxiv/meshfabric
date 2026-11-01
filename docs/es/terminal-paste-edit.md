# Pegado con edición

[English](../terminal-paste-edit.md) | Español

El menú de herramientas conserva Seleccionar y copiar y Pegar desde Android, y añade Pegar con edición como tercera entrada. Solo lee el portapapeles mediante esa acción explícita y precarga la captura de texto plano aceptada en un editor multilínea. Un portapapeles vacío o rechazado inicia el editor vacío y muestra el motivo.

Solo Enviar texto envía el borrador editado por la ruta de pegado existente, sin añadir Intro. Intro dentro del editor inserta un salto de línea; no envía. La aceptación de texto multilínea se vincula al texto actual: modificar un texto aceptado la restablece. Las ediciones inválidas se rechazan completas, conservando el último borrador y mostrando un error; confirmar permanece desactivado hasta una edición válida. No hay truncamiento silencioso. Cerrar descarta el borrador, sin sobrescribir el portapapeles ni registrar o persistir su contenido.

Se reutilizan límites de pegado, validación de controles/Unicode, protección de intento único, comprobaciones del renderizador, pegado entre delimitadores y resultado desconocido sin reintento. El pegado de solo lectura y la salida/transporte del terminal no cambian. CI compila app y APK de pruebas, ejecuta todas las pruebas JVM/Node y regresiones WebView existentes de eco/copia/pegado. Sigue siendo necesaria la verificación en dispositivo de editor/IME, contenido editado enviado, cancelación, restablecimiento de aceptación y disposición del diálogo con teclado.
