# Kamen Rider Dex — mod para Minecraft 26.1.2 (NeoForge)

Dex Driver, cartas, transformación, armas Armed Ride con sus Final Attack Ride
y escaneo de cartas como en el fanfic.

## Cómo abrirlo y probarlo

1. Necesitas **JDK 25** (26.1 lo exige). En IntelliJ: *File → Project Structure → SDK → 25*.
2. Abre la carpeta `dexmod` en IntelliJ como proyecto Gradle y espera a que sincronice
   (la primera vez descarga Minecraft y NeoForge 26.1.2.122; tarda unos minutos).
3. Ejecuta la tarea `runClient` (panel de Gradle → `Tasks → mod development → runClient`)
   o desde terminal: `./gradlew runClient` (Windows: `gradlew.bat runClient`).
4. Para el `.jar` final: `./gradlew build` → queda en `build/libs/dexrider-<versión>.jar`.
5. Para jugarlo en tu Minecraft normal, en la carpeta `mods` van tres jar: **dexrider**, **GeckoLib 5.5.x**
   y **Curios 15.x** (versiones para NeoForge 26.1.2). En el entorno de desarrollo se descargan solos.

## Cómo se juega

### Transformación
1. Pestaña creativa **Kamen Rider Dex**: Driver y todas las cartas.
2. Con el Driver en el inventario, clic derecho con **Kamen Ride: Dex** → la carta queda cargada.
3. Pulsa **V** → *KAMEN RIDE: DEX!* Tu armadura se guarda dentro del peto de Dex.
   - Dex Form base: Fuerza II, Velocidad I, Salto I, Resistencia I.
   - El traje (con cualquier identidad o forma): armadura por encima de netherite (24, dureza 3.5), **+5 corazones**,
     recibes un 25 % menos de daño, tus golpes hacen un 25 % más y, tras 5 s sin recibir golpes, recuperas medio corazón por segundo.
   - La armadura no se puede quitar ni cae al morir.
4. Pulsa **V** otra vez → destransformación; recuperas tu armadura y las armas se desmaterializan.

### Armed Ride (transformado)
Clic derecho con una carta Armed Ride: el arma aparece en esa mano y la carta vuelve al inventario.
Solo hay un arma activa a la vez; sacar otra desmaterializa la anterior.

| Carta | Arma | Uso especial | Final Attack Ride |
|---|---|---|---|
| Ride Booker | espada / pistola | **Shift + clic derecho** cambia de modo; en pistola, clic derecho dispara | Booker Slash (onda de corte) / Booker Blast (rayo de 40 bloques) |
| Shock Baton | porra | los golpes ralentizan | **Volt Crush**: rayos sobre todos en 6 bloques |
| Blast Shot | pistola | clic derecho dispara | **Full Burst**: cinco ráfagas en abanico |
| Sword | espada | la de más daño | **Cross Slash**: dos cortes en X |
| Volt Chain | cadena | clic derecho atrae al primer objetivo | **Volt Lash**: latigazo que atraviesa a todos, explosión y noqueo (los jefes solo se ralentizan) |
| Power Knuckle | guantelete | golpes lentos con mucho empuje | **Double Impact**: embestida y dos puñetazos, el segundo lanza al objetivo |

**Armas de los Riders** (también son Armed Ride). Se **escanean** viendo a su dueño pelear con ella: un Rider
perdido o un amigo con el arma fuera. Aquí sí cuenta aunque el golpe sea contra ti, porque es un combate real.

| Carta | Arma | Clic derecho | Final Attack Ride |
|---|---|---|---|
| Cipher Edge (2 muestras) | espada de Cipher | corte de código: avance corto y corte | **Code Edge Break**: marca y atraviesa |
| Prism Edge (2) | hoja espejada de Mirage | destello que ciega en cono | **Prism Cut**: seis cortes de colores |
| Module Driver (2) | herramienta de Proto | dispara el módulo cargado; **Shift** cambia: taladro / gancho / soplete | **Module Overload**: los tres seguidos |
| Forge Lance (2) | lanza creada de Forge | estocada con avance | **Lance Storm**: llueven seis lanzas |
| Warden Baton (2) | porra de la Comisión | contención a un objetivo; los golpes inmovilizan | **Warden Arrest**: encadena y golpea tres veces |
| Orga Halberd (3) | alabarda de Orga | barrido en arco que empuja | **Orga Judgement**: corte de 12 bloques |
| Spore Rod (2) | bastón de Spore (no corta: traba y empuja) | estallido de esporas; **Shift**: clavarla, la red revela todo en 48 bloques | **Bloom Burst**: duerme a todo en el anillo, sin daño |

**Final Attack Ride**: si llevas un arma activa, usa el finisher de esa arma (el arma pasa a tu mano).
Sin arma, hace la Rider Kick de siempre.

**Attack Ride: Slash** → Fuerza III por 10 s.

### Form Ride (transformado)
Clic derecho con una carta Form Ride cambia de forma; la misma carta otra vez vuelve a la Dex Form base.
Solo una forma activa a la vez. La armadura cambia de color con cada forma (`form_rides_preview.png`).
Cada forma tiene **Nivel 1 a 3**: se sube re-escaneando su fuente (el doble de muestras para Nv.2, el cuádruple para Nv.3).

| Forma | Color | Pasivas | Debilidad / detalle |
|---|---|---|---|
| Speed | azul | Velocidad III→V, prisa, salto | — |
| Gas | violeta grisáceo | los proyectiles lo atraviesan, sin daño de caída, cae lento, algo más rápido; **si está en llamas gana Fuerza** | las explosiones le hacen la mitad pero lo lanzan el doble |
| Terra | marrón | resistencia I→III, minería rápida, golpes que levantan | Nv.1 algo lento |
| Sky | negro violáceo | **vuela** en la oscuridad (Nv.1), con poca luz (Nv.2), siempre (Nv.3); visión nocturna | forma nocturna |
| Frost | celeste | congela el agua al caminar, golpes que congelan y ralentizan, inmune al frío | el fuego le hace ×1,5 |
| Cinder | naranja | inmune al fuego y a la lava, golpes que queman | **el agua y la lluvia** lo debilitan (Debilidad + Lentitud) |
| Half-Cold Half-Hot | mitad blanco hielo / mitad rojo fuego | inmune al fuego y al frío, congela el agua, golpes que queman **y** ralentizan | — |

