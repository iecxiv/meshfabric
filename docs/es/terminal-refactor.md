# Refactorización del terminal: fase 1

[English](../terminal-refactor.md) | Español

## Alcance

Mantener las versiones incluidas de xterm.js y FitAddon y la API del puente Android existente. Sustituir JavaScript en línea por un runtime y un coordinador de redimensionado comprobable independientemente. No cambiar escritorio, archivos, autenticación ni enrutamiento de sockets.

- Un único coordinador con ejecución 120 ms después del último cambio de contenedor, orientación, área visual o fuente.
- Medir mediante FitAddon.proposeDimensions(), sin campos privados de xterm ni métricas de caracteres estimadas en el código de aplicación.
- Una geometría inválida u oculta nunca sustituye la última cuadrícula válida.
- Esperar a la escritura activa del parser antes de redimensionar.
- Serializar escrituras con el callback de finalización de xterm; preservar el búfer alternativo y la posición del historial del usuario.
- Restaurar el CSS nativo del área visible de xterm en vez de forzar scrollTop o desactivar dicha área.
- Conservar finales de línea del terminal (convertEol=false).
- Limitar la salida JavaScript pendiente a 2 Mi unidades UTF-16, incluida la escritura en curso. El desbordamiento muestra un error explícito en vez de corromper silenciosamente el flujo. No es contrapresión de extremo a extremo.
- Los errores de inicialización no envían la señal JavaScript de preparación.

## Pruebas automatizadas

Ejecutar desde la raíz con Node.js:

```bash
node app/src/test/js/terminal-runtime.test.js
```

Usan objetos simulados de terminal/puente. No validan renderizado xterm real, disposición WebView, IME Android ni PTY remoto.

## Trabajo pendiente conocido en esta fase

Este commit no completa la refactorización. TerminalScreen conserva la alternativa de preparación onPageFinished y la reproducción de historial sin procesar. No cambian tryEmit de SharedFlow en Kotlin, decodificación UTF-8 por paquete, propiedad WebView/sesión ni las tres variantes de mensajes remotos de tamaño. Investigar el protocolo de tamaño admitido por el agente MeshCentral antes de sustituirlas. El orden JavaScript no garantiza ausencia de pérdidas anteriores en la cadena.

## Matriz de aceptación en dispositivo, todavía no ejecutada

Probar shell y nano mostrando/ocultando teclado, ida/vuelta vertical/horizontal, cambios de fuente, navegación, salida rápida, selección/pegado y lectura de historial. Verificar `stty size` remoto frente a la cuadrícula mostrada. Una pantalla estática no debe reconectar. Inspeccionar barra de estado y cursor de nano tras cada redimensionado. Cubrir varias sesiones y pérdida de red sin asumir que un PTY antiguo sobrevive a otro túnel.
