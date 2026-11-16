# Jarvis — assistente de voz Android

Assistente de voz em português brasileiro, feito em Kotlin + Jetpack Compose.
O app é **autônomo**: fala direto com o Gemini usando a chave da API do próprio
usuário, sem servidor intermediário.

## O que já funciona

- Onboarding no primeiro uso: chave da API (validada na hora) e escolha da voz.
- **Vozes naturais do Gemini** (12 timbres brasileiros, 6 femininos e 6
  masculinos) para o Jarvis falar. O áudio é recebido em streaming e começa a
  tocar antes de a frase inteira ficar pronta. Se a voz online falhar (sem
  internet, cota ou chave), o app usa automaticamente a voz do aparelho.
- Voz do aparelho como alternativa offline, com perfis (feminina, masculina,
  narrador, robô, animado) e escolha de uma voz específica do motor TTS.
  Em todos os casos há um exemplo falado antes de confirmar.
- Conversa por voz: microfone → Gemini → resposta falada, com histórico curto
  em memória para manter o contexto.
- Escuta contínua em primeiro plano (notificação fixa), inclusive com a tela
  apagada. Só envia ao Gemini depois da palavra de ativação (padrão: **"Jarvis"**).
- Palavra de ativação configurável (aceita uma palavra ou expressão curta).
- Silencia os bipes de início/fim do reconhecedor do Google sem afetar mídia
  nem a voz do Jarvis (pode ser desligado em Ajustes).
- Avatar animado (Aurora, Nexus ou Orbe) na tela inicial e em uma tela cheia que
  acende o aparelho quando o Jarvis é chamado, mesmo com a tela apagada. O
  avatar pisca, mexe a boca enquanto fala e mostra "pensando" durante a consulta.
- **Avatar com a sua foto**: escolha um retrato (pessoa, anime, personagem) e o
  Jarvis anima esse rosto — a boca acompanha a fala, os olhos piscam e o rosto
  reage enquanto você pergunta. O rosto é localizado e animado no próprio
  aparelho; a imagem nunca é enviada para fora.
- Chave da API cifrada com AES/GCM + Android Keystore.

## Requisitos

- Android 7.0+ (minSdk 24).
- Serviço de reconhecimento de voz do Google disponível no aparelho (o mesmo
  usado pelo Google Assistente) e conexão com a internet.
- Chave gratuita do Gemini em <https://aistudio.google.com/apikey>.

## Como rodar

