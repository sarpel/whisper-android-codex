#include <jni.h>
#include <android/log.h>
#include <whisper.h>
#include <string>
#include <vector>

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

static int g_n_threads = 4;

JNIEXPORT jlong JNICALL
Java_com_app_whisper_nativelib_WhisperNative_initContext(
    JNIEnv* env,
    jobject /* this */,
    jstring model_path,
    jint n_threads) {
    g_n_threads = n_threads > 0 ? n_threads : 4;
    
    const char* path = env->GetStringUTFChars(model_path, nullptr);
    
    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;  // CPU only for compatibility
    
    struct whisper_context* ctx = whisper_init_from_file_with_params(path, cparams);
    
    env->ReleaseStringUTFChars(model_path, path);
    
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_app_whisper_nativelib_WhisperNative_transcribeAudio(
    JNIEnv* env,
    jobject /* this */,
    jlong context_ptr,
    jfloatArray audio_data,
    jint sample_rate,
    jstring language,
    jboolean translate) {
    
    struct whisper_context* ctx = reinterpret_cast<whisper_context*>(context_ptr);
    
    jsize audio_length = env->GetArrayLength(audio_data);
    jfloat* audio = env->GetFloatArrayElements(audio_data, nullptr);
    
    struct whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    
    // Configure parameters
    wparams.n_threads = g_n_threads;  // honor configured thread count
    wparams.translate = translate;
    
    const char* lang = env->GetStringUTFChars(language, nullptr);
    wparams.language = lang;
    
    // Process audio
    int result = whisper_full(ctx, wparams, audio, audio_length);
    
    std::string transcription;
    if (result == 0) {
        int n_segments = whisper_full_n_segments(ctx);
        for (int i = 0; i < n_segments; ++i) {
            transcription += whisper_full_get_segment_text(ctx, i);
            transcription += " ";
        }
    }
    
    env->ReleaseFloatArrayElements(audio_data, audio, JNI_ABORT);
    env->ReleaseStringUTFChars(language, lang);
    
    return env->NewStringUTF(transcription.c_str());
}

JNIEXPORT void JNICALL
Java_com_app_whisper_nativelib_WhisperNative_releaseContext(
    JNIEnv* env,
    jobject /* this */,
    jlong context_ptr) {
    
    struct whisper_context* ctx = reinterpret_cast<whisper_context*>(context_ptr);
    whisper_free(ctx);
}

} // extern "C"
