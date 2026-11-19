# Controles de dispositivos, acciones por lote y apariencia

[English](../device-controls.md) | Español

## Exploración y selección
La ordenación, los filtros, favoritos locales y selección pertenecen al contexto de cuenta y servidor activos. La selección se reconcilia con el inventario visible; cambiar de contexto autenticado invalida acciones sensibles. El nombre del sistema operativo conserva la elección guardada y no controla el resaltado del filtro. El estado de energía no se deduce solo de la conexión.

## Acciones por lote revisadas
Selecciona dispositivos o grupos visibles y revisa acción y destinos elegibles. Exportar JSON es local. Las acciones remotas incluyen energía, etiquetas, notificaciones, comandos, movimiento de grupo, actualización de agente, borrado/carga del núcleo predeterminado, desinstalación, eliminación y subida de archivos. La disponibilidad depende de acción, servidor, permisos y agente. Las operaciones de núcleo y desinstalación exigen un agente conectado compatible y las comprobaciones conservadoras de derechos completos de esas acciones.

Revisar y confirmar no constituye una transacción atómica del servidor. Se relee el contexto antes de enviar. Se intenta una vez por destino aprobado, sin reenvío automático. Detener pendientes no revierte solicitudes ni detiene comandos remotos ya iniciados.

Aceptación, encolado local, salida recibida, observaciones limitadas de archivos y cambios verificados son resultados distintos. Recibir salida no demuestra la finalización correcta de un comando. Las observaciones de subida no verifican un hash remoto. Lee las etiquetas y advertencias de incertidumbre.

Los diálogos conservan el tema compartido, adaptan ancho y alto al espacio y contenido y mantienen título y acciones fuera del desplazamiento. Las listas largas, ventanas pequeñas y el teclado pueden requerir scroll. Mover de grupo muestra nombres en lugar de identificadores internos.

## Escritorio remoto
La selección de monitor depende de las capacidades comunicadas por el agente. El selector de clic es persistente; el asistente flotante independiente de clic derecho es de un solo uso. Su control sigue al colapsador. Scroll conserva el modo de clic anterior e Información es la última acción de la barra. Esto no implica que estén implementadas todas las herramientas previstas.

## Apariencia
Sistema sigue Android; Claro y Oscuro son fijos. Programado usa horas locales de inicio de claro y oscuro, que deben ser distintas. Automático solar emplea una ciudad manual de un catálogo incluido de 24 ciudades y calcula localmente amanecer y anochecer en su zona horaria. No pide acceso a ubicación, no usa GPS ni consulta servicios de localización. Cambia la ciudad si viajas. Los horarios son aproximados; sin ciudad o sin eventos disponibles se usa temporalmente Sistema, conservando el modo elegido.

La apariencia se actualiza con la app activa y al reanudarla, sin necesitar alarmas en segundo plano. Se conservan las elecciones de tema fijo. El selector compacto mantiene columnas de iconos y texto y se adapta a pantallas estrechas.

## Límites de validación
Compilación, pruebas JVM y lint condicionan la publicación. Compilar pruebas de interfaz no equivale a ejecutarlas. Revisa ambos idiomas, escala de fuente, orientación, teclado y resultados remotos con los dispositivos y servidor previstos. Compartir y las demás herramientas pendientes no deben considerarse funciones entregadas.