1. Configure o SDK em `local.properties` (`sdk.dir=...`).
2. Compile e instale:

   ```bash
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

3. Abra o app, informe sua chave, valide e escolha a voz.
4. Na tela principal, ligue **Escuta contínua** e permita microfone e
   notificações.
5. Diga **"Jarvis, que horas são?"** (ou a palavra configurada).

## Vozes: Gemini TTS e voz do aparelho

Em **Ajustes → Motor de voz** o usuário escolhe entre:

- **Vozes do Jarvis** (recomendada) — geradas pelo Gemini TTS
  (`gemini-3.8-flash-lite-tts`) com 12 timbres brasileiros selecionados da Voice
  Library do Google. Cada voz tem um estilo curto (*warm and engaging*, *deep,
  calm and unhurried*, ...) que mantém o timbre estável entre respostas. Precisa
  de internet e consome a cota da API.
- **Voz do aparelho** — usa o motor `TextToSpeech` local, funciona offline e
  permite escolher o perfil e uma voz específica instalada no celular.

Como a voz online funciona:

1. O texto é enviado a `POST /v1beta/interactions` com `stream: true`, PCM
   linear de 24 kHz e a voz escolhida em `speech_config`.
2. Os primeiros bytes chegam em cerca de 1,5 s e já são tocados em um
   `AudioTrack` em modo streaming — o Jarvis não espera a frase inteira.
3. O áudio de frases curtas (até 200 caracteres) é guardado em cache
   (`cacheDir/gemini_tts`, chave SHA-1 de voz + estilo + texto, no máximo 40
   arquivos). Frases repetidas, como "Pois não?" e os exemplos de voz, são
   reproduzidas sem rede. O "Pois não?" do despertar é sintetizado assim que a
   escuta contínua liga, então a primeira resposta sai na hora.
4. Se nada puder ser tocado (sem internet, cota esgotada, chave inválida), o
   `VoiceManager` cai automaticamente para a voz do aparelho e registra o motivo
   no log, sem interromper a conversa.

## Escuta contínua: como se comporta

- Usa um `ForegroundService` com tipo `microphone` e uma notificação permanente
  com a ação **Desligar**.
- O reconhecimento de voz é contínuo e usa o serviço do Google; o texto só vai
  ao Gemini após ouvir a palavra de ativação.
- Ao ouvir a palavra sozinha ("Jarvis"), responde "Pois não?" e trata a próxima
  frase como comando.
- Depois de responder, continua aceitando perguntas por cerca de 8 segundos sem
  repetir a palavra de ativação, para conversas de acompanhamento. Se o nome for
  repetido no início da frase, ele é removido antes de enviar ao Gemini.
- Depois de responder, volta a ouvir a palavra de ativação assim que essa janela
  de conversa termina.
- O serviço é encerrado ao desligar pelo app ou pela notificação. Ele **não**
  reinicia sozinho após reiniciar o celular.
- Fabricantes com gestão agressiva de bateria (Xiaomi, Samsung, Motorola) podem
  encerrar o serviço; se isso acontecer, desative a otimização de bateria para
  o Jarvis nas configurações do Android.

### Bipes do reconhecedor

O serviço de voz do Google toca um bipe ao abrir a escuta e outro quando a
sessão termina (por silêncio ou por fala detectada). Como a escuta contínua
reinicia a sessão a cada poucos segundos, esses bipes viram um "metralhadora".

O Jarvis silencia apenas os canais de **notificação** e **sistema** em janelas
curtas: no início da escuta, no fim da fala e nos instantes finais da sessão
(quando o bipe de falha toca antes do callback de erro). O canal de **mídia**
nunca é silenciado, então músicas, vídeos e a voz do Jarvis continuam normais.
O volume anterior é restaurado automaticamente.

Se preferir os bipes originais, desligue **Silenciar bipes do reconhecedor** em
Ajustes. A opção só tem efeito enquanto a escuta contínua está ligada.

## Avatar e despertar da tela

Em **Ajustes → Avatar** você escolhe um dos três avatares vetoriais:

- **Aurora** — acolhedora, cabelos longos e tons rosados.
- **Nexus** — tecnológico, traços retos e tons azuis.
- **Orbe** — minimalista, uma esfera de energia em verde-água.

Eles são desenhados em Compose (sem imagens externas), então ficam nítidos em
qualquer tela e se animam em tempo real: piscam, respiram, olham para cima e
mostram pontinhos ao pensar, e mexem a boca enquanto o Jarvis fala. A tela
inicial usa o avatar escolhido; toque nele para abrir a prévia em tela cheia.

### Avatar com foto ("Minha foto")

Na mesma seção **Ajustes → Avatar** há a opção **Minha foto**: toque em
**Escolher foto** (ou **Trocar foto**) e selecione um retrato. Também é possível
ver o resultado em tela cheia antes de salvar.

Como funciona, tudo no aparelho:

1. A imagem é corrigida pela rotação do EXIF e reduzida para no máximo 1600 px no
   maior lado (economia de memória). A cópia tratada fica em `filesDir/avatar`.
2. O ML Kit (modelo embutido, offline) localiza o maior rosto e extrai os
   contornos da boca e dos olhos.
3. Na hora de falar, um `drawBitmapMesh` deforma apenas os "remendos" da boca e
   dos olhos: a boca abre conforme o volume real do áudio do Gemini (RMS de cada
   fatia de 50 ms) — ou uma envoltória procedural quando a voz é a do aparelho —
   os olhos piscam de tempos em tempos e se deslocam de leve ao "pensar", e a
   cabeça balança sutilmente.
4. A paleta do fundo (brilho, realce e fundo) é tirada das cores da borda da
   foto, para o cenário combinar com o retrato. Fotos em preto e branco ganham
   um tom azulado do tema.

Se a foto não tiver um rosto reconhecível, o app avisa e não troca o avatar.
Escolha um retrato de frente, bem iluminado e em que o rosto ocupe boa parte do
quadro — rostos muito pequenos (menos de ~7% da imagem) não são detectados.

O botão **Remover** volta para os avatares vetoriais; os três continuam
disponíveis e podem ser escolhidos a qualquer momento.

Quando o Jarvis ouve a palavra de ativação, ele:

1. publica uma notificação de alta prioridade com *full-screen intent*;
2. abre a `AvatarActivity` sobre a tela de bloqueio, acendendo a tela;
3. mantém a tela ligada enquanto a conversa estiver ativa;
4. fecha a tela do avatar cerca de 5 segundos depois de voltar ao modo de espera
   pela palavra de ativação.

**Android 14 ou superior:** o sistema exige autorização explícita para abrir
notificações em tela cheia. Se Ajustes mostrar o aviso **"Permita acender a
tela"**, toque em **Autorizar** e ative *Permitir notificações de tela cheia*
para o Jarvis. Sem isso, a notificação vira apenas um aviso e a tela não abre
sozinha com o aparelho bloqueado.

Para o despertar automático, o aparelho precisa estar bloqueado — que é o estado
normal logo após a tela apagar. Se a tela apagar mas o aparelho ainda estiver
destravado, o Android pode bloquear a abertura automática da atividade; nesse
caso a notificação do avatar continua disponível para toque.

## Privacidade e segurança

- A chave fica cifrada no armazenamento local; a chave mestra é gerada no
  Android Keystore e não sai do aparelho.
- As chamadas vão direto para `generativelanguage.googleapis.com` (HTTPS).
  Com as vozes do Gemini, o texto da resposta também é enviado ao mesmo serviço
  para ser transformado em áudio.
- O histórico de conversa fica apenas em memória e pode ser limpo em Ajustes.

## Estrutura

```
app/src/main/java/com/example/jarvisteste/
├── MainActivity.kt              # navegação onboarding → home → ajustes
├── data/
│   ├── SecureStore.kt           # AES/GCM + Android Keystore
│   ├── SettingsStore.kt         # preferências + perfis de voz
│   ├── VoiceOptions.kt          # motor de voz e as 12 vozes do Gemini
│   └── GeminiClient.kt          # chamada REST direta ao Gemini + memória
├── audio/
│   ├── VoiceManager.kt          # fala única: Gemini com fallback para o aparelho
│   ├── GeminiTtsPlayer.kt       # Gemini TTS em streaming, AudioTrack e cache
│   ├── VoiceLevel.kt            # nível da fala (RMS do PCM ou envoltória local)
│   ├── TtsManager.kt            # TextToSpeech, vozes e conclusão de fala
│   └── RecognitionBeepSilencer.kt # silencia bipes do reconhecedor
├── service/
│   └── JarvisListeningService.kt# escuta contínua + palavra de ativação
└── ui/
    ├── OnboardingScreen.kt
    ├── HomeScreen.kt
    ├── SettingsScreen.kt
    ├── avatar/
    │   ├── Avatars.kt           # presets, paletas e humor do avatar
    │   ├── JarvisAvatar.kt      # desenho animado em Compose Canvas
    │   ├── AvatarView.kt        # escolhe entre o avatar vetorial e a foto
    │   ├── PhotoAvatarStore.kt  # modelo da foto + remendos de boca/olhos
    │   ├── PhotoAvatarImporter.kt # ML Kit: rosto, contornos, paleta e EXIF
    │   ├── PhotoAvatarView.kt   # deforma a foto (boca, piscada, balanço)
    │   ├── AvatarScreen.kt      # tela cheia + prévia
    │   ├── AvatarActivity.kt    # atividade sobre a tela de bloqueio
    │   └── AvatarPresenter.kt   # notificação full-screen + wakelock
    └── components/              # cores, cards e seletor de voz
