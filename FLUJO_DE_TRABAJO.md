# Flujo de trabajo de Kamen Rider Dex

Actualización del 17 de noviembre de 2026: por indicación del autor, esta copia se adapta a Minecraft **26.2**. `gradle.properties` fija NeoForge 26.2.0.88, GeckoLib 5.5.6, Curios 16.0.0+26.2 y PAL 1.2.8+mc.26.2. Las menciones a 26.1.2 siguientes describen el estado inicial del análisis.

Documento preparado el 16 de noviembre de 2026 para continuar el proyecto 0.56.0. Describe el flujo de desarrollo a partir del código local y de las referencias del autor. No acredita una compilación ni una prueba dentro de Minecraft.

El proyecto ya implementa transformaciones, cartas, modelos, aliados, combate, misiones y estructuras. Cada trabajo nuevo debe partir del sistema existente y tener un resultado concreto: por ejemplo, modificar el casco de Mirage, ajustar una habilidad de Cipher o incorporar a Monoma.

La configuración de esta copia es Minecraft 26.1.2, NeoForge 26.1.2.122, Java 25, GeckoLib 5.5.2 y Curios 15.0.0+26.1.2. Player Animation Library 1.2.8+mc.26.1 está configurada como opcional para las poses del jugador. Las versiones se leen en `gradle.properties`, `build.gradle` y la plantilla de metadatos; se conserva esta combinación como punto de partida.

`LEEME.md` menciona una variante para 26.2 en la rama `mc-26.2`. Esta carpeta no contiene un repositorio Git, por lo que aquí no se ha inspeccionado esa rama ni su estado actual. La adaptación a 26.2 se hará sobre su código real cuando esté disponible, con su propia compilación y pruebas.

Las rutas de este documento son relativas a la raíz del proyecto para que la guía se pueda trasladar junto con él.

Primera ficha de diseño: [Dex / Yusuke, traje completo y Driver](diseno/dex/FICHA_DEX.md), con una lámina conceptual basada en la referencia visual del autor.

Segunda ficha: [Nine / Izuku, propuesta más cercana a Kuuga](diseno/nine/FICHA_NINE.md), con traje completo y Driver circular.

Tercera ficha: [Forge / Momo con apariencia de Build RabbitTank](diseno/forge/FICHA_FORGE_02.md), actualizada por petición del autor con Driver de dos botellas verticales.

Las referencias creativas quedan fijadas por la tabla del autor:

| Rider | Personaje | Inspiración | Modelo base existente |
| --- | --- | --- | --- |
| Dex | Yusuke | Decade; Driver tipo Neo Decadriver | `dex_armor.geo.json` |
| Nine | Izuku | Kuuga | `rider_nine.geo.json` |
| Forge | Momo | Build | `rider_forge.geo.json` |
| Spore | Kinoko | Gaim | `rider_spore.geo.json` |
| Mirage | Toru | Femme, de Ryuki | `rider_mirage.geo.json` |
| Proto | Mei | Fourze | `rider_proto.geo.json` |
| Cipher | Takumi | Faiz | `rider_cipher.geo.json` |
| Warden | Isamu | Delta | `rider_warden.geo.json` |
| Psyga | Nagisa | Psyga | `rider_psyga.geo.json` |
| Orga | Genzo | Orga | `rider_orga.geo.json` |
| Kaixa | Tetsuya | Kaixa | `rider_kaixa.geo.json` |

La familia Kagami completa la línea de Faiz. Takumi era Faiz e Isamu era Delta; adoptaron Cipher y Warden al romper con la familia. Genzo, Nagisa y Tetsuya conservan los nombres originales. Los trajes, cascos y visores son diseños propios del fanfic; las referencias orientan su desarrollo según las decisiones del autor.

Monoma copia el Quirk de Yusuke y se convierte en un Rider tipo Diend. Es contenido definido para el fanfic cuya implementación no se encontró en esta copia. Form Ride: Sky se inspira en Skyrider; Sky sí está implementada. Esta inspiración no cambia por sí sola sus habilidades o su apariencia actual.

