# Auditoría de traducciones y estado de revisión

[English](../../i18n/README.md) | Español

Ejecuta `python3 tools/i18n/audit.py` desde el repositorio. Se generan summary.json, resource-issues.json y candidates.csv en docs/i18n. Las declaraciones explícitas inglesas/españolas ausentes, recursos duplicados, diferencias de estructura de arrays o formatos incompatibles bloquean esta publicación de mantenimiento; no se rellenan con inglés sin traducir.

Se inventarían literales Kotlin/Java principales, JS/HTML no vendorizados y atributos XML de interfaz literales. Texto HTML, cadenas interpoladas/compuestas, accesibilidad, errores de ejecución, alias, traducciones idénticas y recursos excluidos requieren revisión manual. Los candidatos incluyen claves de protocolo, etiquetas de pruebas, nombres propios, datos técnicos y ayudantes ya bilingües; no se clasifican automáticamente como interfaz sin traducir. Nunca sustituir a ciegas comandos, identificadores ni datos del backend.

La paridad de recursos no acredita el significado ni una localización completa. El informe deja la cobertura global sin verificar. Revisa candidatos, ambos idiomas, fuentes grandes y accesibilidad en dispositivo. Los datos del usuario y la salida del servidor/agente no se traducen automáticamente. No se recogen portapapeles, credenciales, historiales ni tráfico en ejecución.

Los recursos de YubiKit aportados por la app también requieren revisión. Una respuesta completada de la llave no equivale a una sesión autenticada verificada. Los resultados de compilación, pruebas y lint corresponden al trabajo de publicación y sus artefactos, no a una validación visual global.

ONLINE y OFFLINE son etiquetas intencionadamente comunes en ambos idiomas, no traducciones pendientes. El CSV usa finales LF, entrecomilla todos los campos y representa los CR/LF internos mediante secuencias de escape.
