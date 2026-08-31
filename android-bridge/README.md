# TGWA Maker Android

TGWA Maker 2.4.1 is an on-phone Telegram/WhatsApp sticker converter and static
or animated sticker maker.

## Features

- Downloads a complete Telegram sticker set through the official Bot API.
- Incrementally syncs a previously converted Telegram set by its stable
  `file_unique_id`. Existing stickers are read directly from the saved pack;
  only newly detected stickers are downloaded and rendered. Existing 2.1.5
  packs are migrated once by pack order.
- Converts static WebP/PNG, TGS, and WEBM stickers on the phone.
- Separates static and animated stickers into stable 30-sticker Parts. A tail
  Part with 1–2 stickers remains saved and exportable to Telegram; only its
  WhatsApp button stays disabled until it reaches 3.
- Keeps completed 30-sticker Parts byte-for-byte unchanged during later
  Telegram syncs, updates only the affected tail Part, and sends only that
  changed Part through the automatic WhatsApp confirmation flow.
- Makes static stickers from phone images with drag, pinch scaling, position,
  and transparent/black/white backgrounds, compressed to 512 × 512 under
  100 KB.
- Lets a locally made static or animated sticker create a new pack or append
  to a compatible existing pack while preserving that pack's identifier.
- Provides separate start/end selectors with thumbnails, an exact
  `MM:SS.mmm → MM:SS.mmm` range, a selected-segment preview, and a final
  timing preview.
- Applies real 1/8x, 1/4x, 1/2x, 1x, 2x, 4x, or 8x playback timing to frame
  sampling and Animated WebP output. Trim bounds automatically keep the
  adjusted result within 0.2–3 seconds.
- Keeps position, pinch scaling, and transparent/black/white backgrounds.
- Encodes real Animated WebP through JNI and libwebp `WebPAnimEncoder`.
- Imports existing `.wastickers` archives.
- Adds a dedicated on-phone pack-manager tab instead of placing saved packs
  underneath both converters.
- Sends saved packs back through Telegram's official Android sticker-import
  intent. Original Telegram TGS/WEBM files are preserved; locally made
  Animated WebP is converted to moving VP9 WEBM with a separate alpha stream
  when the device supports it. Video packs are created through the official
  Bot API because Telegram's Android import intent does not recognise WEBM.
- Adds packs to WhatsApp or WhatsApp Business through the official provider
  and enable-pack intent contract.
- Uses an obfuscated build-time shared Bot credential by default, with the
  token editor hidden behind `Customize token/API`. A third-party token
  overrides it and is encrypted with Android Keystore on the phone.
- Matches the desktop tool's dark green card layout with Telegram, sticker-maker,
  and saved-pack tabs.
- Defaults the pack publisher to `ゴメン先生` and includes an expressive
  Telegram-to-WhatsApp mascot launcher icon.
- Includes an in-app English / Traditional Chinese (Hong Kong) switch and
  keeps the current form and selected video when the language changes.
- Provides paste, show/hide, saved-state, forget, and BotFather shortcuts for
  the optional custom Token flow. Removing a custom token returns to the
  built-in Bot.
- Shows a prominent staged progress card as soon as conversion starts.
- Opens WhatsApp automatically when conversion completes and continues through
  every generated Part after each official WhatsApp confirmation.
- Sequentially decodes Telegram VP9 WEBM packets through
  `MediaExtractor + MediaCodec`, selects frames by their real presentation
  timestamps, and converts flexible YUV output with its reported row and pixel
  strides. Indexed and timestamp retrieval remain compatibility fallbacks.
- Detects crop-relative and already-cropped plane origins and safely reuses the
  final valid chroma sample when a vendor decoder truncates the last chroma
  row or column of an odd-sized frame.
- Reads decoder YUV planes through Android's stride-aware Java buffer view so
  vendor-specific plane origins cannot turn the output grey with green or
  magenta chroma blocks.
- Decodes each WEBM clip once into a guarded composed-frame cache and reuses
  those frames across quality/FPS attempts. Low-memory devices retain the
  compatibility path.
- Reports the current sticker, download progress, animation progress, and
  whole-pack progress instead of leaving the first sticker at 0%.
- Uses a balanced libwebp search level plus two-minute decode and five-minute
  per-sticker safety limits, while keeping the same strict animation checks.

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

To inject the optional shared Bot for a distribution build, set
`TGWA_DEFAULT_BOT_TOKEN` in the build environment. The plaintext value is not
stored in this repository; Gradle emits only masked Base64 data. This is
obfuscation, not a secure secret boundary: credentials shipped in any APK can
still be recovered by a determined reverse engineer.

If the checkout path contains non-ASCII characters, map it to an ASCII drive
letter before running Gradle on Windows. The APK is written to
`app/build/outputs/apk/debug/app-debug.apk`.

This is an independent implementation based on WhatsApp's documented Android
sticker provider contract. No StickerVibe or SigStick source code or assets
are included.
