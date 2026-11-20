# Jarvis — assistente de voz Android

Assistente de voz em português brasileiro, feito em Kotlin + Jetpack Compose.
O app é **autônomo**: fala direto com o Gemini usando a chave da API do próprio
usuário, sem servidor intermediário.

A interface toda tem um visual **"quase TRON"**: fundo escuro, linhas de
instrumento em ciano, rótulos em caixa alta e um **orbe de energia** que reage à
voz. A arte em `images/hud.png` serviu de referência para a estética — moldura
com cantos recortados, trilhos com marcas, barras de leitura, painéis em fita e
um retículo circular ao redor do orbe —, mas tudo é **desenhado em Compose**,
sem esticar a imagem atrás dos textos. O ícone vem de `icons/`.

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
- **Ações no aparelho por *function calling***: além de conversar, o Jarvis
  executa comandos como abrir aplicativos e playlists no Spotify, abrir conversas
  ou ligar em viva-voz pelo WhatsApp (com confirmação do contato), pesquisar na
  web, fazer chamadas telefônicas, controlar a mídia e criar alarmes/timers.
- Escuta contínua em primeiro plano (notificação fixa), inclusive com a tela
  apagada. Só envia ao Gemini depois da palavra de ativação (padrão: **"Jarvis"**).
- Palavra de ativação configurável (aceita uma palavra ou expressão curta).
- Silencia os bipes de início/fim do reconhecedor do Google sem afetar mídia
  nem a voz do Jarvis (pode ser desligado em Ajustes).
- **Orbe de energia reativo** na tela inicial e em uma tela cheia que acende o
  aparelho quando o Jarvis é chamado, mesmo com a tela apagada. O orbe vibra de
  forma frenética com a sua voz, gira mais rápido ao "pensar" e pulsa quando o
  Jarvis responde.
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
4. Na tela principal, ligue **Escuta contínua** e permita microfone,
   notificações, telefone e contatos. Em **Ajustes → Ações no aparelho**,
   autorize *Aparecer sobre outros apps* para o Jarvis abrir apps sozinho.
5. Para ligar pelo WhatsApp, autorize também **Acessibilidade para ligar no
   WhatsApp** na mesma seção.
6. Diga **"Jarvis, que horas são?"** (ou a palavra configurada).

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

## O orbe e o despertar da tela

O avatar do Jarvis é um único **orbe de energia** desenhado inteiramente em
Compose (`Canvas`), sem imagens externas. Ele reage em tempo real às ondas de
voz:

- **Você fala:** o nível do microfone (`onRmsChanged` do reconhecedor) chega pelo
  `MicLevel`. O orbe vibra de forma frenética, com ataque rápido e queda mais
  lenta, e a cada sílaba o núcleo treme e os raios de energia se alongam.
- **O Jarvis fala:** o nível do áudio (`VoiceLevel`, medido no PCM do Gemini ou
  por uma envoltória procedural na voz do aparelho) faz o orbe pulsar.
- **Pensando:** os anéis giram mais rápido e a energia oscila.
- **Parado:** apenas respira devagar.

Três anéis giram em velocidades e sentidos diferentes, há raios ao redor do
núcleo e um traçador branco circula por cima — o conjunto dá o movimento inquieto
típico das interfaces do filme. Em **Ajustes → Avatar** há uma prévia do orbe e o
botão **Ver em tela cheia**; na tela inicial, toque no orbe para abrir a mesma
prévia.

Quando o Jarvis ouve a palavra de ativação, ele:

1. publica uma notificação de alta prioridade com *full-screen intent*;
2. abre a `AvatarActivity` sobre a tela de bloqueio, acendendo a tela;
3. mantém a tela ligada enquanto a conversa estiver ativa;
4. fecha a tela do orbe cerca de 5 segundos depois de voltar ao modo de espera
   pela palavra de ativação.

**Android 14 ou superior:** o sistema exige autorização explícita para abrir
notificações em tela cheia. Se Ajustes mostrar o aviso **"Permita acender a
tela"**, toque em **Autorizar** e ative *Permitir notificações de tela cheia*
para o Jarvis. Sem isso, a notificação vira apenas um aviso e a tela não abre
sozinha com o aparelho bloqueado.

