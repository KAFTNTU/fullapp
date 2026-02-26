# RoboScratch — Android App

Android app з нативним BLE, WebView + Blockly.

## Структура

```
app/src/main/
├── kotlin/com/roboscratch/app/
│   ├── MainActivity.kt        # Головна активність + WebView
│   ├── BleBridge.kt           # JS ↔ Kotlin міст (@JavascriptInterface)
│   └── ble/
│       └── BleManager.kt      # Нативний BLE (сканування, підключення, GATT)
├── assets/
│   └── index.html             # Blockly / Joystick UI
└── AndroidManifest.xml
```

## Як зібрати локально

**Вимоги:** JDK 17, Android SDK

```bash
# 1. Завантажити gradle wrapper jar (один раз)
mkdir -p gradle/wrapper
curl -L -o gradle/wrapper/gradle-wrapper.jar \
  "https://github.com/nickvdyck/webbundle/raw/main/gradle/wrapper/gradle-wrapper.jar"

# 2. Зібрати debug APK
chmod +x gradlew
./gradlew assembleDebug

# APK буде тут:
# app/build/outputs/apk/debug/app-debug.apk
```

## Збірка через GitHub Actions

1. Залити цей проєкт на GitHub
2. GitHub автоматично збере APK при кожному push
3. APK знайдеш у: **Actions → Build APK → Artifacts → RoboScratch-debug**

## BLE

Підтримуються пристрої з:
- Service UUID `0000FFE0` (HM-10, HC-08, та інші BT модулі)
- Nordic UART Service `6E400001...` (ESP32, nRF52)

Дані надсилаються як `Int8Array[m1, m2, m3, m4]` з підтримкою SLIP.

## Дозволи

- `BLUETOOTH_SCAN` — сканування BLE пристроїв
- `BLUETOOTH_CONNECT` — підключення до пристрою
- `ACCESS_FINE_LOCATION` — потрібне для BLE на Android ≤ 11
