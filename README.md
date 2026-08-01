# Blazify

A music player for Linux and Windows.

## Building

Needs JDK 21. Everything else the wrapper fetches.

```bash
./gradlew :app:run                              # run it
./gradlew :app:packageReleaseDeb                # .deb
./gradlew :app:packageReleaseAppImage           # .AppImage
./gradlew :app:packageReleaseMsi                # .msi   (run on Windows)
```

## Layout

| Module | What it holds |
|---|---|
| `app` | The application: window, screens, playback |
| `innertube` | The catalogue client — search, browse, streams |

## Licence

GPL-3.0. See `LICENSE`.
