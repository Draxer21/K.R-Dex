# Kamen Rider Dex — mod para Minecraft 26.1.2 (NeoForge)

Dex Driver, cartas, transformación, armas Armed Ride con sus Final Attack Ride
y escaneo de cartas como en el fanfic.


> **Versión para Minecraft 26.2**: la rama `mc-26.2` del repositorio compila el mismo mod para 26.2
> (NeoForge 26.2.0.88, GeckoLib 5.5.6, Curios 16.0.0+26.2, Player Animation Library 1.2.8+mc.26.2).
> El jar se llama `dexrider-<versión>+26.2.jar`. La rama `main` sigue siendo para 26.1.2.

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

**Attack Ride del fanfic** (se escanean y suben a Nv. 2 y 3 re-escaneando; sirven con cualquier identidad o forma):

| Carta | Se escanea viendo… | Efecto | Nv. 2-3 |
|---|---|---|---|
| Overclock | un enderman peleando, o a Cipher en Accel | todo a tu alrededor va lento, tú vas rápido; al terminar, cansancio | el cuerpo alcanza a la cabeza (más velocidad y duración); aviso a los 7 s: "siete y vuelves" |
| Gale | un breeze atacando | ráfaga en cono que empuja y deshace los proyectiles que vienen | más alcance y empuje |
| Razor Feather | un vex atacando | plumas que buscan hasta 5 enemigos a la vez | más plumas; plumas vigía (los enemigos cercanos quedan marcados) |
| Nitro Burst | una explosión (creeper, TNT) | detonación delante sin romper bloques; el retroceso te impulsa | 3 y 5 detonaciones encadenadas |
| Ground Wave | un golpe de maza o el rugido sónico del warden | onda por el suelo que lanza y frena | más radio; anula la lentitud y el mareo que lleves |

### Form Ride (transformado)
Clic derecho con una carta Form Ride cambia de forma; la misma carta otra vez vuelve a la Dex Form base.
Solo una forma activa a la vez. La armadura cambia de color con cada forma (`form_rides_preview.png`).
Cada forma tiene **Nivel 1 a 3**: se sube re-escaneando su fuente (el doble de muestras para Nv.2, el cuádruple para Nv.3).

| Forma | Color | Pasivas | Debilidad / detalle |
|---|---|---|---|
| Speed | azul | Velocidad III→V, prisa, salto | — |
| Gas | violeta grisáceo | los proyectiles lo atraviesan, sin daño de caída, cae lento, algo más rápido; **si está en llamas gana Fuerza** | las explosiones le hacen la mitad pero lo lanzan el doble |
| Terra | marrón | resistencia I→III, minería rápida, golpes que levantan | Nv.1 algo lento |
| Sky | negro violáceo, **alas de membrana** | **vuela** en la oscuridad (Nv.1), con poca luz (Nv.2), siempre (Nv.3); visión nocturna. Las alas se pliegan en el suelo y aletean al volar; crecen con el nivel y en Nv.3 dejan estela | forma nocturna |
| Frost | celeste | congela el agua al caminar, golpes que congelan y ralentizan, inmune al frío | el fuego le hace ×1,5 |
| Cinder | naranja | inmune al fuego y a la lava, golpes que queman | **el agua y la lluvia** lo debilitan (Debilidad + Lentitud) |
| Hydro | verde azulado | respira bajo el agua y nada como delfín; golpes que empujan y apagan el fuego, más fuertes en el agua | el fuego le hace la mitad |
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

### La Señal y el Arquitecto
"No toda Dai-Shocker es la casa de la montaña": hay gente de fuera de la familia que pensó qué haría con diez Riders
que se encienden con un botón.
1. En el cofre de notas del **laboratorio de Gunma** hay un **Rastreador de la Señal** (la Quimera también lo suelta al caer).
2. Clic derecho con él: la aguja apunta a la **base del Arquitecto** más cercana y te dice a cuántos bloques está.
   La base está bajo tierra (llanuras, bosques, sabanas, taigas...): en la superficie solo se ve una caseta con un pozo y una escalera.
3. Abajo: la sala de las diez cápsulas vacías, el laboratorio con sus notas, las celdas y la **arena**. Al fondo, el
   **Núcleo de la Señal**. Clic derecho lo despierta.
