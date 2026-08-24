package br.com.thiago.legendaoffline

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ModelsActivity : AppCompatActivity() {

    private lateinit var modelsContainer: LinearLayout

    private val languages = listOf(
        LanguageModel("Português", "pt"),
        LanguageModel("Inglês", "en"),
        LanguageModel("Espanhol", "es"),
        LanguageModel("Francês", "fr"),
        LanguageModel("Alemão", "de"),
        LanguageModel("Italiano", "it"),
        LanguageModel("Japonês", "ja"),
        LanguageModel("Coreano", "ko"),
        LanguageModel("Chinês", "zh"),
        LanguageModel("Russo", "ru"),
        LanguageModel("Árabe", "ar"),
        LanguageModel("Hindi", "hi"),
        LanguageModel("Turco", "tr"),
        LanguageModel("Holandês", "nl"),
        LanguageModel("Polonês", "pl"),
        LanguageModel("Sueco", "sv"),
        LanguageModel("Dinamarquês", "da"),
        LanguageModel("Finlandês", "fi"),
        LanguageModel("Indonésio", "id")
    )

    data class LanguageModel(
        val name: String,
        val code: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createScreen()
        loadModels()
    }

    private fun createScreen() {

        val scrollView = ScrollView(this)

        val mainLayout = LinearLayout(this)
        mainLayout.orientation = LinearLayout.VERTICAL
        mainLayout.setPadding(24, 24, 24, 24)

        scrollView.addView(mainLayout)

        val title = TextView(this)
        title.text = "Modelos de idiomas"
        title.textSize = 24f
        title.setPadding(0, 0, 0, 12)

        mainLayout.addView(title)

        val description = TextView(this)
        description.text =
            "Baixe os modelos necessários para traduzir legendas offline."

        description.textSize = 16f
        description.setPadding(0, 0, 0, 24)

        mainLayout.addView(description)

        modelsContainer = LinearLayout(this)
        modelsContainer.orientation = LinearLayout.VERTICAL

        mainLayout.addView(modelsContainer)

        val backButton = Button(this)
        backButton.text = "← Voltar para a página principal"

        val backParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        backParams.topMargin = 24

        backButton.layoutParams = backParams

        backButton.setOnClickListener {
            finish()
        }

        mainLayout.addView(backButton)

        setContentView(scrollView)
    }

    private fun loadModels() {

        modelsContainer.removeAllViews()

        languages.forEach { language ->

            addLanguageItem(language)
        }
    }

    private fun addLanguageItem(language: LanguageModel) {

        val itemLayout = LinearLayout(this)
        itemLayout.orientation = LinearLayout.HORIZONTAL
        itemLayout.gravity = Gravity.CENTER_VERTICAL
        itemLayout.setPadding(16, 16, 16, 16)

        val itemParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        itemParams.bottomMargin = 12

        itemLayout.layoutParams = itemParams


        val textLayout = LinearLayout(this)
        textLayout.orientation = LinearLayout.VERTICAL

        val textParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )

        textLayout.layoutParams = textParams


        val languageName = TextView(this)
        languageName.text = language.name
        languageName.textSize = 18f

        textLayout.addView(languageName)


        val statusText = TextView(this)
        statusText.text = "Verificando..."
        statusText.textSize = 14f

        textLayout.addView(statusText)


        val actionButton = Button(this)
        actionButton.text = "..."

        itemLayout.addView(textLayout)
        itemLayout.addView(actionButton)

        modelsContainer.addView(itemLayout)


        lifecycleScope.launch {

            val installed = withContext(Dispatchers.IO) {

                try {

                    val model =
                        TranslateRemoteModel.Builder(language.code)
                            .build()

                    val manager =
                        RemoteModelManager.getInstance()

                    Tasks.await(
                        manager.isModelDownloaded(model)
                    )

                } catch (e: Exception) {

                    false
                }
            }


            if (installed) {

                statusText.text = "Modelo instalado"
                actionButton.text = "✓ Instalado"
                actionButton.isEnabled = false

            } else {

                statusText.text = "Modelo não instalado"
                actionButton.text = "↓ Baixar"
                actionButton.isEnabled = true
            }


            actionButton.setOnClickListener {

                if (!installed) {

                    downloadModel(
                        language,
                        actionButton,
                        statusText
                    )
                }
            }
        }
    }

    private fun downloadModel(
        language: LanguageModel,
        button: Button,
        statusText: TextView
    ) {

        button.isEnabled = false
        button.text = "Baixando..."

        statusText.text =
            "Baixando modelo de ${language.name}..."


        lifecycleScope.launch {

            val result = withContext(Dispatchers.IO) {

                try {

                    val model =
                        TranslateRemoteModel.Builder(language.code)
                            .build()

                    val conditions =
                        DownloadConditions.Builder()
                            .build()

                    val manager =
                        RemoteModelManager.getInstance()

                    Tasks.await(
                        manager.download(model, conditions)
                    )

                    true

                } catch (e: Exception) {

                    false
                }
            }


            if (result) {

                statusText.text = "Modelo instalado"

                button.text = "✓ Instalado"
                button.isEnabled = false

                Toast.makeText(
                    this@ModelsActivity,
                    "Modelo de ${language.name} instalado com sucesso.",
                    Toast.LENGTH_SHORT
                ).show()

            } else {

                statusText.text = "Erro ao baixar"

                button.text = "↓ Baixar"
                button.isEnabled = true

                Toast.makeText(
                    this@ModelsActivity,
                    "Não foi possível baixar o modelo.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
