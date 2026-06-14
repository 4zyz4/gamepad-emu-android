# Gamepad Emu

Turn your Android phone into a wireless gamepad for Windows, Android, or other devices.

Supports Xbox 360 and DualShock 4 controller emulation via Wi-Fi or Bluetooth Classic.

## Features

- **Wi-Fi Mode** — TCP connection, low latency, auto-discovery via UDP broadcast
- **Bluetooth Classic Mode** — HID device profile for direct pairing with Windows, macOS, or handhelds
- **Controller Modes** — Xbox 360 or DualShock 4 emulation
- **Gyro & Accelerometer** — Full IMU support with calibration (DS4 mode)
- **Touchpad** — Touchpad input mapping (DS4 Wi-Fi mode)
- **Custom Layouts** — Preset system with import/export, drag-to-edit layout designer
- **Multiple Display Modes** — Xbox, PlayStation, or Nintendo Switch button labels

## Architecture

```
┌─────────────┐    TCP/UDP/BT     ┌─────────────────┐
│  Android App │ ◄──────────────► │  PC Host (WPF)  │
│  (gamepad)   │    BLE (optional)│  (ViGEm virtual │
└─────────────┘                    │   controller)    │
                                   └─────────────────┘
```

### Components

- **Android App** — Virtual gamepad with D-pad, joysticks, triggers, and IMU sensor integration
- **PC Host (WPF)** — Receives input via Protocol Buffers over TCP or Bluetooth and drives a virtual controller via viGEm

### Protocol

Binary protocol defined in `proto/gamepad_state.proto` using Protocol Buffers. Message framing: 4-byte length prefix (big-endian) + proto payload.

## Requirements

### Windows Host
- Windows 10/11 (64-bit)
- [.NET 8.0 Runtime](https://dotnet.microsoft.com/download/dotnet/8.0)
- [viGEm Driver](https://github.com/nefarius/viGEm) — installed automatically by NuGet package

### Android Device
- Android 7.0+ (API 24+)
- Bluetooth (if using Bluetooth mode)
- Wi-Fi connection to the host PC

## Quick Start

1. Build and run the Windows host:
   ```
   cd ../gamepad_emu_windows
   dotnet run
   ```
2. Build and install the Android app on your phone
3. Connect both devices to the same Wi-Fi network (Wi-Fi mode) or pair via Bluetooth (Bluetooth mode)
4. The host will auto-discover the device and you can start emulating a controller

## Building

### Windows Host
```bash
cd ../gamepad_emu_windows
dotnet restore
dotnet build
dotnet run
```

### Android App
```bash
./gradlew assembleDebug
```

## Project Structure

```
gamepad_emu_android/          # Android application (this repo)
├── android/src/main/java/    # Kotlin source (MainActivity, services, viewmodels)
├── proto/                    # Protocol buffer definitions
└── build/                    # Compiled assets

Shared:
├── proto/gamepad_state.proto # Protocol definition (shared concept)
```

## Protobuf Schema

Shared with the Windows host project. See `proto/gamepad_state.proto` for the full message definitions.

## License

MIT