Para ubicar un cambio, se utiliza esta estructura real:

| Trabajo | Archivos o carpetas principales |
| --- | --- |
| Geometría de armaduras, Riders civiles y Kaijin | `src/main/resources/assets/dexrider/geckolib/models/item/armor/` |
| Texturas de esos modelos y máscaras de brillo | `src/main/resources/assets/dexrider/textures/item/armor/` |
| Animaciones de los NPC Rider | `src/main/resources/assets/dexrider/geckolib/animations/entity/rider_echo.animation.json` |
| Recurso de animaciones de la armadura | `src/main/resources/assets/dexrider/geckolib/animations/item/armor/dex_armor.animation.json` |
| Poses del jugador, con Player Animation Library | `src/main/resources/assets/dexrider/player_animations/dex_poses.json` |
| Modelos y animaciones de motos | `src/main/resources/assets/dexrider/geckolib/models/entity/` y `geckolib/animations/entity/rider_bike.animation.json` |
| Modelos de objetos y sus definiciones | `src/main/resources/assets/dexrider/models/item/` y `src/main/resources/assets/dexrider/items/` |
| Identidades, transformaciones y poderes | `src/main/java/com/draxer/dexrider/rider/` |
| IA, formas civiles, formas de NPC y comportamiento | `src/main/java/com/draxer/dexrider/entity/` |
| Renderizado, editor del traje, visor y pantallas | `src/main/java/com/draxer/dexrider/client/` |
| Registros y componentes persistentes | `src/main/java/com/draxer/dexrider/registry/` |
| Sincronización entre cliente y servidor | `src/main/java/com/draxer/dexrider/network/` |
| Recetas, logros, botín y generación del mundo | `src/main/resources/data/dexrider/` |
| Traducciones | `src/main/resources/assets/dexrider/lang/` |

El namespace actual es `dexrider` y el paquete Java es `com.draxer.dexrider`. Las rutas genéricas del texto de referencia, como `assets/kamenrider/geo/`, no corresponden a este proyecto.

Cada cambio visual se trabaja en esta secuencia:

1. **Definir el resultado.** Escribir qué Rider y forma se editan, qué partes cambian, qué referencia se usa y cómo debe verse el resultado. Usar las capturas y modelos actuales como referencia del diseño propio. Una petición concreta ya autoriza preparar ese cambio; las preguntas se reservan para decisiones creativas que falten.
2. **Identificar los archivos que lo producen.** Revisar modelo, textura, brillo, capas y posibles generadores antes de modificar nada. Registrar las variantes que comparten esas piezas y conservar una copia de los archivos afectados mientras esta carpeta carezca de historial Git.
3. **Editar la geometría.** Partir del `.geo.json` existente. Se puede editar su estructura directamente o abrirlo con las herramientas de GeckoLib de Blockbench. Si se guarda un proyecto `.bbmodel`, conservarlo como archivo de edición y exportar el `.geo.json` usado por el juego. En esta copia no se encontraron archivos `.bbmodel` ni `.blend`.
4. **Ajustar UV, texturas y brillo.** Comprobar dimensiones declaradas, zonas de textura y máscaras `_glowmask.png`. Dex tiene capas teñibles y piezas seleccionables: un cambio en sus texturas debe funcionar con la personalización del traje.
5. **Preparar vistas comparables.** Frente, tres cuartos, perfil y espalda con la textura aplicada. Revisar silueta, visor, uniones, piezas superpuestas, UV y brillo. El script de previsualización ayuda en esta etapa; el resultado dentro del juego se revisa después.
6. **Propagar el cambio a las variantes necesarias.** Aplicar las mismas decisiones de diseño a las formas afectadas y conservar sus rasgos propios. Revisar las animaciones si se han alterado articulaciones o piezas móviles.
7. **Comprobar la integración.** Ver el traje en el jugador y en el NPC correspondiente, tanto quieto como caminando, atacando y transformándose. Para Dex, comprobar también el editor del traje y los estados afectados.
8. **Entregar el resultado.** Incluir archivos editables, recursos exportados, vistas del resultado y un registro breve de las comprobaciones realizadas. Si se compiló el mod, identificar el `.jar` y su versión de Minecraft.