4. **El Arquitecto** (jefe, 3 fases):
   - Fase 1: llama refuerzos, rayo a distancia y un pulso si lo rodean.
   - Fase 2: **se adapta**. Cada golpe seguido con la misma identidad, forma y arma le hace menos daño (hasta un 75 % menos).
     Cambia de Kamen Ride, Form Ride o arma y lo tomas por sorpresa (+25 %).
   - Fase 3: carga **la Señal** (la correa). **Di que no**: pulsa Shift justo cuando la suelta (o haz una parada mientras carga).
     Si lo logras, la Señal se corta y queda aturdido 3 s (recibe +50 %). Si no, te mareas, tus Riders amigos pierden el
     control un rato y, en la Complete Form, la correa empieza a arrastrarte.
5. Al caer deja la carta **Attack Ride: Severance** (cortar la correa): corta tu correa y tu Violent Emotion, limpia efectos
   negativos de ti y de tus amigos, y deja sin órdenes a los enemigos cercanos. El Núcleo se apaga para siempre.

### La interfaz del visor
El Rider System es tecnología: transformado, ves lo que ve Dex desde dentro del casco.
- **Arranque** al hacer Henshin: líneas de sistema que se escriben (*RIDER SYSTEM · DEX DRIVER · ENLACE OK · N CARTAS*…).
- **Marco del visor**: esquinas, la forma del lente arriba y un barrido suave, del **color de los ojos de tu traje**
  (cambia con la forma y la Emotion; con la Violent Emotion, interferencia roja).
- **Lecturas** arriba a la izquierda: Rider y forma con su nivel, **integridad** (vida), **energía** (recarga de R),
  Accel y Emotion. La carta elegida, junto a la barra de objetos. La lectura del objetivo, arriba a la derecha.
- **Retícula de escaneo** alrededor de la mira mientras el Driver toma muestras (*ANALIZANDO* / *COPIADO*).
- **Alertas**: integridad crítica, correa activa, salida abierta (Heroic) y oxígeno bajo.

- **Filtro de la visión** (en primera persona): el mundo se ve a través del lente, con un velo de su color y los
  bordes más oscuros. Con la Violent Emotion el velo late en rojo.

**Ajustable** (Archivo del Driver → *Visor*): modo **completo**, **mínimo** (marco, carta, retícula y alertas) o
**clásico** (el HUD de antes); opacidad, tamaño, color (ojos del traje o uno elegido) y cada pieza por separado, incluidos el filtro (con su intensidad) y los bordes del lente.
Se guarda en `config/dexrider-visor.json`.

### La Lectura del Driver
Transformado, cuando intercambias golpes con algo, el Driver lo lee y aparece un panel a la **derecha de la barra de objetos**:
- **Nombre y vida** del objetivo (barra verde → amarilla → roja, y los corazones que le quedan).
- **Tú**: cuánto le hace tu último golpe y **cuántos golpes faltan** para tumbarlo (media de tus tres últimos golpes).
- **Él**: cuánto te hace su golpe y **cuántos aguantas** tú (en rojo si son dos o menos).
El daño se mide de verdad, con armaduras, encantamientos, Form Ride, Emotion y la adaptación del Arquitecto incluidas.
Se apaga tras 10 s sin golpes, o si el objetivo cae o se aleja. Se puede desactivar en la configuración (`dex.targetRead`).

### Parada y contraataque
- **Parada**: pulsa **Shift** justo antes de que te llegue un golpe de frente (en la Upgrade Form tienes un poco más de margen).
  El golpe se anula, el atacante queda aturdido y tu siguiente golpe en 2 s es un **contraataque** (+60 %).
- **Bloqueo**: si mantienes Shift (hasta 1,5 s), los golpes de frente te hacen la mitad, a cambio de un poco de hambre.
- Pulsar Shift sin parar no sirve: si lo aprietas más de dos veces por segundo, no hay parada.

### Combos entre Riders
- Usa el **Final Attack Ride** con tus Riders amigos cerca (invocados, dobles o proyecciones del K-Touch): lanzan su ataque
  especial a la vez contra tu objetivo y se suma un **golpe combinado** con nombre propio (Fuego Cruzado con Kaixa,
  Pilar del Monarca con Orga...). Con tres o más: **Diez Luces**.
- Dos jugadores transformados que usen su Final Attack Ride cerca, con menos de 3 s de diferencia, hacen un **Doble Final Attack**.

### Tonos de las cartas
Cada carta suena distinto al activarse (una nota propia). **Orga y Kaixa** que peleen contra ti leen los tonos:
- Si usas la **misma carta dos veces en 10 s**, la reconocen y **esquivan tu siguiente golpe**.
- **Señuelo**: Shift + usar la carta del Booker (Alt izquierdo) la corta a medio sonar; quien lo lea queda expuesto
  y tu siguiente golpe pega un 50 % más.
