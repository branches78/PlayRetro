# PlayRetro

PlayRetro is a retro game emulator for Android phones, tablets and Android TV, based on
[libretro](https://www.libretro.com/) cores.

It started as a fork of [Lemuroid](https://github.com/Swordfish90/Lemuroid) by Filippo Scognamiglio (Swordfish90)
and is now developed independently. Like the original project, it is distributed under the
GNU General Public License v3.0 (see `COPYING`).

Everything from Lemuroid is already here, plus:

- New Android TV interface: tabs for Home, Consoles, Settings and About, made for the remote control
- Immersive home screen on TV: a screenshot, the logo and the description of the selected game
- New home rows: Because you played, Play again, Classics of… and More from…
- Discover row that changes every day and mixes games from different consoles
- Game details page on phone and TV: screenshot, logo, system, developer, region, description and last played
- Game descriptions from Wikipedia in your language
- Game logos instead of plain titles
- Covers shown with their real shape, and better cover and game matching
- Play your games from a network folder (SMB/NAS), without copying them to the device
- Sync saves, save states, favorites and "Continue playing" between your devices through your network folder
- Choose the core for each game
- Core options for each game or for a whole console, and for every console in Settings
- SwanStation as a second PlayStation core
- Aspect ratio and widescreen options for Nintendo 64 and PlayStation
- Auto Low Latency Mode (ALLM) for smooth, low-lag play
- Moved or renamed games keep their saves, favorites and play history
- Alphabet bar to jump between games on TV
- Grid or list view for consoles on the phone
- Redesigned in-game menu, the same on phone and TV
- Screen orientation setting for games on the phone
- Smooth gameplay on 90 and 120 Hz screens
- Library loading progress, and folders of unsupported consoles are skipped
- Improved "Continue playing" channel on the Android TV home screen
- Navigation sounds on TV

## Supported systems

| System | Core |
|---|---|
| Atari 2600 | [Stella](https://docs.libretro.com/library/stella/) |
| Atari 7800 | [ProSystem](https://docs.libretro.com/library/prosystem/) |
| Atari Lynx | [Handy](https://docs.libretro.com/library/handy/) |
| Nintendo (NES) | [FCEUmm](https://docs.libretro.com/library/fceumm/) |
| Super Nintendo (SNES) | [Snes9x](https://docs.libretro.com/library/snes9x/) |
| Nintendo 64 | [Mupen64Plus-Next](https://docs.libretro.com/library/mupen64plus/) |
| Game Boy / Game Boy Color | [Gambatte](https://docs.libretro.com/library/gambatte/) |
| Game Boy Advance | [mGBA](https://docs.libretro.com/library/mgba/) |
| Nintendo DS | [melonDS](https://docs.libretro.com/library/melonds/) |
| Nintendo 3DS (beta, 64-bit only) | [Citra](https://docs.libretro.com/library/citra/) |
| Sega Genesis / Mega Drive, Sega CD, Master System, Game Gear | [Genesis Plus GX](https://docs.libretro.com/library/genesis_plus_gx/) |
| PlayStation | [PCSX ReARMed](https://docs.libretro.com/library/pcsx_rearmed/) (default) or [SwanStation](https://github.com/libretro/swanstation) |
| PlayStation Portable | [PPSSPP](https://docs.libretro.com/library/ppsspp/) |
| Arcade | [FinalBurn Neo](https://github.com/libretro/FBNeo/), [MAME 2003-Plus](https://docs.libretro.com/library/mame2003_plus/) |
| NEC PC Engine | [Beetle PCE Fast](https://docs.libretro.com/library/beetle_pce_fast/) |
| Neo Geo Pocket / Pocket Color | [Beetle NeoPop](https://docs.libretro.com/library/beetle_neopop/) |
| WonderSwan / WonderSwan Color | [Beetle Cygne](https://docs.libretro.com/library/beetle_cygne/) |
| DOS (beta) | [DOSBox Pure](https://docs.libretro.com/library/dosbox_pure/) |

Cores are downloaded the first time a system is used and verified before they run.
SwanStation needs a real PlayStation BIOS (`scph5500.bin`, `scph5501.bin` or `scph5502.bin`, depending on the game region).

## Building

The app is built with Gradle (Android Gradle Plugin 8, JDK 21):

```
./gradlew :lemuroid-app:assembleFreeDynamicDebug
```

The `freeDynamic` flavor has no Google libraries and downloads cores on demand. Release builds read their signing
data from a `keystore.properties` file (not committed) with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`.

## Credits

- Original project: [Lemuroid](https://github.com/Swordfish90/Lemuroid) by Swordfish90, built on
  [LibretroDroid](https://github.com/Swordfish90/LibretroDroid).
- Emulation cores: [libretro](https://github.com/libretro) cores (GPL and other open source licenses), distributed
  from [PlayRetroCores](https://github.com/branches78/PlayRetroCores), a fork of
  [LemuroidCores](https://github.com/Swordfish90/LemuroidCores) that adds [SwanStation](https://github.com/libretro/swanstation).
  The source code of each core is available in its libretro repository.
- System icons: "FlatUI" theme from [RetroArch assets](https://github.com/libretro/retroarch-assets) by libretro,
  licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
- Box art: [libretro thumbnails](https://github.com/libretro-thumbnails/libretro-thumbnails).
