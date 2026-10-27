# Lumen Home

An ad-free, Apple TV-style home screen for Amazon Fire TV (Fire OS devices).

- Liquid Glass surfaces: widgets, dock, buttons and menus are frosted glass that refracts the wallpaper
- Parallax tilt and light sweep on the selected app
- Favourites dock, Recently used row and an All apps grid; pin, reorder, hide or uninstall from the remote
- Wallpapers: four built-in scenes, colour gradients or your own picture, with an adjustable dark overlay
- Screensaver, Apple TV style: your wallpaper and the built-in scenes drift and slowly zoom, cross-fading every 40 seconds, with a big clock, the date, weather and battery. Starts after 3 minutes on the home screen (Customise → Screensaver: off, 2–15 minutes, or Preview). Any button wakes it. To use it as Fire TV's system screensaver too (where Fire OS allows it): `adb shell settings put secure screensaver_components com.droidkay.lumen/.LumenDream`
- Widgets: analog clock, date, weather (Open-Meteo, no key), power from your Felicity inverter (battery %, solar, NEPA on/off, with an alert when NEPA comes or goes), storage, network and a sleep timer; each can be switched off in Customise without stretching the others
- Status pill (Minimal, Weather or Full) that opens Control Center
- Folders: group apps, opened on frosted glass
- Control Center, tvOS style: a glass sheet in the top-right corner with the time, big Sleep and Sleep timer buttons, then Wi-Fi, Bluetooth, Screensaver, Recent Apps, Customise and Fire TV Settings tiles that turn white when selected
- Sleep timer: 15 min to 2 hours, uses the Home-button service to put the TV to sleep
- Recent Apps switcher with a live RAM meter; press Up on a card to close that app
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
