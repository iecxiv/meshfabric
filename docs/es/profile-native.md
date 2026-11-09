# Mi cuenta — MeshFabric 1.3.1

[English](../profile-native.md) | [Gestión](profile.md)

Mi cuenta utiliza controles nativos en lugar de los antiguos accesos al navegador. Su canal autenticado privado es independiente de los demás canales de la aplicación.

## Seguridad y acciones

- Cambio de contraseña mediante el diálogo existente y comprobación de contraseña actual.
- Alta de autenticador TOTP con clave manual y código de seis dígitos; desactivación con confirmación explícita.
- Consulta y eliminación de claves de seguridad registradas. El inicio de sesión FIDO2 nativo y el alta de claves nuevas no están implementados; no se abre automáticamente un navegador como alternativa de alta.
- Consulta, regeneración y borrado de códigos de respaldo con controles de visibilidad y confirmación. Las respuestas estructuradas conservan el estado de uso informado.
- Historial web registrado por el servidor; puede no incluir accesos de la aplicación.
- Creación y consulta de tokens de inicio de sesión, con revocación individual confirmada.
- Idioma de la cuenta del servidor y preferencias locales de fecha y hora.

Tu propia ficha de Usuarios ofrece dos acciones: Cambiar contraseña abre directamente su diálogo después de navegar a Mi cuenta; Gestionar MFA abre un selector de autenticador, claves existentes y códigos de respaldo. Abrirlos no cambia credenciales ni restablece MFA. La navegación espera a que el canal esté preparado, consume la petición una sola vez y rechaza actor o servidor distintos.

## Notificaciones

Las preferencias se sincronizan con el estado web de la cuenta, conservando ajustes ajenos y bits desconocidos. Guardar se bloquea si falta ese estado. El idioma de la cuenta del servidor es distinto del idioma Android de la aplicación.

La bandeja de sesión y las alertas Android opcionales muestran avisos recibidos, incluidos los eventos de conectividad y AMT configurados. Recepción, permiso Android, configuración del canal y políticas del servidor son independientes. No se garantiza entrega con el proceso detenido. Sonido y nombre del grupo son preferencias de presentación, no tipos adicionales de aviso.

## Límites operativos

Las solicitudes se procesan de una en una y no se reintentan automáticamente. Cada acción exige su acuse o relectura; no todas las respuestas antiguas están correlacionadas transaccionalmente. El cambio de contraseña distingue notificaciones conocidas de éxito y rechazo. El borrado de cuenta exige confirmación y HTTPS: una redirección no demuestra eliminación. Un intento ambiguo requiere comprobar la cuenta y cerrar sesión según las indicaciones.

Estos diálogos no guardan ni registran deliberadamente secretos o borradores. La copia es explícita y marca el portapapeles como sensible; no lo cifra. Los diálogos de seguridad solicitan protección de ventana. El ciclo de vida pausa o cierra el canal. Las pruebas no demuestran que todas las modificaciones de cuenta se hayan verificado contra un servidor real.
