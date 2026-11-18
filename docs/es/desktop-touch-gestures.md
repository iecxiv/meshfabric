# Gestos táctiles del escritorio

[English](../desktop-touch-gestures.md) | Español

## Uso de los controles

Selecciona el modo izquierdo para apuntar normalmente. Toca para hacer clic izquierdo. Con zoom aplicado, desliza un dedo para mover la vista local sin hacer clic en el escritorio remoto. Toca dos veces rápidamente para hacer doble clic. Para arrastrar una ventana o seleccionar texto, toca y vuelve a apoyar el dedo cerca, manteniendo el segundo contacto mientras lo mueves; levántalo para soltar.

Pellizca con dos dedos para cambiar el zoom y mueve ambos para desplazar la vista. En modo derecho, tocar el escritorio envía un clic derecho. El modo Scroll usa movimientos verticales para desplazar el escritorio remoto. El botón flotante independiente de clic derecho prepara el siguiente toque sobre el escritorio como clic derecho. Una pulsación larga aislada no produce clic derecho. Con ratón físico, mantén el botón izquierdo para arrastrar; el botón derecho y la rueda conservan sus funciones habituales.

El botón de información de la barra flotante abre la guía integrada en español o inglés. El icono de información más pequeño y gris no reduce el diseño existente del IconButton.

## Coordinación de clics y límites

La ruta táctil del modo izquierdo retrasa el clic simple mientras considera un segundo toque y coordina el par de clics del doble toque. Los clics pendientes quedan vinculados a la conexión original y pueden cancelarse cuando cambia la sesión o el contexto de entrada. No es una confirmación de ejecución remota ni una garantía de doble clic fiable en cualquier equipo o red. La aplicación y el sistema operativo remotos siguen interpretando los clics recibidos.

## Validación

Compila y ejecuta las pruebas JUnit correspondientes, incluidas las 13 añadidas para el coordinador de clics. Su existencia no demuestra que se hayan ejecutado correctamente. Comprueba en dispositivos reales el toque simple, el doble toque, el arrastre con segundo toque y su liberación, el desplazamiento con un dedo con zoom, el zoom y movimiento con dos dedos, el asistente de clic derecho, la rotación y las transiciones de diálogos. Repite con latencia, pérdida de cobertura, desconexiones y reconexiones, verificando que la entrada antigua no se reproduzca en una sesión nueva.

El selector de pantallas y los cambios visuales propuestos de los controles izquierdo/derecho quedan fuera de estos cambios. Consulta [Sesiones de dispositivos](device-sessions.md) para la confirmación configurable de Desconectar todo.
