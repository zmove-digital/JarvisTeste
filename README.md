# Jarvis — assistente de voz Android

Assistente de voz em português brasileiro, feito em Kotlin + Jetpack Compose.
O app é **autônomo**: fala direto com o Gemini usando a chave da API do próprio
usuário, sem servidor intermediário.

## O que já funciona

- Onboarding no primeiro uso: chave da API (validada na hora) e escolha da voz.
- Vozes por perfil: feminina, masculina, narrador, robô e animado, com exemplo
  falado antes de confirmar. Também é possível escolher uma voz específica do
  motor TTS do aparelho.
- Conversa por voz: microfone → Gemini → resposta falada, com histórico curto
  em memória para manter o contexto.
- Escuta contínua em primeiro plano (notificação fixa), inclusive com a tela
  apagada. Só envia ao Gemini depois da palavra de ativação (padrão: **"Jarvis"**).
- Palavra de ativação configurável (aceita uma palavra ou expressão curta).
- Silencia os bipes de início/fim do reconhecedor do Google sem afetar mídia
  nem a voz do Jarvis (pode ser desligado em Ajustes).
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

## Escuta contínua: como se comporta

- Usa um `ForegroundService` com tipo `microphone` e uma notificação permanente
  com a ação **Desligar**.
- O reconhecimento de voz é contínuo e usa o serviço do Google; o texto só vai
  ao Gemini após ouvir a palavra de ativação.
- Ao ouvir a palavra sozinha ("Jarvis"), responde "Pois não?" e trata a próxima
  frase como comando.
- Depois de responder, volta a ouvir a palavra de ativação.
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

## Privacidade e segurança

- A chave fica cifrada no armazenamento local; a chave mestra é gerada no
  Android Keystore e não sai do aparelho.
- As chamadas vão direto para `generativelanguage.googleapis.com` (HTTPS).
- O histórico de conversa fica apenas em memória e pode ser limpo em Ajustes.

## Estrutura

```
app/src/main/java/com/example/jarvisteste/
├── MainActivity.kt              # navegação onboarding → home → ajustes
├── data/
│   ├── SecureStore.kt           # AES/GCM + Android Keystore
│   ├── SettingsStore.kt         # preferências + perfis de voz
│   └── GeminiClient.kt          # chamada REST direta ao Gemini + memória
├── audio/
│   ├── TtsManager.kt            # TextToSpeech, vozes e conclusão de fala
│   └── RecognitionBeepSilencer.kt # silencia bipes do reconhecedor
├── service/
│   └── JarvisListeningService.kt# escuta contínua + palavra de ativação
└── ui/
    ├── OnboardingScreen.kt
    ├── HomeScreen.kt
    ├── SettingsScreen.kt
    └── components/              # cores, cards e seletor de voz
```

O arquivo `jarvis_server.py` é o backend antigo (FastAPI + Gemini no PC) e **não
é mais necessário**; ficou no repositório apenas como referência.

## Limitações conhecidas

- O reconhecimento de voz depende do serviço do Google (na prática, precisa de
  internet para pt-BR). A palavra de ativação é detectada na transcrição, não é
  um hotword offline.
- A qualidade e o "gênero" das vozes variam por aparelho; os perfis ajustam
  pitch/velocidade e o usuário pode escolher a voz exata ouvindo o exemplo.
- A cota gratuita do Gemini tem limite de requisições; o app avisa quando ela é
  atingida.
- Ainda não há avatar com imagem/animação, entrada por texto ou tradução.

## Próximos passos

- Avatar: escolher imagem da galeria e animar ao ouvir/falar.
- Ajustar UX de nome personalizado (já configurável em Ajustes).
- Entrada por texto como alternativa ao microfone.
- Distribuição assinada (release) e revisão das permissões para publicação.