Para o despertar automático, o aparelho precisa estar bloqueado — que é o estado
normal logo após a tela apagar. Se a tela apagar mas o aparelho ainda estiver
destravado, o Android pode bloquear a abertura automática da atividade; nesse
caso a notificação do orbe continua disponível para toque.

## Ações no aparelho (function calling)

Além de conversar, o Jarvis **executa comandos** no celular. Em vez de adivinhar
a intenção com expressões regulares, o app declara ao Gemini um conjunto de
ferramentas (*function calling*); o modelo escolhe a ferramenta e os parâmetros,
o app roda a ação **localmente** e devolve o resultado, e só então o Jarvis fala
a confirmação. Assim, "abra o Spotify e toque a Playlist Rock/Pop" vira uma
chamada real ao sistema, e não um chute.

Ferramentas disponíveis (Nível 1 — comandos de sistema, Nível 2 — deep links e
Nível 3 — Acessibilidade para chamadas no WhatsApp):

| Ferramenta                | Exemplo de fala                                | O que faz |
| ------------------------- | ---------------------------------------------- | --------- |
| `abrir_app`               | "abre o Spotify"                               | Abre qualquer app instalado, casando o nome falado (tolerante a erros de transcrição). |
| `abrir_playlist_spotify`  | "toque a playlist Rock Pop no Spotify"         | Abre a busca na aba Playlists; com link/ID direto, inicia a reprodução. |
| `abrir_conversa_whatsapp` | "abra a conversa com Tamiris no WhatsApp" / "ligue para Tamiris no zap" | Confirma o contato e abre o chat ou liga em viva-voz (com Acessibilidade). |
| `consultar_clima`         | "como está o tempo em Porto Alegre?"           | Busca clima e previsão reais e devolve o dado para o Jarvis falar. |
| `pesquisar_web`           | "pesquise isso no navegador"                   | Abre o navegador somente mediante pedido explícito. |
| `ligar_telefone`          | "liga para o João" / "liga para 11 91234-5678" | Ligação telefônica normal, com viva-voz automático. Aceita contato da agenda ou número. |
| `controlar_midia`         | "pausa a música", "próxima faixa"              | Play/pause/próxima/anterior/parar no que estiver tocando. |
| `definir_alarme`          | "me acorda às 7h30"                            | Cria um alarme no relógio do aparelho. |
| `definir_timer`           | "timer de 5 minutos"                           | Cria um timer (contagem regressiva). |

### Perguntas são respondidas, não delegadas ao navegador

O prompt do sistema deixa isso explícito: **pergunta é para ser respondida com
texto**. Fatos, temas, dúvidas e notícias viram resposta falada. Além do
conhecimento do próprio Gemini, as requisições usam *grounding* com a busca do
Google (quando o modelo/key permitem), para informações recentes — sem abrir
navegador e sem pedir ao usuário que leia uma página.

`pesquisar_web` foi restrito a pedidos literais como "abra o navegador", "abra
um site" ou "pesquise na internet". Há ainda uma trava no cliente: se o modelo
chamar `pesquisar_web` sem o usuário ter citado navegador/browser/site/url/"na
internet", a chamada é recusada internamente e ele é obrigado a responder em
texto. Para clima, a ferramenta `consultar_clima`
consulta a API gratuita **Open-Meteo** (sem chave) e devolve um resumo com
temperatura, condição, umidade, vento e previsão dos próximos dias. Se a cidade
não for informada, o app usa a última localização conhecida; se não houver
permissão de localização ou cidade inválida, o Jarvis pergunta qual cidade.

A ligação usa `Intent.ACTION_CALL` e ativa o viva-voz pelo `AudioManager`
(`setCommunicationDevice` no Android 12+, `isSpeakerphoneOn` abaixo disso). O
acesso a contatos usa o `ContentResolver` da agenda.

### Deep links (Nível 2)

- **Spotify**: "toque a playlist Rock Pop" abre
  `https://open.spotify.com/search/Rock%20Pop/playlists`, que cai direto na aba
  **Playlists**. Com um link ou ID direto (`spotify:playlist:<id>`), o Jarvis abre
  o conteúdo e envia um `KEYCODE_MEDIA_PLAY` para iniciar a reprodução — mas
  **só se não houver música tocando**, pois no Spotify essa tecla alterna e
  pausaria a faixa atual.