#### Habilidades activas: tecla **R** (se cambia en Controles)
- **R** usa la habilidad del nivel más alto que tengas de la forma activa.
- **Shift + R** usa la habilidad básica (Nv.1).
- Cada habilidad tiene enfriamiento; baja un 20 % en Nv.2 y un 40 % en Nv.3.
- Los bloques que crean (hielo, piedra, escaleras) son **temporales**: desaparecen solos, solo se ponen en aire/agua/hierba y nunca encima de nadie, y vuelve lo que había.

| Forma | Nv.1 | Nv.2 | Nv.3 |
|---|---|---|---|
| Frost | Muro de hielo (8 s) | Rayo congelante de 12 bloques, atraviesa | Glaciar: cono de picos de hielo que dañan, congelan y levantan |
| Cinder | Bola de fuego (quema) | Lanzallamas 1 s | Erupción: anillo de fuego de radio 6 |
| Gas | Cuerpo de humo 3 s: **intangible**, pero no puede atacar | + nube cegadora alrededor | 6 s y corre más |
| Terra | Pilar de piedra que lanza al objetivo | Rampa de escaleras de piedra | Terremoto: radio 7, a los que están en el suelo |
| Sky | Salto de sombra: avanza 8/10/12 bloques en línea recta y daña lo que atraviesa | ← | ← |
| Speed | Ráfaga: 2 s a velocidad extrema arrollando enemigos | ← | + estela cortante |
| Half-Cold Half-Hot | **Choque térmico**: rayo de hielo y medio segundo después explosión de fuego en los congelados (salpica alrededor). Shift + R: muro de hielo | más daño | más daño |

Con una forma activa, el daño también se multiplica por el escalado de la familia Form Ride.

### Violent Emotion y Heroic Emotion (solo como Dex)
El Driver responde a lo que siente quien lo lleva. Se despiertan solas:
- **Violent Emotion**: al bajar del 30 % de vida o recibir de golpe el 40 % de tu vida en 3 s. 20 s con Fuerza III,
  Velocidad II y golpes ×1.3; el traje se oscurece a carmesí y morado con un cristal violeta en el pecho.
  Cobra su precio: medio corazón cada 2 s (nunca te deja por debajo de 2 corazones).
- **Heroic Emotion**: cuando un amigo Rider tuyo o un aldeano cercano recibe un golpe de un monstruo. 20 s con
  Regeneración II, Resistencia II, un 20 % menos de daño recibido y golpes ×1.15; traje azul y blanco con cristal blanco.
- En las dos, las cartas del Ride Booker responden solas: elegir una con **Z / X** ya la activa, sin insertarla.
- No tienen recarga: en cuanto una se apaga, puede volver a despertar.

### HUD del Rider
Transformado, a la izquierda de la barra de objetos: la carta elegida del Ride Booker (con el color del mazo),
la recarga de **R**, los segundos de Accel (parpadea al final) y el tiempo que le queda a la Emotion activa.

### Configuración
`config/dexrider-common.toml`: probabilidad de aparición de los Riders perdidos, multiplicador de daño de los Riders
del juego y de Dex, si los finales fallados rompen bloques, activar las Emotion y mostrar el HUD.

### Ride Booker: el estuche de cartas
El Ride Booker guarda tus cartas. Vive en el **Dex Driver**, así que va contigo (en el inventario o en el cinturón).
Pulsa **G** para abrirlo (configurable). Tiene **5 mazos** de 18 cartas, como en el fanfic: *Rescate, Multitudes,
1 vs 1, Persecución* y *Todo salió mal*. Solo admite cartas (y la carta de Complete Form).
- **Guardar**: mueves cartas como en un cofre (Shift + clic las pasa entre el Booker y tu inventario).
- **Insertar**: haces clic en una carta y **se activa al instante sin sacarla del Booker** (inserción directa).
  Un Armed Ride aparece en tu mano principal y lo que tenías ahí pasa al inventario.

**Uso rápido en combate (sin abrir el Booker)**: **Z / X** eligen la carta anterior / siguiente del mazo
(con **Shift**, cambian de mazo) y **Alt izquierdo** inserta la elegida. La carta elegida aparece encima de la
barra de objetos. Así cambias de arma o de carta en plena pelea. Todas las teclas se pueden cambiar en Controles.

### Kamen Ride de otros Riders (transformado como Dex)
Clic derecho con la carta Kamen Ride de un Rider estando transformado → Dex **se convierte en ese Rider**
(armadura 3D propia, poderes y Final Attack Ride propios). La misma carta otra vez, o *Kamen Ride: Dex*, vuelve a Dex.
Las Form Ride de Dex solo funcionan en la forma de Dex. Las habilidades usan la misma tecla **R** (Shift + R = la segunda).

| Rider | Pasivas | R | Shift + R | Final Attack Ride |
|---|---|---|---|---|
| Nine | Fuerza II, Velocidad I, Salto II, Resistencia I | **Smash**: puñetazo con onda de choque (el retroceso te ralentiza) | **Air Force**: disparo de aire de 24 bloques que empuja | **Rising Smash**: salto y picada con relámpago verde |
| Cipher | Velocidad II, Fuerza I, Prisa, Salto I | **Code Marker**: fija al objetivo en un cono de código 3 s | **Code Shot**: ráfaga de 3 disparos | **Code Strike**: patada-taladro que atraviesa al marcado (si no hay, marca primero) |
| Spore | Velocidad I, Visión nocturna; los hostiles a 24 bloques brillan | **Red de esporas**: revela todo en 48 bloques y te dice cuántos hay | **Nube de esporas**: debilita, ralentiza y marea | **Spore Bloom**: anillo de setas gigantes temporales y estallido |
| Forge | Resistencia I, Fuerza I, Prisa I | **Forge Cannon**: crea un cañón, tres disparos | **Muro de creación**: pared de hierro temporal 8 s | **Forge Barrage**: bombardeo de seis proyectiles creados |
| Mirage | Velocidad II, Salto I, Visión nocturna | **Destello**: ciega y ralentiza en cono | **Dobles**: dos duplicados sólidos que pelean 10 s | **Prism Refraction**: seis rayos de luz convergen en el objetivo |
| Proto | Prisa II, Salto I, Velocidad I | **Módulo propulsor**: impulso hacia donde miras | **Módulo gancho**: atrae al objetivo | **Plasma Cutter**: soplete de plasma que atraviesa todo |
| Warden | Velocidad III, Fuerza I, Salto I | **Contención**: inmoviliza todo lo que hay en un cono 3 s | **Embestida**: 8 bloques en línea recta golpeando | **Warden Sentence**: tres embestidas instantáneas contra hasta tres objetivos |
| Psyga | **vuela** (mochila de vuelo), Velocidad I, Salto II | **Picada**: en el aire cae sobre el objetivo; en el suelo, despega | **Ráfaga de plumas**: cinco disparos en abanico | **Psyga Skyfall**: sube y llueven ocho impactos sobre la zona |
| Orga | Fuerza II, Resistencia II | **Golpe demoledor**: un golpe que lanza lejos | **Leer los tonos**: 5 s esquivando todo golpe cuerpo a cuerpo | **Orga Judgement**: la alabarda se despliega en un corte de 12 bloques |

