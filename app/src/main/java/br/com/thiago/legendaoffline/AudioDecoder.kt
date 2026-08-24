package br.com.thiago.legendaoffline

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

object AudioDecoder {

    private const val TARGET_SAMPLE_RATE = 16000
    private const val DEFAULT_CHUNK_SECONDS = 30

    data class AudioChunk(
        val samples: FloatArray,
        val startMs: Long,
        val endMs: Long
    )

    /**
     * Decodifica o áudio do vídeo em blocos.
     *
     * Exemplo:
     * 00:00 -> 00:30
     * 00:30 -> 01:00
     * 01:00 -> 01:30
     *
     * O áudio inteiro NÃO fica armazenado na memória.
     */
    fun decodeTo16kMonoChunks(
        context: Context,
        uri: Uri,
        chunkSeconds: Int = DEFAULT_CHUNK_SECONDS,
        onChunk: (AudioChunk) -> Unit
    ) {

        require(chunkSeconds > 0) {
            "chunkSeconds deve ser maior que zero."
        }

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {

            context.contentResolver
                .openFileDescriptor(uri, "r")
                ?.use { pfd ->
                    extractor.setDataSource(pfd.fileDescriptor)
                }
                ?: throw IllegalArgumentException(
                    "Não foi possível abrir o vídeo."
                )

            var audioTrack = -1

            for (i in 0 until extractor.trackCount) {

                val format = extractor.getTrackFormat(i)

                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""

                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    break
                }
            }

            if (audioTrack < 0) {
                throw IllegalArgumentException(
                    "O vídeo não possui faixa de áudio."
                )
            }

            extractor.selectTrack(audioTrack)

            val inputFormat = extractor.getTrackFormat(audioTrack)

            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalArgumentException(
                    "Formato de áudio inválido."
                )

            codec = MediaCodec.createDecoderByType(mime)

            codec.configure(
                inputFormat,
                null,
                null,
                0
            )

            codec.start()

            val samplesPerChunk =
                TARGET_SAMPLE_RATE * chunkSeconds

            var chunkBuffer =
                FloatArray(samplesPerChunk)

            var chunkPosition = 0

            var chunkStartMs = 0L

            var totalOutputSamples = 0L

            var inputFinished = false
            var outputFinished = false

            var sourceSampleRate =
                inputFormat.getIntegerOrDefault(
                    MediaFormat.KEY_SAMPLE_RATE,
                    TARGET_SAMPLE_RATE
                )

            var sourceChannels =
                inputFormat.getIntegerOrDefault(
                    MediaFormat.KEY_CHANNEL_COUNT,
                    1
                )

            val bufferInfo = MediaCodec.BufferInfo()

            while (!outputFinished) {

                /*
                 * Alimenta o decoder.
                 */
                if (!inputFinished) {

                    val inputIndex =
                        codec.dequeueInputBuffer(10_000)

                    if (inputIndex >= 0) {

                        val inputBuffer =
                            codec.getInputBuffer(inputIndex)
                                ?: throw IllegalStateException(
                                    "Buffer de entrada indisponível."
                                )

                        inputBuffer.clear()

                        val sampleSize =
                            extractor.readSampleData(
                                inputBuffer,
                                0
                            )

                        if (sampleSize < 0) {

                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )

                            inputFinished = true

                        } else {

                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime,
                                0
                            )

                            extractor.advance()
                        }
                    }
                }

                /*
                 * Recebe áudio decodificado.
                 */
                val outputIndex =
                    codec.dequeueOutputBuffer(
                        bufferInfo,
                        10_000
                    )

                when {

                    outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // Ainda não há saída disponível.
                    }

                    outputIndex ==
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {

                        val outputFormat =
                            codec.outputFormat

                        sourceSampleRate =
                            outputFormat.getIntegerOrDefault(
                                MediaFormat.KEY_SAMPLE_RATE,
                                sourceSampleRate
                            )

                        sourceChannels =
                            outputFormat.getIntegerOrDefault(
                                MediaFormat.KEY_CHANNEL_COUNT,
                                sourceChannels
                            )
                    }