- **WhatsApp**: o número do contato é resolvido na agenda (até 5 candidatos),
  normalizado para E.164 (ex.: `55 51 9 8142-4944` → `5551981424944`) e aberto
  por `whatsapp://send?phone=...` no app comum (ou Business), com
  `https://wa.me/...` como fallback.

### Confirmação de contato

Toda ação no WhatsApp passa por uma confirmação antes de executar. Se houver um
único contato, o Jarvis pergunta "Encontrei X. Confirma abrir a conversa com X?
Diga sim ou não."; se houver vários, lista as opções numeradas. A resposta
(**sim**, **não**, o número da opção ou o nome) é interpretada **localmente**,
sem ida ao Gemini, e a janela de escuta é estendida para 25 s. Um pedido de
"ligar" usa a mesma confirmação, mas com o verbo "ligar para".

### Permissões e ajustes

- **Telefone e contatos** (`CALL_PHONE`, `READ_CONTACTS`) são pedidos ao ligar a
  **Escuta contínua**. Sem `READ_CONTACTS` o Jarvis só liga para números ditos
  em voz alta; sem `CALL_PHONE` ele avisa que falta permissão.
- **Localização aproximada** (`ACCESS_COARSE_LOCATION`) é opcional e permite
  responder "como está o tempo?" sem dizer a cidade. Sem ela, o Jarvis pergunta.
- **Aparecer sobre outros apps** (`SYSTEM_ALERT_WINDOW`): permite que o serviço
  em segundo plano abra atividades (app, navegador, discador). Em **Ajustes →
  Ações no aparelho** há o botão **Autorizar**; sem isso, o Jarvis só abre apps
  quando a tela do Jarvis já está em primeiro plano.
- **Listar apps instalados** (`QUERY_ALL_PACKAGES`): necessária para montar o
  índice de aplicativos e casar o nome falado com o pacote. É aceitável para uso
  pessoal (sideload), mas **bloqueia a publicação na Play Store**.
- **Alarme** (`com.android.alarm.permission.SET_ALARM`) e **áudio**
  (`MODIFY_AUDIO_SETTINGS`) completam o conjunto.
- **Acessibilidade** (`BIND_ACCESSIBILITY_SERVICE`): necessária apenas para
  ligar pelo WhatsApp. Em **Ajustes → Ações no aparelho** há o cartão
  **"Acessibilidade para ligar no WhatsApp"** com o botão **Autorizar**, que abre
  as configurações do Android. Sem ela, o Jarvis abre a conversa e avisa que o
  usuário precisa tocar no ícone de chamada.

### Níveis de confiabilidade (o que ainda não é feito)

Estão implementados os três níveis:

- **Nível 1** — comandos de sistema padronizados do Android (abrir apps, web,
  chamada telefônica, mídia, alarme/timer).
- **Nível 2** — deep links (Spotify, WhatsApp).
- **Nível 3** — `WhatsAppCallService`, um Serviço de Acessibilidade que toca no
  botão de **chamada de voz** e depois no **viva-voz** do WhatsApp. Ele só age
  quando há uma ligação pendente pedida pelo Jarvis, limita-se aos pacotes do
  WhatsApp e expira após 30 s. **É a parte mais frágil:** depende do texto dos
  botões e do layout atual do WhatsApp; se o app mudar, o clique pode não
  acontecer (o Jarvis avisa para ligar manualmente).

Ainda de fora:

- **Autoplay de playlist por nome no Spotify.** Sem a API do Spotify, o comando
  abre a busca na aba Playlists; o usuário escolhe a playlist. Com link/ID
  direto, a reprodução começa via comando de mídia.
- **Escolher automaticamente a primeira playlist da busca** — isso exigiria
  dirigir a UI do Spotify pela Acessibilidade, com a mesma fragilidade.

## Privacidade e segurança

- A chave fica cifrada no armazenamento local; a chave mestra é gerada no
  Android Keystore e não sai do aparelho.
- As chamadas vão direto para `generativelanguage.googleapis.com` (HTTPS).
  Com as vozes do Gemini, o texto da resposta também é enviado ao mesmo serviço
  para ser transformado em áudio.
- O histórico de conversa fica apenas em memória e pode ser limpo em Ajustes.
- O orbe é desenhado em código: nada de áudio, imagem ou vídeo sai do aparelho
  por causa do avatar.
