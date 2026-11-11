# Instalación de agentes e invitaciones

[English](../agent-installation.md) | [Gestión](profile.md) | [README](../../README_ES.md)

## Acceso

En Gestión, abre Grupos de dispositivos y un grupo con agente. Agregar agente prepara material de instalación; Invitar genera un enlace de instalación para ese grupo. Invitar no aparece en el menú de tres puntos de las tarjetas de Equipos/Dispositivos. Ninguna de estas acciones concede acceso temporal a un dispositivo existente ni envía invitaciones por correo. Los permisos del servidor siguen siendo la autoridad.

## Agregar agente

Selecciona plataforma y arquitectura o modo disponible. Se ofrecen instaladores Windows, comandos Linux/BSD y arquitecturas binarias, instaladores macOS, Asistente MeshCentral y configuración Android MeshAgent mediante APK/QR. La disponibilidad depende de los datos recibidos y del servidor; no se inventa información ausente.

Las descargas usan la sesión actual contra el origen configurado, sin seguir redirecciones, con su política de certificados y límite de 128 MiB. Se prepara un temporal privado antes de abrir el selector de destino de Android. El guardado cuenta los bytes copiados y relee el destino para comparar tamaño y SHA-256. Un archivo de cero bytes, una verificación fallida o un cambio de identidad no se presenta como guardado correcto. La relectura comprueba la copia, no la identidad del editor ni la firma del instalador. Cancelaciones y errores del proveedor pueden impedir guardar; borrar un destino incompleto es un intento de mejor esfuerzo.

El diálogo se mantiene fuera del contenido de la ficha del grupo para que retirar ese contenido no destruya por sí mismo el guardado pendiente. Esto no garantiza recuperación tras la muerte del proceso.

Los comandos Linux/BSD de instalación y desinstalación conservan la alternativa de ejecución directa tras sudo. Ambas invocaciones reciben los mismos argumentos; la desinstalación incluye su argumento en las dos. Por el orden de los operadores AND/OR, la alternativa puede ejecutarse también tras un fallo anterior, no solo por ausencia de sudo. Revisa los comandos antes de usarlos en un equipo de prueba. MeshFabric no ejecuta comandos ni instaladores localmente.

## Invitar

Elige caducidad, agentes y tipo de instalación y pulsa Generar enlace. Abrir el diálogo o cambiar un selector no crea el enlace.

- Caducidad: 1 h, 8 h, 1 día, 1 semana, 1 mes o Ilimitado. Un mes equivale a 30 días.
- Agentes: todos los disponibles, Windows, Linux, macOS, Asistente MeshCentral o Android MeshAgent.
- Instalación: segundo plano e interactivo, segundo plano solamente o solo interactivo. Assistant y Android ocultan la opción y reinician su valor; al volver no se restaura una selección oculta.

El canal de control independiente envía los argumentos de autenticación antes de solicitar información de usuario. Debe validar la identidad esperada antes de pedir el enlace del grupo. Las respuestas se correlacionan con la petición y el grupo. No hay reenvíos automáticos de la operación.

Copia el enlace solo tras confirmar su generación. Cambios de opciones, identidad, conexión o permisos y salir de primer plano invalidan el resultado mostrado localmente. Eso no revoca un enlace generado en el servidor. Los enlaces de invitación y la configuración QR son sensibles.

## Errores y comprobaciones

Los diagnósticos distinguen conexión, envío del handshake, espera de identidad y espera de respuesta del enlace. La aceptación local del envío no prueba procesamiento en el servidor. Un resultado desconocido no es éxito: comprueba el estado antes de repetir. Comparte solo texto/código de error, nunca cookies, enlaces completos ni QR de configuración.

Pruebas automatizadas, revisión visual, descarga, instalación e incorporación al grupo correcto son comprobaciones diferentes. Publicar un workflow o APK no demuestra todas ellas. Registra lo observado en la [lista de aceptación](../features/add-device-invite-acceptance.md).
