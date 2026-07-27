# Maker Full Android plan

## Non-negotiable output contract

The full Android maker must never enable **Add to WhatsApp** unless every
animated sticker passes all of these checks:

- RIFF `WEBP` container with an `ANIM` chunk
- at least two real `ANMF` frame chunks
- exactly 512 × 512
- every frame duration is at least 8 ms
- total duration is no more than 10 seconds
- file size is no more than 500 KB
- pack metadata has `animated_sticker_pack: true`

The resulting Animated WebP bytes must be written unchanged to private storage
and streamed unchanged by `StickerContentProvider`.

## Proposed product flavors

### Bridge Lite

This is the current small APK. It imports `.wastickers`, separates static and
animated stickers, validates files, and exposes them to WhatsApp. It deliberately
does not decode or re-encode video.

### Maker Full

The full flavor will add:

1. Telegram Bot API connection and encrypted token storage using Android
   Keystore.
2. Telegram sticker-set download, including static WebP/PNG, TGS, and VP9 WEBM.
3. A video editor with start time, duration, scale, translation, background,
   and first-frame preview.
4. Frame decoding:
   - Android media APIs where they preserve the required image data.
   - A native VP9/alpha fallback for Telegram video stickers.
   - A Lottie-compatible TGS renderer.
5. Native Animated WebP encoding through `libwebp` `WebPAnimEncoder`.
6. The same adaptive quality/fps search used by the desktop tool.
7. Final `WebpInspector` and pack-level validation before WhatsApp handoff.

## Why native encoding is required

Android's bitmap WebP compression API writes one bitmap. It is suitable for
static stickers and tray icons, but it is not an Animated WebP muxer. A loop
that compresses frames separately cannot create one valid animation and often
leaves only the first frame visible in WhatsApp.

The development environment currently has Android SDK 35 but no Android NDK.
The next implementation phase therefore begins by adding NDK/CMake and building
`libwebp` for selected ABIs. Separate ABI APKs should be produced to avoid the
size of one universal package.

## Clean-room reference decisions

Useful StickerVibe behaviors observed from public app resources were treated as
product behavior references only:

- separate static and animated packs
- cap and rebalance packs around WhatsApp's 3–30 rule
- batch GIF/video workflow and frame selection
- crop, scale, translation, outline/background, text, and drawing tools
- pack repair feedback, import/export/backup
- WhatsApp and WhatsApp Business status

The current version implements the first two, detailed validation feedback, and
both WhatsApp targets. Frame selection, batch editing, reorder/cover editing,
and backup are useful candidates for Maker Full. Ads, billing, analytics,
network-only AI, proprietary code, and proprietary assets are out of scope.