- **Solapar**: dos cartas distintas en menos de 1 s mezclan los tonos y durante 5 s nadie te puede leer.

### La correa y la sangre (Complete Form)
Sostener a un **Kagami** presente (Cipher, Warden, Psyga, Orga, Kaixa) cuesta la mitad que a un amigo. Pero si un
golpe te deja bajo el 30 % con un familiar presente, **la correa viaja por la sangre**:
eco (8 s) → arrastre (los que sostienes dejan de seguirte; Fuerza II) → bucle (soltar un blasón falla la mitad de las veces)
→ a los 45 s te consume y la Complete Form se rompe. Se corta **soltando a tiempo los blasones de la familia** (tu "no"),
o si Takumi o Nagisa dicen su palabra de salida ("Tres", "Sótano"). Los amigos presentes son toma de tierra:
con alguno, el contagio avanza a la mitad.

### Complete Form Nv.2
Entrar en la Complete Form **desde la Upgrade Form** sube la carta a Nv.2 para siempre: vuelve el blanco (cruz X,
rayas), púas en los hombros y una capa rota en tiras que hace de reloj. Cada Kamen Ride suma +0,3 al multiplicador
(en vez de +0,2) y agota un 25 % menos.

### Driver prestado (multijugador)
Sin transformarte, **clic derecho con el Dex Driver sobre otro jugador** para prestárselo. Se transforma con V sin carta,
con una paleta propia (el Driver cambia con cada usuario). Pelear con él drena hambre y no escanea.
La primera vez nace **Kamen Ride: Portador** en el mazo (úsala transformado como Dex para tomar su forma).
**V sin Driver** revoca el préstamo al instante; el portador puede devolverlo con clic derecho.

### Kaixa completo y más
- **Armed Ride: Kaixa Gunblade** (se escanea viendo pelear a Kaixa): clic derecho, dos disparos; Shift, tajo de remate;
  FAR = Cobalt Breaker. **Vehicle Ride: Cobalt Sidewinder**, moto con sidecar (R = ráfaga del cañón); te la deja al irse.
  Tetsuya tiene forma civil.
- **Cartas de los Kaijin**: Mantis Reap (salto y dos tajos que hacen sangrar), Rhino Charge (embestida que lanza),
  Chimera Dive (sube con alas y cae aplastando). Se escanean viendo atacar a cada Kaijin.
- **Armed Ride y Vehicle Ride suben de nivel** re-escaneando: armas +20 % por nivel, motos +12 % de velocidad y +25 % de embestida.
- **Motos con más detalle**: discos de freno, radiador, aletas del cilindro, cadena, amortiguadores, matrícula, intermitentes.
- **Laboratorio de Gunma** (taigas y bosques de montaña): ruinas de Dai-Shocker con el sótano que cedió, celdas vacías,
  la Quimera de guardia y tres notas de los investigadores sobre la correa.

### Poses del jugador (opcional)
Si instalas **Player Animation Library** (NeoForge 26.1), Dex hace poses: henshin al transformarse, lanzar la carta,
la Rider Kick, el Final Attack Ride y pulsar los blasones del K-Touch. Sin la librería, el mod funciona igual sin poses.

### Kaixa, la undécima Kamen Ride
**Tetsuya Kagami**, el hermano menor, aparece perdido en taigas y bosques de montaña. Vencerlo da **Kamen Ride: Kaixa**
(grafito y cobalto con líneas doradas que brillan, visor violeta en triángulo invertido, espada-pistola). Pasivas: Fuerza I, Velocidad I, Prisa I, Salto I.
R = **Twin Shot** (dos disparos) · Shift+R = **Tajo de remate** (avanza cortando; pega más a los que ya están tocados) ·
FAR = **Cobalt Breaker** (seis disparos que lo frenan y un tajo en cruz).
Con el K-Touch ya forjado, la undécima Kamen Ride forja la **Carta de Complete Form II** (Kaixa ocupa el hueco de Proto).
Clic derecho con una carta de Complete Form la inserta en el K-Touch: decide qué nueve blasones usa.

### Complete Form: los blasones
En Complete Form, los pads del K-Touch (B o el teclado numérico) son **blasones**: cada uno invoca a ese Rider.
Si ese amigo está cerca de verdad, no sale una proyección: se potencia y pelea contigo con todo. Si no, sale su
**proyección**, que pelea como el original. Pulsar otra vez el blasón lo libera. Cada blasón sostenido agota más rápido
(las proyecciones cuestan el doble que los amigos presentes); si caes, todos caen.

