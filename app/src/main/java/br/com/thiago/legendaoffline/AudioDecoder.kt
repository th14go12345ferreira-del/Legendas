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

    fun decodeTo16kMono(
        context: Context,
        uri: Uri
    ): FloatArray {

        val extractor = MediaExtractor()

        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            extractor.setDataSource(pfd.fileDescriptor)
        } ?: throw IllegalArgumentException("Não foi possível abrir o vídeo.")

        var track = -1

        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""

            if (mime.startsWith("audio/")) {
                track = i
                break
            }
        }

        if (track < 0) {
            extractor.release()
            throw IllegalArgumentException("O vídeo não possui faixa de áudio.")
        }

        val inputFormat = extractor.getTrackFormat(track)
        val mime = inputFormat.getString(MediaFormat.KEY_MIME)
            ?: throw IllegalArgumentException("Formato de áudio inválido.")

        extractor.selectTrack(track)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(inputFormat, null, null, 0)
        codec.start()

        var sampleRate =
            inputFormat.getInteger(
                MediaFormat.KEY_SAMPLE_RATE,
                16000
            )

        var channels =
            inputFormat.getInteger(
                MediaFormat.KEY_CHANNEL_COUNT,
                1
            )

        val chunks = ArrayList<FloatArray>()

        val bufferInfo = MediaCodec.BufferInfo()

        var inputFinished = false
        var outputFinished = false

        while (!outputFinished) {

            if (!inputFinished) {

                val inputIndex = codec.dequeueInputBuffer(10_000)

                if (inputIndex >= 0) {

                    val inputBuffer =
                        codec.getInputBuffer(inputIndex)
                            ?: throw IllegalStateException(
                                "Buffer de entrada indisponível."
                            )

                    inputBuffer.clear()

                    val sampleSize =
                        extractor.readSampleData(inputBuffer, 0)

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

            val outputIndex =
                codec.dequeueOutputBuffer(bufferInfo, 10_000)

            when {

                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {

                    val outputFormat = codec.outputFormat

                    sampleRate =
                        outputFormat.getInteger(
                            MediaFormat.KEY_SAMPLE_RATE,
                            sampleRate
                        )

                    channels =
                        outputFormat.getInteger(
                            MediaFormat.KEY_CHANNEL_COUNT,
                            channels
                        )
                }

                outputIndex >= 0 -> {

                    if (bufferInfo.size > 0) {

                        val outputBuffer =
                            codec.getOutputBuffer(outputIndex)

                        if (outputBuffer != null) {

                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(
                                bufferInfo.offset + bufferInfo.size
                            )

                            val pcm = readPcm16(
                                outputBuffer,
                                channels
                            )

                            if (pcm.isNotEmpty()) {
                                chunks.add(pcm)
                            }
                        }
                    }

                    codec.releaseOutputBuffer(
                        outputIndex,
                        false
                    )

                    if (
                        bufferInfo.flags and
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    ) {
                        outputFinished = true
                    }
                }
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        val totalSize = chunks.sumOf { it.size }

        if (totalSize == 0) {
            throw IllegalArgumentException(
                "Não foi possível extrair o áudio do vídeo."
            )
        }

        val mono = FloatArray(totalSize)

        var position = 0

        for (chunk in chunks) {
            chunk.copyInto(mono, position)
            position += chunk.size
        }

        return resample(
            mono,
            sampleRate,
            16000
        )
    }

    private fun readPcm16(
        buffer: ByteBuffer,
        channels: Int
    ): FloatArray {

        buffer.order(ByteOrder.LITTLE_ENDIAN)

        val samples =
            buffer.remaining() / 2

        if (samples <= 0) {
            return FloatArray(0)
        }

        val frames =
            samples / channels.coerceAtLeast(1)

        val output = FloatArray(frames)

        for (frame in 0 until frames) {

            var sum = 0f

            for (channel in 0 until channels) {

                if (buffer.remaining() < 2) {
                    break
                }

                val sample =
                    buffer.short.toInt() / 32768f

                sum += sample
            }

            output[frame] =
                sum / channels.coerceAtLeast(1)
        }

        return output
    }

    private fun resample(
        input: FloatArray,
        fromRate: Int,
        toRate: Int
    ): FloatArray {

        if (input.isEmpty()) {
            return FloatArray(0)
        }

        if (fromRate == toRate) {
            return input
        }

        val outputSize =
            (input.size.toDouble() *
                toRate.toDouble() /
                fromRate.toDouble())
                .roundToInt()
                .coerceAtLeast(1)

        val output = FloatArray(outputSize)

        val ratio =
            fromRate.toDouble() /
            toRate.toDouble()

        for (i in output.indices) {

            val position = i * ratio

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
                position - index

            output[i] =
                input[index] +
                    (
                        input[nextIndex] -
                            input[index]
                    ) * fraction.toFloat()
        }

        return output
    }
}
