package br.com.thiago.legendaoffline

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var generate: Button
    private lateinit var export: Button
    private lateinit var playerView: PlayerView
    private lateinit var subtitles: TextView
    private var player: ExoPlayer? = null
    private var video: Uri? = null
    private var cues = emptyList<Cue>()
    private var lastSrt = ""

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        video = uri
        status.text = "Vídeo selecionado. O áudio será processado no aparelho."
        generate.isEnabled = true
        player?.release()
        player = ExoPlayer.Builder(this).build().also {
            playerView.player = it
            it.setMediaItem(MediaItem.fromUri(uri))
            it.prepare()
        }
    }

    private val saver = registerForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip")) { uri ->
        if (uri != null) contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(lastSrt) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status=findViewById(R.id.status); generate=findViewById(R.id.generateButton)
        export=findViewById(R.id.exportButton); playerView=findViewById(R.id.playerView)
        subtitles=findViewById(R.id.subtitles)

        val spinner=findViewById<Spinner>(R.id.languageSpinner)
        val langs=listOf("auto","pt","en","es","fr","de","it","ja","ko","zh")
        spinner.adapter=ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, langs)

        findViewById<Button>(R.id.selectButton).setOnClickListener { picker.launch(arrayOf("video/*")) }

        generate.setOnClickListener {
            val uri=video ?: return@setOnClickListener
            generate.isEnabled=false; export.isEnabled=false
            status.text="Extraindo áudio e transcrevendo offline…"
            lifecycleScope.launch {
                try {
                    val lang=spinner.selectedItem.toString()
                    val result=withContext(Dispatchers.Default) {
                        val audio=AudioDecoder.decodeTo16kMono(this@MainActivity, uri)
                        WhisperBridge.transcribe(copyModel(), audio, lang, maxOf(2, Runtime.getRuntime().availableProcessors()-1))
                    }
                    cues=Srt.parse(result)
                    lastSrt=Srt.format(cues)
                    subtitles.text=cues.joinToString("\n") { "[${it.start/1000}s] ${it.text}" }
                    status.text="Concluído: ${cues.size} legendas. Nenhum áudio foi enviado para a Internet."
                    export.isEnabled=cues.isNotEmpty()
                } catch (e: Exception) {
                    status.text="Erro: ${e.message ?: "não foi possível processar o vídeo."}"
                } finally { generate.isEnabled=true }
            }
        }
        export.setOnClickListener { saver.launch("legenda.srt") }
    }

    private fun copyModel(): String {
        val out=File(filesDir,"ggml-tiny.bin")
        if (!out.exists()) assets.open("models/ggml-tiny.bin").use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        return out.absolutePath
    }

    override fun onDestroy() { player?.release(); super.onDestroy() }
}