### Upgrade Form
La forma sin miedo, solo de Dex. Nace cuando el Driver ya sintió **las dos Emotion** (Violent y Heroic): la carta aparece sola.
Clic derecho (o desde el Booker) la activa y la desactiva. Fuerza III, Velocidad II, Salto II, Resistencia II, golpes ×1.2;
las **Form Ride se apilan encima y cuentan un nivel más** (hasta Nv3). No se combina con las Emotion. Traje propio:
casco rojo con espolones dorados, cruz X blanca sobre el torso negro, púas rojas en los hombros (`upgrade_kaixa_preview.png`).

### Dai-Shocker (enemigos y estructuras)
- **Combatiente** (máscara de hueso, visor de una ranura; van en grupo), **Kaijin Mantis** (guadañas, salta y hace sangrar;
  junglas y pantanos), **Kaijin Rinoceronte** (blindado, embiste; sabanas y montes). Aparecen de noche como cualquier monstruo.
- **Quimera** (jefe, 180 corazones, barra de jefe): salta y aplasta, embiste, barre con la guadaña y, herida, llama a combatientes.
- **Emboscadas**: de noche, transformado y con 3+ Kamen Ride, a veces Dai-Shocker te tiende una; con 6+ puede venir la Quimera.
- **Casa de la montaña de los Kagami** (biomas de montaña): cabaña con chimenea, mesa del té y la cama roja de Nagisa;
  en el sótano, el taller con el **equipo Blaster** (cofre), vigilado por combatientes.
- **Depósito de la Comisión** (llanuras y sabanas): Dai-Shocker lo está asaltando; cofres con cartas y materiales.
- Escanear: la Mantis da Sword, el Rinoceronte Power Knuckle y la Quimera Ground Wave.

### Vehicle Ride: Scarab
Izuku te deja su escarabajo al hacerse amigo tuyo. La carta lo **fusiona con la moto que conduces** (o invoca la Dex Machine):
caparazón verde esmeralda, embestida que daña a lo que atropellas, planeo al caer, Resistencia II para el piloto y
**R = Scarab Rush**. Dura 2 minutos; después descansa 3.

### Cuaderno de Takumi y logros
- **Cuaderno de Takumi** (pestaña creativa, o libro + tinte rojo + pepita de oro): guía del Rider System en 14 páginas.
- **Logros** propios (pestaña Kamen Rider Dex): transformarse, escanear, Form Ride, Attack Ride, Nivel 3,
  Final Attack Ride, amigos, otras identidades, Accel, K-Touch, Complete Form y las dos Emotion (ocultas).

### Violent Emotion y Heroic Emotion (solo como Dex)
El Driver responde a lo que siente quien lo lleva. Se despiertan solas:
- **Violent Emotion**: al bajar del 30 % de vida o recibir de golpe el 40 % de tu vida en 3 s. 20 s con Fuerza III,
  Velocidad II y golpes ×1.3; el traje se oscurece a carmesí y morado, le salen púas en hombros, guanteletes, casco y espinazo, y un cristal violeta brilla en la frente.
  Cobra su precio: medio corazón cada 2 s (nunca te deja por debajo de 2 corazones).
- **Heroic Emotion**: cuando un amigo Rider tuyo o un aldeano cercano recibe un golpe de un monstruo. 20 s con
  Regeneración II, Resistencia II, un 20 % menos de daño recibido y golpes ×1.15; traje azul y blanco con un cristal blanco en la frente.
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
Pulsa **G** para abrirlo (configurable): se abre **como un libro**, un estuche negro con costuras doradas y dos
páginas de bolsillos con las cartas a la vista (diseño propio de Dex). **Clic en una carta: la sacas y se usa.**
Las páginas se pasan con las flechas o la rueda del ratón; las **cintas de color** del costado saltan a cada mazo
(3 páginas de 9 por mazo). El bolsillo con borde verde es la carta elegida para Alt. **Ordenar cartas** abre el
Booker como cofre para meter, sacar y mover cartas.

