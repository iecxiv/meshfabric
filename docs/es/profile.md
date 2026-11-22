# Gestión — MeshFabric

[English](../profile.md) | [README](../../README_ES.md)

Gestión sustituye la etiqueta Perfil. Sus cuatro pestañas son Mi cuenta, Grupos de dispositivos, Usuarios y Grupos (de usuarios). Los dos tipos de grupo son entidades distintas.

## Funciones disponibles

- Las acciones de grupos con agente ofrecen Agregar agente e invitaciones de instalación: [Instalación de agentes e invitaciones](agent-installation.md).

- Controles nativos de seguridad y preferencias: [Mi cuenta](profile-native.md).
- Usuarios, grupos, membresías y accesos: [Administración de usuarios y accesos](profile-user-administration.md).
- Fichas de grupos de dispositivos con resumen, dispositivos y accesos recibidos; ajustes avanzados de características, consentimiento y caducidad.
- Enlaces desde usuarios a membresías, grupos y dispositivos visibles. La navegación utiliza identificadores internos exactos y fichas disponibles, no solo nombres. Se recupera el origen cuando sigue siendo válido.
- Indicadores de conexión y espaciado exterior coherentes en las cuatro pestañas de Gestión y las cuatro de Servidor. Cada indicador describe su propio canal, no todas las conexiones de la aplicación.

## Búsqueda y navegación

La búsqueda del directorio se abre y cierra desde la cabecera. La entrada está limitada a 256 caracteres. Las pruebas de integración de pantalla completa cubren cambios de sección y navegación con Atrás, también con una escala de fuente de 1,8.

Dentro de un grupo de dispositivos, la búsqueda filtra los dispositivos de ese grupo, no todo el inventario del servidor. Cada grupo abierto conserva su propia consulta al volver al directorio, cuyo filtro se mantiene por separado. La búsqueda se oculta en Accesos y Resumen; al regresar a Dispositivos se recupera su consulta. Con la búsqueda de dispositivos del grupo abierta, Atrás primero la cierra; otra pulsación vuelve al directorio.

Cerrar sesión conserva las preferencias locales de la aplicación. Esto no equivale a conservar datos de la cuenta autenticada ni a restaurar una sesión activa.

## Seguridad y alcance

El servidor conserva la autoridad sobre permisos y políticas. Datos ausentes o lecturas fallidas significan información no disponible, no inventario vacío ni prueba de eliminación. Los accesos comunes proceden de datos visibles recibidos, no de un cálculo completo de permisos efectivos.

Los cambios sensibles de acceso separan revisión y confirmación. Cambios de identidad, datos, permisos o conexión pueden invalidar la revisión. Un resultado desconocido no es éxito y no se reenvía automáticamente. Consultas y escrituras no son atómicas: siguen siendo posibles modificaciones concurrentes.

Están implementados el login FIDO2/WebAuthn y el alta, consulta y baja de llaves. El usuario confirma alta, baja y login físicos, incluido el login por USB, con la llave utilizada en este trabajo. Consulta [FIDO y alcance de validación](fido.md). Estas pruebas no garantizan compatibilidad universal ni validan todas las operaciones de cuenta.

## Continuidad al volver de segundo plano

La pérdida temporal de disponibilidad ya no borra por sí sola la navegación del diálogo principal. Pueden mostrarse datos anteriores con acciones bloqueadas hasta recibir datos actuales de la cuenta coincidente. Los cambios de identidad borran el contexto; las revisiones sensibles pueden invalidarse permanentemente al interrumpirse. No garantiza conservar todos los diálogos anidados ni restaurarlos tras morir el proceso. Los diálogos de seguridad de cuenta mantienen sus reglas. Consulta [Avisos y continuidad](notices.md).

## Alcance de validación

El mantenedor confirma ambos jobs de la CI permanente en verde, con once clases instrumentadas en la suite de regresión. Las pruebas de búsqueda de pantalla completa utilizan datos de sesión controlados; no acreditan permisos ni entrega de eventos contra un servidor real.

El mantenedor confirma manualmente el inicio de sesión, Compartir y el retorno desde segundo plano al escritorio remoto y al terminal en el entorno probado. Estas comprobaciones no garantizan todos los servidores, cuentas, dispositivos Android o interrupciones, ni cubren el retorno al gestor de archivos o la recuperación tras morir el proceso.

Sigue pendiente comprobar contra el servidor previsto:

- Cuentas con roles restringidos, incluida la retirada de acceso con una ficha de dispositivo abierta; comparar la misma cuenta y dispositivo con la interfaz web del servidor.
- Consultas históricas y detalles de eventos, contrastando identidad, fecha/hora y ausencia de duplicados al actualizar con los registros visibles en la web.
- Cambios de inventario como nombres de dispositivos/grupos, visibilidad y estado de conexión, distinguiendo actualizaciones automáticas de refrescos explícitos, también después de pasar a segundo plano.