- As ações rodam **no próprio aparelho**: abrir app, ligar, controlar mídia e
  criar alarmes usam APIs locais do Android. Só o texto do comando e o resultado
  da ação trafegam até o Gemini, como parte da conversa.

## Estrutura

```
app/src/main/java/com/example/jarvisteste/
├── MainActivity.kt              # navegação onboarding → home → ajustes
├── data/
│   ├── SecureStore.kt           # AES/GCM + Android Keystore
│   ├── SettingsStore.kt         # preferências + perfis de voz
│   ├── VoiceOptions.kt          # motor de voz e as 12 vozes do Gemini
│   └── GeminiClient.kt          # REST direto ao Gemini, function calling e memória
├── actions/
│   ├── JarvisActions.kt         # as ações no aparelho expostas como ferramentas
│   ├── ToolSpec.kt              # declaração e esquema JSON das ferramentas
│   ├── AppIndex.kt              # índice dos apps instalados + casamento por nome
│   └── WeatherService.kt        # clima e previsão via Open-Meteo (sem chave)
├── audio/
│   ├── VoiceManager.kt          # fala única: Gemini com fallback para o aparelho
│   ├── GeminiTtsPlayer.kt       # Gemini TTS em streaming, AudioTrack e cache
│   ├── VoiceLevel.kt            # nível da fala do Jarvis (PCM ou envoltória local)
│   ├── MicLevel.kt              # nível da sua voz (RMS do reconhecedor)
│   ├── TtsManager.kt            # TextToSpeech, vozes e conclusão de fala
│   └── RecognitionBeepSilencer.kt # silencia bipes do reconhecedor
├── accessibility/
│   └── WhatsAppCallService.kt   # Nível 3: toca chamada e viva-voz no WhatsApp
├── service/
│   └── JarvisListeningService.kt# escuta contínua + palavra de ativação
└── ui/
    ├── OnboardingScreen.kt
    ├── HomeScreen.kt
    ├── SettingsScreen.kt
    ├── theme/                   # esquema "quase TRON" sempre escuro
    ├── avatar/
    │   ├── JarvisOrb.kt         # o orbe: desenho e reação às ondas de voz
    │   ├── AvatarMood.kt        # humor do orbe (ocioso/ouvindo/pensando/falando)
    │   ├── AvatarScreen.kt      # tela cheia + medidor de nível
    │   ├── AvatarActivity.kt    # atividade sobre a tela de bloqueio
    │   └── AvatarPresenter.kt   # notificação full-screen + wakelock
    └── components/              # cores, moldura HUD, retículo, painéis e botões
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
- O orbe é uma animação 2D em `Canvas`: representa a voz por intensidade, não
  por um espectro de frequências real. A leitura do nível depende do
  `onRmsChanged`, que alguns aparelhos reportam de forma mais grosseira.
- O despertar da tela com o aparelho bloqueado depende do *full-screen intent*;
  se o fabricante limitar essa permissão, a tela pode não abrir sozinha.
- As ações vão até o **Nível 3**. Pelo nome da playlist, o Spotify abre a busca
  e o usuário precisa tocar na playlist; a ligação pelo WhatsApp depende do
  Serviço de Acessibilidade e pode falhar se o layout do app mudar.
- A confirmação de contato usa `READ_CONTACTS`. Se houver muitos contatos com o
  mesmo nome, o Jarvis mostra até 5 opções; diga o número da opção ou o nome
  completo.
- O índice de apps exige `QUERY_ALL_PACKAGES` (impede publicar na Play Store) e o
  casamento por nome pode errar em apps com nomes parecidos; nesse caso, fale o
  nome mais completo.
- Abrir apps a partir do serviço em segundo plano depende de *Aparecer sobre
  outros apps*; sem essa permissão, o Jarvis só abre apps com a própria tela em
  primeiro plano.

## Próximos passos

- Ampliar a Acessibilidade para escolher a playlist na busca do Spotify.
- Ampliar os deep links (Nível 2) para YouTube, mapas e outros apps.
- Ajustar o orbe para variar a cor por humor e testar em telas menores.
- Ajustar UX de nome personalizado (já configurável em Ajustes).
- Entrada por texto como alternativa ao microfone.
- Distribuição assinada (release) e revisão das permissões para publicação
  (remover `QUERY_ALL_PACKAGES` ou justificar o uso).