**Selector rápido** (transformado): **mantén la rueda del ratón** y aparecen en círculo tus cartas de uso rápido;
mueve el ratón hacia una y **suéltala**: se usa. Para elegir cuáles salen, en el libro activa **★ Selector** y haz clic
en hasta **8** cartas (la estrella marca las elegidas; otro clic la quita). Transformado, la rueda deja de copiar el
bloque que miras. La tecla se cambia en Controles. Tiene **5 mazos** de 27 cartas (135 en total), como en el fanfic: *Rescate, Multitudes,
1 vs 1, Persecución* y *Todo salió mal*. Solo admite cartas (y la carta de Complete Form).
- **Guardar**: mueves cartas como en un cofre (Shift + clic las pasa entre el Booker y tu inventario).
- **Insertar**: haces clic en una carta y **se activa al instante sin sacarla del Booker** (inserción directa).
  Un Armed Ride aparece en tu mano principal y lo que tenías ahí pasa al inventario.
- **Guardar todo**: el botón a la derecha mueve todas las cartas de tu inventario al Booker de una vez.
- **Guardado automático**: las cartas nuevas (escaneadas, recogidas del suelo, de un eco derrotado, del K-Touch…)
  van **directas al Booker** si llevas el Driver, empezando por el mazo elegido. Las repetidas sí van al inventario.
  Los Booker de versiones anteriores (18 por mazo) se reordenan solos al abrirlos.

**Uso rápido en combate (sin abrir el Booker)**:
- **Selector rápido** (**mantener la rueda del ratón** transformado, o **C** desde el teclado): tus cartas favoritas
  en círculo; apuntas a una y al soltar se usa. Sin favoritas elegidas, muestra las 8 primeras del mazo actual.
- **Alt izquierdo + rueda del ratón**: mientras mantienes Alt, la rueda pasa de carta y el HUD muestra la anterior,
  la actual y la siguiente. **Al soltar Alt** se usa la carta elegida (si giraste la rueda, soltar solo elige).
- **Z / X** siguen eligiendo la carta anterior / siguiente (con **Shift**, cambian de mazo).

La carta elegida aparece junto a la barra de objetos. Así cambias de arma o de carta en plena pelea. Todas las teclas se pueden cambiar en Controles.

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
- **Invocarlos**: tecla **B** (pantalla con forma de K-Touch: tocas el blasón del Rider; el de Dex abre la Complete Form) o **teclado numérico 1-9** (en orden de lectura, sin abrir nada: sirve en plena pelea). El **0 del numérico** es el blasón de Dex: entra o sale de la Complete Form. Clic derecho con el K-Touch abre la misma pantalla
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

### Editor del traje
**Shift + clic derecho con el Dex Driver en la mano** abre el editor. A la izquierda, un **maniquí 3D** con tu
diseño que gira solo (la rueda del ratón encima o las flechas lo giran a mano; `||` lo para).

**Estados** (arriba): *Base, Violent, Heroic, Upgrade* y cada **Form Ride** (el último botón; púlsalo otra vez
para pasar a la siguiente forma). Cada estado **hereda de la base** lo que no cambies:
- Violent / Heroic parten de tu base con los colores de la Emotion, el cristal y (Violent) las púas.
- Las Form Ride parten de tu base con el color de la forma en las zonas principales (Half: lado derecho e izquierdo).
- La Upgrade Form tiene su propio modelo: conserva su paleta original y hereda solo el brillo y el acabado.
**Restablecer estado** borra lo que cambiaste en ese estado.

