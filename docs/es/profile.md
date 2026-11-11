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

## Seguridad y alcance

El servidor conserva la autoridad sobre permisos y políticas. Datos ausentes o lecturas fallidas significan información no disponible, no inventario vacío ni prueba de eliminación. Los accesos comunes proceden de datos visibles recibidos, no de un cálculo completo de permisos efectivos.

Los cambios sensibles de acceso separan revisión y confirmación. Cambios de identidad, datos, permisos o conexión pueden invalidar la revisión. Un resultado desconocido no es éxito y no se reenvía automáticamente. Consultas y escrituras no son atómicas: siguen siendo posibles modificaciones concurrentes.

El inicio de sesión FIDO2 nativo y el alta de claves nuevas siguen pendientes; las claves existentes se pueden consultar y eliminar. Las pruebas automatizadas no sustituyen la validación de cada operación en un servidor y dispositivo reales.
