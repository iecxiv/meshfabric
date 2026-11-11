# Aceptación de Agregar agente e Invitar

## Identificar la evidencia

Registrar rama, commit funcional, ejecución de validación, artefacto probado, entorno Android, servidor y cuenta de prueba sin credenciales. Utilizar el APK construido desde el código que se está revisando. El commit de un workflow no sustituye al commit funcional. No confundir los métodos de regresión exigidos con el total real de pruebas ejecutadas.

Se notificaron fallos reales de generación de enlaces y guardado de archivos vacíos. Hay correcciones publicadas de autenticación, propiedad de diálogos y verificación del guardado; la publicación no acredita por sí sola que hayan superado una nueva prueba en el servidor real. Las casillas siguientes permanecen pendientes hasta registrar evidencia concreta.

## Interfaz y navegación

- [ ] Agregar agente e Invitar aparecen en Gestión dentro de un grupo con agente.
- [ ] Invitar no aparece en el menú de tres puntos de tarjetas de Equipos/Dispositivos; las demás acciones mantienen su comportamiento.
- [ ] Se presentan seis caducidades y seis tipos de agente. Un mes se explica como 30 días.
- [ ] Los tres modos se muestran donde corresponde; Assistant y Android los ocultan y reinician sin restaurar una selección anterior.
- [ ] Menús, textos largos, fuente ampliada y orientación no ocultan controles.
- [ ] Plataformas, arquitecturas, Assistant y desinstalación se presentan según los datos disponibles; no se inventa un QR con información incompleta.

## Servidor y archivos

- [ ] Generar un enlace desde Gestión y verificar las opciones y el grupo de destino al abrirlo en otro equipo.
- [ ] Instalar el agente en un equipo de prueba y comprobar su incorporación al grupo correcto.
- [ ] Descargar un instalador, elegir destino y volver desde el selector externo sin perder el diálogo ni su archivo pendiente.
- [ ] Verificar que el destino no está vacío y coincide con el contenido descargado. No confundir esta comprobación con la firma del instalador.
- [ ] Cancelar descarga/guardado y simular errores de proveedor: no debe anunciarse éxito ni conservarse deliberadamente un parcial como válido; registrar fallos de limpieza.
- [ ] Revisar servidor, dominio y grupo de los comandos Linux/BSD. Instalación y desinstalación incluyen la alternativa directa con argumentos iguales en ambas invocaciones.
- [ ] Escanear el QR en un móvil de prueba y comprobar servidor y grupo.
- [ ] Probar cuenta con permisos limitados, información ausente, cambio de cuenta/servidor, pérdida de conexión y cierre del diálogo: sin enlaces de otra sesión ni reenvíos automáticos.
- [ ] Distinguir errores de conexión, autenticación, espera de identidad y respuesta del enlace. No presentar un resultado desconocido como éxito.

No es necesario ejecutar desinstalaciones para la revisión visual. Toda instalación o desinstalación funcional requiere un equipo de prueba elegido expresamente.

## Registro e integración

Conservar informes reales de unitarios, lint e instrumentación cuando correspondan, sus totales, el resultado visual y las plataformas probadas. No marcar comprobaciones como realizadas basándose solo en que un APK se publicó. No adjuntar cookies, enlaces completos, QR de conexión ni configuración sensible; ocultarlos en capturas.

No fusionar automáticamente en dev ni main. Tras cambios de código, ejecutar las comprobaciones pertinentes. Los cambios exclusivamente documentales requieren revisión documental y de enlaces, no compilación ni emulador.

Guía: [Instalación de agentes e invitaciones](../es/agent-installation.md).
