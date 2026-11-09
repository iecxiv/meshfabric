# Administración de usuarios y accesos — MeshFabric 1.3.1

[English](../profile-user-administration.md) | [Gestión](profile.md)

## Usuarios

La administración exige autoridad del servidor y permanece en el dominio actual. El alta sigue el modo de identidad anunciado: con userNameIsEmail=false, nombre de usuario y correo son campos separados; con true, el correo es el identificador de acceso. Una cuenta antigua no determina el modo de las nuevas. El formulario actual exige correo válido y contraseñas repetidas coincidentes en ambos modos.

Se editan correo y nombre real; teléfono y estado administrativo de verificación del correo solo aparecen cuando las condiciones del servidor lo permiten. Marcar un correo verificado no demuestra propiedad del buzón ni envía un mensaje de verificación.

Los ajustes avanzados incluyen permisos del servidor, restricciones, características, consentimiento, cuota y reinos administrativos. Se protegen los bits desconocidos, no delegables y las máscaras de administración completa. Los reinos no son membresías de grupos. Bloquear/desbloquear conserva los demás derechos. El borrado exige nombre exacto, revisión y confirmación separada.

En la propia cuenta se ocultan mensaje, bloqueo/desbloqueo y eliminación administrativa. Dos acciones separadas de contraseña y MFA llevan a los controles nativos de Mi cuenta. Para otros usuarios se mantienen sus controles, incluido cambio de contraseña conservando MFA, restablecimiento MFA explícito y notificación individual revisada. Ocultar botones no sustituye las comprobaciones internas.

## Grupos y accesos

- Crear, editar y eliminar grupos de usuarios compatibles, respetando restricciones de grupos externos.
- Añadir o retirar un miembro elegible cada vez, conservando comprobaciones de identificador y enlaces conocidos.
- Crear, revisar, editar y retirar asignaciones entre grupos de usuarios y grupos de dispositivos.
- Conceder acceso a dispositivos existentes y visibles; no instala agentes.
- Conceder, revisar, editar y retirar acceso directo de usuarios a grupos de dispositivos sin confundirlo con acceso heredado.
- Ajustes avanzados de grupos de dispositivos: características compatibles, consentimiento obligatorio y caducidad de 0 a 2000 días. Cero elimina la excepción de caducidad del grupo; no garantiza desactivar la eliminación global.

Permisos y restricciones son distintos. Las concesiones iniciales no inventan dependencias ni bits desconocidos. La edición conserva bits protegidos y desconocidos y rechaza cambios de administración completa no admitidos. Retirar una membresía o asignación no demuestra pérdida de todas las vías de acceso efectivo.

## Mensajes, resultados y validación

Broadcast y notificaciones individuales tienen alcances distintos y confirmación revisada. No ofrecen garantía de entrega/lectura ni reintentos automáticos.

Se prefieren nombres y contexto a identificadores visibles. Una identidad ambigua o incompleta no autoriza cambios sensibles. Los acuses correlacionados y las relecturas requeridas distinguen estados confirmados u observados de resultados desconocidos. Pueden producirse cambios concurrentes entre consulta y envío.

Hay regresiones unitarias y Android/Compose de políticas, identidad, navegación, interfaz y relecturas. Compilar pruebas no equivale a ejecutarlas; superar automatización no valida todas las modificaciones reales ni todos los diseños en dispositivos físicos.
