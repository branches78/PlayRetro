# GameStation — instrucciones para Claude

GameStation es un fork independiente de [Lemuroid](https://github.com/Swordfish90/Lemuroid) (GPL-3.0),
un emulador para Android con interfaz para teléfono (Jetpack Compose) y para Android TV (Leanback).
No se sincroniza con Lemuroid: el código es propio y va por su cuenta.

## Reglas de trabajo (obligatorias)

1. **Idioma:** responde siempre en **español**, incluidos los mensajes cortos de progreso y los resúmenes.
   Los **comentarios dentro del código** van en **inglés**, como el resto del código.
2. **Autorización:** no corrijas, edites ni compiles nada hasta que el usuario lo autorice explícitamente.
   Investigar y leer código sí está permitido. Cada cambio o compilación necesita su propia autorización.
3. **Una corrección a la vez:** el usuario menciona las correcciones una por una; aplica solo la que pide.
4. **Revisiones de código muerto, bugs o seguridad:** empieza siempre por un **resumen de hallazgos**
   (cantidad por tipo y gravedad) y después el detalle. No corrijas nada hasta que el usuario lo apruebe.
5. **Diseño:** antes de cambios visuales grandes, ofrece un mockup.
6. **Commits:** no hagas commit ni push de nada hasta que el usuario lo diga, aunque un hook lo pida.
7. **Textos en todos los idiomas:** cualquier cambio de texto visible se aplica al español, al inglés por
   defecto (`values/`) y a **todos** los idiomas de `res/values-*/strings.xml`, con traducción propia.
   Si a un idioma le falta la cadena, se añade. Los saltos de línea dentro de un texto se escriben como `\n`:
   Android convierte en un espacio los saltos de línea escritos tal cual en `strings.xml`.

## Trabajo en la nube vs. local

- **En la nube** (claude.ai/code): solo se hacen **correcciones de código**, commit y push.
  **No compiles** ni intentes instalar el APK: la nube no tiene el SDK de Android configurado
  ni acceso a los dispositivos del usuario.
- **En local** (PC del usuario, Windows): se compila, se copia el APK y se instala en la TV.
  - Repositorio: `D:\Descargas\Proyectos\PlayRetro\Source-Code` (remoto `https://github.com/branches78/PlayRetro.git`)
  - APKs: copiar a `D:\Descargas\Proyectos\PlayRetro\APK-Releases\` como `PlayRetro-debug.apk`
    (sale de `lemuroid-app\build\outputs\apk\freeDynamic\debug\`). En cmd, cambiar de unidad con `cd /d`.
  - Compilar con `JAVA_HOME=C:\Users\Ricardo\.jdks\jbr-21.0.11` (el JDK de Android Studio es Java 25, demasiado nuevo
    para Gradle 8.10.2): `./gradlew --no-daemon :lemuroid-app:assembleFreeDynamicDebug`
  - SDK en `D:\Aplicaciones\AndroidSDK` (`local.properties`, no se sube al repositorio).
  - Tras cada compilación autorizada, instalar **solo en la TV** (BuzzTV, `192.168.18.56:5555`) con
    `adb install -r`, **sin abrir la app**. En el teléfono la instala el usuario.

## Datos del proyecto

- **Nombre visible:** **PlayRetro** ("GameStation" ya existía en Play Store y "RetroDeck" es un proyecto de PC conocido). Se define en
  `lemuroid-app/build.gradle.kts` (`lemuroid_name`), en los textos de `strings.xml` y en el banner de TV
  (`res/mipmap-xhdpi/ic_banner.png`). El repositorio, las carpetas y los nombres internos siguen como GameStation.
  **Logo:** el nombre en letras de píxeles (original en `..\Pictures\PlayRetro.jpg`), recortado en blanco sobre
  transparente en `res/drawable-nodpi/playretro_logo.png` y teñido con el verde fluorescente del icono
  (`playretro_brand_green`, #00C64E; en la barra de TV con `android:tint`, `app:tint` no se aplica). Va a la
  derecha de la barra de pestañas de TV (`tv_app_name`), en la barra superior de las pestañas del móvil y en Acerca de
  (`shared/about/PlayRetroLogo.kt`), y en el banner de TV, a la derecha del icono, sobre el fondo oscuro de la app.
  Imágenes para la Play Console en `D:\Descargas\Proyectos\PlayRetro\Play-Assets` (banner de TV 1280×720 y feature
  graphic 1024×500). **Icono del lanzador:** mando negro con botones blancos sobre verde #00C64E
  (`lemuroid_launcher_foreground.xml`, `lemuroid_launcher_background`, PNG de `mipmap-*` y canal de TV), el de la
  primera versión del fork.
- `applicationId`: `com.playretro.tv` (debug: `com.playretro.tv.debug`), versión `1.0.0` (`versionCode` 1).
  Antes era `com.gamestation.tv`: es una app distinta para Android, así que la antigua hay que desinstalarla aparte
  y sus datos (partidas, ajustes, biblioteca) no pasan a la nueva.
- Variante que se compila: **freeDynamicDebug** (sin librerías de Google; los cores se descargan al usarlos).
  La sincronización con Google Drive solo existe en la variante `play` y requiere credenciales propias en Google Cloud.
- Tema fijo en teléfono y TV (sin Material You): blanco para textos de acento, iconos, interruptores y la lupa de TV
  (`primary`; solo el logo es verde) y verde oscuro #3A4B39 para rellenos (`primaryContainer`, `secondaryContainer`).
  Un tono casi gris del verde del logo (`playretro_soft_green`, #54635A, con contenido blanco) va en los interruptores
  (`BrandGreenFills` en `Components.kt`), en la pestaña seleccionada de TV cuando la barra no tiene el foco
  (`tv_tab_selected_background`) y en el botón seleccionado de la barra inferior del móvil (`MainNavigationBar.kt`). Colores en
  `lemuroid-app/src/main/java/.../mobile/shared/compose/ui/Color.kt` y `res/values/material-colors.xml` (deben coincidir).
- Base de datos de la app (Room): versión 11 (`fileSize` y `fileLastModified` para detectar archivos cambiados al re-escanear).

## Piezas importantes

- **Inicio (filas):** `app/shared/home/HomeDynamicRows.kt` (filas dinámicas compartidas por móvil y TV),
  `mobile/feature/home/HomeScreen.kt`, `tv/home/TVHomeFragment.kt`.
  Orden: Continuar jugando, Favoritos, Añadidos recientemente, Porque jugaste, Vuelve a jugar, Descubrir,
  Clásicos de…, Más de…. Las filas sin juegos se ocultan (Añadidos recientemente siempre tiene juegos);
  con la biblioteca vacía no hay filas. Descubrir (`shared/home/HomeDiscovery.kt`, móvil y TV) muestra juegos sin
  jugar de distintas consolas (una de cada en turno), en un orden que cambia cada día y cada vez que se juega; si no
  hay bastantes, completa con los jugados hace más tiempo. En la TV, Descubrir va siempre primero y sin título, como lista inmersiva
  (ver más abajo).
- **Descubrir inmersivo (TV):** al enfocar un juego de la primera fila se muestran arriba su título, consola ·
  desarrollador · región y descripción, en el mismo orden que en la ficha (`tv_home_immersive_info.xml`, `TVHomeFragment.showImmersiveGame`), y detrás de la barra
  de pestañas una captura del juego arriba a la derecha con degradado (`tv/home/TVBackdropView.kt`, en el layout de
  `TVMainTabsFragment`, `setHomeBackdrop`; la barra se vuelve transparente mientras se ve). Captura: `Named_Snaps` del
  servidor de carátulas, si no `Named_Titles` y si no la carátula difuminada. Descripción: Wikipedia en el idioma del
  dispositivo (o inglés), buscando el juego en Wikidata por título y consola (`shared/details/GameDescriptionProvider.kt`,
  caché en las preferencias `game_descriptions`). Captura y descripción esperan 300 ms con el foco quieto. Al bajar a
  otras filas, los textos y la captura se ocultan. Medidas de la guía de Android TV (pantalla de 960×540 dp): captura 16:9 de 758×426 dp arriba a la derecha, recortada a su vista
  y con bordes difuminados (degradados suavizados a la izquierda, abajo y arriba, con un tramo opaco en el borde). Muchas
  capturas (p. ej. de N64) traen franjas negras a los lados: se recortan al cargarlas
  (`shared/covers/TrimBlackBordersTransformation.kt`).
  Los textos (título de 1 línea a 32 sp; debajo, sistema, desarrollador y región en pequeño; descripción de 2 líneas; 485 dp de
  ancho, `tv_home_immersive_info.xml`) acaban 40 dp por encima de las tarjetas (`tv_immersive_info_gap`), y las tarjetas
  de la fila inmersiva van a 267 dp del borde superior del contenido (347 dp de la pantalla, la fila siguiente asoma
  86 dp); las demás filas, al enfocarse, a 46 dp, para que se vea
  su título sin que asome el borde inferior de la fila anterior bajo la barra
  (`TVHomeFragment.updateRowsAlignment`; Leanback alinea por las tarjetas, no por el título, y la alineación se cambia
  al seleccionar fila). Tarjeta enfocada ampliada 1.1 (`ZOOM_FACTOR_SMALL`), como pide la guía.
- **TV:** pantalla de inicio con pestañas (Inicio, Consolas, Ajustes, Acerca de) en `tv/main/TVMainTabsFragment.kt`,
  todas con márgenes laterales de 58 dp, como pide la guía (`tv_content_side_margin`; el ancho del contenido es el de la
  pantalla menos ambos márgenes, `TVHomeFragment.contentWidth`; en Ajustes y Acerca de es el padding de Compose). La píldora de la pestaña es una vista
  aparte (`tv_tab_pill`) que se desliza a la pestaña elegida (blanca con texto oscuro si la barra tiene el foco, verde si
  no) y el contenido entra desde el lado de la pestaña nueva (`res/anim/tv_tab_*`, `tv_tab_animation_duration`). Las filas de Inicio avanzan de una tarjeta
  en una con la enfocada en la primera posición y se recortan en ese ancho (`HomeListRowPresenter` en `TVHomeFragment.kt`).
  Los juegos de una consola (`tv/games/TVGamesFragment.kt`) y "Mostrar todo" de Favoritos (`tv/favorites/TVFavoritesFragment.kt`)
  se abren dentro de su pestaña, sin ocultar la barra (`showSystemGames` / `showFavorites`; Atrás vuelve a la pestaña),
  con tarjetas cuadradas, 5 por fila en el ancho del contenido y alineadas a la izquierda (`TVTabGrids.setUpHomeCardsGrid`,
  también en Consolas). Al volver de la ficha, la rejilla de una consola vuelve al juego abierto
  (`TVGamesFragment.positionToRefocus`). El abecedario va dentro del margen derecho.
  Con más de 12 juegos (`tv_alphabet_min_games`), los de una consola tienen a la derecha un abecedario (`#`, A–Z;
  `tv/games/TVAlphabetBar.kt`): → desde la última columna entra en él, ↑ ↓ saltan al primer juego de cada letra
  (muestra la letra grande y marca con borde el juego de destino) y OK o ← vuelven a la rejilla. Las posiciones salen de
  `GameDao.selectNamesBySystems`, con el mismo orden que la rejilla.
  "Configurar núcleo" del menú de un juego muestra las opciones dentro de la misma ventana (`TVGameContextMenu.kt`).
  El título de esas rejillas va alineado a la izquierda sobre una franja opaca, y las carátulas pasan por debajo al
  hacer scroll (`TVTabGrids.addTitle`). La pestaña se llama "Consolas" también en el móvil (`title_consoles`). En el móvil, Consolas tiene el mismo botón de lista/cuadrícula que los juegos de una consola, con su propio modo guardado (`systems_grid_mode`, `MainViewModel`), y la cuadrícula es de 3 por fila (`MetaSystemScreen.kt`).
  El foco no sale del contenido de una pestaña con ←, → o ↓ (antes ← en la primera tarjeta saltaba a la lupa);
  con ↑ va a la pestaña seleccionada (`TVMainTabsFragment.onFocusSearch`). Las rejillas de TV no oscurecen las tarjetas sin foco (`VerticalGridPresenter(..., useFocusDimmer = false)`); Ajustes de TV reutilizan las
  pantallas Compose del móvil (`tv/settings/TVSettingsTabFragment.kt`); al abrir una sub-opción el foco va a su primera
  opción y al volver con Atrás a la que la abrió (`TVSettingsDestination` + `SettingsFocusMemory.kt`, que registra las
  filas de `Components.kt`; la primera es la que está más arriba en pantalla; como pantallas como Dispositivos externos
  cargan sus opciones en segundo plano, durante 3 s el foco sigue a la de más arriba mientras el usuario no lo mueva); estilo de tarjetas en `tv/shared/TVCardStyle.kt`.
