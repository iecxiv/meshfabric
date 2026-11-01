# Entrega de salida del terminal tras 32a5aaf

[English](../terminal-output-step.md) | Español

## Alcance

No cambian la programación del redimensionado, mensajes de tamaño PTY, negociación, codificación de entrada, atajos ni retención WebView. Es un paso de entrega sobre la fase 1 validada y el control de preparación.

Un registro por sesión almacena como máximo 1048576 unidades UTF-16, aproximadamente 2 MiB de caracteres más sobrecarga del runtime. Cada lectura tiene una época del flujo y un cursor absoluto. Las revisiones StateFlow solo despiertan al lector; agrupar notificaciones no omite por sí mismo salida almacenada. SharedFlow antiguo permanece por compatibilidad de API, pero la vista ya no lo consume.

Un WebView envía como máximo un lote de 16384 unidades hasta que el callback de xterm.write confirma su token. Terminar evaluateJavascript no equivale a terminar el parser. Las confirmaciones de vistas antiguas, incorrectas o duplicadas no avanzan el cursor. Las lecturas no dividen pares sustitutos UTF-16.

Si el registro acotado ya no contiene un intervalo necesario o se ha borrado el historial, la entrega se detiene de forma visible. No avanza silenciosamente ni desconecta/reinicia el shell remoto. El error pide desconectar y crear otra sesión si no puede restaurarse la continuidad. Es entrega acotada con detección de huecos, no contrapresión ilimitada ni de red.

## Pendiente en esta fase

- Retener emulador/WebView durante navegación y cambios de configuración.
- Instantáneas VT exactas: el historial en disco sigue siendo ANSI sin procesar y puede estar truncado. Este cambio no hace correcta la reproducción con otra geometría.
- Recuperación del proceso del renderizador y tiempo límite ACK consciente del primer plano. Un ACK ausente deja un lote pendiente; el registro sigue acotado y una reanudación posterior puede detectar un hueco.
- La decodificación incremental UTF-8 del transporte y los cambios de protocolo siguen excluidos deliberadamente tras la regresión anterior.

## Verificación

Catorce casos Kotlin/JUnit nuevos del pipeline, seis de preparación existentes y 21 JavaScript (14 existentes y siete nuevos). GitHub Actions debe superar Node, assembleDebug y testDebugUnitTest antes de publicar el commit funcional. Esto no sustituye pruebas físicas.

Comprobaciones en dispositivo: historial seguido de salida continua al abrir, navegación rápida de ida/vuelta, varias sesiones, salida rápida, teclado con nano, giro, fuente, lectura de historial y atajos. No afirmar entrega sin pérdidas tras la muerte del proceso ni al reconectar un PTY remoto.
