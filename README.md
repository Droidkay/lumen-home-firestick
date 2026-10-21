# Lumen Home

An ad-free, Apple TV-style home screen for Amazon Fire TV (Fire OS devices).

- Liquid Glass surfaces: widgets, dock, buttons and menus are frosted glass that refracts the wallpaper
- Parallax tilt and light sweep on the selected app
- Favourites dock, Recently used row and an All apps grid; pin, reorder, hide or uninstall from the remote
- Wallpapers: four built-in scenes, colour gradients or your own picture, with an adjustable dark overlay
- Widgets: weather (Open-Meteo, no key), verse of the day (KJV), storage and network
- No ads, no tracking; everything stays on the device

## Install

Download [`apk/LumenHome-latest.apk`](apk/LumenHome-latest.apk) and sideload it. On the Fire TV, the Downloader app can fetch it directly from:

```
https://github.com/Droidkay/lumen-home-firestick/raw/main/apk/LumenHome-latest.apk
```

### Make the Home button open Lumen (one time)

Fire TV hides third-party accessibility services, so enable Lumen's Home-button service with ADB:

```
adb connect <firestick-ip>:5555
adb shell settings put secure enabled_accessibility_services com.droidkay.lumen/com.droidkay.lumen.HomeWatcherService
adb shell settings put secure accessibility_enabled 1
```

"Fire TV Home" in Lumen's top bar still opens Amazon's screen when you need it.

Not supported on Vega OS devices (2025+ Fire TV Stick HD / 4K Select / 4K 2026), which block sideloading.

## Build

`build.sh` builds without Gradle. It needs `aapt2`, `dx`, Android platform jars (API 29) and a JDK; set
`AAPT2`, `DX`, `JAR_RES` and `JAR_CODE` to point at them. It signs with `keystore/lumen.jks`
(not committed — keep your own copy so updates install over the existing app).

`tools/make_art.py` and `tools/make_scenes.py` regenerate the icon, banner and wallpaper scenes (Python + Pillow + NumPy).
