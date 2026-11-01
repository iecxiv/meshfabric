# Solo enrutamiento del desplazamiento táctil

[English](../terminal-touch-scroll.md) | Español

Con un puntero principal grueso, la pantalla renderizada deja pasar las pruebas de impacto al área nativa existente de xterm solo en el búfer normal y con mouseTrackingMode público igual a none. La clase se actualiza después de abrir y después de cada lote de salida procesado, y se elimina al fallar o liberar la vista. Si faltan las API de modo, el paso no se habilita. Los dispositivos de puntero fino y los modos alternativo/con informes de ratón conservan el comportamiento existente. No se añaden eventos sintéticos de rueda, física personalizada, cambios de sensibilidad, reenvío de entrada, cambios de foco/IME, redimensionado ni transporte.

Contrapartida: mientras está activo este paso móvil, no se conservan la selección directa ni los toques de enlaces sobre el lienzo. Para seleccionar texto usa el menú probado Seleccionar y copiar. Los diálogos nativos de copia/pegado están fuera del selector CSS y no cambian.

CI reutiliza xterm incluido real y la misma hoja de estilos de producción. Una prueba Android inyecta deslizamientos táctiles sobre texto y columnas vacías, exige que ambos desplacen al menos tres filas, comprueba que las pruebas de impacto alcancen el área visible y verifica la restauración del enrutamiento de ratón y búfer alternativo. También se ejecutan las pruebas existentes de eco/copia/pegado, Node y JVM. Sigue siendo necesaria la confirmación en dispositivo de suavidad percibida, inercia, toques/teclado y posición durante la llegada de salida.
