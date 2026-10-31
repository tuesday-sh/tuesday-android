# Tuesday.sh Android (`tuesday-android`)

Native Android companion application for [Tuesday.sh](https://tuesday.sh) built with **Kotlin**, **Jetpack Compose**, and **Material 3**.

## Features

- **Hardware-Accelerated Candle Telemetry (`CandleGraphCanvas`)**: High-framerate Compose `Canvas` rendering gliding score indicators with spring physics at 60/120Hz display refresh rates.
- **GCP Gemini 3.8 Flash AI Photo Inventory Wizard**: Progressive device discovery from photos of routers, switches, NAS, and IoT devices. Analyzes chassis profiles, disambiguates with barcodes, serial numbers, QR codes, models, and MAC addresses, and supports instant "Confirm Match" or "Add as Incomplete / Guessed".
- **Home Assistant HACS Integration**: Live sync with HACS Tuesday bridge, local Wi-Fi LAN Guard protection, and manual simulation controls.
- **Fleet Package Diff Tracking**: View pending package updates across APT, APK, Homebrew, YUM, Docker, and Home Assistant HACS with CVE severity badges.

## Architecture

- **UI**: Jetpack Compose (BOM 2024.12.01), Material 3, Navigation Compose.
- **State & Concurrency**: Kotlin Coroutines, `StateFlow`, Lifecycle ViewModel.
- **AI & Network**: GCP Gemini Interactions REST API (`gemini-3.8-flash`), OkHttp, `kotlinx.serialization`.

## Building & Testing

```bash
# Run unit tests
./gradlew test

# Assemble debug APK
./gradlew assembleDebug
```
