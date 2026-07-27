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

## Completed in 2.1.1

- prominent staged Telegram conversion progress card
- immediate visual error feedback for missing or invalid inputs
- automatic WhatsApp handoff after conversion
- sequential multi-Part WhatsApp handoff after each official confirmation

## Completed in 2.1.2

- frame-indexed Telegram VP9 WEBM decoding on Android 9+
- bounded eight-frame decode batches to control memory
- timestamp-seek fallback for older or incompatible devices
- regression coverage for 61-frame single-keyframe Telegram WEBM sampling
- explicit non-animation decode errors instead of a misleading 500 KB error

## Completed in 2.1.3

- sequential Telegram WEBM packet decoding with MediaExtractor and MediaCodec
- raw decoded-frame access without timestamp or frame-index seeking
- presentation-timestamp sampling for full clips and trimmed ranges
- flexible YUV plane conversion with crop, row-stride, and pixel-stride support
- frame-index and timestamp compatibility fallbacks retained
- explicit sequential-decoder diagnostics when every device path fails

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
