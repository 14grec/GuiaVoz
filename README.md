# GuiaVoz — MVP de assistente de voz Android

Protótipo nativo em Java para testar uma interface por voz voltada a pessoas com deficiência visual. O app reconhece comandos em português do Brasil, responde por síntese de fala e delega ações a outros apps com `Intent`.

## Versão 0.3.0 — interação rápida e interface renovada

- Respostas faladas curtas, TTS mais rápido e status detalhado somente na tela.
- Parser por famílias de verbos: entende **ouvir**, **escutar**, **tocar**, **reproduzir** e **executar**.
- “Ouvir minhas mensagens”: lê até quatro mensagens e reproduz o áudio recente sem sobrepor a voz do GuiaVoz.
- “Reproduzir áudios do WhatsApp”: abre a última mensagem de voz e toca em **Reproduzir**.
- “Ligar para Maria no WhatsApp”: procura primeiro nas conversas recentes e depois nos contatos do Android.
- “Pausar música”, “Continuar música” e “Próxima faixa”: controla uma sessão de mídia ativa em segundo plano.
- Opção para bloquear a tela depois que o áudio do WhatsApp começar.
- Nova interface em cartões, alto contraste, áreas de toque grandes e ações rápidas.

Na primeira execução, use **Acesso às notificações** e **Controle do WhatsApp**. O Android exige que a pessoa habilite manualmente os dois serviços. O serviço de acessibilidade é restrito a `com.whatsapp`, só executa uma ação após comando explícito e cancela a ação se o WhatsApp não responder dentro do prazo.

O WhatsApp não oferece API pública para histórico, chamadas ou reprodução de áudios. A leitura cobre apenas notificações recebidas após a ativação. Chamada e áudio dependem dos rótulos da interface em português do Brasil e podem precisar de ajustes quando o WhatsApp mudar.

## Baixar o APK compilado

O GitHub Actions compila automaticamente um APK de teste a cada atualização da branch `main`.

1. Abra a aba **Actions** deste repositório.
2. Selecione a execução mais recente chamada **Compilar APK**.
3. Aguarde o indicador verde.
4. Em **Artifacts**, baixe **GuiaVoz-apk**.
5. Extraia o arquivo ZIP e instale `app-debug.apk` no Android.

O Android pode solicitar autorização para instalar aplicativos desta fonte. Este APK é de depuração, destinado somente ao teste; ele não é uma versão assinada para publicação na Play Store.

## O que já funciona

- Ouvir e transcrever comandos com `SpeechRecognizer`.
- Falar respostas com `TextToSpeech`.
- Procurar contatos, após consentimento, com `ContactsContract`.
- Abrir o discador com número preenchido usando `ACTION_DIAL`.
- Abrir o app de SMS com destinatário e texto preenchidos usando `ACTION_SENDTO`.
- Abrir um local em um app de mapas.
- Encontrar, listar e abrir apps que possuem uma tela inicializável.
- Informar a hora, explicar os comandos e abrir os ajustes de acessibilidade.
- Simular comandos por texto quando não houver microfone/emulador configurado.

## Comandos para testar

- “Ajuda”
- “Que horas são?”
- “Abrir WhatsApp”
- “Ouvir minhas mensagens”
- “Reproduzir áudios do WhatsApp”
- “Escute a mensagem de voz do zap”
- “Ligar para Maria no WhatsApp”
- “Pausar música”
- “Próxima faixa”
- “Quais aplicativos estão instalados?”
- “Ligar para Maria”
- “Ligar para 11 99999 1234”
- “Enviar mensagem para João dizendo estou chegando”
- “Abrir mapa para Avenida Paulista 1000”
- “Abrir acessibilidade”

## Abrir e executar

1. Abra esta pasta no Android Studio.
2. Aguarde a sincronização do Gradle.
3. Use um aparelho ou emulador com Android 8.0 (API 26) ou superior.
4. Execute o módulo `app`.
5. Conceda microfone ao tocar em **Ouvir comando**. O acesso a contatos só é solicitado ao usar um comando que precise localizar uma pessoa.

O projeto usa `compileSdk 35`, `targetSdk 35`, Java 17 e Android Gradle Plugin 8.7.3. Nenhuma biblioteca externa de runtime é necessária. O pacote-fonte não inclui binários do Gradle Wrapper; se a IDE solicitar a distribuição, use Gradle 8.9.

## Limite de “todos os aplicativos”

O Android isola aplicativos. Este MVP consegue descobrir e abrir apps com atividade de inicialização visível. Ações internas dependem do que cada app publica como `Intent`, deep link ou API. O manifesto usa uma consulta direcionada a atividades `MAIN/LAUNCHER`; não solicita a permissão ampla e sensível `QUERY_ALL_PACKAGES`.

Controlar elementos de tela de terceiros exigiria um `AccessibilityService`. Isso deve ser uma fase separada, limitada a ações assistivas claras, com ativação e consentimento explícitos; não é uma API para controle irrestrito ou autônomo.

## Segurança e privacidade

- Nesta versão, o reconhecimento ainda é iniciado por toque e pode usar o serviço de voz configurado no aparelho.
- Contatos são lidos somente no dispositivo e apenas para resolver o comando atual.
- O app não faz chamada direta e não envia mensagem sozinho: abre o app apropriado para revisão e confirmação.
- Não há coleta, servidor próprio, analytics ou armazenamento do áudio/transcrição neste protótipo.

Veja também [docs/ARQUITETURA_E_LIMITES.md](docs/ARQUITETURA_E_LIMITES.md).

## Referências oficiais

- [Intents comuns no Android](https://developer.android.com/guide/components/intents-common)
- [Visibilidade de pacotes](https://developer.android.com/training/package-visibility)
- [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [Como recuperar contatos](https://developer.android.com/training/contacts-provider/retrieve-names)