                    outputIndex >= 0 -> {

                        if (bufferInfo.size > 0) {

                            val outputBuffer =
                                codec.getOutputBuffer(outputIndex)

                            if (outputBuffer != null) {

                                outputBuffer.position(
                                    bufferInfo.offset
                                )

                                outputBuffer.limit(
                                    bufferInfo.offset +
                                        bufferInfo.size
                                )

                                val pcmSamples =
                                    pcm16ToMonoFloat(
                                        outputBuffer,
                                        sourceChannels
                                    )

                                val samples16k =
                                    resample(
                                        pcmSamples,
                                        sourceSampleRate,
                                        TARGET_SAMPLE_RATE
                                    )

                                var sourcePosition = 0

                                /*
                                 * Copia para o bloco atual.
                                 *
                                 * Quando o bloco chega a 30 segundos,
                                 * envia para o MainActivity e cria
                                 * outro bloco.
                                 */
                                while (
                                    sourcePosition <
                                    samples16k.size
                                ) {

                                    val remainingInChunk =
                                        samplesPerChunk -
                                            chunkPosition

                                    val remainingInSource =
                                        samples16k.size -
                                            sourcePosition

                                    val copySize =
                                        minOf(
                                            remainingInChunk,
                                            remainingInSource
                                        )

                                    System.arraycopy(
                                        samples16k,
                                        sourcePosition,
                                        chunkBuffer,
                                        chunkPosition,
                                        copySize
                                    )

                                    sourcePosition += copySize
                                    chunkPosition += copySize
                                    totalOutputSamples += copySize

                                    if (
                                        chunkPosition >=
                                        samplesPerChunk
                                    ) {

                                        val chunkEndMs =
                                            (
                                                totalOutputSamples *
                                                    1000L
                                                /
                                                TARGET_SAMPLE_RATE
                                            )

                                        onChunk(
                                            AudioChunk(
                                                samples =
                                                    chunkBuffer,
                                                startMs =
                                                    chunkStartMs,
                                                endMs =
                                                    chunkEndMs
                                            )
                                        )

                                        chunkStartMs =
                                            chunkEndMs

                                        chunkBuffer =
                                            FloatArray(
                                                samplesPerChunk
                                            )

                                        chunkPosition = 0
                                    }
                                }
                            }
                        }

                        codec.releaseOutputBuffer(
                            outputIndex,
                            false
                        )

                        if (
                            bufferInfo.flags and
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            != 0
                        ) {
                            outputFinished = true
                        }
                    }
                }
            }

            /*
             * Envia o último bloco.
             *
             * Exemplo:
             * Se o vídeo terminou em 12:14,
             * o último bloco pode ter apenas
             * 14 segundos.
             */
            if (chunkPosition > 0) {

                val finalSamples =
                    chunkBuffer.copyOf(chunkPosition)

                val chunkEndMs =
                    (
                        totalOutputSamples *
                            1000L
                        /
                        TARGET_SAMPLE_RATE
                    )

                onChunk(
                    AudioChunk(
                        samples = finalSamples,
                        startMs = chunkStartMs,
                        endMs = chunkEndMs
                    )
                )
            }

        } finally {

            try {
                codec?.stop()
            } catch (_: Exception) {
            }

            try {
                codec?.release()
            } catch (_: Exception) {
            }

            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Converte PCM 16-bit para FloatArray mono.
     *
     * Se o áudio tiver 2 canais, mistura os canais.
     */
    private fun pcm16ToMonoFloat(
        buffer: ByteBuffer,
        channels: Int
    ): FloatArray {

        val safeChannels =
            channels.coerceAtLeast(1)

        val byteBuffer =
            buffer.slice().order(
                ByteOrder.LITTLE_ENDIAN
            )

        val totalSamples =
            byteBuffer.remaining() / 2

        val frames =
            totalSamples / safeChannels

        if (frames <= 0) {
            return FloatArray(0)
        }

        val output =
            FloatArray(frames)

        for (frame in 0 until frames) {

            var sum = 0f
            var channelsRead = 0

            for (
                channel in
                0 until safeChannels
            ) {

                if (
                    byteBuffer.remaining() < 2
                ) {
                    break
                }

                val sample =
                    byteBuffer.short
                        .toInt()
                        .toFloat() /
                        32768f

                sum += sample
                channelsRead++
            }

            output[frame] =
                if (channelsRead > 0) {
                    sum / channelsRead
                } else {
                    0f
                }
        }

        return output
    }

    /**
     * Converte qualquer taxa de amostragem
     * para 16 kHz usando interpolação linear.
     */
    private fun resample(
        input: FloatArray,
        fromRate: Int,
        toRate: Int
    ): FloatArray {

        if (input.isEmpty()) {
            return FloatArray(0)
        }

        if (fromRate <= 0) {
            return input
        }

        if (fromRate == toRate) {
            return input
        }

        val outputSize =
            (
                input.size.toDouble() *
                    toRate.toDouble() /
                    fromRate.toDouble()
            )
                .roundToInt()
                .coerceAtLeast(1)

        val output =
            FloatArray(outputSize)

        val ratio =
            fromRate.toDouble() /
                toRate.toDouble()

        for (i in output.indices) {

            val position =
                i.toDouble() * ratio

            val index =
                position.toInt()
                    .coerceIn(
                        0,
                        input.lastIndex
                    )

            val nextIndex =
                (index + 1)
                    .coerceAtMost(
                        input.lastIndex
                    )

            val fraction =
                position - index.toDouble()

            output[i] =
                (
                    input[index] *
                        (1.0 - fraction) +
                    input[nextIndex] *
                        fraction
                ).toFloat()
        }

        return output
    }

    private fun MediaFormat.getIntegerOrDefault(
        key: String,
        defaultValue: Int
    ): Int {

        return try {

            if (containsKey(key)) {
                getInteger(key)
            } else {
                defaultValue
            }

        } catch (_: Exception) {
            defaultValue
        }
    }
}