Los modelos de los Riders se comparten entre el jugador y los NPC: `RiderArmorModel.java` selecciona los recursos de armadura y `RiderEchoRenderer.java` reutiliza esos modelos para ecos y aliados. Los huesos `armorHead`, `armorBody`, brazos, piernas y botas, los huesos `biped…` y el hueso `weapon` cumplen funciones en el renderizado o las animaciones. Renombrarlos o mover sus pivotes exige revisar esos consumidores. También hay nombres de piezas utilizados por el editor y por `DexArmorRenderer.java`, como los cuernos y las alas de Sky.

La revisión de variantes depende del Rider:

| Rider o sistema | Variantes que revisar al cambiar piezas comunes |
| --- | --- |
| Dex | Base, personalización, Violent/Heroic, Form Ride, Upgrade y los modelos Complete afectados |
| Nine | Base, Dragon, Pegasus, Titan y sus versiones Rising; las formas adicionales son exclusivas del NPC Izuku |
| Forge | Las nueve combinaciones de mitades; las combinaciones adicionales son exclusivas del NPC Momo |
| Cipher | Base, Accel, Blaster y, si el cambio corresponde al personaje civil, Takumi |
| Warden, Psyga, Orga y Kaixa | Traje y forma civil si se altera una característica compartida del personaje |
| Spore, Mirage y Proto | Modelo base, uso como armadura, uso como NPC y arma incorporada cuando corresponda |

Los generadores son parte del proceso de edición. Algunas salidas se regeneran por completo y otras reciben piezas adicionales. Para conservar una modificación, hay que actualizar el generador responsable o dejar documentado que esa parte pasa a editarse manualmente; volver a ejecutar un generador puede sobrescribirla.

| Herramienta existente | Uso y archivos que afecta |
| --- | --- |
| `tools/geo_preview.py` | Lee geometría y texturas de armadura para producir una imagen de revisión; utiliza NumPy y Pillow |
| `tools/dex_reference_model.py` | Genera el traje base de Dex y sus tres representaciones del Driver desde la nueva referencia; conserva el respaldo original y comprueba UV, huesos y máscaras del visor |
| `tools/rider_relief.py` | Genera relieves y zonas de textura de Nine, Forge, Cipher, Warden, Spore, Mirage y Proto, incluidas variantes; registra información en `tools/rider_relief.json` |
| `tools/suit_parts.py` | Genera piezas y capas de Dex/Upgrade, accesorios de Form Ride y variantes del visor |
| `tools/ride_booker_model.py` | Genera textura, seis estados del modelo del Ride Booker y su definición de objeto |
| `tools/moveset_anims.py` | Actualiza las animaciones de combate del jugador en `dex_poses.json` |
| `tools/boss_skins.py` | Copia geometrías base y recolorea texturas y brillo de los tres jefes de arena |
| `tools/rare_cards.py` | Genera las texturas de las tres cartas raras de la arena |
| `tools/mountain_house.py` | Genera la estructura de la casa de la montaña |

Se ejecuta únicamente el generador que corresponde al cambio. El archivo `rider_relief.json` acompaña los resultados de `rider_relief.py`; debe mantenerse junto a ellos. Su opción `--clean` elimina el relieve generado, por lo que no es un paso habitual de exportación.

Ejemplo de previsualización, desde la raíz del proyecto y con NumPy y Pillow disponibles en el Python utilizado:

```powershell
python tools/geo_preview.py rider_mirage --out=mirage_revision.png --views=front,q,side,back
```