Los nueve están completos (`kamen_rides_preview.png`, `forge_mirage_proto_preview.png`, `familia_kagami_preview.png`).

### Formas propias de Momo e Izuku (solo ellos)
Las formas de Forge y de Nine son **exclusivas de Momo e Izuku**: las usan la Forge y el Nine del juego (perdidos o amigos).
Cuando tú usas *Kamen Ride: Forge* o *Kamen Ride: Nine*, tienes su forma base.

**Forge: mitades intercambiables (como Build)** (`forge_formas_preview.png`). Momo cambia de mitades durante la pelea y lo dice por el chat.
- Derecha: **Engranaje** (cañón de tres disparos) · **Cañón** (disparo pesado que lanza lejos) · **Escudo** (absorción y rechazo).
- Izquierda: **Carbono** · **Magnesio** (bengala que ciega y marca) · **Fibra** (telarañas sobre el objetivo).
- **Sintonía**: Engranaje + Carbono (base), **Cañón + Magnesio = FORGE FLARE**, **Escudo + Fibra = FORGE BASTIÓN**:
  pega un 25 % más y usa las dos mitades a la vez. Las otras seis combinaciones son formas de prueba.

**Nine: las formas de Izuku** (`nine_formas_preview.png`). Elige la forma según la pelea:
| Forma | Cuándo la usa | Cómo pelea | Final |
|---|---|---|---|
| **Nine** (verde) | normal | Smash con onda de choque | Rider Kick |
| **Dragon** (azul) | el objetivo está arriba o volando | muy rápido, salta altísimo; barrido con el **Dragon Rod** | patada en picada |
| **Pegasus** (plateado) | el objetivo está lejos | disparos de precisión con el **Pegasus Bow** que marcan al objetivo | **disparo** de precisión (puede fallar) |
| **Titan** (morado) | está muy herido | lento y muy resistente; tajos pesados con las **Titan Blades** | patada en picada |

**Rising (One For All)**: cuando Izuku está herido (o a veces justo antes del final) activa Full Cowl y pasa a la
forma **Rising** de la que tenga puesta (acentos dorados, armas más grandes) durante **30 segundos**; luego descansa un minuto.
En Rising pega un 50 % más, es más rápido y fuerte, y sus **finales hacen explosiones mucho más grandes**.
Si un final en Rising **falla**, el golpe abre un **cráter que rompe bloques** (respeta la regla `mobGriefing` y no rompe
bloques muy duros como obsidiana o bedrock). Ojo: el Nine amigo también lo hace.

### Cómo se hablan entre ellos
Los Riders se llaman por su nombre, con el trato que se tienen: Nine le dice «Yaoyorozu-san» a Forge y ella «Midoriya-san»;
Mirage le dice «Yaomomo»; Proto llama a Cipher «Señor Driver Modular»; Psyga les dice «Takumi-nii» e «Isamu-nii» a sus
hermanos y «papá» a Genzo; Cipher y Warden le dicen «Padre». Los que se conocen bien tienen **conversaciones propias**
(Nine y Forge, Mirage y Nine, Spore y Mirage, Proto con todos, los hermanos Kagami, Genzo con sus hijos...), y al pedir
ayuda en pánico también usan el nombre del otro.

### Cipher: Accel y Blaster (equiparse algo)
Como en el fanfic, cada cambio de Cipher necesita su equipo (`cipher_formas_preview.png`):
- **Accel (reloj)**: siendo *Kamen Ride: Cipher*, clic derecho con el reloj → **10 s de velocidad absoluta**:
  todo alrededor va lentísimo, tus golpes pegan ×1.5, el peto se abre y el visor y las trazas pasan a rojo.
  Regla de Takumi, "siete y vuelves": aviso a los 7 s; a los 10 el cuerpo cae (lento y débil 5 s). Recarga de 60 s.
  Final Attack Ride en Accel: **Accel Strike** (golpea casi a la vez hasta a seis enemigos).
- **Blaster (arma)**: llévala en la **mano principal** siendo Cipher → forma de combate total (Fuerza III,
  Resistencia II, ×1.4 de daño; blindaje negro con trazas rojas, generador a la espalda). Clic derecho: disparo de 40 bloques;
  Shift + clic derecho: tajo amplio. Final Attack Ride en Blaster: **Blaster Overdrive** (haz enorme que explota).
- **Cómo se consiguen**: el **Accel** te lo presta Takumi si lo vences después de que lo usara (o, si ya es tu amigo,
  tras usarlo peleando a tu lado). El **Blaster** lo guarda Genzo: cuando **Takumi y Genzo** son tus amigos, Genzo te lo entrega.
- El Takumi del juego también los usa: activa el Accel cuando está herido (una vez por pelea), y el Takumi amigo
  pelea en Blaster si Genzo también es tu amigo.

### Los Drivers de cada Rider
Al llegar a Minecraft, los chicos de U.A. ya no tenían la energía del Rider System de Yusuke para sostener sus trajes:
esa energía **se combinó con cada uno y les generó un Driver a su medida**, inspirado en la idea del cinturón de su
serie pero con forma propia. Todos guardan un detalle **magenta**: la energía de Dex, que sigue buscando al Driver
que los creó (por eso todos buscan a Dex). La familia Kagami tiene su propio Rider System, así que sus Drivers son suyos
(`drivers_preview.png`).
| Rider | Driver |
|---|---|
| Nine | cinturón orgánico con una piedra en rombo (del color de la forma), garras como raíces y venas de relámpago |
| Forge | Driver de laboratorio: dos cápsulas de materia acostadas y una ruedita que las mezcla |
| Spore | hebilla con forma de sombrero de seta cerrado y una pequeña hoja que lo abre |
| Mirage | espejo ovalado y biselado con el reflejo azul → violeta → dorado |
| Proto | carcasa llena de puertos, botones, un módulo enchufado, cables y una pantallita |
| Cipher | zócalo modular con un cartucho de código (rojo en Accel y Blaster) y piezas en la cadera |
| Warden | placa de la Comisión con lente ámbar, barra de luz y funda lateral |
| Psyga | hebilla blanca con dos plumas en V y lente celeste |
| Orga | el más antiguo: pesado, negro y oro, con remaches y lente ámbar vertical |

