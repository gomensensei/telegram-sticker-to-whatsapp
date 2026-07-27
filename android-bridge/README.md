# TGWA Bridge

Minimal Android bridge for importing `.wastickers` archives into WhatsApp.

It implements the WhatsApp Android sticker-pack contract:

- `metadata`, `metadata/<identifier>`, `stickers/<identifier>` and
  `stickers_asset/<identifier>/<file>` provider routes
- `com.whatsapp.sticker.READ` read permission
- `com.whatsapp.intent.action.ENABLE_STICKER_PACK` with pack id, authority and
  name
- standard WhatsApp and WhatsApp Business targets

The importer accepts up to 60 WebP stickers, checks 512 × 512 dimensions and
the static/animated file-size limits. It inspects RIFF chunks instead of trusting
the filename or metadata: an animated sticker must contain `ANIM`, at least two
`ANMF` frames, frame durations of at least 8 ms, and no more than 10 seconds in
total.

Mixed imports are separated into independent Animated and Static packs, then
each group is split into 3–30-sticker parts. A group below three is rejected
instead of duplicating stickers. Sticker bytes are copied unchanged, preventing
an animated WebP from being flattened during import. The metadata exposed to
WhatsApp uses the matching `animated_sticker_pack` value.

ZIP entries must be plain root filenames, which prevents archive path traversal.
Imported packs stay in the app's private storage. The UI supports WhatsApp and
WhatsApp Business, displays WhatsApp validation errors, and uses the optional
whitelist provider to show when a pack has already been added.

## Build

Requirements: JDK 17+, Android SDK platform 35, and Gradle 8.7.

```powershell
gradle --no-daemon testDebugUnitTest assembleDebug
```

If the checkout path contains non-ASCII characters and Gradle's Windows test
worker cannot read its UTF-8 classpath argument file, map the project to an
ASCII drive letter for the test command. APK compilation itself is unaffected.

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

This is an independent implementation based on the documented provider and
intent contract. No source code or assets were copied from StickerVibe.
