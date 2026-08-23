#include <jni.h>
#include <string>
#include <sstream>
#include "whisper.h"

extern "C" JNIEXPORT jstring JNICALL
Java_br_com_thiago_legendaoffline_WhisperBridge_transcribe(
        JNIEnv* env,
        jobject,
        jstring modelPath,
        jfloatArray audio,
        jstring language,
        jint threads) {

    const char* model = env->GetStringUTFChars(modelPath, nullptr);
    const char* lang = env->GetStringUTFChars(language, nullptr);

    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    cp.flash_attn = false;

    whisper_context* ctx =
            whisper_init_from_file_with_params(model, cp);

    if (!ctx) {
        env->ReleaseStringUTFChars(modelPath, model);
        env->ReleaseStringUTFChars(language, lang);

        return env->NewStringUTF(
                "ERROR|Falha ao carregar o modelo Whisper."
        );
    }

    jsize n = env->GetArrayLength(audio);
    jfloat* pcm = env->GetFloatArrayElements(audio, nullptr);

    whisper_full_params wp =
            whisper_full_default_params(WHISPER_SAMPLING_GREEDY);

    wp.print_progress = false;
    wp.print_realtime = false;
    wp.print_timestamps = false;

    wp.no_timestamps = false;
    wp.no_context = false;
    wp.single_segment = false;

    wp.language = lang;
    wp.translate = false;

    wp.n_threads = threads > 0 ? threads : 2;

    int rc = whisper_full(ctx, wp, pcm, n);

    std::ostringstream out;

    if (rc != 0) {
        out << "ERROR|Falha durante o reconhecimento.";
    } else {
        int count = whisper_full_n_segments(ctx);

        for (int i = 0; i < count; ++i) {
            int64_t t0 =
                    whisper_full_get_segment_t0(ctx, i) * 10;

            int64_t t1 =
                    whisper_full_get_segment_t1(ctx, i) * 10;

            const char* text =
                    whisper_full_get_segment_text(ctx, i);

            if (text && *text) {
                out << t0
                    << "|"
                    << t1
                    << "|"
                    << text
                    << "\n";
            }
        }
    }

    env->ReleaseFloatArrayElements(
            audio,
            pcm,
            JNI_ABORT
    );

    env->ReleaseStringUTFChars(modelPath, model);
    env->ReleaseStringUTFChars(language, lang);

    whisper_free(ctx);

    return env->NewStringUTF(out.str().c_str());
}