```

O arquivo `jarvis_server.py` é o backend antigo (FastAPI + Gemini no PC) e **não
é mais necessário**; ficou no repositório apenas como referência.

## Limitações conhecidas

- O reconhecimento de voz depende do serviço do Google (na prática, precisa de
  internet para pt-BR). A palavra de ativação é detectada na transcrição, não é
  um hotword offline.
- A qualidade e o "gênero" das vozes do aparelho variam por aparelho; os perfis
  ajustam pitch/velocidade e o usuário pode escolher a voz exata ouvindo o
  exemplo. (Isso não se aplica às vozes do Gemini, que têm timbres definidos.)
- As vozes do Gemini precisam de internet e consomem cota da API; o áudio
  começa a tocar em ~1,5 s, um pouco mais devagar que a voz local. Sem conexão
  ou cota, o Jarvis continua falando com a voz do aparelho.
- A cota gratuita do Gemini tem limite de requisições; o app avisa quando ela é
  atingida.
- O avatar com foto é uma animação 2D por deformação da imagem ("talking
  photo"): boca, piscada e balanço sutil. Não é Live2D nem gera movimento de
  cabeça em 3D, e depende de um rosto detectável na foto escolhida.
- A foto do avatar fica só no aparelho (`filesDir/avatar`) e não é enviada a
  nenhum serviço.
- O despertar da tela com o aparelho bloqueado depende do *full-screen intent*;
  se o fabricante limitar essa permissão, a tela pode não abrir sozinha.

## Próximos passos

- Melhorar o avatar com foto (expressões, mais movimento de cabeça) e permitir
  recortar/enquadrar o rosto na importação.
- Ajustar UX de nome personalizado (já configurável em Ajustes).
- Entrada por texto como alternativa ao microfone.
- Distribuição assinada (release) e revisão das permissões para publicação.