El analizador de argumentos actual utiliza `--out=...` y `--views=...`. Esta previsualización no reproduce todos los efectos del renderer de Minecraft, las capas personalizadas de Dex ni las animaciones en movimiento.

Para cambios de funciones se utiliza una ficha breve que acompaña la implementación:

| Campo | Qué debe contener |
| --- | --- |
| Objetivo | Un comportamiento observable: qué hace el jugador o NPC y qué sucede |
| Referencia creativa | Rider, personaje, forma y relación con el fanfic |
| Estado actual | Archivos y comportamiento que ya existen |
| Cambio | Modelo, habilidad, objeto, IA, interfaz o combinación necesaria |
| Obtención y uso | Cómo se obtiene, cómo se activa, coste y enfriamiento cuando corresponda |
| Persistencia | Qué debe mantenerse al guardar, morir o reconectarse |
| Multijugador | Qué resuelve el servidor y qué deben ver los otros jugadores |
| Entrega | Recursos, código, vistas y comprobaciones aplicables |

Una nueva habilidad aprovecha los sistemas de transformación y combate actuales. Un Rider nuevo, como Monoma, necesita definir primero su identidad propia, su transformación y su forma de obtener poderes; después se incorporan los registros, recursos, poderes, entidad si corresponde, traducciones y sincronización. Su inspiración en Diend no determina automáticamente todas esas decisiones.

Las comprobaciones se eligen según el cambio. Para un documento basta revisar su contenido y las rutas. Para un modelo se revisan los recursos y la apariencia en sus usos afectados. Para lógica de juego se compila y se ejercitan los comportamientos modificados, aprovechando los GameTests existentes cuando los cubran. Una entrega de versión requiere compilación, pruebas aplicables y revisión en cliente y servidor.

Las tareas ya configuradas son:

| Comando desde la raíz | Propósito |
| --- | --- |
| `.\gradlew.bat compileJava processResources` | Comprobar compilación Java y procesamiento de recursos |
| `.\gradlew.bat runClient` | Revisar el mod en el cliente de desarrollo |
| `.\gradlew.bat runGameTestServer` | Ejecutar los GameTests registrados dentro de Minecraft |
| `.\gradlew.bat runServer` | Comprobar carga y funcionamiento en servidor dedicado |
| `.\gradlew.bat runShots` | Ejecutar las escenas de captura de `ShotDirector` en el mundo `dextest` |
| `.\gradlew.bat build` | Generar el artefacto de esta configuración en `build/libs/` |

`runShots` presupone un mundo `dextest` preparado y modifica la escena para tomar fotos; se usa con un mundo de pruebas dedicado. Las pruebas de dos jugadores requieren observar ambos clientes para comprobar sincronización de trajes, transformaciones, habilidades y aliados. Un `.jar` generado por `build` no acredita por sí solo esa revisión.

El workflow existente en `.github/workflows/build.yml` compila, inicia servidor, ejecuta GameTests y toma capturas. Los pasos de servidor, GameTests y capturas tienen `continue-on-error`, de modo que también deben consultarse sus registros. En un `push` a `main` o `mc-26.2`, el workflow puede publicar automáticamente un release si encuentra el mensaje de éxito requerido en los GameTests. Las notas automáticas dicen que Player Animation Library es obligatoria, aunque los metadatos y la guía la marcan como opcional; ese texto deberá corregirse al preparar una publicación.

Al retomar el repositorio Git original, cada cambio se trabajará en una rama propia con sus recursos y generadores juntos. Se completará primero en la configuración disponible de 26.1.2; después se trasladará el mismo cambio a la variante real de 26.2 y se verificará allí. Cada versión de Minecraft tendrá su artefacto identificado.

El siguiente encargo puede comenzar directamente con el Rider o sistema que indique el autor. Para una modificación visual, el primer resultado será una vista del modelo actual y de las piezas afectadas; después se entregará el modelo editado con sus texturas y las comprobaciones correspondientes. La creación de esta guía no ha modificado modelos, código de juego ni configuraciones.
