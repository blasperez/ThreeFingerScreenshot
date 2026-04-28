# Three Finger Screenshot

An LSPosed/Xposed module that enables **three-finger swipe down** gesture to take screenshots on Android devices.

## Features

- 🖐️ **Three Finger Swipe Down** → Takes a screenshot instantly
- 👍 **Thumb Corner Gesture** (separate module) → Hold thumb in bottom-right corner + diagonal swipe
- ⚡ Lightweight, no background services
- 🔒 Requires Root + LSPosed Framework

## Requirements

- Android 8.0+ (API 26)
- Root access (Magisk recommended)
- [LSPosed Framework](https://github.com/LSPosed/LSPosed) installed

## Installation

1. Download the latest APK from [Releases](https://github.com/blasperez/ThreeFingerScreenshot/releases)
2. Install the APK
3. Open **LSPosed Manager** → **Modules**
4. Enable **Three Finger Screenshot**
5. Set scope: **System Framework** + **SystemUI**
6. Reboot your device
7. Swipe down with 3 fingers to take a screenshot!

## Modules

| Module | Gesture | Package |
|---|---|---|
| Three Finger Screenshot | 3 fingers swipe down | `com.gemini.threefingerscreenshot` |
| Thumb Corner Screenshot | Thumb hold + diagonal swipe | `com.gemini.thumbcornerscreenshot` |

## Building from Source

# com.gemini.threefingerscreenshot
