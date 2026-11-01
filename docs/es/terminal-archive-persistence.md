# Historiales del terminal y ajuste de guardado de búferes

[English](../terminal-archive-persistence.md) | Español

Cerrar con X, o cerrar todas las sesiones, retira el transporte actual y archiva su registro local. Los historiales archivados están separados del registro de sesiones actuales: no resaltan el botón del dispositivo, no reconectan automáticamente ni indican que haya sobrevivido un proceso remoto. Abrir el terminal inicia un registro nuevo; los anteriores se seleccionan mediante Historial.

Con Guardar búferes del terminal activado, los historiales y puntos de control usan almacenamiento privado de la app excluido de backups, separado mediante un hash de la URL del servidor y el usuario. La salida es texto sin cifrar. Los identificadores estables de registro evitan duplicados. Las escrituras utilizan un archivo temporal sincronizado, fsync y reemplazo; los registros guardados malformados o demasiado grandes no se cargan.

Con el ajuste desactivado, los registros solo permanecen en memoria. Desactivarlo elimina los archivos de historial y los búferes antiguos guardados. El ajuste se comprueba de nuevo al escribir, incluidos los puntos de control diferidos, y se conserva al cerrar sesión. Activarlo permite que los siguientes puntos de control y cierres vuelvan a guardar.

Los puntos de control de salida se limitan aproximadamente a uno por segundo. La actividad también guarda en onStop. Retirar la tarea cancela reconexiones pendientes y archiva todos los registros actuales del terminal mediante el callback existente del servicio. Estos callbacks no garantizan recuperar salida nunca escrita antes de una terminación abrupta del proceso; al reiniciar se restaura el último registro completo guardado como historial de solo lectura, nunca como sesión desconectada actual.

Eliminar historiales también borra sus archivos guardados. Solo se eliminan los registros revisados por la acción de borrado; las sesiones actuales quedan excluidas. Los búferes antiguos por dispositivo solo se migran cuando una cuenta recordada identifica a su propietario al arrancar. Los archivos antiguos sin propietario identificado no se asignan a una cuenta recién introducida.

Validación: las pruebas JVM del almacén en disco cubren inserción/actualización y deduplicación, separación de cuentas, persistencia desactivada, eliminación duradera, contención de rutas y rechazo de registros demasiado grandes. La validación en dispositivo debe comprobar todavía X, retirada de tareas recientes, reinicio, ambos estados del ajuste, cambios de cuenta y eliminación seguida de reinicio.
