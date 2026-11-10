# Aceptación de Agregar agente e Invitar

## Versión que se debe revisar

- Rama: `add-device-invite`.
- Código de la app: `2f50fd4a9940fe6c5ba8f54addcec52d0af9c347`.
- Workflow de suite completa: `e4ca5dd102d45a2adf284bfc5ba4fa676a294700`.
- APK previsto de esa ejecución: artefacto `meshfabric-add-device-invite-full-debug-<run_id>`. El identificador de ejecución concreto no se ha registrado todavía aquí.

El propietario informó de que la suite completa terminó correctamente. Eso no acredita por sí solo una revisión visual ni una instalación o invitación comprobada contra el servidor real. Este documento no marca esas comprobaciones como realizadas.

## Revisión visual pendiente

- [ ] Agregar agente e Invitar aparecen en el grupo de dispositivos con agente; el resto de acciones mantiene su disposición.
- [ ] Invitar aparece en el menú del dispositivo y abre el mismo diálogo de instalación para su grupo, no un diálogo de acceso temporal.
- [ ] Invitar muestra los seis vencimientos y los seis tipos de agente solicitados. Un mes se identifica como 30 días cuando se selecciona.
- [ ] El tipo de instalación tiene las tres opciones; desaparece para Asistente MeshCentral y Android MeshAgent. Una selección anterior no reaparece al volver a Windows.
- [ ] Cada menú respeta la anchura de su botón; textos largos, desplazamiento, orientación y teclado no ocultan los controles.
- [ ] Agregar agente presenta las plataformas, arquitecturas, modos de Assistant y opciones de desinstalación de la referencia.
- [ ] Android muestra un QR legible cuando llegan todos los datos de configuración; si faltan, muestra un aviso en lugar de un QR inventado.

## Comprobación funcional pendiente con MeshCentral 1.3.1

- [ ] Generar un enlace desde un grupo de prueba y comprobar sus opciones. Abrirlo en otro navegador o equipo y verificar que ofrece la instalación para ese grupo.
- [ ] Generar otro enlace desde un dispositivo del grupo de prueba y verificar que el destino vuelve a ser ese grupo, no el equipo existente. No se exige que ambos enlaces tengan el mismo token.
- [ ] Con un equipo de prueba, comprobar que el agente instalado mediante la invitación se incorpora al grupo correcto.
- [ ] Descargar al menos un instalador desde Agregar agente y comprobar el selector de guardado, el archivo recibido y el grupo incorporado en el instalador. Ampliar la comprobación a las plataformas disponibles que se vayan a usar.
- [ ] Revisar el comando Linux/BSD: servidor, prefijo de dominio, identificador del grupo y modo instalación/desinstalación. Probar la instalación solo en un equipo de prueba apropiado.
- [ ] Escanear el QR con MeshAgent en un móvil de prueba y comprobar servidor y grupo de destino.
- [ ] Comprobar con una cuenta de permisos limitados que un grupo no autorizado o datos incompletos no permiten generar una invitación ni descargar como otra cuenta.
- [ ] Cambiar de cuenta o servidor, interrumpir la conexión y cerrar el diálogo durante una petición: no deben aparecer enlaces de la sesión anterior ni reenvíos automáticos.
- [ ] Cancelar una descarga o el guardado y comprobar que no se presenta un archivo parcial como una descarga correcta.

No es necesario ejecutar desinstalaciones para completar la revisión visual. Si se prueban funcionalmente, deben realizarse únicamente sobre un equipo de prueba elegido expresamente.

## Evidencia que falta registrar

- Identificador de la ejecución de la suite completa y su `summary.json`.
- Totales reales de unitarios e instrumentación del informe, sin confundirlos con los 39 métodos de regresión específicamente exigidos.
- Resultado de la revisión visual del APK.
- Resultado de las comprobaciones contra el servidor real y plataformas probadas.

Al comunicar resultados, basta con indicar cada comprobación y cualquier error. No adjuntar cookies, enlaces completos de invitación, QR de conexión ni archivos de configuración del agente. Si se comparten capturas, ocultar esos datos.

## Integración

No fusionar automáticamente en `dev` ni en `main`. Si se modifica código tras la validación, ejecutar las comprobaciones pertinentes para ese cambio. Los cambios exclusivamente documentales no requieren volver a compilar ni ejecutar el emulador.