**Colores**: casco, pecho, brazos, piernas, botas, acentos, ojos, plata, negro, cristal y bufanda. 16 colores rápidos,
barras R G B y código hexadecimal (#RRGGBB). El marco dorado marca las zonas cambiadas en ese estado;
**Por defecto** hace que la zona vuelva a heredar.

**Piezas**: cuernos (rectos, curvos, cortos, ninguno), visor con o sin franjas, hombreras (en capas, lisas, ninguna),
bufanda (ninguna, corta, larga), cresta, Booker en la cadera, cristal de la frente, púas (cabeza, espalda, brazos),
alas (en Sky), **brillo** (ojos, cristal, acentos, traje entero) y **acabado** (normal, mate o metálico).

**Diseños**: 5 ranuras con nombre; la marcada con `>` es la que llevas. **Copiar código** pone el diseño en el
portapapeles como texto (`DEX1-…`) para pasárselo a otro jugador; **Pegar código** lo carga en la ranura.

**Guardar** lo deja en el Driver y se copia a la armadura al transformarte: los demás jugadores ven tu diseño.
Si cambias algo estando transformado, se ve al instante. El Driver (cinturón) no se tiñe: siempre conserva su diseño.
Los trajes guardados antes de la 0.33 pasan solos a la primera ranura.

### Cómo empezar en supervivencia
La primera vez que entras a un mundo recibes **una carta de Genzo** (en el chat), la **Guía del Rider System** y la
**Brújula de los Kagami**: clic derecho y la aguja apunta a la **casa de la montaña** más cercana (biomas de montaña)
y te dice a cuántos bloques y en qué dirección está. Ahí, en el cofre del taller del sótano, está el **Dex Driver** de
repuesto con sus primeras cartas: **Kamen Ride: Dex**, **Attack Ride: Slash**, **Final Attack Ride: Dex** y
**Armed Ride: Ride Booker** (el arma espada / pistola).
Si pierdes la brújula: brújula + tinte rojo + lingote de oro.

El **estuche de cartas** del Ride Booker no es un objeto aparte: va dentro del Dex Driver y se abre con **G**.
La carta **Armed Ride: Ride Booker** (el arma) también se fabrica, una vez que tienes el Driver:

```
 . tinte negro .
 hierro  libro  hierro
 . redstone .
```

### Form Ride: Hydro
La forma del agua (traje verde azulado). Se escanea viendo **nadar rápido a un delfín** o **atacar a un ajolote** (2 muestras).
- Bajo el agua **respira** y **nada como un delfín** (más rápido con el nivel); Nv2 más fuerza en el agua; Nv3 poder del
  conducto y visión bajo el agua. Con lluvia, algo de velocidad. Se apaga solo si se prende fuego, y el fuego le hace la mitad.
- Sus golpes **empujan**, apagan el fuego del enemigo y, en el agua o con lluvia, pegan un poco más.
- **R**: Nv1 **chorro a presión** (rayo de agua de 12 bloques que atraviesa y empuja; apaga fuegos) · Nv2 **torrente**
  (cabalgas una ola 1,5 s arrastrando y levantando a los enemigos) · Nv3 **remolino** (a 8 bloques: atrapa 3 s a los
  enemigos, los arrastra al centro y estalla). En el agua o con lluvia hacen un 50 % más. **Shift + R**: el chorro.

### Misiones de los Kagami
**Tecla J** (o el botón *Misiones* del Archivo del Driver). Genzo, Nagisa y Tetsuya te escriben cartas, una misión
a la vez y en orden. Cada misión se cumple sola cuando haces lo que pide: da su **recompensa** y llega la carta siguiente.

| # | De | Misión | Recompensa |
|---|---|---|---|
| 1 | Genzo | Llega a la casa de la montaña | 2 manzanas doradas, 16 antorchas |
| 2 | Genzo | Transfórmate en Dex | 5 niveles |
| 3 | Nagisa | Escanea tu primera carta | un pastel y 8 galletas |
| 4 | Tetsuya | Usa una Form Ride | 3 diamantes |
| 5 | Nagisa | Consigue la Form Ride: Hydro | un tridente |
| 6 | Genzo | Para un golpe en el momento justo | 2 diamantes, 5 niveles |
| 7 | Tetsuya | Vence a un Rider perdido siendo Dex | 4 diamantes |
| 8 | Nagisa | Despierta la Violent Emotion | 3 manzanas doradas |
| 9 | Nagisa | Despierta la Heroic Emotion | una manzana de Notch |
| 10 | Genzo | Encuentra el laboratorio de Gunma | 2 fragmentos de netherita |
| 11 | Tetsuya | Vence a Tetsuya (Kaixa) | un lingote de netherita |
| 12 | Nagisa | Encuentra el depósito de la Comisión | 10 esmeraldas, 3 diamantes |
| 13 | Genzo | Derrota al Arquitecto | tótem de la inmortalidad, manzana de Notch, 10 niveles |

Si ya habías hecho algo de esto antes de la 0.36, se cumplen solas al entrar, una cada 2 segundos.

### Escaneo (cómo conseguir cartas en supervivencia)
Transformado y con el Driver encima, el Driver registra lo que **ve en uso real** a 20 bloques o
menos (con línea de visión y mirando hacia ahí). Lo que te hacen a ti no cuenta.

**Archivo del Driver** (tecla **K**, o el botón *Archivo* del Ride Booker): todas las cartas del juego por familia,
como un álbum. Las que tienes se ven a color con su nivel (puntos dorados); las que no, en silueta, con las muestras
que llevas y una **pista de dónde conseguirla**. Al sumar una muestra, generar una carta o subirla de nivel aparece
un aviso arriba de la pantalla con la carta y su barra de muestras.

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
| Form Ride: Hydro | un delfín nadando rápido, o un ajolote atacando | 2 |
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

**Voces**: grabadas por Draxer y procesadas como la voz del Driver (un toque más grave, brillo metálico y
eco corto). El Driver dice *Kamen Ride* seguido del nombre del Rider (*Dex, Nine, Forge, Psyga, Cipher, Warden,
Orga, Spore, Mirage, Proto, Kaixa*; en la Complete Form, *Complete*), *Form Ride*, *Attack Ride*, *Armed Ride*,
*Vehicle Ride*, *Final Attack Ride* y *Upgrade*. Al transformarte, tu personaje grita *¡Henshin!* y el Driver
responde *Kamen Ride… Dex*. Para cambiar una voz basta con reemplazar su `voice_*.ogg` (mono).

Para agregar una carta nueva: un valor en `DexCard`, su textura `card_<id>.png`,
el modelo en `items/` y `models/item/`, y la traducción.

## Si algo no compila

Escrito siguiendo la documentación de NeoForge 26.1, pero sin compilar aquí. Si falla, suele
ser un nombre de API que cambió; pega el error y se arregla.

## Siguientes pasos

- Opciones de equilibrio en la configuración, duelos entre jugadores.

## Historial de versiones

- **0.39.1** — Filtro de la visión del color del lente (ajustable: intensidad y bordes). Arreglo: el maniquí del editor del traje no giraba.
- **0.39.0** — Interfaz del visor: arranque al hacer Henshin, marco del lente con el color de los ojos del traje, lecturas de integridad y energía, retícula de escaneo y alertas. Ajustable desde el Archivo (modo, opacidad, tamaño, color y piezas).
- **0.38.1** — La tecla C abre el mismo selector rápido (la rueda de cartas antigua se retira). La Guía del Rider System suma páginas: Archivo, cartas de casa (misiones), editor del traje e Hydro, y las teclas y el Ride Booker al día.
- **0.38.0** — Selector rápido: mantén la rueda del ratón para elegir entre tus cartas favoritas en círculo y soltar para usarla; se eligen en el libro del Ride Booker (★ Selector, hasta 8). 25 pruebas automáticas.
- **0.37.0** — El Ride Booker se abre como un libro: estuche con páginas de bolsillos y las cartas a la vista, cintas por mazo y clic en una carta para sacarla y usarla. *Ordenar cartas* abre la vista de cofre.
- **0.36.0** — Form Ride **Hydro** (forma del agua: respira y nada como delfín, chorro a presión, torrente y remolino; se escanea con delfines y ajolotes). **Misiones de los Kagami**: 13 cartas de Genzo, Nagisa y Tetsuya en orden, con recompensas y registro (tecla J). 24 pruebas automáticas.
- **0.35.0** — Voces del Driver grabadas por Draxer: Kamen Ride + nombre de cada Rider, Form Ride, Attack Ride, Armed Ride, Vehicle Ride, Final Attack Ride, Upgrade y Complete, y el ¡Henshin! del jugador al transformarse. 22 pruebas automáticas.
- **0.34.0** — Archivo del Driver (tecla K o botón del Ride Booker): todas las cartas como álbum, en silueta las que faltan, con muestras, niveles y una pista de cómo conseguir cada una. Aviso de escaneo en pantalla al sumar muestras, generar una carta o subirla de nivel. 21 pruebas automáticas.
- **0.33.0** — Editor del traje: maniquí 3D, colores por zona (casco, pecho, brazos, piernas, botas, acentos, ojos, plata, negro, cristal, bufanda), piezas nuevas (hombreras lisas, bufanda corta o larga, cresta) y opciones (púas, brillo, acabado mate o metálico), una apariencia propia para Base, Violent, Heroic, Upgrade y cada Form Ride, 5 ranuras de diseños y código para compartir. 20 pruebas automáticas.
- **0.32.1** — Receta de la carta *Armed Ride: Ride Booker* (tinte negro, libro, 2 lingotes de hierro, redstone; se desbloquea al tener el Driver) y la carta aparece en el cofre del taller de la casa de la montaña.
- **0.32.0** — Ride Booker más grande (5 mazos × 27) y guardado automático de cartas nuevas, botón *Guardar todo*, rueda de cartas (C) y Alt + rueda del ratón para pasar de carta. 19 pruebas automáticas.
- **0.31.0** — Al entrar por primera vez a un mundo: carta de Genzo, Guía y Brújula de los Kagami, que apunta a la casa de la montaña (donde está el Dex Driver). Receta de la brújula. 18 pruebas automáticas.
- **0.30.0+26.2** — Primera versión para Minecraft 26.2 (rama `mc-26.2`): mismas funciones, adaptada a los cambios de 26.2 (tipos de entidad, pantallas, predicados de logros).
- **0.30.0** — Form Ride Sky con alas de membrana (diseño propio): plegadas a la espalda en el suelo, se abren y aletean al volar; más grandes con el nivel y en Nv.3 dejan estela.
- **0.29.0** — Cartas en alta resolución (128x128): marco dorado biselado, nombre de la familia arriba, arte ampliado sobre un degradado y el nombre de la carta abajo. Las de Complete Form con los blasones de verdad y borde de código de barras; icono del K-Touch a partir de la carcasa del aparato; la carta se ve más grande en la muesca (`cartas_hd_preview.png`).
- **0.28.3** — Cartas de Complete Form con rayas tipo código de barras por el borde del marco.
- **0.28.2** — Cartas de Complete Form en vertical, como se insertan por la muesca lateral: 3x3 blasones arriba, separador y tres botones abajo (Dex al centro). En la pantalla del K-Touch la carta se ve metida en la muesca.
- **0.28.1** — El K-Touch abre la pantalla nueva con forma de aparato (la misma de la tecla B); icono del K-Touch y cartas de Complete Form con la silueta y la disposición del aparato (3x3 blasones + Dex al lado). Teclado numérico 0 = blasón de Dex (entrar/salir de la Complete Form), además de 1-9 para los blasones.
- **0.28.0** — La Lectura del Driver: tras intercambiar golpes, panel con la vida del objetivo, tu golpe, su golpe y los golpes que faltan de cada lado. 17 pruebas automáticas.
- **0.27.1** — El Dex Driver y sus tres cartas básicas (Kamen Ride: Dex, Slash y Final Attack Ride) se consiguen en supervivencia: cofre del taller de la casa de la montaña.
- **0.27.0** — Arco de la Señal: Rastreador, base subterránea, el Arquitecto (jefe de 3 fases que se adapta y lanza la Señal) y la carta Severance. Parada y contraataque con Shift. Combos entre Riders y entre jugadores. 16 pruebas automáticas.
- **0.26.0** — Kaixa completo (gunblade, Cobalt Sidewinder, forma civil), cartas de los Kaijin, niveles de armas y motos, motos con más detalle, laboratorio de Gunma, Complete Form Nv.2, tonos de las cartas, la correa por la sangre, Driver prestado, poses (con Player Animation Library). 13 pruebas automáticas dentro del juego.
- **0.25.4** — Pruebas automáticas dentro del juego (9 pruebas con jugador simulado: `runGameTestServer`) y fotos automáticas con gráficos (`runShots`), las dos en GitHub Actions. Arreglos encontrados por las pruebas: los amigos Rider encuentran a su dueño aunque no esté en la lista de jugadores, el HUD no se envía a clientes sin el mod, animación vacía de la armadura.
- **0.25.3** — Kaixa: líneas doradas que brillan en el traje, el Driver y el arma; sus disparos también son dorados.
- **0.25.2** — Violent Emotion con púas y cristal morado en la frente; Heroic Emotion con cristal blanco en la frente (`emotions_preview.png`).
- **0.25.1** — Kaixa: el visor pasa a un triángulo invertido violeta (carta y blasón del K-Touch a juego).
- **0.25.0** — Kaixa (Tetsuya) como undécima Kamen Ride, con eco, amigo, poderes y diálogos, y la Carta de Complete Form II. Blasones de la Complete Form: invocan proyecciones o potencian al amigo presente. Upgrade Form (carta, traje propio y Form Ride amplificadas).
- **0.24.0** — Dai-Shocker: combatientes, Kaijin Mantis y Rinoceronte, la Quimera (jefe) y emboscadas (`daishocker_preview.png`). Casa de la montaña de los Kagami (equipo Blaster en el sótano) y depósito de la Comisión. Vehicle Ride: Scarab, el escarabajo que se fusiona con la moto (`scarab_preview.png`). Logros nuevos.
- **0.23.0** — Attack Ride del fanfic: Overclock, Gale, Razor Feather, Nitro Burst y Ground Wave, con niveles (`attack_rides_preview.png`). Cuaderno de Takumi (guía en el juego) y logros propios.
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
