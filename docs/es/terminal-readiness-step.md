# Terminal: preparación tras la fase 1 validada

[English](../terminal-readiness-step.md) | Español

Este paso no reintroduce la fase 2 rechazada del protocolo de transporte.

## Cambios

- Kotlin ya no inicializa el historial ni inicia el colector en directo 150 ms después de onPageFinished.
- Solo la señal onHtmlReady del runtime puede inicializar la vista.
- Un control de generación de página y la comprobación exacta de identidad WebView rechazan callbacks tardíos de vistas liberadas o reemplazadas.
- Las señales repetidas inicializan una página como máximo una vez.
- Liberar invalida el token de página sin cambiar la política existente de retención de sesiones.

## Sin cambios

Se mantienen el runtime JavaScript de fase 1, su coordinador de redimensionado de 120 ms, comportamiento Kotlin de relay/protocolo/UTF-8, atajos y diálogo de comandos.

## Limitaciones

Refuerza la inicialización, no completa la refactorización del ciclo de vida. Quedan sin resolver retención WebView, recuperación del proceso del renderizador, reproducción ANSI sin procesar y traspaso atómico entre historial y flujo en directo. No corrige la saturación SharedFlow. No afirma salida sin pérdidas ni estabilidad de nano más allá de la validación de fase 1 del usuario.

## Validación

Se aportan seis casos JUnit para el control de generación. Requieren ejecutarse en el proyecto Android; el entorno generador no tiene compilador Kotlin/JVM. El aplicador Python se prueba por separado con repositorios Git locales desechables. Esas comprobaciones no compilan Kotlin ni ejercitan Android WebView.

En dispositivo: abrir en frío, navegar fuera/dentro rápidamente, repetir con nano, mostrar/ocultar teclado, girar, cambiar fuente y comprobar atajos/diálogo de comandos. Este paso no introduce reconexión forzada del terminal.
