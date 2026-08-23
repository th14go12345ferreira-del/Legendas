package br.com.thiago.legendaoffline

object WhisperBridge {
    init {
        System.loadLibrary("whisper_jni")
    }

    external fun transcribe(
        modelPath: String,
        audio: FloatArray,
        language: String,
        threads: Int
    ): String
}
