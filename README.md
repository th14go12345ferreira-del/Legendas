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
O GitHub Actions baixa automaticamente o modelo multilíngue `ggml-tiny.bin` durante a compilação. O modelo tem cerca de 78 MB e é a opção mais leve recomendada para Android; modelos base são maiores e tendem a exigir mais recursos.

Depois de instalado, o APK não precisa baixar o modelo novamente.

## GitHub pelo celular
1. Crie um repositório vazio no GitHub.
2. Envie o conteúdo deste ZIP.
3. Abra **Actions**.
4. Execute **Build APK com Whisper offline**.
5. Abra o workflow concluído e baixe o artefato **LegendaOffline-APK**.
6. No Android 11, abra o APK e permita a instalação de fontes desconhecidas se o sistema solicitar.

## Observação de desempenho
Whisper é computacionalmente pesado. Em celulares mais simples, vídeos longos podem levar bastante tempo e consumir memória/bateria. O modelo tiny foi escolhido para reduzir esse custo.

## Limitação atual
A etapa 2 gera legenda no idioma falado e exporta SRT. Tradução automática para outro idioma ainda é uma etapa separada; não foi fingida como "offline" sem adicionar um modelo de tradução ao APK.

## Base técnica
A integração segue o padrão de uso Android/JNI mostrado pelo projeto oficial whisper.cpp. O projeto oficial recomenda modelos tiny/base para Android.
