# Maker Full Android implementation status

## Completed in 2.0

- Android Keystore-protected Telegram Bot Token
- Telegram `getStickerSet` / `getFile` whole-pack download
- static WebP/PNG conversion
- TGS rendering with Lottie
- WEBM and local video frame extraction
- trim, position, pinch scale, and background controls
- native libwebp `WebPAnimEncoder` for real Animated WebP output
- adaptive FPS/quality profiles under the WhatsApp size limits
- automatic static/animated separation
- automatic 3–30 pack partitioning with valid rebalanced tail packs
- WhatsApp and WhatsApp Business handoff
- strict pre-handoff WebP structure, duration, dimensions, and size checks

## Completed in 2.1

- desktop-matched dark green card layout
- Telegram and video maker tabbed workflow
- English and Traditional Chinese (Hong Kong) in-app language switch
- secure Token paste, reveal/hide, saved status, forget, and BotFather actions
- state preservation across language changes

## Non-negotiable output contract

The app never exposes an animated pack to WhatsApp unless every sticker:

- is a RIFF `WEBP` with `ANIM` and at least two `ANMF` chunks;
- is exactly 512 × 512;
- has no frame shorter than 8 ms;
- lasts no more than 10 seconds;
- is no larger than 500 KB; and
- belongs to a pack marked `animated_sticker_pack: true`.

Validated Animated WebP bytes are written unchanged to app-private storage and
streamed unchanged by `StickerContentProvider`.

## Possible follow-ups

- native VP9 alpha fallback for Android devices whose hardware media decoder
  does not expose Telegram WEBM alpha;
- batch reorder and custom tray editing;
- local pack export/backup;
- text, drawing, and outline layers;
- persistent release signing for public distribution.