Además, todos tienen **bodysuit** con musculatura (silueta femenina en Forge, Spore, Mirage, Proto y Psyga; Forge bicolor),
piezas 3D en el casco (mandíbula, protector de nuca, mejillas; visor saliente en Cipher), **armas con más detalle**
y **animaciones propias**: cada uno tiene su pose de espera (Nine en guardia, Warden de brazos cruzados, Psyga con las
manos en la cintura, Orga apoyado en la alabarda, Proto con las manos en un módulo...), guardia con el arma fuera,
patada en el aire durante la Rider Kick y retroceso al recibir golpes.

### La historia: los Riders perdidos
Los Riders del mundo de Yusuke **llegaron a este mundo por accidente**. Todos siguen la señal del Driver de Dex,
pero no saben si el Dex de aquí es el de verdad, así que **pelean para comprobarlo**.
Sus trajes les avisan que **no aguantarían la atmósfera** de este mundo: no pueden destransformarse.
Los OC de la familia Kagami sí pueden (su Rider System se lo permite): **Takumi (Cipher), Isamu (Warden),
Nagisa (Psyga) y Genzo (Orga)** andan sin el traje y se transforman cuando empieza una pelea (`familia_civil_preview.png`).

**Hablan por el chat** (se oye a 40 bloques): cuando andan perdidos, al verte, al pelear, cuando están heridos,
entre ellos cuando están cerca, al hacerse amigos, al invocarlos...

**Riders perdidos** (aparecen de noche, raros). Pelean como Riders de verdad: ataque especial, **arma** que sacan
y guardan por fases (Cipher Edge, Prism Edge, Module Driver, Forge Lance) y **Rider Kick** (saltan, una fila de cartas
apunta hacia ti y caen en picada).
- A veces **dos ya se encontraron** y viajan juntos. Si atacas a uno, **el otro acude a ayudarlo** (y los dos entran en pánico).
- **Nadie muere**: si los vence alguien que no es Dex, se retiran.
- Si los vences **como Dex transformado**, se convencen de que eres tú: te dan su **carta Kamen Ride** y se quedan como **amigos**.
  Tras unos segundos te cuentan lo de su traje.
- Los que ya son tus amigos no vuelven a aparecer perdidos cerca de ti.

| Rider | Dónde aparece | Ataque especial |
|---|---|---|
| Nine | llanuras, prados, bosques | salta y golpea con onda de choque |
| Cipher | sabana, badlands, desierto | cono de código y embestida |
| Spore | campos de champiñones, bosque oscuro, pantano | nube de esporas |
| Forge | bosque de cerezos, bosque de flores, abedules | disparo de cañón creado |
| Mirage | playas, llanuras nevadas, picos de hielo | destello cegador |
| Proto | colinas, picos pedregosos, cuevas de goteo | gancho o propulsor |
| Warden | taiga, llanuras, sabana | contención y embestida |
| Psyga | prados, picos, bosque de cerezos | sube y cae en picada |
| Orga | arboledas y laderas nevadas, bosques de abetos | golpe al suelo con la alabarda |

Para probarlos: `/summon dexrider:echo_nine` (o `echo_cipher`, `echo_spore`, `echo_forge`, `echo_mirage`, `echo_proto`,
`echo_warden`, `echo_psyga`, `echo_orga`).

### Amigos Rider
- **Hacen su vida**: viven alrededor de su casa (donde los venciste), la patrullan, pelean contra los monstruos
  que se acerquen y se curan con el tiempo. Aparecen cuando pasas cerca de su casa. Charlan entre ellos.
- **Invocarlos**: tecla **B** (pantalla con forma de K-Touch: tocas el blasón del Rider; el de Dex abre la Complete Form) o **teclado numérico 1-9** (en orden de lectura)
  (mismo orden: 1 arriba y en sentido horario). Clic derecho sobre tu amigo también lo llama o lo despide.
- Invocado, viene a tu lado y **pelea contigo como Rider** contra lo que te ataca o lo que atacas.
  Dura **90 s**, luego vuelve a casa y descansa **2 min**. Máximo **tres** a la vez.
- Si cae en combate no muere: se retira agotado y descansa 5 min.
- **Shift + clic derecho** sobre tu amigo: "tu casa ahora es aquí" (sirve para juntarlos a todos).
- Ni tú le haces daño a tus amigos ni ellos a ti.

### El portal a casa
Todos buscan a Dex: cuando **todos los Riders perdidos que existen ya son tus amigos** y están **juntos a tu lado**
(a 20 bloques o menos, invocados o viviendo cerca; muda sus casas al mismo sitio con Shift + clic derecho) y estás
transformado, su señal y la del Driver **abren un portal**:
1. cada uno dice algo al verse todos juntos;
2. se abre el portal;
3. uno a uno se despiden, **te dejan su moto como carta Vehicle Ride** y cruzan.

Después siguen siendo tus amigos: **si los llamas (B / teclado numérico), cruzan un portal** para pelear contigo y
se vuelven por otro al terminar. Hay que reunir a **los nueve**.

### K-Touch y Complete Form
- Al reunir **nueve Kamen Ride** (Dex + ocho), el Driver forja el **K-Touch** y la **carta de Complete Form**.
  La carta sale incompleta: falta un blasón.
- Con la **décima** la carta se completa.
- Transformado como Dex, clic derecho con el K-Touch (con la carta en el inventario) → pantalla con nueve pads en
  anillo y el de Dex en el centro. **Sin uso parcial:** hay que pulsar los diez.
