# Objetivo: Minecraft 26.2

Se adopta la combinación que ya documentaba el proyecto para 26.2: NeoForge 26.2.0.88, GeckoLib 5.5.6, Curios 16.0.0+26.2 y Player Animation Library 1.2.8+mc.26.2 (opcional), con Java 25. Gradle resolvió esos artefactos y preparó Minecraft 26.2 correctamente.

La salida se identifica como `dexrider-0.56.0+26.2.jar` para distinguirla del paquete anterior para 26.1.2.

Cambios de API verificados contra los fuentes descargados de Minecraft 26.2:

- Las pantallas se consultan mediante `Minecraft.gui.screen()` y se abren con `Minecraft.gui.setScreen(...)`.
- El HUD se consulta mediante `gui.hud.isHidden()`. La herramienta de capturas usa `toggle()` solo cuando el estado actual difiere del solicitado.
- El objetivo de renderizado para capturas se obtiene de `gameRenderer.mainRenderTarget()`.
- El centro de un bloque se calcula con `Vec3.atCenterOf(pos)`.
- Las constantes de entidades vanilla se obtienen de `EntityTypes`; los tipos y constructores siguen usando `EntityType`.
- Los logros Chimera y Daishocker usan el subpredicado `minecraft:entity_type` de 26.2.

El build Java y el empaquetado terminaron con `BUILD SUCCESSFUL`. La tarea Gradle `test` no tiene pruebas JUnit; las pruebas funcionales del proyecto se ejecutan aparte mediante `runGameTestServer`.

Validación del 17 de noviembre de 2026: `build runGameTestServer` ejecutó las 40 pruebas obligatorias y todas pasaron. Registro: `build/pruebas-26.2.log`. Se comprobó además que el JAR declara exactamente Minecraft 26.2, contiene los recursos finales de Dex y las ocho variantes de Nine, y sus JSON son sintácticamente válidos.

Los archivos de configuración y las fuentes Java anteriores están respaldados en esta carpeta. Los respaldos de modelos se encuentran en las carpetas de diseño de Dex y Nine.

La compilación no sustituye la revisión dentro del juego: hay que comprobar el arranque con las dependencias de 26.2, transformación en Dex y Nine, pantallas, HUD, armas y cambios de forma del NPC Izuku.
