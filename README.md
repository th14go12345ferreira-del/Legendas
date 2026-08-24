# Legenda Offline — Etapa 2

Versão 2 adiciona reconhecimento de fala offline com whisper.cpp, extração de áudio usando MediaExtractor/MediaCodec e exportação SRT.

## Fluxo
1. Selecionar vídeo.
2. O app decodifica a faixa de áudio no próprio aparelho.
3. Converte para mono/16 kHz.
4. Whisper.cpp reconhece a fala sem API ou servidor.
5. Os intervalos de cada segmento viram legendas SRT.
6. O usuário pode exportar `legenda.srt`.

## Modelo
O GitHub Actions baixa automaticamente o modelo multilíngue `ggml-small.bin` durante a compilação. O modelo tem cerca de 466 MB e oferece maior precisão de reconhecimento, porém exige mais memória e processamento do aparelho.

Depois de instalado, o APK não precisa baixar o modelo novamente.

## GitHub pelo celular
1. Crie um repositório vazio no GitHub.
2. Envie o conteúdo deste ZIP.
3. Abra **Actions**.
4. Execute **Build APK com Whisper offline**.
5. Abra o workflow concluído e baixe o artefato **LegendaOffline-APK**.
6. No Android 11, abra o APK e permita a instalação de fontes desconhecidas se o sistema solicitar.

## Observação de desempenho
Whisper é computacionalmente pesado. O modelo small foi escolhido para melhorar a precisão do reconhecimento, mas em celulares mais simples vídeos longos podem levar mais tempo e consumir mais memória e bateria.

## Limitação atual
A etapa 2 gera legenda no idioma falado e exporta SRT. Tradução automática para outro idioma ainda é uma etapa separada; não foi fingida como "offline" sem adicionar um modelo de tradução ao APK.

## Base técnica
A integração segue o padrão de uso Android/JNI mostrado pelo projeto oficial whisper.cpp. O projeto oficial recomenda modelos small para Android.
