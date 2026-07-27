# TGWA Bridge

Minimal Android bridge for importing `.wastickers` archives into WhatsApp.

It implements the WhatsApp Android sticker-pack contract:

- `metadata`, `metadata/<identifier>`, `stickers/<identifier>` and
  `stickers_asset/<identifier>/<file>` provider routes
- `com.whatsapp.sticker.READ` read permission
- `com.whatsapp.intent.action.ENABLE_STICKER_PACK` with pack id, authority and
  name
- standard WhatsApp and WhatsApp Business targets

The importer accepts 3–30 WebP stickers, checks 512 × 512 dimensions, file-size
limits, and rejects mixed static/animated packs. ZIP entries must be plain root
filenames, which prevents archive path traversal. Imported packs stay in the
app's private storage.

## Build

Requirements: JDK 17+, Android SDK platform 35, and Gradle 8.7.

```powershell
gradle --no-daemon assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

This is an independent implementation based on the documented provider and
intent contract. No source code or assets were copied from StickerVibe.