- **Complete Form**: armadura carmesí y oro con las cartas de los nueve en hombreras y antebrazo, seis cuernos en corona,
  el K-Touch en el centro del cinturón y el Driver en la cadera derecha.
  - Fuerza III, Resistencia II, Velocidad I, inmune al fuego.
  - Daño ×(1 + 0,2 por cada Kamen Ride): con diez, **×3**, encima del escalado normal.
  - **R**: Card Cannon (rayo magenta desde la pila de cartas del antebrazo).
  - Agota: consume hambre rápido y, sin comida, la forma se cae (quedas lento y débil un rato).
  - Clic derecho con el K-Touch otra vez → vuelves a Dex.
- Pendiente para las próximas tandas: invocar la proyección de cada Rider pulsando su blasón.
- Para probar en creativo: usa las nueve cartas Kamen Ride (clic derecho) y el Driver forja el K-Touch.

### Vehicle Ride: motos (transformado)
La carta invoca la moto y te monta en ella: W acelera, A/D giran con la cámara, S frena, Shift para bajarte.
Solo la puede montar quien la invocó. Es invulnerable, sube bloques de una altura, las ruedas giran al moverse
y desaparece si nadie la usa en 30 s o cuando te destransformas. **R mientras conduces** = habilidad de la moto
(`motos_preview.png`).

| Moto | De quién | Habilidad (R) |
|---|---|---|
| Dex Machine | Dex (rehecha: faros con los ojos verdes, X en el tanque, aletas doradas del Driver) | **Card Boost**: acelera atravesando una fila de cartas que empuja a todo lo que toca |
| Nine Striker | Nine | **Smash Charge**: embestida con onda de choque |
| Cipher Runner | Cipher | **Code Lock**: fija con el cono de código a los enemigos de delante |
| Machine Forger | Forge | **Puente de creación**: puente de hierro temporal delante (cruza ríos y barrancos) |
| Myco Hurricane | Spore | su estela de esporas revela todo lo que pasa cerca · **R**: nube de esporas |
| Mirror Shooter | Mirage | **Espejismo**: tú y la moto invisibles; los monstruos pierden el rastro |
| Proto Racer | Proto | **Propulsor**: salto largo con planeo |
| Warden Interceptor | Warden | **Persecución**: acelera y ralentiza a los enemigos de delante |
| Psyga Glider | Psyga | **Vuelo**: despega y planea varios segundos |
| Orga Monarch | Orga | **Embestida real**: embestida demoledora |

### Dex Driver en el cinturón (Curios)
Con **Curios** instalado, el Driver tiene su ranura de **cinturón** (tecla G o el botón del inventario).
Puesto ahí se ve en tu cintura aunque no estés transformado, con el diseño de tu boceto, y funciona
igual que llevarlo en el inventario. Al transformarte se oculta porque la armadura ya lo trae.

### Final Attack Ride con explosión
La Rider Kick termina con una **explosión** (sonido, fuego, humo y onda expansiva). Es solo visual y
de daño a enemigos: no rompe bloques ni te hace daño a ti.

### Personalizar el traje
**Shift + clic derecho con el Dex Driver en la mano** abre la pantalla de personalización:

- **Colores** (pestañas Principal / Ojos / Acentos): 12 colores rápidos o barras R, G, B para cualquier color.
  Los acentos son el dorado (cuernos, X del pecho, guanteletes, botas). Los ojos brillan de noche en el color que elijas.
- **Cuernos**: rectos, curvos, cortos o ninguno.
- **Ojos**: con franjas o lisos. **Ride Booker** en la cadera: visible u oculto.
- **Original** vuelve al traje de Dex; **Guardar** lo deja en el Driver.

Se guarda en el Driver y se copia a la armadura al transformarte, así los demás jugadores ven tu versión.
Si cambias algo estando transformado, se ve al instante. Las **Form Ride mantienen su color** principal
mientras están activas; tus colores de ojos y acentos se conservan. `personalizacion_preview.png` muestra ejemplos.
El Driver (cinturón) no se tiñe: siempre conserva su diseño.

### Escaneo (cómo conseguir cartas en supervivencia)
Transformado y con el Driver encima, el Driver registra lo que **ve en uso real** a 20 bloques o
menos (con línea de visión y mirando hacia ahí). Lo que te hacen a ti no cuenta.

| Carta | Qué tienes que ver | Muestras |
|---|---|---|
| Sword | a alguien atacar con una espada | 1 |
| Blast Shot | a alguien acertar con arco o ballesta | 2 |
| Shock Baton | un ataque con tridente o un rayo que dé a alguien | 2 |
| Volt Chain | el láser de un guardián dando en el blanco | 3 |
| Power Knuckle | un golem de hierro, ravager o warden golpeando | 3 |

Cada muestra hace vibrar el Driver ("1/3"...). Hay un máximo de una muestra cada 2 s por carta.
Al completarse aparece la carta en tu inventario.

Cartas nuevas de la 0.4.0 (de mobs):

| Carta | Qué tienes que ver | Muestras |
|---|---|---|
| Form Ride: Speed | un caballo, burro, mula o camello galopando con jinete | 2 |
| Form Ride: Gas | un ghast atacando | 2 |
| Form Ride: Terra | un warden o un sniffer excavando / saliendo de la tierra | 2 |
| Form Ride: Sky | un phantom en picada | 2 |
| Form Ride: Frost | un stray o un golem de nieve atacando | 2 |
| Form Ride: Cinder | un blaze atacando | 2 |
| Form Ride: Half-Cold Half-Hot | **teniendo ya Frost y Cinder**: ver un ataque de fuego (blaze) y uno de hielo (stray / golem de nieve) con menos de 5 s de diferencia | 2 |
| Vehicle Ride: Dex Machine | alguien montado en un vehículo en marcha (bote, vagoneta, caballo, strider...) | 2 |

### Escalado del mazo
Igual que en el fanfic: **+15 % por cada carta de la familia**. Con arma activa se usa la familia
Armed Ride; sin arma, Kamen Ride. Clic derecho con el Driver en la mano muestra el mazo,
los multiplicadores y los escaneos a medias. Usar una carta también la anota en el mazo.

La tecla se cambia en *Opciones → Controles → Kamen Rider Dex*.

## Estructura

