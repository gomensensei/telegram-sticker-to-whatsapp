#include <android/bitmap.h>
#include <jni.h>

#include <cstdint>
#include <memory>
#include <new>
#include <string>

#include <webp/encode.h>
#include <webp/mux.h>

namespace {

struct EncoderHandle {
    WebPAnimEncoder* encoder = nullptr;
    WebPConfig config{};
    int width = 0;
    int height = 0;
    int last_timestamp_ms = -1;
    bool finished = false;
    std::string error;

    ~EncoderHandle() {
        if (encoder != nullptr) {
            WebPAnimEncoderDelete(encoder);
        }
    }
};

EncoderHandle* fromHandle(jlong value) {
    return reinterpret_cast<EncoderHandle*>(
        static_cast<intptr_t>(value)
    );
}

void setError(EncoderHandle* handle, const char* fallback) {
    if (handle == nullptr) {
        return;
    }
    const char* detail = handle->encoder == nullptr
        ? nullptr
        : WebPAnimEncoderGetError(handle->encoder);
    handle->error = (
        detail != nullptr && detail[0] != '\0'
            ? detail
            : fallback
    );
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_tool48_tgwabridge_NativeWebpEncoder_nativeCreate(
    JNIEnv*,
    jclass,
    jint width,
    jint height,
    jfloat quality
) {
    if (
        width <= 0
        || height <= 0
        || quality < 1.0f
        || quality > 100.0f
    ) {
        return 0;
    }
    std::unique_ptr<EncoderHandle> handle(
        new (std::nothrow) EncoderHandle()
    );
    if (!handle) {
        return 0;
    }
    WebPAnimEncoderOptions options;
    if (!WebPAnimEncoderOptionsInit(&options)) {
        return 0;
    }
    options.anim_params.loop_count = 0;
    options.anim_params.bgcolor = 0x00000000;
    options.allow_mixed = 1;
    options.minimize_size = 1;
    options.kmin = 9;
    options.kmax = 17;

    if (!WebPConfigInit(&handle->config)) {
        return 0;
    }
    handle->config.lossless = 0;
    handle->config.quality = quality;
    // Method 4 keeps high visual quality while avoiding the very expensive
    // exhaustive search used by method 6 on mid-range Android phones.
    handle->config.method = 4;
    handle->config.alpha_quality = 100;
    handle->config.thread_level = 1;
    handle->config.exact = 1;
    if (!WebPValidateConfig(&handle->config)) {
        return 0;
    }

    handle->width = width;
    handle->height = height;
    handle->encoder = WebPAnimEncoderNew(width, height, &options);
    if (handle->encoder == nullptr) {
        return 0;
    }
    return static_cast<jlong>(
        reinterpret_cast<intptr_t>(handle.release())
    );
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_tool48_tgwabridge_NativeWebpEncoder_nativeAddFrame(
    JNIEnv* env,
    jclass,
    jlong native_handle,
    jobject bitmap,
    jint timestamp_ms
) {
    EncoderHandle* handle = fromHandle(native_handle);
    if (
        handle == nullptr
        || handle->encoder == nullptr
        || handle->finished
        || bitmap == nullptr
        || timestamp_ms < 0
        || timestamp_ms <= handle->last_timestamp_ms
    ) {
        return JNI_FALSE;
    }

    AndroidBitmapInfo info{};
    if (
        AndroidBitmap_getInfo(env, bitmap, &info)
            != ANDROID_BITMAP_RESULT_SUCCESS
        || static_cast<int>(info.width) != handle->width
        || static_cast<int>(info.height) != handle->height
        || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888
    ) {
        handle->error = "Frame must be a 512 x 512 RGBA bitmap.";
        return JNI_FALSE;
    }

    void* pixels = nullptr;
    if (
        AndroidBitmap_lockPixels(env, bitmap, &pixels)
            != ANDROID_BITMAP_RESULT_SUCCESS
        || pixels == nullptr
    ) {
        handle->error = "Cannot lock Android bitmap pixels.";
        return JNI_FALSE;
    }

    WebPPicture picture{};
    bool imported = WebPPictureInit(&picture) != 0;
    if (imported) {
        picture.use_argb = 1;
        picture.width = handle->width;
        picture.height = handle->height;
        imported = WebPPictureImportRGBA(
            &picture,
            static_cast<const uint8_t*>(pixels),
            static_cast<int>(info.stride)
        ) != 0;
    }
    AndroidBitmap_unlockPixels(env, bitmap);

    if (!imported) {
        WebPPictureFree(&picture);
        handle->error = "Cannot import the RGBA animation frame.";
        return JNI_FALSE;
    }
    int added = WebPAnimEncoderAdd(
        handle->encoder,
        &picture,
        timestamp_ms,
        &handle->config
    );
    WebPPictureFree(&picture);
    if (!added) {
        setError(handle, "libwebp rejected an animation frame.");
        return JNI_FALSE;
    }
    handle->last_timestamp_ms = timestamp_ms;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_tool48_tgwabridge_NativeWebpEncoder_nativeFinish(
    JNIEnv* env,
    jclass,
    jlong native_handle,
    jint final_timestamp_ms
) {
    EncoderHandle* handle = fromHandle(native_handle);
    if (
        handle == nullptr
        || handle->encoder == nullptr
        || handle->finished
        || handle->last_timestamp_ms < 0
        || final_timestamp_ms <= handle->last_timestamp_ms
    ) {
        return nullptr;
    }
    if (
        !WebPAnimEncoderAdd(
            handle->encoder,
            nullptr,
            final_timestamp_ms,
            nullptr
        )
    ) {
        setError(handle, "Cannot finalize animation timing.");
        return nullptr;
    }

    WebPData data;
    WebPDataInit(&data);
    if (!WebPAnimEncoderAssemble(handle->encoder, &data)) {
        setError(handle, "Cannot assemble Animated WebP.");
        WebPDataClear(&data);
        return nullptr;
    }
    handle->finished = true;
    jbyteArray result = env->NewByteArray(
        static_cast<jsize>(data.size)
    );
    if (result != nullptr && data.size > 0) {
        env->SetByteArrayRegion(
            result,
            0,
            static_cast<jsize>(data.size),
            reinterpret_cast<const jbyte*>(data.bytes)
        );
    }
    WebPDataClear(&data);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_tool48_tgwabridge_NativeWebpEncoder_nativeError(
    JNIEnv* env,
    jclass,
    jlong native_handle
) {
    EncoderHandle* handle = fromHandle(native_handle);
    const char* message = (
        handle == nullptr || handle->error.empty()
            ? "Unknown Animated WebP encoder error."
            : handle->error.c_str()
    );
    return env->NewStringUTF(message);
}

extern "C" JNIEXPORT void JNICALL
Java_com_tool48_tgwabridge_NativeWebpEncoder_nativeDestroy(
    JNIEnv*,
    jclass,
    jlong native_handle
) {
    delete fromHandle(native_handle);
}
