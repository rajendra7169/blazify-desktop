# Blazify

A music player for Linux and Windows.

Search a catalogue of millions of songs or point it at the music already on your
machine, keep what you want for offline, and follow the words as they're sung.

## Installing

### Linux

Download `blazify_1.0.0_amd64.deb` and install it:

```bash
sudo apt install ./blazify_1.0.0_amd64.deb
```

It lands in the applications menu under Sound & Video. The package brings its
own Java runtime, so nothing else needs installing — except the audio library,
which `apt` pulls in for you:

```
libvlc5, vlc-plugin-base
```

That dependency is deliberate rather than an oversight. The catalogue serves
fragmented MP4, and the lighter media libraries refuse to open it at all.

### Windows

Run `Blazify-1.0.0.msi`. It offers a folder to install into, adds a Start menu
entry and a desktop shortcut, and needs no administrator password. Installing a
newer version upgrades the old one rather than sitting alongside it.

## Building

Needs JDK 21. Everything else the wrapper fetches.

```bash
./gradlew :app:run                  # run it from source
./gradlew :app:createDistributable  # a self-contained folder you can run
./gradlew :app:packageDeb           # .deb   (build on Linux)
./gradlew :app:packageMsi           # .msi   (build on Windows)
./gradlew :app:packageExe           # .exe   (build on Windows)
```

Each installer bundles a runtime, so it is built on the platform it targets —
there is no cross-building.

## Keyboard

| | |
|---|---|
| <kbd>Space</kbd> · <kbd>K</kbd> | Play or pause |
| <kbd>←</kbd> <kbd>→</kbd> · <kbd>J</kbd> <kbd>L</kbd> | Back and forward five seconds |
| <kbd>P</kbd> · <kbd>N</kbd> | Previous and next track |
| <kbd>↑</kbd> <kbd>↓</kbd> | Volume |
| <kbd>M</kbd> | Mute |
| Media keys | Play, pause, next, previous |

The letter shortcuts stand down while you're typing. The media keys never do.

## Where things are kept

Liked songs, history, saved albums, downloads and the list of watched music
folders live in the folder each platform sets aside:

| | |
|---|---|
| Linux | `~/.local/share/blazify` |
| Windows | `%APPDATA%\Blazify` |

Plain JSON, one file per thing, safe to read and safe to delete.

## Layout

| Module | What it holds |
|---|---|
| `app` | The application: window, screens, playback |
| `innertube` | The catalogue client — search, browse, streams |

## Probes

Ways to exercise one piece of the app from a terminal, which is far quicker than
clicking through the window when something needs checking.

```bash
./gradlew :app:probe          --args="let her go"    # search and resolve a stream
./gradlew :app:playProbe      --args="let her go"    # resolve and try to play
./gradlew :app:downloadProbe  --args="let her go"    # keep offline, play the copy
./gradlew :app:lyricsProbe    --args="Kesariya 'Arijit Singh' 269"
./gradlew :app:localScanProbe --args="$HOME/Music"   # scan a folder and play a find
./gradlew :app:homeProbe                             # the feed
./gradlew :app:feedProbe
./gradlew :app:discoverProbe
```

## Licence

GPL-3.0. See `LICENSE`.
