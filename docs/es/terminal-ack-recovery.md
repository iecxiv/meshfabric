# Supervisor ACK del terminal solo en primer plano

[English](../terminal-ack-recovery.md) | Español

Una confirmación pendiente del parser tiene un plazo de 20 segundos únicamente mientras su renderizador está adjunto y el ciclo de vida de la navegación actual está en RESUMED. Separar la vista o pasar a segundo plano cancela la comprobación programada sin abandonar la salida pendiente. Volver a RESUMED concede un plazo completo nuevo. Los callbacks RESUMED repetidos no lo amplían. Un terminal inactivo o estático no tiene plazo.

Al vencer, la vista muestra ACK_TIMEOUT y deja de avanzar la salida. Las confirmaciones tardías no borran silenciosamente ese error. No se reinician el socket, el PTY remoto ni el registro de salida. No se envía Ctrl+L ni ninguna otra entrada sintética.

ACK_TIMEOUT y la pérdida del renderizador ofrecen la acción explícita y confirmada Recrear vista. Esta elimina únicamente el renderizador esperado exacto de la caché y construye otro asociado a la sesión existente. El inicio de la sesión de red se vincula al dispositivo/navegación, no al reemplazo del renderizador. Los huecos OUTPUT_CONTINUITY no ofrecen este botón porque reproducir la salida no recupera un registro ya truncado.

Recrear no restaura una instantánea VT. La confirmación advierte de que nano y otros programas a pantalla completa pueden necesitar redibujarse. Se conservan las limitaciones existentes de reproducción y geometría. No se añade un bucle de reintentos automáticos ni una desconexión de sesión como alternativa. El supervisor aplica una heurística conservadora de tiempo de espera, no demuestra un fallo del proceso del renderizador.

Las asociaciones de visibilidad usan tokens para impedir que una pantalla anterior pause un host nuevo. La recuperación comprueba la identidad al eliminar, de modo que un botón obsoleto no destruya un renderizador de reemplazo. Los observadores se eliminan al liberar la composición; el renderizador no almacena objetos Lifecycle/Activity.

Se incluyen catorce pruebas JVM puras nuevas del supervisor y dos de recuperación del almacén. Las pruebas existentes JavaScript, pipeline, preparación, negociación y cuatro pruebas de eco con WebView real deben pasar en CI antes de publicar. Todavía no ejercitan el diálogo de recuperación ni las transiciones Android entre primer y segundo plano en un dispositivo físico. Compruébalos manualmente, junto con nano/teclado/giro y la entrada c/1.