- **Carátulas:** forma por sistema en `app/shared/covers/CoverAspectRatio.kt`, sustituida por la forma real de cada
  carátula una vez cargada (`CoverRatioCache.kt`). Cada tarjeta tiene la forma real de su carátula, sin franjas:
  en las filas de Inicio todas tienen el mismo alto y el ancho de su carátula (móvil: el alto de una caja vertical de
  un tercio de la fila, `homeCardWidth`; TV: el alto de una tarjeta 16:9 de las 5 del ancho del contenido, unos 86 dp,
  `tv_home_row_cards`, `tv_home_row_card_spacing`). En las rejillas cada juego ocupa una celda fija para que las
  columnas sigan alineadas y la tarjeta toma la forma de la carátula dentro: móvil `LemuroidGameGridCard` (celda con
  la forma del sistema en una consola y cuadrada en Favoritos, carátula abajo, textos debajo); TV
  `GamePresenter(fitInCell = true)` (celda cuadrada, 5 por fila, `tv_home_cards_per_row`, tarjeta centrada con
  márgenes). Canal de la pantalla principal de la TV (`tv/channel/ChannelHandler.kt`): los juegos de "Continuar
  jugando" (misma consulta y orden), con un póster 16:9 hecho por la app con la carátula entera centrada
  (`ChannelPosterProvider`, archivos en `channel-posters`); se actualiza al abrir la app, al cerrar un juego, al
  marcar favoritos y tras una sincronización con cambios. El canal se busca por ser de la app, no por nombre: se
  conserva el que muestra el inicio (el creado cuando la app se llamaba GameStation), se renombra y se borran los
  sobrantes. En las filas de Inicio de la TV las carátulas van en celdas cuadradas de 5 por fila, sin la sombra de
  Leanback (`shadowEnabled = false`, pintaba la celda entera); la fila inmersiva está en `tv_immersive_row_top`
  (225 dp) para que quepan las tarjetas con su nombre. Alternativas de nombre y caché de
  resultados en `ThumbnailFallbackInterceptor.kt`. Reconocimiento de juegos (CRC, TOSEC → No-Intro, búsqueda por título)
  en `lemuroid-metadata-libretro-db/.../LibretroDBMetadataProvider.kt`.
