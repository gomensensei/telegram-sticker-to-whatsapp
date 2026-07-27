# TGWA Maker Android

TGWA Maker 2.1.1 is an on-phone Telegram/WhatsApp sticker converter and video
sticker maker.

## Features

- Downloads a complete Telegram sticker set through the official Bot API.
- Converts static WebP/PNG, TGS, and WEBM stickers on the phone.
- Separates static and animated stickers, then splits each type into valid
  3–30-sticker packs without duplicating stickers.
- Provides a video editor with 0.2–3 second trimming, position, pinch scaling,
  and transparent/black/white backgrounds.
- Encodes real Animated WebP through JNI and libwebp `WebPAnimEncoder`.
- Imports existing `.wastickers` archives.
- Adds packs to WhatsApp or WhatsApp Business through the official provider
  and enable-pack intent contract.
- Encrypts the Telegram Bot Token with Android Keystore. It is never included
  in a pack, log, or build artifact.
- Matches the desktop tool's dark green card layout with separate Telegram and
  video-maker tabs.
- Includes an in-app English / Traditional Chinese (Hong Kong) switch and
  keeps the current form and selected video when the language changes.
- Provides paste, show/hide, saved-state, forget, and BotFather shortcuts for
  the on-device Token flow without embedding a Token in the APK.
- Shows a prominent staged progress card as soon as conversion starts.
- Opens WhatsApp automatically when conversion completes and continues through
  every generated Part after each official WhatsApp confirmation.

Animated output is accepted only when it contains `ANIM` and at least two
`ANMF` chunks, is exactly 512 × 512, has frame durations of at least 8 ms,
lasts no more than 10 seconds, and is no larger than 500 KB. Accepted bytes are
copied unchanged into private app storage and streamed unchanged to WhatsApp.
Static output is limited to 100 KB.

## Build

Requirements:

- JDK 17+
- Android SDK platform 35
- Android NDK 27.3.13750724
- CMake 3.22.1
- Gradle 8.7

```powershell
gradle --no-daemon testDebugUnitTest assembleDebug
```

If the checkout path contains non-ASCII characters, map it to an ASCII drive
letter before running Gradle on Windows. The APK is written to
`app/build/outputs/apk/debug/app-debug.apk`.

This is an independent implementation based on WhatsApp's documented Android
sticker provider contract. No StickerVibe source code or assets are included.