```
com.draxer.dexrider
├── DexRider.java              clase principal, paquete de red
├── registry/DexItems          Driver, cartas, armadura, armas, pestaña creativa
├── registry/DexComponents     loaded_card, deck (mazo), gun_mode (Ride Booker)
├── item/DexCard               lista de todas las cartas, familia y muestras de escaneo
├── item/DriverDeck            el mazo guardado en el Driver + escalado
├── item/DexDriverItem         el Driver (clic derecho: muestra el mazo)
├── item/RiderCardItem         uso de cada carta según su familia
├── item/DexWeaponItem         las seis armas y su clic derecho
├── rider/Transformation       transformar / destransformar
├── rider/ArmedRide            materializar armas y despachar el Final Attack Ride
├── rider/WeaponFinishers      los finishers de cada arma
├── rider/RiderKick            la Rider Kick con cartas holográficas
├── rider/Scanner              escaneo de combates cercanos
├── rider/Scheduler            golpes con retraso (finishers de varios golpes)
├── rider/FormRide             las Form Ride: pasivas, color, vuelo
├── rider/FormAbilities        habilidades de forma (tecla R)
├── rider/TempBlocks           bloques temporales de las habilidades
├── rider/KamenRide            identidades de Rider (Dex, Nine, Cipher, Spore... y Complete Form)
├── rider/RiderPowers          pasivas, habilidades y finishers de cada Rider
├── rider/KTouch               K-Touch y Complete Form
├── entity/RiderMob            base de los Riders del juego: especial, arma, Rider Kick, voz y forma civil
├── rider/RiderDialogue        los Riders hablan por el chat
├── vehicle/BikeKind           las siete motos
├── vehicle/BikeAbilities      habilidades de las motos (R conduciendo)
├── entity/RiderEchoEntity     ecos de Rider (dan la carta Kamen Ride)
├── entity/RiderAllyEntity     amigos Rider (viven en su casa, se invocan)
├── rider/AllyManager          invocar, despedir, descanso y casa de los amigos
├── client/SummonScreen        pantalla de invocar (tecla B)
├── client/KTouchScreen        pantalla de los diez pads
├── rider/FX                   partículas, rayos, cartas holográficas, daño
├── rider/DexEvents            ticks, daño (escalado + escaneo), muerte, limpieza
├── item/DexArmorItem          armadura con modelo 3D (GeckoLib)
├── registry/DexSounds         sonidos del mod
├── network/TransformPayload   paquete cliente → servidor al pulsar V
├── client/DexArmorRenderer    renderer 3D de la armadura (+ brillo de los ojos)
└── client/DexRiderClient      teclas V (transformación) y R (habilidad de forma)
```

## Armadura 3D (GeckoLib)

Desde la 0.3.0 la armadura es un **modelo 3D** con cuernos, hombreras, placa de la X, guanteletes,
cinturón con el Driver y el Ride Booker en la cadera. Usa **GeckoLib 5.5.2** (se descarga solo al
compilar; para jugar fuera del entorno de desarrollo hay que poner GeckoLib en la carpeta `mods`).
Los ojos brillan en la oscuridad (capa *glowmask*).

Archivos (en `src/main/resources/assets/dexrider/`):

| Archivo | Qué es |
|---|---|
| `geckolib/models/item/armor/dex_armor.geo.json` | el modelo |
| `textures/item/armor/dex_armor.png` | base fija (negro, plata, Driver) |
| `textures/item/armor/dex_armor_main/accent/eyes(_plain).png` | capas en gris que se tiñen con los colores elegidos |
| `geckolib/animations/item/armor/dex_armor.animation.json` | animaciones (vacío por ahora) |

`armadura_3d_preview.png` muestra cómo se ve de frente, 3/4, lado, espalda y de noche.

**Editarlo en Blockbench** (gratis, blockbench.net):
1. *File → Plugins* → instala **GeckoLib Animation Utils**.
2. *File → Open Model* → abre `dex_armor.geo.json`, y arrastra encima `dex_armor.png` como textura.
3. Mueve cubos, pinta, agrega piezas **solo dentro de los huesos `armor…`**
   (`armorHead`, `armorBody`, `armorRightArm`, `armorLeftArm`, `armorRightLeg`, `armorLeftLeg`,
   `armorRightBoot`, `armorLeftBoot`).
4. *File → Export → Export GeckoLib Model* encima del mismo archivo, y guarda la textura.

Las texturas de los ítems siguen en `textures/item/` (32×32). La textura plana antigua de
armadura (`textures/entity/equipment/`) queda como respaldo.

## Sonidos

Todos los efectos son **originales**, sintetizados para el mod (en `assets/dexrider/sounds/`):
insertar carta, transformación, destransformación, Armed Ride, carga y golpe del Final Attack Ride,
escaneo, disparo y cambio de modo del Ride Booker.

**Voces**: `voice_kamen_ride.ogg`, `voice_armed_ride.ogg`, `voice_attack_ride.ogg` y
`voice_final_attack_ride.ogg` vienen **en silencio**. Para ponerles voz basta con reemplazar cada
archivo por tu grabación con el mismo nombre (formato .ogg, **mono**). Suenan junto con el efecto
correspondiente.

Para agregar una carta nueva: un valor en `DexCard`, su textura `card_<id>.png`,
el modelo en `items/` y `models/item/`, y la traducción.

## Si algo no compila

Escrito siguiendo la documentación de NeoForge 26.1, pero sin compilar aquí. Si falla, suele
ser un nombre de API que cambió; pega el error y se arregla.

## Siguientes pasos

- Attack Ride del fanfic (Blast, Nitro Burst, Ground Wave, Gale, Razor Feather, Overclock).

## Historial de versiones

