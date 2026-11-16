# FIDO2/WebAuthn y alcance de validación — 1.3.8

[English](../fido/README.md) | [Mi cuenta](profile-native.md) | [README](../../README_ES.md)

Describe fido-support con versionName 1.3.8. No es un anuncio de etiqueta ni de release.

## Comportamiento implementado

- Login FIDO2/WebAuthn y alta, consulta y baja nativos de llaves de seguridad de la cuenta.
- YubiKit permanece fijado en 3.3.0. La WebView se restringe al servidor seleccionado, no abre un navegador externo y cancela ante errores TLS.
- El login utiliza hwtoken/hwstate vinculados al intento y el campo token vacío para recuperar las credenciales de sesión en el servidor. El éxito del autenticador, un HTTP correcto o las cookies no prueban por sí solos la autenticación: el canal de control debe verificar la identidad de la cuenta.
- El alta requiere acuse del servidor y relectura de la lista. La baja no se confirma por enviar la petición: la llave debe dejar de aparecer en la lista recibida.
- Los textos y resultados de YubiKit tienen recursos españoles y declaraciones predeterminadas/inglesas. Completar la operación con la llave se distingue del acceso final a la cuenta.
- El selector de llaves propuesto para la baja fue cancelado y no se incluye. Se mantiene la entrada actual del índice/ID.

## Comprobaciones físicas comunicadas por el usuario

Fecha: 16 de noviembre de 2026. La llave identificada en este trabajo es una YubiKey 5 NFC.

| Operación | Evidencia y alcance |
| --- | --- |
| Alta de llave | El usuario confirma éxito; el alta por NFC ya se había confirmado anteriormente. |
| Baja de llave | El usuario confirma éxito. No se aportó una matriz completa de baja por transporte. |
| Login FIDO | El usuario confirma éxito y especifica que también inicia sesión por USB. |
| Copiar/pegar | El usuario confirma funcionamiento tras el trabajo de portapapeles de terminal/escritorio. No se registró una matriz exacta de dispositivos, hosts y casos límite. |

Son comprobaciones físicas comunicadas por el usuario, no pruebas de hardware independientes del asistente. El login por USB no demuestra alta/baja por USB. No certifican otros modelos, todas las versiones Android/agente/servidor, cada combinación USB/NFC ni el aislamiento multiserver. Sigue abierta la matriz completa de operación por transporte.

## Configuración de compilación y ejecución

La fuente de verdad es app/build.gradle.kts.

| Ajuste | Valor |
| --- | --- |
| versionName | 1.3.8 |
| versionCode | 1, sin cambios |
| minSdk | 26; mínimo Android 8.0 |
| targetSdk | 35, sin cambios |
| compileSdk | API 37, API menor 2; platforms;android-37.2 |
| Build Tools | 37.0.0 |
| Java fuente/destino | 17 |
| JDK de validación | 21 |
| Android Command-line Tools validadas | 23.0 |

compileSdk selecciona las API de compilación, no el mínimo ni el objetivo de la app. Esta actualización documental y de versionName no cambia los SDK ni YubiKit.

## Automatización y límites pendientes

Se han incorporado las migraciones de constructores Locale, reglas Compose v2 y API del portapapeles. El usuario comunicó resultados correctos de sus workflows. Esto no afirma que queden cero warnings: hay que revisar los informes recientes por separado.

Los cambios se validan con compilación, todos los unitarios JVM, lint y toda la instrumentación Android, sin pruebas omitidas ni aserciones debilitadas. Esta actualización ejecuta también los scripts JavaScript independientes. No hace falta subir APK: el desarrollador compila localmente e instala por ADB.

La validación física sigue separada de la automatizada. Las listas detalladas de ciclo de vida/disposición del portapapeles deben revisarse al modificar esos flujos. Una CI correcta no acredita compatibilidad FIDO universal ni validación multiserver. Esta actualización no realiza merges, etiquetas ni releases.
