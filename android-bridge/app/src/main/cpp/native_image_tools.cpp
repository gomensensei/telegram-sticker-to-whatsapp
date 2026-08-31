#include <android/bitmap.h>
#include <jni.h>

#include <algorithm>
#include <cstdint>
#include <cstring>
#include <memory>
#include <new>
#include <vector>

#include <webp/demux.h>

namespace {

struct DecoderHandle {
    std::vector<uint8_t> bytes;
    WebPAnimDecoder* decoder = nullptr;
    WebPAnimInfo info{};

    ~DecoderHandle() {
        if (decoder != nullptr) {
            WebPAnimDecoderDelete(decoder);
        }
    }
};

DecoderHandle* decoderFrom(jlong value) {
    return reinterpret_cast<DecoderHandle*>(static_cast<intptr_t>(value));
}

uint8_t clampByte(int value) {
    return static_cast<uint8_t>(std::max(0, std::min(255, value)));
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_tool48_tgwabridge_NativeAnimatedWebpDecoder_nativeCreate(
    JNIEnv* env,
    jclass,
    jbyteArray input
) {
    if (input == nullptr) {
        return 0;
    }
    jsize size = env->GetArrayLength(input);
    if (size <= 0 || size > 8 * 1024 * 1024) {
        return 0;
    }
    std::unique_ptr<DecoderHandle> handle(
        new (std::nothrow) DecoderHandle()
    );
    if (!handle) {
        return 0;
    }
    handle->bytes.resize(static_cast<size_t>(size));
    env->GetByteArrayRegion(
        input,
        0,
        size,
        reinterpret_cast<jbyte*>(handle->bytes.data())
    );
    if (env->ExceptionCheck()) {
        return 0;
    }
    WebPData data{handle->bytes.data(), handle->bytes.size()};
    WebPAnimDecoderOptions options;
    if (!WebPAnimDecoderOptionsInit(&options)) {
        return 0;
    }
    options.color_mode = MODE_RGBA;
    options.use_threads = 1;
    handle->decoder = WebPAnimDecoderNew(&data, &options);
    if (
        handle->decoder == nullptr
        || !WebPAnimDecoderGetInfo(handle->decoder, &handle->info)
        || handle->info.frame_count < 2
    ) {
        return 0;
    }
    return static_cast<jlong>(
        reinterpret_cast<intptr_t>(handle.release())
    );
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tool48_tgwabridge_NativeAnimatedWebpDecoder_nativeWidth(
    JNIEnv*, jclass, jlong value
) {
    DecoderHandle* handle = decoderFrom(value);
    return handle == nullptr ? 0 : static_cast<jint>(handle->info.canvas_width);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tool48_tgwabridge_NativeAnimatedWebpDecoder_nativeHeight(
    JNIEnv*, jclass, jlong value
) {
    DecoderHandle* handle = decoderFrom(value);
    return handle == nullptr ? 0 : static_cast<jint>(handle->info.canvas_height);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tool48_tgwabridge_NativeAnimatedWebpDecoder_nativeFrameCount(
    JNIEnv*, jclass, jlong value
) {
    DecoderHandle* handle = decoderFrom(value);
    return handle == nullptr ? 0 : static_cast<jint>(handle->info.frame_count);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tool48_tgwabridge_NativeAnimatedWebpDecoder_nativeNextFrame(
    JNIEnv* env,
    jclass,
    jlong value,
    jobject bitmap
) {
    DecoderHandle* handle = decoderFrom(value);
    if (handle == nullptr || handle->decoder == nullptr || bitmap == nullptr) {
        return -1;
    }
    AndroidBitmapInfo info{};
    void* pixels = nullptr;
    if (
        AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS
        || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888
        || info.width != handle->info.canvas_width
        || info.height != handle->info.canvas_height
        || AndroidBitmap_lockPixels(env, bitmap, &pixels)
            != ANDROID_BITMAP_RESULT_SUCCESS
    ) {
        return -1;
    }
    uint8_t* frame = nullptr;
    int timestamp = -1;
    bool ok = WebPAnimDecoderGetNext(handle->decoder, &frame, &timestamp) != 0;
    if (ok) {
        size_t rowBytes = static_cast<size_t>(info.width) * 4U;
        for (uint32_t row = 0; row < info.height; ++row) {
            std::memcpy(
                static_cast<uint8_t*>(pixels) + row * info.stride,
                frame + row * rowBytes,
                rowBytes
            );
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    return ok ? timestamp : -1;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_tool48_tgwabridge_NativeAnimatedWebpDecoder_nativeReset(
    JNIEnv*, jclass, jlong value
) {
    DecoderHandle* handle = decoderFrom(value);
    if (handle == nullptr || handle->decoder == nullptr) {
        return JNI_FALSE;
    }
    WebPAnimDecoderReset(handle->decoder);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_tool48_tgwabridge_NativeAnimatedWebpDecoder_nativeDestroy(
    JNIEnv*, jclass, jlong value
) {
    delete decoderFrom(value);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_tool48_tgwabridge_NativePixelConverter_bitmapToYuv420(
    JNIEnv* env,
    jclass,
    jobject bitmap,
    jbyteArray output,
    jboolean semiPlanar,
    jboolean alphaOnly
) {
    AndroidBitmapInfo info{};
    void* pixels = nullptr;
    if (
        bitmap == nullptr
        || output == nullptr
        || AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS
        || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888
        || (info.width & 1U) != 0
        || (info.height & 1U) != 0
    ) {
        return JNI_FALSE;
    }
    size_t ySize = static_cast<size_t>(info.width) * info.height;
    size_t required = ySize + ySize / 2U;
    if (static_cast<size_t>(env->GetArrayLength(output)) < required) {
        return JNI_FALSE;
    }
    if (
        AndroidBitmap_lockPixels(env, bitmap, &pixels)
            != ANDROID_BITMAP_RESULT_SUCCESS
    ) {
        return JNI_FALSE;
    }
    jbyte* targetBytes = env->GetByteArrayElements(output, nullptr);
    if (targetBytes == nullptr) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return JNI_FALSE;
    }
    uint8_t* target = reinterpret_cast<uint8_t*>(targetBytes);
    uint8_t* yPlane = target;
    uint8_t* uPlane = target + ySize;
    uint8_t* vPlane = semiPlanar
        ? uPlane + 1
        : uPlane + ySize / 4U;
    int chromaStep = semiPlanar ? 2 : 1;
    for (uint32_t row = 0; row < info.height; ++row) {
        const uint8_t* rgba = static_cast<const uint8_t*>(pixels)
            + row * info.stride;
        for (uint32_t column = 0; column < info.width; ++column) {
            int red = rgba[column * 4U];
            int green = rgba[column * 4U + 1U];
            int blue = rgba[column * 4U + 2U];
            if (alphaOnly) {
                red = green = blue = rgba[column * 4U + 3U];
            }
            yPlane[row * info.width + column] = clampByte(
                ((66 * red + 129 * green + 25 * blue + 128) >> 8) + 16
            );
            if ((row & 1U) == 0 && (column & 1U) == 0) {
                size_t chroma = (row / 2U) * (info.width / 2U)
                    + column / 2U;
                uPlane[chroma * chromaStep] = clampByte(
                    ((-38 * red - 74 * green + 112 * blue + 128) >> 8) + 128
                );
                vPlane[chroma * chromaStep] = clampByte(
                    ((112 * red - 94 * green - 18 * blue + 128) >> 8) + 128
                );
            }
        }
    }
    env->ReleaseByteArrayElements(output, targetBytes, 0);
    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}
