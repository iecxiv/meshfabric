# Reconexión manual del terminal: primer incremento

[English](../terminal-manual-reconnect.md) | Español

- Desconectar la pantalla del terminal cierra únicamente su transporte. El historial y el renderizador retenido siguen disponibles para lectura; el botón de cierre ya no abandona la pantalla.
- Reconectar crea un candidato independiente con un plazo total de 30 segundos, esperando a la conexión de control existente antes de abrir el túnel del terminal. No reintenta automáticamente el terminal.
- Los candidatos fallidos o cancelados no sustituyen el registro anterior. Una negociación correcta promueve una TerminalSession nueva; los renderizadores archivados siguen asociados a sus objetos de sesión originales.
- El selector de historiales anteriores es de solo lectura. La salida actual y la anterior nunca comparten un registro. La entrada se controla en Compose, el renderizador, JavaScript y el transporte de la sesión.
- Los registros permanecen en memoria durante la vida de este ViewModel. Este incremento no añade persistencia de historiales, serialización del terminal ni garantías de continuidad del proceso remoto. Los ajustes existentes de persistencia del búfer actual no cambian.
- Los WebView retenidos siguen usando la caché LRU existente de cuatro renderizadores. Tras una expulsión, la reproducción se hace con el mejor esfuerzo a partir de la salida retenida, no como restauración exacta de aplicaciones de pantalla alternativa.
- No cambian el protocolo de negociación, la versión de xterm ni el algoritmo de redimensionado.

## Validación

CI debe ejecutar todas las pruebas unitarias debug y assembleDebug antes de publicar el commit funcional. Las pruebas de los controles de entrada no son pruebas integrales de sockets ni de interfaz Android.

En un dispositivo: desconecta Wi-Fi; comprueba desplazamiento/copia y bloqueo del teclado/pegado/teclas; provoca un fallo y cancela una reconexión; reconecta y ejecuta clear; revisa el historial anterior; sal de la pantalla y vuelve; repite reconexiones; abre nano y alterna teclado/orientación para comprobar regresiones de redimensionado. Confirma que desconectar voluntariamente nunca inicia una reconexión automática del terminal. Comprueba la persistencia activada/desactivada después de reiniciar la app.

Corrección de reproducción del historial: read() conserva su detección estricta original de huecos, incluido un cursor de arranque nulo. Solo la reproducción inicial de una vista desconectada usa explícitamente readHistory(). Las pruebas de continuidad existentes no cambian; otras pruebas cubren la reproducción explícita y los cursores obsoletos.
