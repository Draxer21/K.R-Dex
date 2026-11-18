# Corrección 0.56.1+26.2: sincronización del Ride Booker

El informe `crash-2026-11-17_18.54.54-server.txt` señala `Failed to hash dexrider:booker_shift=>1253591: Component of type dexrider:booker_shift is not encodable` durante la sincronización del inventario del jugador.

`BOOKER_SHIFT` tenía un StreamCodec de red pero carecía de Codec persistente. Minecraft 26.2 calcula los hashes de componentes mediante `TypedDataComponent.encodeValue`, que requiere este segundo codificador. Se añade `Codec.LONG`, conservando el identificador y el formato de red del componente.

La prueba existente `booker_morph` ahora crea y compara un `HashedStack` real usando HashOps y el registro del servidor; comprueba que una copia coincide y que cambiar el instante altera la comparación. Además verifica la codificación y lectura del valor 1253591 del informe. Las pruebas anteriores comprobaban la animación y el modo, pero no esta ruta de sincronización.

El nuevo paquete conserva los modelos de Dex y Nine y se entrega con versión distinta para identificar la corrección. Reemplaza el JAR anterior en la instalación; no instales ambas versiones juntas. No es necesario borrar el mundo ni el inventario para aplicar este cambio.

Validación: `build runGameTestServer --offline` terminó con `BUILD SUCCESSFUL`; pasaron las 40 pruebas, incluida la nueva comprobación del hash dentro de `booker_morph`. Registro: `build/correccion-booker-26.2.log`. No se ha reproducido toda la combinación de mods de la instalación del usuario.
