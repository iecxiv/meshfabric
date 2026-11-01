# Renderizador del terminal retenido tras 40479b0

[English](../terminal-renderer-retention.md) | Español

## Alcance

Cada sesión puede conservar WebView, instancia xterm, cursor de salida, ACK pendiente, desplazamiento/búfer alternativo y tamaño de fuente durante la navegación. Compose crea solo un host FrameLayout. Separar no detiene la carga ni reinicia la entrega; volver a adjuntar no reproduce historial. No cambian la programación del redimensionado, el protocolo PTY ni el runtime JavaScript.

El puente de la página captura el renderizador/sesión, no un composable ya liberado. Los permisos de asociación impiden que una liberación tardía del host anterior quite la vista de un host nuevo. La salida sigue controlada por el registro acotado y las confirmaciones del parser. No se garantiza salida oculta sin pérdidas si JavaScript se ralentiza tanto que se supera el registro.

## Propiedad y memoria

El ViewModel mantiene en el hilo principal un almacén LRU con límite normal de cuatro renderizadores. Los adjuntos nunca se expulsan; una transición puede superar temporalmente el límite hasta liberar un host. Solo se expulsan los separados. Expulsar destruye el WebView sin desconectar el PTY remoto. Reabrir usa la reproducción del registro existente, con sus limitaciones para pantallas alternativas y flujos truncados. No es retención ilimitada.

MutableContextWrapper usa el contexto del host actual solo mientras está adjunto y vuelve al contexto de aplicación al separarse/destruirse. No se retienen intencionadamente Activities antiguas tras liberarlas. Detener sesión, cerrar sesión de cuenta y limpiar el ViewModel liberan los renderizadores. El renderizador elimina su padre, cancela el trabajo de salida, invalida tokens, elimina el puente y destruye el WebView exactamente una vez. Nunca se llama a pauseTimers porque afectaría a otros WebView.

Perder el proceso del renderizador libera la vista inutilizable y muestra un error localizado, sin reconectar automáticamente el shell remoto. Reabrir crea otro renderizador, no restaura una instantánea VT. Se bloquea la navegación incrustada para mantener el puente JavaScript en la página local del terminal.

## Verificación

Once pruebas JVM nuevas cubren reutilización, aislamiento, reemplazo, eliminación, limpieza, LRU, protección de vistas adjuntas y liberaciones obsoletas. CI debe ejecutar también JavaScript, pipeline y preparación existentes y compilar el APK debug antes de publicar. Estas pruebas unitarias no ejercitan un WebView ni un IME reales.

Sigue siendo necesaria la aceptación en dispositivo: ejecutar nano, ir a Dispositivos y volver repetidamente, comprobar que no se reproduce historial ni se abre otro shell, cambiar fuente y volver, alternar dos terminales, girar y mostrar/ocultar teclado, desconectar una sesión, cerrar sesión de cuenta y probar un quinto terminal para observar el límite de caché. La retención ante cambios de configuración depende de que sobreviva el ámbito existente del ViewModel; la muerte del proceso no conserva el WebView.
