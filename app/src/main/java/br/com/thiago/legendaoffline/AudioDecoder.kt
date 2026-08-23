package br.com.thiago.legendaoffline

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

object AudioDecoder {
    fun decodeTo16kMono(context: Context, uri: Uri): FloatArray {
        val ex = MediaExtractor()
        context.contentResolver.openFileDescriptor(uri, "r")?.use { ex.setDataSource(it.fileDescriptor) }
            ?: throw IllegalArgumentException("Não foi possível abrir o vídeo.")

        var track = -1
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) { track = i; break }
        }
        if (track < 0) { ex.release(); throw IllegalArgumentException("O vídeo não possui faixa de áudio.") }

        val format = ex.getTrackFormat(track)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE, 16000)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
        ex.selectTrack(track)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val chunks = ArrayList<FloatArray>()
        var done = false
        val info = MediaCodec.BufferInfo()
        while (!done) {
            val inIndex = codec.dequeueInputBuffer(10000)
            if (inIndex >= 0) {
                val input = codec.getInputBuffer(inIndex)!!
                val size = ex.readSampleData(input, 0)
                if (size < 0) {
                    codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    done = true
                } else {
                    codec.queueInputBuffer(inIndex, 0, size, ex.sampleTime, 0)
                    ex.advance()
                }
            }

            var outIndex = codec.dequeueOutputBuffer(info, 10000)
            while (outIndex >= 0) {
                if (info.size > 0) {
                    val out = codec.getOutputBuffer(outIndex)!!
                    out.position(info.offset); out.limit(info.offset + info.size)
                    chunks.add(readPcm16(out, channels))
                }
                codec.releaseOutputBuffer(outIndex, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) done = true
                outIndex = codec.dequeueOutputBuffer(info, 0)
            }
        }
        codec.stop(); codec.release(); ex.release()

        val total = chunks.sumOf { it.size }
        val mono = FloatArray(total)
        var p = 0
        for (c in chunks) { c.copyInto(mono, p); p += c.size }
        return resample(mono, srcRate, 16000)
    }

    private fun readPcm16(buf: ByteBuffer, channels: Int): FloatArray {
        buf.order(ByteOrder.LITTLE_ENDIAN)
        val shorts = buf.remaining() / 2
        val frames = shorts / channels.coerceAtLeast(1)
        val out = FloatArray(frames)
        for (i in 0 until frames) {
            var sum = 0f
            for (c in 0 until channels) sum += buf.short / 32768f
            out[i] = sum / channels.coerceAtLeast(1)
        }
        return out
    }

    private fun resample(src: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to) return src
        val outN = (src.size.toDouble() * to / from).roundToInt().coerceAtLeast(1)
        val out = FloatArray(outN)
        for (i in out.indices) {
            val pos = i.toDouble() * from / to
            val a = pos.toInt().coerceIn(0, src.lastIndex)
            val b = (a + 1).coerceAtMost(src.lastIndex)
            val frac = (pos - a).toFloat()
            out[i] = src[a] * (1f - frac) + src[b] * frac
        }
        return out
    }
}
