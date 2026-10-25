# Gestión nativa: primera integración

Conserva DeviceListScreen, tema, recursos y clientes nativos originales. Añade pestañas Equipos/Gestión/Actividad, selección por nombre, comandos shell/CMD/PowerShell, notas, notificaciones, apertura de URL, portapapeles, WoL, reinicio, apagado y suspensión. No muestra JSON como interfaz.

Los cambios requieren revisión y confirmación. Usa un canal de control auxiliar con la sesión existente y valida userinfo antes de habilitar operaciones. No modifica el login ni las opciones TLS existentes. No reenvía comandos tras cortes. Las respuestas sin correlación son información de la sesión, no éxito atribuido a un equipo.

Pendiente: usuarios/grupos/permisos, compartir, inventario avanzado, filtros/favoritos, exportación y YubiKey. No se ha compilado ni probado contra servidor real. No implementa aún todo Next. Probar en equipos no productivos.