- **0.22.1** — Violent y Heroic Emotion sin recarga.
- **0.22.0** — Dex rehecho: bodysuit con musculatura, Driver en 3D (anillo, lente que sobresale, luces laterales y punto verde), Ride Booker en 3D con tapa, cartas asomando y broche, mandíbula, nuca y mejillas del casco en relieve, hombreras en capas y rodilleras (`dex_0_22_preview.png`). Violent Emotion y Heroic Emotion, HUD del Rider y archivo de configuración.
- **0.21.2** — Armas en alta resolución: el triple de píxeles por bloque (12 en vez de 4) y acabado propio: bisel con luz y sombra en cada pieza, brillo metálico en diagonal y desgaste fino (`armas_preview.png`).
- **0.21.1** — Armas rehechas en 3D con piezas que se afinan: hojas con filo, nervio y punta (Cipher Edge, Prism Edge en facetas, Titan Blades con guarda en cruz), punta de lanza con aletas (Forge Lance, del color de las mitades), alabarda con hoja de media luna, gancho y púa (Orga), Pegasus Bow con brazos curvos y cuerda, Dragon Rod con puntas en ambos extremos, Cipher Blaster con cañón, empuñadura inclinada y hoja inferior, Module Driver y bastón de Warden telescópico (`armas_preview.png`).
- **0.21.0** — Driver propio para cada Rider (los de U.A. nacen de la energía de Yusuke y guardan un detalle magenta; los Kagami tienen el suyo), piedra en rombo y cinturón más ancho para Nine. Bodysuit con musculatura para todos, piezas 3D en los cascos, armas con más detalle y animaciones nuevas (pose de espera por Rider, guardia con arma, patada en el aire, retroceso) (`riders_0_21_preview.png`, `drivers_preview.png`).
- **0.20.3** — Nine con bodysuit: traje de goma satinado con musculatura sombreada (pectorales, abdominales, bíceps, antebrazos, cuádriceps, gemelos, omóplatos), oblicuos y nervaduras verdes finas (doradas y brillantes en Rising). El peto ahora cubre solo el pecho y la protección del antebrazo es más corta, para que se vea el traje (`nine_bodysuit_preview.png`).
- **0.20.2** — Cuernos de Nine rehechos en 3D (segmentos con grosor que se afinan hacia la punta), naciendo del borde superior de los ojos, con una joya central. Nine: relámpago en zigzag; Dragon: aletas largas barridas; Pegasus: antenas-sensor con punta verde; Titan: placas anchas con placa frontal; Rising: más largos, punta extra y brillo (`nine_cuernos_preview.png`).
- **0.20.1** — Nine con cuernos dorados sobre los ojos; cada forma tiene su propia silueta (Nine en V con horquilla, Dragon largos y barridos hacia atrás, Pegasus cortos con púa central, Titan gruesos con placa frontal) y en Rising son más largos con una punta extra (`nine_cuernos_preview.png`).
- **0.20.0** — Cipher Accel y Cipher Blaster: el reloj Accel (10 s de velocidad absoluta, "siete y vuelves") y el arma Blaster (forma de combate total), con modelos propios, Final Attack Ride propios y cómo conseguirlos como en el fanfic (Takumi presta el Accel, Genzo entrega el Blaster). El Takumi del juego también los usa.
- **0.19.1** — Dex más fuerte: Dex Form base con Fuerza II y Resistencia I; el traje da armadura por encima de netherite, +5 corazones, −25 % de daño recibido, +25 % de daño y recuperación lenta fuera de combate (con cualquier identidad).
- **0.19.0** — K-Touch apaisado como el juguete pero con el casco de Dex (cuerpo negro con láminas rojas escalonadas, cuernos dorados en V, lente verde, muesca de la carta), con el blasón de Dex en un panel lateral a la derecha. Teclas rápidas del Ride Booker (Z / X / Shift / Alt) para cambiar de carta o de arma en combate. Los Riders perdidos invocados con /summon o huevo llegan solos (sin pareja).
- **0.18.0** — Pantalla K-Touch rehecha con texturas: carcasa perlada con bisel y franja dorada, láminas rojas con relieve, pantalla de cristal con reflejo, marcos redondeados que brillan según el estado, blasones con contorno y luz, tres luces de "en pelea" y K-TOUCH grabado. Nuevo **blasón de Dex** debajo de los nueve: abre la Complete Form (o la cierra si ya estás en ella) y muestra cuántas Kamen Ride tienes (x/10).
- **0.17.0** — La pantalla de invocar amigos (B) ahora es un K-Touch: carcasa blanca perlada con franja dorada, láminas rojas escalonadas, pantalla táctil con los nueve blasones de los Riders (3x3) y K-TOUCH grabado. Tocar un blasón invoca a ese Rider. El teclado numérico 1-9 sigue el orden de lectura (`ktouch_ui_preview.png`).
- **0.16.0** — Ride Booker como estuche de cartas: se abre con G, guarda las cartas en el Driver en cinco mazos de 18 (Rescate, Multitudes, 1 vs 1, Persecución, Todo salió mal) y tiene modo Insertar para activar una carta con un clic sin sacarla.
- **0.15.0** — Las formas de Forge pasan a ser exclusivas de Momo (se quitan las cartas de mitad del jugador; la Forge del juego sigue cambiando de mitades y en sintonía pega más). Formas de Izuku para el Nine del juego: Dragon (Dragon Rod), Pegasus (Pegasus Bow, final de disparo) y Titan (Titan Blades), y **Rising** con One For All: acentos dorados, más daño, finales con explosiones más grandes que rompen bloques si fallan. Ocho modelos nuevos de Nine (`nine_formas_preview.png`).
- **0.14.0** — Formas de prueba de Forge: tres mitades derechas (Engranaje, Cañón, Escudo) y tres izquierdas (Carbono, Magnesio, Fibra), nueve combinaciones con modelo 3D propio, habilidades por mitad y sintonías (FORGE FLARE y FORGE BASTIÓN) (`forge_formas_preview.png`). Los Riders se llaman por su nombre con honoríficos y los que se conocen tienen conversaciones propias.
- **0.13.0** — Forge rehecha con la idea de Build: traje de dos mitades que encajan. Derecha "ingeniería" (rojo, plano técnico, ojo de tuerca, cresta de engranaje, brazo con placas atornilladas, rodilla de engranaje) e izquierda "materia" (grafito, red de moléculas, ojo de gema, cresta de cristal, brazo liso con anillo de síntesis, rodilla de cristal), unidas por una costura blanca (`forge_build_preview.png`).
- **0.12.0** — Siluetas femeninas para Spore, Forge, Mirage, Proto y Psyga (busto contorneado, cintura estrecha, caderas abiertas y hombreras más finas, sin cambiar el tamaño de los huesos), y para Nagisa sin el traje. Spore rehecha como armadura de samurái: kabuto con cubrenuca de láminas, cresta en abanico de laminillas de seta, menpo, sode colgantes, dō con cordón, kusazuri y grebas (`spore_samurai_preview.png`).
- **0.11.0** — Armas de los Riders como Armed Ride para Dex (Cipher Edge, Prism Edge, Module Driver, Forge Lance, Warden Baton, Orga Halberd y Spore Rod), cada una con clic derecho y Final Attack Ride, escaneables viendo a su dueño pelear con ella. Spore ahora pelea con su Spore Rod.
- **0.10.0** — Repertorio completo: Warden, Psyga y Orga jugables (armadura 3D, poderes, Final Attack Ride, Rider perdido, amigo y moto), formas civiles de Isamu, Nagisa y Genzo, y sus diálogos. El portal ahora reúne a los nueve.
- **0.9.0** — Historia: los Riders llegaron por accidente y buscan a Dex; hablan por el chat, viajan en parejas que se defienden entre sí, nadie muere, y si los reúnes a todos abren un portal a casa (después vuelven por un portal cuando los llamas). Takumi (Cipher) tiene forma civil. Dex Machine rehecha con ruedas redondas que giran, y seis motos nuevas (una por Rider) con su habilidad.
- **0.8.1** — Corrige la compilación en 26.1: el evento de romper bloques ahora es `BreakBlockEvent` y se quita un método que ya no existe en los ecos.
- **0.8.0** — Forge, Mirage y Proto jugables (armadura 3D, poderes y Final Attack Ride). Combate de Riders de verdad: ecos y amigos usan arma y Rider Kick. **Amigos Rider**: al vencer a un eco se queda como amigo, vive en su casa y se invoca con la tecla B o el teclado numérico. Los bloques temporales ya no sueltan nada al romperlos.
- **0.7.0** — Kamen Ride de otros Riders: cartas de Nine, Forge, Psyga, Cipher, Warden, Orga, Spore, Mirage y Proto. Primera tanda jugable con armadura 3D, poderes y Final Attack Ride: **Nine, Cipher y Spore**. Ecos de Rider (monstruos raros) que sueltan su carta al vencerlos como Dex. **K-Touch** (se forja con nueve Kamen Ride y se completa con la décima), pantalla de los diez pads y **Complete Form** con armadura propia y multiplicador.
- **0.6.0** — Rework de las Form Ride: habilidades activas con la tecla **R** (Shift + R = básica) para todas las formas, con enfriamiento por nivel y bloques temporales. Nuevas pasivas y debilidades (Cinder se apaga con el agua, Gas se fortalece en llamas y vuela con las explosiones). Nueva **Form Ride: Half-Cold Half-Hot**, con traje partido en dos colores (derecha hielo, izquierda fuego), carta propia y escaneo combinado.
- **0.5.1** — Corrige la compilación de la pantalla de personalización en 26.1 (GuiGraphicsExtractor / extractRenderState).
- **0.5.0** — Personalización del traje: color principal, de ojos y de acentos (paleta + RGB), cuernos rectos/curvos/cortos/ninguno, ojos con o sin franjas y Ride Booker visible u oculto. Se guarda en el Driver y lo ven todos. Las Form Ride ahora tiñen el traje en vez de usar texturas aparte.
- **0.4.7** — Cuernos laterales en línea recta diagonal; cuernos centrales nacen de la esquina interior superior de cada ojo; sin los píxeles grises de las mejillas.
- **0.4.6** — Dos ojos grandes casi tocándose, con 2 píxeles de separación al centro (en vez del visor único).
- **0.4.5** — Ojos más grandes y unidos en un solo visor verde de lado a lado (borde superior en V hacia el centro, franjas horizontales); las barras negras y la franja central terminan arriba del visor y siguen debajo hacia la boca.
- **0.4.4** — Cuernos laterales rehechos según la referencia: más gruesos, suben en diagonal hacia afuera y la punta se abre hacia afuera.
- **0.4.3** — Ojos con siete líneas horizontales (una cada dos píxeles), como un visor de franjas.
- **0.4.2** — Ojos: dos líneas negras horizontales los cruzan (a media altura y cerca del borde inferior); de noche el brillo se ve en franjas.
- **0.4.1** — Casco: líneas verticales negras en relieve que bajan por la cara y siguen por arriba hasta la nuca; ojos rehechos con mirada decidida (el borde superior baja hacia el centro).
- **0.4.0** — Form Ride (Speed, Gas, Terra, Sky, Frost, Cinder) con poderes, niveles 1-3 y color de armadura propio.
  Vehicle Ride: Dex Machine, moto 3D montable. Driver con el diseño propio de Draxer (ítem, armadura y cinturón).
  Ranura de cinturón con Curios. Explosión al final de la Rider Kick. Escaneo por observación continua
  (además de por golpes) y subida de nivel de cartas.
