# Avisos, Broadcast, Mensajes y continuidad

[English](../notices.md) | [README](../../README_ES.md) | [Gestión](profile.md) | [Registro](../log.md)

## Pestañas y destinatarios

Avisos es la bandeja local recibida. Broadcast utiliza el alcance efectivo autorizado por el servidor y mostrado en su revisión. Mensajes ofrece notificaciones individuales a usuarios o a dispositivos con agente conectado; no es correo, acceso temporal ni historial de chat. El usuario muestra solo su nombre y el dispositivo muestra dispositivo (grupo). Los IDs quedan en el enrutamiento interno.

La búsqueda de Mensajes empieza oculta y se alterna desde la cabecera. Filtra etiquetas visibles, incluido el grupo. Cerrar, cambiar de pestaña o alternar Usuarios/Dispositivos oculta y limpia el filtro. El envío depende de conexión, permisos y capacidades actuales. Revisa destinatario y contenido: aceptar una solicitud no confirma entrega ni lectura, y los resultados desconocidos no se reenvían automáticamente.

## Persistencia de avisos recibidos

La bandeja guarda entradas, estado local de lectura y contador de no leídos. Se restauran tras autenticar y vincular la misma cuenta y servidor. La clave de almacenamiento es un hash de URL del servidor e ID del actor, no de la cookie de sesión. Otras cuentas/servidores utilizan archivos separados. Cerrar sesión oculta y vacía la vista en memoria, no su archivo guardado. Restaurar no vuelve a publicar alertas Android. Entrar en Avisos o descartar la notificación Android no marca como leído. La lectura es local a esta instalación, no un acuse al servidor.

Se conservan todos los no leídos; la retención automática mantiene hasta 200 leídos. Marcar como leído no implica eliminar inmediatamente. El borrado individual es explícito; vaciar exige confirmación e incluye no leídos. Solo afecta a esta bandeja local, no a eventos o mensajes del servidor. No recupera avisos que ya se hubieran perdido con el almacenamiento anterior en memoria.

## Almacenamiento y fallos

NoticeInboxDisk usa AES-GCM con clave generada por Android Keystore, autenticación ligada al ámbito, sustitución atómica de archivos y almacenamiento privado excluido de backups. No tiene alternativa en texto plano. Es independiente de las preferencias de inicio de sesión y de los historiales de terminal sin cifrar. Borrar datos de la app o desinstalar no permite conservar esta bandeja local. Las claves no disponibles o el contenido cifrado dañado se notifican; no se sustituyen silenciosamente por una bandeja vacía.

Lecturas y escrituras usan un ejecutor serializado. La restauración combina los avisos que ya hayan llegado a memoria; la interfaz informa de carga, guardados pendientes y errores. El contenido previo al cifrado se limita a 16 MiB por servidor/cuenta. Si falla o se supera, se conserva el archivo anterior sin eliminar automáticamente no leídos. Puede haber cambios solo en memoria. Espera a que termine el guardado antes de cerrar; una terminación abrupta puede perder escrituras pendientes. El reintento es explícito. Vaciar tras un error es destructivo y exige la misma confirmación de borrado.

Persistir conserva datos recibidos; no añade push, no recupera mensajes omitidos del servidor ni garantiza recepción con el proceso detenido. El permiso Android y la activación de recepción de la cuenta son independientes.

## Límites al volver de segundo plano

- Gestión conserva la navegación principal del diálogo y datos de presentación, bloqueando escrituras hasta disponer de datos actuales de la misma cuenta. La identidad invalida el contexto. Las confirmaciones sensibles pueden invalidarse y no se garantiza conservar todos los diálogos anidados.
- Servidor conserva resultados abiertos admitidos independientemente del lector pausado y mantiene su presentación mientras sigan compuestos. Los cambios de identidad/rol observado los retiran. La actualización del servidor mantiene autorización y confirmación propias, sin reenvíos. Consola/Seguimiento siguen borrando su salida al pasar a segundo plano.
- Eventos conserva el detalle solo durante una pausa verificada y recarga con la misma vinculación. La autorización recibida debe coincidir y, tras recargar, debe existir exactamente un registro resuelto igual. Cambios de selección/inventario, pérdida de conexión principal, denegación, errores o cambios de identidad/autorización cierran el detalle.
- Actividad sigue siendo un diario de sesión solo en memoria, no la bandeja persistente de Avisos. Estos cambios no restauran diálogos ni sesiones remotas vivas tras morir el proceso.

## Aceptación real pendiente

Compilación, unitarios, lint e instrumentación no demuestran entrega real ni comportamiento del dispositivo. En el móvil previsto: recibir no leídos; esperar al guardado; cerrar y abrir; verificar lectura y contador; cambiar de cuenta/servidor y volver; descartar alertas Android sin marcar leído; borrar/vaciar y abrir de nuevo; y pasar a segundo plano con cada diálogo relevante. Comprobar aparte Broadcast y mensajes a usuario/dispositivo, incluyendo desconexión y permisos insuficientes. No se declara completado aquí ninguno de esos escenarios manuales.