- **Texto bajo las tarjetas de juegos** (`utils/games/GameCardSubtitle.kt`, móvil y TV): la consola en Inicio, Favoritos
  y Búsqueda; el desarrollador dentro de una consola (vacío si no hay). Tarjetas de Acerca de y el cuadro de la
  biblioteca vacía con el borde fino de las de Ajustes (`outlineVariant`, #424940).
- **Orientación de pantalla (móvil):** Ajustes → Pantalla, debajo de "Alineación de la imagen"
  (`pref_key_game_orientation`: Automática por defecto, horizontal, horizontal invertida, vertical, vertical
  invertida); `GameActivity` la aplica al abrir un juego.
- **Botones de diálogos y formularios** (`utils/android/PlayRetroButton.kt`): píldora verde oscuro con texto blanco,
  blanca con texto oscuro al enfocar, como los de la ficha. Tarjetas de Ajustes con el fondo de las de Acerca de
  (`surfaceContainer`, filas transparentes).
- **Frecuencia de pantalla (móvil):** mientras hay un juego abierto se pide el modo de 60 Hz
  (`GameActivity.preferGameRefreshRate`): el emulador avanza un fotograma por refresco y en pantallas de 90/120 Hz
  adaptables el juego se aceleraba al tocar la pantalla.
- **Iconos de sistemas:** PNG del tema "FlatUI" de RetroArch (`retroarch-assets`, CC BY 4.0, crédito en `README.md`)
  en `retrograde-app-shared/src/main/res/drawable-nodpi/game_system_*.png`, sobre fondo oscuro de tarjeta.
- **Opciones de cores:** Ajustes → Otros → Cores lista todos los sistemas (`CoresSelectionScreen.kt`) y cada uno abre
  `CoreOptionsSettingsScreen.kt` (ruta `settings/cores/{systemId}`, también en la TV). Las opciones y sus valores los
  envía el core al jugar y se guardan en `lib/core/CoreOptionsCache.kt` (Harmony, el juego corre en el proceso `:game`).
  Las filas son las mismas que en el menú del juego (`shared/coreoptions/CoreOptionsItems.kt`) y comparten los valores.
  Valores por defecto de la app en `GameSystem.kt` (`defaultSettings`): Dynarec de PlayStation y JIT de DS activados.
  **Por juego:** en los juegos sin jugar cuya consola tiene más de un núcleo, la ficha y el menú de pulsación larga
  (móvil y TV) muestran "Seleccionar núcleo" (`canSelectCore` en `shared/details/GameDetailsActions.kt`), que abre la
  misma pantalla que "Configurar núcleo". Este solo aparece tras jugar y si hay algo que configurar
  (`canConfigureCore`: más de un núcleo en la consola, u opciones expuestas por la app que el núcleo informó; Snes9x,
  MAME 2003-Plus, ProSystem, Beetle PCE Fast, Beetle NGP y DOSBox Pure no tienen ninguna). "Configurar núcleo" de un juego y el menú
  dentro del juego tienen las pestañas "Solo este juego"
  (a la izquierda y elegida por defecto) y "Todos los juegos de [consola]" (`CoreOptionsScope`,
  `CoreOptionsScopeSelector.kt`). Los valores de un juego se guardan como `cvg_<gameId>_<opción>` y el core propio como
  `pref_key_game_core_selection_<gameId>`; al jugar mandan sobre los de la consola (`CoreVariablesManager`,
  `CoresSelection.getCoreConfigForGame`). Las opciones cambiadas llevan la marca "Este juego".
  **Relación de aspecto** (grupo "Pantalla"): Nintendo 64 (`mupen64plus-aspect`: 4:3 y 16:9 adjusted = pantalla ancha;
  el 16:9 estirado se ocultó porque no se ve bien) y SwanStation (`swanstation_Display_AspectRatio` + widescreen hack).
  mupen64plus-next solo lee el aspecto y la resolución al cargar el juego: si cambian en el menú del juego, la app guarda
  el punto actual, cierra el juego y lo vuelve a abrir desde ahí (`STARTUP_ONLY_OPTIONS` en
  `GameViewModelRetroGameView`, `RESULT_RELOAD` en `BaseGameActivity`, `GameLaunchTaskHandler.handleGameReload`).
  SwanStation aplica el aspecto al momento. PCSX ReARMed no la tiene:
  se indica que hay que elegir SwanStation para ese juego. LibretroDroid no permite forzar la proporción desde la app.
- **Cores (descarga):** la app los baja de `github.com/branches78/PlayRetroCores` (fork de LemuroidCores), etiqueta
  `pr-1.0.0`, ruta `lemuroid_core_<core>/src/main/jniLibs/<abi>/lib<core>_libretro_android.so`
  (`baseUri` en `lemuroid-app-ext-free/.../CoreUpdaterImpl.kt`; `CORES_VERSION` y las huellas de cada core en
  `CoreChecksums.kt`, junto a él). Para añadir o actualizar un core: subirlo a `master` de PlayRetroCores, crear una
  etiqueta nueva, cambiar `CORES_VERSION` y regenerar las huellas (si no coinciden, la app rechaza el core).
  PlayStation tiene dos cores: PCSX ReARMed (por defecto) y SwanStation (necesita BIOS scph5500/5501/5502).
  Los archivos extra de PSP (`assets/ppsspp.zip`, versión 1.15) también se bajan de PlayRetroCores, por el commit
  de esa versión (`PPSSPPAssetsManager.kt`). Las descargas de cores se hacen a un `.part` y se renombran al terminar.
- **Carpeta de red (SMB):** "Directorio" en Ajustes (móvil y TV) abre "Origen de tus juegos"
  (`mobile/feature/settings/smb/LibrarySourceDialog.kt`): carpeta del dispositivo (el selector de siempre) o carpeta de
  red; solo una a la vez (`SmbConfigStore.librarySource`; los proveedores local y SAF no listan nada con SMB y al
  cambiar se re-escanea, así que desaparecen los juegos del otro origen). El formulario (`SmbSettingsScreen.kt`, ruta
  `settings/smb`) guarda servidor, ruta desde el recurso compartido, usuario y contraseña (cifrada con una clave del
  almacén de Android, `SmbConfigStore`); botones Probar, Guardar y Cancelar centrados (Probar no se desactiva mientras
  prueba, para no perder el foco). En la TV lleva el título arriba y los campos solo abren el teclado al pulsar OK
  (`SmbTextField`); la tecla del teclado es "siguiente". Botones de diálogos y formularios con borde blanco al
  enfocar (`utils/android/FocusBorder.kt`). Librería **smbj** 0.13.0 (SMB2/3, con
  BouncyCastle). `lib/storage/smb/`: `SmbShare` (conexión, listar, leer), `SmbStorageProvider` (URIs
  `smb://servidor/recurso/ruta`, una conexión reutilizada, lectura en bloques de 1 MB; los núcleos que leen por descriptor de archivo —PCSX
  ReARMed, PPSSPP, Citra— leen el juego directamente del NAS con `SmbProxyFile`, bloques de 1 MB y 8 en memoria, y
  si la conexión se cierra a mitad de partida vuelve a conectar y reintenta hasta 4 veces (sin timeout de socket,
  que cortaba conexiones solo por estar quietas, y cerrando las rotas en segundo plano para no esperar 30 s); los
  demás copian la ROM a la caché `smb-games` la primera vez, con `.part` mientras se copia). `DocumentFileParser` lee con la función de cada proveedor. Con la biblioteca vacía, "Elegir
  carpeta" (Inicio del móvil y tarjeta de Inicio/Consolas en TV) abre `shared/settings/LibrarySourceActivity.kt`:
  el mismo diálogo de origen y, si se elige SMB, el formulario en la misma actividad.
  Mientras se ve "Cargando tus juegos" en la TV no se muestran la captura ni los textos de la lista inmersiva.
  **Sincronización por SMB** (`lib/savesync/SmbSaveSync.kt`): en la variante sin Google es la única
  (`lemuroid-app-ext-free/.../SaveSyncManagerImpl.kt`); en la de Play se elige Google Drive o la carpeta de red
  (`pref_key_save_sync_provider`, "Sincronizar con" en Ajustes → Sincronización). Solo con la biblioteca en SMB. Partidas
  (`.srm`) y estados (con el núcleo en el nombre: `Juego.cue.pcsx_rearmed.state`, `.slotN`, `.metadata` y la `.jpg` de
  cada ranura) van junto a la ROM en el NAS; si existe en un lado se copia, y si cambió en los dos gana el más reciente
  (lo sincronizado se recuerda en las preferencias `smb_save_sync`). Continuar jugando (la fecha más reciente) y
  Favoritos (gana el último cambio; en la primera sincronización de un dispositivo se suman los de los dos lados) en
  `playretro-sync.json` en la raíz de la biblioteca. Si ese archivo existe pero no se puede leer, no se escribe (para no
  borrar lo de otros dispositivos). Solo se trabaja si hay cambios: huella local (archivos, favoritos, fechas, núcleos)
  y `revision` del JSON, que sube cada vez que un dispositivo sube algo; si ninguna cambió, termina enseguida. Se
  sincroniza al abrir la app (si la automática está activa, `SaveSyncWork.enqueueStartupWork`), al salir de un juego,
  con la nube del móvil, la nube junto a la lupa en la TV (`tv_sync_orb`, solo si está configurada) y con
  "Sincronización manual". Mientras corre se ve abajo "Sincronizando partidas, por favor espere"
  (`shared/savesync/SaveSyncBanner.kt`, `PendingOperationsMonitor.saveSyncRunning`; solo cuando hay algo que copiar: `sync(cores, onCopyStarted)` marca el progreso `PROGRESS_COPYING` del trabajo) y no se pueden
  abrir juegos; la notificación de Android solo sale si la app pasa a segundo plano. En la pantalla de sincronización
  las filas no se desactivan durante la sincronización (para no perder el foco en TV: se ignoran los clics), se
  actualiza sola al terminar y los núcleos de "Sincronizar estados" van en orden alfabético.
  El JSON lleva también `writer` (id de cada escritura): si otro dispositivo escribió la misma versión a la vez, el
  primero lo detecta y une favoritos como en una primera sincronización. El estado (favoritos sincronizados, versión,
  id, huella) se guarda solo después de escribir (`prepareWrite` / `commit`). Si el JSON está dañado, se siguen
  sincronizando partidas y estados, no se escribe y "Sincronización manual" muestra `sync_metadata_unreadable`.
  Favoritos, Continuar jugando, número de versión y huella van en `lib/savesync/SyncMetadata.kt`, compartido con
  Google Drive (variante `play`): ahí `playretro-sync.json` va en la carpeta privada de la app en Drive, con las
  mismas reglas y la misma comprobación rápida (registros en las preferencias `gdrive_save_sync`; sin probar, faltan
  credenciales de Google Cloud). Los botones de sincronizar (nube del móvil, nube de la TV, "Sincronización manual")
  solo se activan si hay cambios: `SaveSyncManager.hasChanges`, comprobado por `shared/savesync/SaveSyncChanges.kt`
  al abrir o volver a la pantalla principal, cada minuto y al terminar una sincronización.
  Cuando hay cambios, cada carpeta de juegos del NAS se lista una sola vez (`RemoteFolders`) y solo se consulta
  aparte un archivo después de copiarlo; antes se preguntaba por cada archivo posible de cada juego.
  Todos los dispositivos deben usar la misma carpeta raíz de la biblioteca: `playretro-sync.json` va en esa raíz y
  con raíces distintas cada uno tendría su propio archivo (decisión del usuario: no se unifica por código).
  Las notificaciones de Android de escaneo, cores y sincronización solo salen si la app pasa a segundo plano
  (`utils/android/WorkManagerUtils.kt`, `withNotificationInBackground`). Nube de la TV: `ic_tv_sync_orb` (24 dp, sin
  color fijo, la tiñe el orbe). Sonido de navegación en las pantallas Compose de TV (Ajustes, Acerca de, ficha,
  menú contextual y menú de partida): `utils/android/TVNavigationSounds.kt`.
  **Filtro del escaneo SMB:** solo se tienen en cuenta archivos con extensiones de ROMs de los sistemas, discos (`bin`,
  `cue`, `img`, `chd`, `m3u`…), BIOS y `zip`; el resto (p. ej. juegos de PC) se salta sin leerlo.
- **Progreso del escaneo:** `LemuroidLibrary.indexLibrary` lista primero todos los archivos y después los procesa,
  avisando del progreso (`IndexProgress`, por cada archivo nuevo identificado, no solo por lote de 200); después `LibraryIndexWork` descarga los cores de los sistemas encontrados
  (antes lo hacía `CoreUpdateWork` aparte). Publica las dos etapas con `setProgress` (`LibraryLoadingProgress`:
  archivos hasta el 85 %, cores el resto) y `PendingOperationsMonitor.directoryScanProgress()` lo lee. Con la biblioteca vacía, Inicio (móvil y TV) y Consolas (TV)
  muestran "Cargando tus juegos" con barra y porcentaje en lugar del cuadro de la carpeta (`TVEmptyLibraryCard.kt`,
  `HomeLibraryLoading` en `HomeScreen.kt`); al re-escanear, la barra sale bajo "Directorio" en Ajustes
  (`LibraryScanProgress.kt`). Ya no hay línea de progreso en el móvil ni círculo de carga en la TV.
- **Ficha del juego:** al elegir un juego se abre su ficha en lugar de jugarlo (ajuste "Mostrar ficha del juego",
  `pref_key_show_game_details`, activado por defecto, en Ajustes → ROMs; `GameInteractor.showDetailsOnClick`).
  Continuar jugando siempre juega directamente y es la única fila que conserva el menú de pulsación larga; en el resto,
  con la ficha activada, la pulsación larga también abre la ficha. Datos compartidos en `shared/details/`
  (`GameDetailsViewModel`: juego observado con `GameDao.observeById`, descripción de Wikipedia y "Más de
  [consola]"; `GameActionsHost`: las mismas acciones del menú, con el diálogo de nombre y el selector de carátula).
  TV: `tv/details/TVGameDetailsFragment.kt` (destino `navigation_game_details` del grafo de TV, se abre y cierra con
  fundido `res/anim/tv_details_fade_*`), captura con `TVBackdropView`, Jugar/Reanudar en píldora con nombre y el resto
  como iconos redondos cuyo nombre sale debajo al enfocarlos (la fila se desplaza hasta el enfocado si no cabe), Jugar/Continuar enfocado al abrir, "Configurar núcleo" abre el diálogo de opciones
  (`TVGameContextMenu.show(..., startWithCoreOptions = true)`). La ficha se muestra entera cuando están los datos
  locales (`State.isReady`); la descripción ocupa las líneas que necesita (hasta 4) y no se dibuja si no hay, así que lo
  demás sube; solo si no cabe se enfoca con ↑ desde los botones, muestra "Leer más" y con OK se abre completa en un
  diálogo (↑ ↓ la desplazan). Botones de 44 dp de alto. "Más de…" usa tarjetas como las de Inicio (tamaño, fondo, título y
  subtítulo) con margen para el zoom; la ficha se mide con su alto completo (`wrapContentHeight(unbounded = true)`)
  para que esa fila no se recorte. Al volver, el foco regresa a la carátula abierta en Inicio
  (`TVHomeFragment.gameToRefocus`; `TVMainTabsFragment` devuelve el foco al contenido si lo tenía en `onPause`).
  En la TV el borde de foco es blanco (tarjetas `tv_card_focus_foreground.xml`, ficha y Acerca de).
  Tamaños: títulos de fila 20 sp en TV y `titleMedium` en móvil, siempre mayores que los nombres de los juegos.
  Móvil: `mobile/feature/details/GameDetailsScreen.kt` (ruta `MainRoute.GAME_DETAILS`; bajo la captura solo el
  logo o nombre y `consola · compañía · país`, sin carátula). Es inmersiva: la captura sube hasta el borde superior,
  por detrás de la barra de estado, sin barra superior y con la flecha de volver y la lupa encima
  (`OverlayIconButton`); la descripción larga se recorta a 2 líneas más "Leer más" debajo; mantiene la barra inferior, que marca la pestaña desde la que se abrió
  (argumento `tab`, `navigateToGameDetails` / `mainTab`); sin la línea "Núcleo" (tampoco en la TV). La ficha de TV y la del móvil
  muestran los textos en el mismo orden: título, `consola · desarrollador · región`, descripción y "Última partida". Al volver, Inicio, juegos de una consola, Favoritos y Búsqueda conservan su posición: Inicio lee su
  `StateFlow` sin estado vacío inicial, las listas paginadas van con `cachedIn`, su estado de desplazamiento se guarda
  fuera de la lista y no se muestra "vacío" mientras cargan; Búsqueda solo borra el texto al entrar desde la lupa.
  Mientras se abre otra pantalla encima, la de atrás mantiene sus márgenes (`stablePadding` en
  `MainNavigationRoutes.kt`) para no crecer y perder posición.
  Dispositivos externos no dibuja sus opciones hasta tener los mandos (`State.isLoaded`); sin mandos activados, las
  asignaciones y atajos emiten listas vacías (`InputDeviceManager`), antes no emitían nada. Capturas en `shared/covers/GameScreenshots.kt` y recorte de franjas en `TrimBlackBordersTransformation`.
  Logo del juego (`Named_Logos` del mismo servidor, `GameScreenshots.logoUrl`) en lugar del título en la lista
  inmersiva (`tv_immersive_logo`, hasta 360×72 dp), la ficha de TV (480×96 dp) y la del móvil (56 dp de alto)
  (`shared/covers/GameLogo.kt`, `GameLogoOrTitle`); mientras carga o si no hay logo (muchos juegos, y PSP y DS
  nunca lo tienen) se ve el título en texto.
- **Menú de partida (L3 + R3):** el mismo en el móvil y en la TV (`mobile/feature/gamemenu/GameMenuActivity.kt`;
  la TV ya no usa el menú de Leanback): ventana modal centrada sobre el juego (`CenteredMenu`, hasta 520 dp de ancho y
  560 dp de alto, fijo para que no crezca al aparecer; tocar fuera la cierra; se abre y cierra con fundido), con el juego
  y `consola · núcleo` centrados arriba y sin botón de cerrar (solo la flecha en las subpantallas). Atrás vuelve o
  cierra con una pulsación (`dispatchKeyEvent`: Compose la usaba antes para sacar el foco de cada grupo). Orden: Salir del juego,
  Reiniciar, Guardar, Cargar, Silenciar audio, Avance rápido, Editar controles y Sensor de inclinación (no en la TV),
  Cambiar disco y Opciones (`GameMenuHomeScreen.kt`). Cada pantalla enfoca su primera opción al abrirse
  (`SettingsFocusDestination`, compartido con Ajustes de TV).
- **Acerca de:** misma información en la pestaña "Acerca de" de la TV (`tv/about/TVAboutFragment.kt`) y en la del
  móvil, junto a Ajustes (`MainRoute.ABOUT`): `shared/about/AboutContent.kt` (nombre y versión centrados, sin icono;
  qué es PlayRetro, ayuda y licencia/créditos).
- **Búsqueda (móvil):** ya no es pestaña; se abre con la lupa de la barra superior (donde estaba el botón de ayuda,
  que se quitó) y tiene flecha para volver (`MainRoute.hasBackButton`).
- **Favoritos (móvil):** ya no es pestaña. Con más de 10 favoritos, la fila de Inicio termina en una tarjeta
  "Mostrar todo" que abre `mobile/feature/favorites/FavoritesScreen.kt` (ruta `MainRoute.FAVORITES`), igual que en la TV.
  Cómo volver a hacerla pestaña: `saved-code/favorites-tab/RESTORE.md`.

## Revisiones

- Ronda 1 (2026-10-21): corregidos todos los hallazgos (código muerto de Ajustes y asignación de mandos de TV,
  textos sin uso, descargas de cores atómicas y verificadas con `CoreChecksums.kt`, Zip Slip en ROM y en archivos
  de PSP, enlaces externos inválidos, BIOS de SwanStation, carátulas por HTTPS, firma fuera del código en
  `keystore.properties`). Al cambiar `CORES_VERSION` hay que regenerar `CoreChecksums.kt` con `git ls-tree -r <tag>`.
- Ronda 2 (2026-10-22): corregidos todos los hallazgos. La limpieza de la biblioteca solo se hace si el escaneo
  terminó bien (antes "Detener" o un error borraban juegos), la caché de opciones no guarda como valor de la consola
  el propio de un juego, descargas de cores de una en una (`Mutex` en `CoreUpdaterImpl`), progreso que no retrocede,
  "Cargando tus juegos" guardado fuera de la pantalla (`EmptyLibraryLoading`) y código muerto.
  Pendiente menor: los valores propios de un juego no se borran al quitarlo de la biblioteca.

## Pendientes conocidos

- Correcciones de navegación con el mando en TV (el usuario las irá indicando).
- Sincronización con Google Drive (variante `play` + configuración en Google Cloud).
- Actualización automática de cores en `PlayRetroCores`: acción de GitHub que descargue periódicamente del buildbot
  de libretro los cores que usa la app y los suba a una rama aparte (`updates`) para probarlos antes de pasarlos
  a `master` y crear una etiqueta nueva. Acordado implementarlo más adelante.
