# Inventario de localización de interfaz: no es una localización completada

[English](../../i18n/README.md) | Español

El nombre de versión es 1.1.1; el código permanece en 1. Cuatro módulos revisados de recursos del terminal, con inglés predeterminado, son ahora recursos explícitos values-en. Las claves traducibles predeterminadas tienen cobertura inglesa explícita y se comprueban los marcadores de posición. Esto verifica la estructura de recursos, no el significado de las traducciones.

Sigue habiendo texto de interfaz escrito directamente en código, especialmente estados de gestión, etiquetas de operaciones, errores y etiquetas del terminal. [candidates.csv](../../i18n/candidates.csv) inventaría literales de Kotlin/Java principales, JS/HTML no vendorizados y atributos XML de interfaz literales. Es heurístico: puede incluir comentarios, claves de protocolo, datos técnicos y cadenas ajenas a la interfaz; el texto interpolado/compuesto y los errores de ejecución/servidor requieren revisión manual. El contenido/contexto de candidatos está acotado. Nunca sustituir a ciegas cadenas de protocolo, comandos o datos del backend. No se leen portapapeles en ejecución, historiales del terminal, credenciales ni contenido de red.

Este inventario no demuestra que se hayan localizado o traducido todas las frases. all_ui_externalized es deliberadamente false. La migración restante necesita correspondencias de recursos revisadas y resolución segura respecto al ciclo de vida fuera de composables. No se introduce detección automática de idioma ni español como alternativa al inglés.

La compilación y comprobaciones JVM/Node condicionan la publicación. Lint se recoge por separado y su resultado se registra debajo; un fallo no se considera limpio. Deben revisarse disposición y accesibilidad en inglés/español sobre dispositivo. No cambian foco, teclado, teclas especiales, desplazamiento ni red.

Resultado de lint: correcto.
