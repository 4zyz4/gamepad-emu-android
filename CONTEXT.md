# gamepad-emu 领域语言

## 角色

- **手机** — 运行 gamepad-emu 的 Android 设备。充当 HID 外设。
- **主机** — 手机连接的目标设备（Windows PC、Android 设备、苹果设备）。充当 HID 主机。

## 能力

- **经典 HID 外设** — 通过蓝牙经典 `BluetoothHidDevice` API 注册 HID 设备配置文件。用于 Windows 和 macOS 目标。
- **BLE HID 外设** — 通过 BLE GATT HID 服务（HID over GATT 配置文件）通告。用于 iOS 目标。
