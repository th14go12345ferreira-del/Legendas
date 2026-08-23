package br.com.thiago.legendaoffline

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView

import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var generate: Button
    private lateinit var translateButton: Button
    private lateinit var export: Button
    private lateinit var subtitles: TextView
    private lateinit var playerView: PlayerView

    private lateinit var sourceLanguageSpinner: Spinner
    private lateinit var targetLanguageSpinner: Spinner

    private var player: ExoPlayer? = null
    private var video: Uri? = null

    private var cues = emptyList<Srt.Cue>()
    private var lastSrt = ""

    private val languages = listOf(
        "Auto",
        "Português",
        "Inglês",
        "Espanhol",
        "Francês",
        "Alemão",
        "Italiano",
        "Japonês",
        "Coreano",
        "Chinês",
        "Russo",
        "Árabe",
        "Hindi",
        "Turco",
        "Holandês",
        "Polonês",
        "Sueco",
        "Dinamarquês",
        "Finlandês",
        "Indonésio"
    )

    private val languageCodes = mapOf(
        "Português" to "pt",
        "Inglês" to "en",
        "Espanhol" to "es",
        "Francês" to "fr",
        "Alemão" to "de",
        "Italiano" to "it",
        "Japonês" to "ja",
        "Coreano" to "ko",
        "Chinês" to "zh",
        "Russo" to "ru",
        "Árabe" to "ar",
        "Hindi" to "hi",
        "Turco" to "tr",
        "Holandês" to "nl",
        "Polonês" to "pl",
        "Sueco" to "sv",
        "Dinamarquês" to "da",
        "Finlandês" to "fi",
        "Indonésio" to "id"
    )

    private val picker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->

            if (uri == null) return@registerForActivityResult

            video = uri

            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }

            status.text =
                "Vídeo selecionado. O áudio será processado no aparelho."

            generate.isEnabled = true
            translateButton.isEnabled = false
            export.isEnabled = false

            player?.release()

            player = ExoPlayer.Builder(this).build().also { exoPlayer ->

                playerView.player = exoPlayer

                exoPlayer.setMediaItem(
                    MediaItem.fromUri(uri)
                )

                exoPlayer.prepare()
            }
        }

    private val saver =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/x-subrip")
        ) { uri ->

            if (uri == null || lastSrt.isEmpty()) {
                return@registerForActivityResult
            }

            try {

                contentResolver.openOutputStream(uri)
                    ?.bufferedWriter()
                    ?.use { writer ->

                        writer.write(lastSrt)
                    }

                status.text = "Legenda SRT exportada com sucesso."

            } catch (e: Exception) {

                status.text =
                    "Erro ao exportar a legenda: ${e.message}"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        generate = findViewById(R.id.generateButton)
        translateButton = findViewById(R.id.translateButton)
        export = findViewById(R.id.exportButton)
        playerView = findViewById(R.id.playerView)
        subtitles = findViewById(R.id.subtitles)

        sourceLanguageSpinner =
            findViewById(R.id.sourceLanguageSpinner)

        targetLanguageSpinner =
            findViewById(R.id.targetLanguageSpinner)

        val sourceAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            languages
        )

        sourceLanguageSpinner.adapter = sourceAdapter

        val targetLanguages =
            languages.filter { it != "Auto" }

        val targetAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            targetLanguages
        )

        targetLanguageSpinner.adapter = targetAdapter

        generate.isEnabled = false
        translateButton.isEnabled = false
        export.isEnabled = false

        findViewById<Button>(R.id.selectButton)
            .setOnClickListener {

                picker.launch(arrayOf("video/*"))
            }

        generate.setOnClickListener {

            val uri = video

            if (uri == null) {

                status.text = "Primeiro selecione um vídeo."
                return@setOnClickListener
            }

            generate.isEnabled = false
            translateButton.isEnabled = false
            export.isEnabled = false

            status.text =
                "Extraindo áudio e gerando legenda offline..."

            lifecycleScope.launch {

                try {

                    val selectedLanguage =
                        sourceLanguageSpinner.selectedItem.toString()

                    val whisperLanguage =
                        if (selectedLanguage == "Auto") {
                            "auto"
                        } else {
                            languageCodes[selectedLanguage] ?: "auto"
                        }

                    val result =
                        withContext(Dispatchers.Default) {

                            val audio =
                                AudioDecoder.decodeTo16kMono(
                                    this@MainActivity,
                                    uri
                                )

                            WhisperBridge.transcribe(
                                copyModel(),
                                audio,
                                whisperLanguage,
                                maxOf(
                                    2,
                                    Runtime.getRuntime()
                                        .availableProcessors() / 2
                                )
                            )
                        }

                    cues = Srt.parse(result)

                    lastSrt = Srt.format(cues)

                    subtitles.text =
                        lastSrt

                    status.text =
                        "Concluído. ${cues.size} legendas geradas."

                    generate.isEnabled = true
                    export.isEnabled = cues.isNotEmpty()
                    translateButton.isEnabled = cues.isNotEmpty()

                } catch (e: Exception) {

                    status.text =
                        "Erro ao processar o vídeo: ${e.message}"

                    generate.isEnabled = true
                }
            }
        }

        translateButton.setOnClickListener {

            if (cues.isEmpty()) {

                status.text =
                    "Primeiro gere a legenda."

                return@setOnClickListener
            }

            val sourceName =
                sourceLanguageSpinner.selectedItem.toString()

            val targetName =
                targetLanguageSpinner.selectedItem.toString()

            if (sourceName == "Auto") {

                status.text =
                    "Para traduzir, escolha o idioma de origem da legenda."

                return@setOnClickListener
            }

            if (sourceName == targetName) {

                status.text =
                    "Escolha idiomas diferentes."

                return@setOnClickListener
            }

            val sourceCode =
                languageCodes[sourceName]

            val targetCode =
                languageCodes[targetName]

            if (sourceCode == null || targetCode == null) {

                status.text =
                    "Idioma não suportado."

                return@setOnClickListener
            }

            translateButton.isEnabled = false
            export.isEnabled = false

            status.text =
                "Preparando tradução..."

            lifecycleScope.launch {

                var translator: Translator? = null

                try {

                    val options =
                        TranslatorOptions.Builder()
                            .setSourceLanguage(sourceCode)
                            .setTargetLanguage(targetCode)
                            .build()

                    translator =
                        com.google.mlkit.nl.translate.Translation
                            .getClient(options)

                    status.text =
                        "Verificando o modelo de tradução..."

                    val conditions =
                        DownloadConditions.Builder()
                            .build()

                    Tasks.await(
                        translator.downloadModelIfNeeded(conditions)
                    )

                    status.text =
                        "Traduzindo legenda no aparelho..."

                    val translatedCues =
                        withContext(Dispatchers.Default) {

                            cues.map { cue ->

                                val translatedText =
                                    Tasks.await(
                                        translator.translate(cue.text)
                                    )

                                Srt.Cue(
                                    cue.start,
                                    cue.end,
                                    translatedText
                                )
                            }
                        }

                    cues = translatedCues

                    lastSrt = Srt.format(cues)

                    subtitles.text =
                        lastSrt

                    status.text =
                        "Legenda traduzida para $targetName."

                    export.isEnabled = true

                } catch (e: Exception) {

                    status.text =
                        "Erro na tradução: ${e.message}"

                } finally {

                    translator?.close()

                    translateButton.isEnabled = true
                }
            }
        }

        export.setOnClickListener {

            if (lastSrt.isEmpty()) {

                status.text =
                    "Não existe legenda para exportar."

                return@setOnClickListener
            }

            saver.launch("legenda.srt")
        }
    }

    private fun copyModel(): String {

        val output =
            File(filesDir, "ggml-tiny.bin")

        if (!output.exists()) {

            assets.open("models/ggml-tiny.bin")
                .use { input ->

                    output.outputStream()
                        .use { outputStream ->

                            input.copyTo(outputStream)
                        }
                }
        }

        return output.absolutePath
    }

    override fun onDestroy() {

        player?.release()

        super.onDestroy()
    }
}
