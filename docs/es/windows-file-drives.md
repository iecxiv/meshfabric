# Navegación por unidades Windows

[English](../windows-file-drives.md) | Español

Los nodos Windows identificados por su descripción del sistema solicitan el selector virtual con ls y path "". La interfaz mantiene "/" como ubicación lógica del selector; Linux sigue solicitando "/" sin cambios. Solo aparecen las unidades realmente devueltas por el agente.

Las respuestas usan t=1 y n/name, con metadatos dt/s/f opcionales. Se aceptan variantes normales y recovery-core y los alias dir/files/list. Las rutas son absolutas, con separadores Windows normalizados, y nunca se prefijan con la raíz virtual. Unidades, discos extraíbles y unidades ópticas tienen filas solo de navegación; se omiten capacidades desconocidas. Subir desde una unidad, punto de montaje de volumen en caché o recurso UNC vuelve al selector. El botón Unidades de cabecera vuelve directamente.

Se conservan identificadores de solicitudes de navegación, épocas de socket, solicitudes en cola y reconexión existentes. Las respuestas antiguas de raíz no sobrescriben una selección de carpeta posterior. Se bloquean creación, subida y pegado en el selector, y las acciones de modificación sobre raíces de unidades. No cambian las operaciones de carpetas normales ni las transferencias en segundo plano.

Las pruebas JVM cubren rutas solicitadas, metadatos modernos/de recuperación, tamaños desconocidos, entradas inseguras/duplicadas, volúmenes montados, raíces UNC, rutas Linux y respuestas obsoletas. Se requiere todavía validación con agentes Windows reales, cambios de unidades, permisos, unidades de red desconectadas o mapeadas, reconexión y transferencias dentro de unidades. Los nodos sin descripción Windows no se consideran Windows. Los volúmenes de inventario no se usan para inventar unidades navegables.