- **0.3.2** — Sonidos más mecánicos y metálicos: engranajes con trinquete, servos, choques de placas metálicas y resonancias tipo tubo en todos los efectos (carta, transformación, Armed Ride, Final Attack Ride, impacto, disparo y cambio de modo).
- **0.3.1** — Efectos de sonido rehechos (siguen siendo originales y sintetizados): transformación más larga con tres acentos y golpe final, impacto más grave con cola larga, clics dobles más secos en carta y Armed Ride, cartas del Final Attack Ride cada 0,25 s con destello final.
- **0.3.0** — Armadura en 3D con GeckoLib según la imagen de referencia: cuernos dorados en medialuna,
  cuernos centrales en V, ojos verdes que brillan de noche, hombreras rojas con franjas doradas,
  placa del pecho con la X, guanteletes y botas doradas, cinturón con el Dex Driver y Ride Booker
  en la cadera. Textura con 4× más resolución. Sonidos originales para todas las acciones y
  huecos para las voces.
- **0.2.1** — Texturas rehechas según la imagen de referencia: casco rojo con franjas negras, ojos verdes y cuernos dorados; torso negro con X dorada; hombreras rojas; guanteletes y botas dorados; Driver negro con lente roja; Ride Booker negro, plateado y rojo.
- **0.2.0** — Armed Ride: Ride Booker (espada/pistola), Shock Baton, Blast Shot, Sword, Volt Chain y
  Power Knuckle, cada una con su Final Attack Ride. Escaneo de cartas en supervivencia con muestras.
  Mazo guardado en el Driver con escalado +15 % por carta. Texturas nuevas a 32×32 y armadura a 128×64.
- **0.1.2** — Corrige el crash al iniciar: el valor de encantamiento de la armadura debe ser mayor que 0.
- **0.1.1** — Se agregan los archivos de Gradle (`gradlew`, `gradlew.bat`, `build.gradle`, wrapper) y `neoforge.mods.toml`, que faltaban en el primer zip.
- **0.1.0** — Dex Driver, cartas Kamen Ride / Attack Ride: Slash / Final Attack Ride, transformación con V y Rider Kick.
