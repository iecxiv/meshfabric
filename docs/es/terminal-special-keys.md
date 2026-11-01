# Teclas especiales y comodidad del historial

[English](../terminal-special-keys.md) | Español

Fila inicial: Esc, Tab, Ctrl+C, Intro y Retroceso. Se pueden expandir las teclas de navegación/edición, el selector Ctrl A–Z, Ctrl+Z/X/O/W y el control fijo de última salida. Los botones reservan al menos 48 dp por dimensión; las filas se desplazan horizontalmente. El selector Ctrl envía un atajo elegido, no activa un modificador persistente del IME. Sin conexión, la entrada se desactiva, pero el historial retenido sano sigue disponible.

Se usa la API pública de entrada de xterm incluido y el puente onData/nativo existente. Flechas/Inicio/Fin respetan applicationCursorKeysMode. Se conserva el byte 8 para Retroceso y CR para Intro. No hay repetición al mantener pulsado ni reintentos. RePág/AvPág/Inicio/Fin son teclas remotas. Última salida hace scrollToBottom local solo en el búfer normal, sin enviar entrada; el búfer alternativo muestra información localizada. El foco del desplegable usa el control de superposiciones existente; se permite el envío deliberado del atajo mientras se cierra. No cambian CSS, física/sensibilidad de desplazamiento, seguimiento de salida, máquina de foco, flags IME, editor de comandos ni versión (1.1.1/código 1).

CI: comprobaciones de codificación, pruebas Compose de tamaño/expansión/desactivación/envío único, secuencias normales/de aplicación con xterm real y pruebas de historial sin entrada; regresiones existentes de foco, eco, copia, pegado y tacto. Quedan pruebas en dispositivo de escalado de fuente, espacio horizontal/IME, editores remotos y disposición percibida. Enviar no confirma ejecución remota.
