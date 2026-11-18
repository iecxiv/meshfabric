# Sesiones y chat de dispositivos

[English](../device-sessions.md) | Español

## Tarjetas de dispositivos

El chevrón de la tarjeta muestra General, Eventos, Detalles y la Consola del agente. La disponibilidad depende de la cuenta autenticada, permisos actuales, inventario y capacidades remotas. Se elimina el icono de información duplicado junto al indicador de conexión; Detalles permanece entre las opciones desplegadas. Compartir está oculto hasta completar su implementación.

## Escritorio remoto

El subtítulo muestra Conectado con un visor o un contador desconocido, y Conectado, 2 viendo o Conectado, 3 viendo cuando hay varios. Suma las conexiones comunicadas en los metadatos del servidor, no solo las cuentas distintas. Desconectar limpia el contador anterior. Recibir metadatos no solicita un refresco de pantalla.

Reanudar y refrescar son operaciones distintas del protocolo. La compresión incluye el intervalo de fotogramas y cambiar la velocidad actualiza ese ajuste. No hay un bucle de refrescos usado como heartbeat. El arrastre y plegado de la barra, el ratón físico y la conservación de la vista requieren validación en los dispositivos Android y remotos previstos.

## Chat y segundo plano

Chat, desde General, abre una ventana local. Iniciar chat es explícito y solicita al equipo seleccionado abrir su navegador; no confirma presencia humana. Minimizar conserva borrador e historial en una burbuja móvil dentro de la app. Puede restaurarse o arrastrarse al destino de cierre. No son burbujas superpuestas sobre otras aplicaciones.

Las sesiones de chat abiertas se cuentan en el servicio keep-alive existente junto a escritorio, terminal y archivos. Pasar a segundo plano no cierra deliberadamente el chat. La notificación de sesiones activas puede mantenerse aunque solo haya un chat y solicita volver a la app al pulsarla. Cerrar la última sesión elegible permite detener el servicio; otras sesiones elegibles lo mantienen.

Cerrar un chat, liberar su host autenticado, perder la autorización verificada o retirar la tarea puede cerrar la sesión. No se reabre automáticamente el navegador remoto ni se reenvían mensajes. El servicio en primer plano no restaura sesiones después de morir el proceso. El borrador y el historial del chat no se guardan en disco. Solo admite texto, con historial local limitado y sin confirmaciones de entrega o lectura.

## Apariencia

El contraste de los iconos de la barra de estado se elige según el fondo y se reaplica tras el diseño de configuración, al reanudar y al recuperar el foco. Debe comprobarse la rotación con tema claro y oscuro sin usar el panel de notificaciones como solución temporal.

## Lista de aceptación

- Abrir el escritorio en la app, añadir un visor web y repetir en orden inverso; comprobar imagen y contador al salir cualquiera.
- Repetir con varias conexiones de la misma cuenta y con tres visores.
- Girar, arrastrar y plegar/desplegar la barra; comprobar posición de vista y puntero.
- Dejar solo un chat, pasar a segundo plano, recibir un mensaje remoto y volver con la notificación. Verificar borrador e historial en memoria.
- Repetir con chats minimizados y otras sesiones activas; cerrar chats y comprobar la duración de la notificación.
- Revisar cierre explícito, logout, pérdida de permisos/red y retirada de tarea sin reenvío automático de mensajes.

Son comprobaciones de aceptación, no una afirmación de que todas hayan pasado. Publicación, compilación, tests unitarios, instrumentación y validación física contra un servidor real son evidencias distintas.
