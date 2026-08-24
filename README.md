# GuiaVoz — MVP de assistente de voz Android

Protótipo nativo em Java para testar uma interface por voz voltada a pessoas com deficiência visual. O app reconhece comandos em português do Brasil, responde por síntese de fala e delega ações a outros apps com `Intent`.

## Versão 0.4.0 — cérebro neural local

- Rede neural offline treinada para 24 intenções, com confiança e saída estruturada.
- Corpus local em português, avaliação separada e treinamento reproduzível em Python.
- Fallback determinístico quando a confiança neural não é suficiente.
- Mensagens novas agrupadas: **“Novas mensagens de João: texto um; texto dois.”**
- Estado local de áudio novo, solicitado e ouvido, com **próximo áudio** e **repetir áudio**.
- Leitura conjunta de textos e áudios sem a voz do GuiaVoz sobrepor a reprodução.
- Navegação assistiva experimental: ler tela, tocar em elemento, digitar, rolar e voltar.
- Interface reduzida para fala, teste do cérebro, permissões e preferências.

Na primeira execução, use **Acesso às notificações** e **Navegação por voz**. O Android exige que a pessoa habilite manualmente os dois serviços. O serviço de acessibilidade só executa uma ação após comando explícito, ignora campos de senha, bloqueia alvos sensíveis genéricos e cancela a ação quando ela expira.

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
- “Toque o próximo áudio”
- “Repita o último áudio”
- “Ligar para Maria no WhatsApp”
- “Pausar música”
- “Próxima faixa”
- “Quais aplicativos estão instalados?”
- “Ligar para Maria”
- “Ligar para 11 99999 1234”
- “Enviar mensagem para João dizendo estou chegando”
- “Abrir mapa para Avenida Paulista 1000”
- “Abrir acessibilidade”
- “Leia esta tela”
- “Toque em pesquisar”
- “Digite restaurante”
- “Role para baixo”
- “Volte”

## Treinar o cérebro

O modelo incluído no APK é uma rede neural compacta de duas etapas: n-gramas de
caracteres alimentam uma camada oculta treinada para reconhecer a intenção. A
inferência é implementada em Java puro e o arquivo de pesos tem menos de 500 KB.

```bash
python -m pip install -r training/requirements.txt
python training/test_brain.py
python training/train_brain.py
python training/check_quality.py --minimum 0.90
```

Veja [`training/README.md`](training/README.md) para o corpus, relatório e formato
de exportação. A arquitetura permite substituir esse primeiro cérebro por um
Transformer quantizado posteriormente, sem reescrever o executor Android.

## Abrir e executar

1. Abra esta pasta no Android Studio.
2. Aguarde a sincronização do Gradle.
3. Use um aparelho ou emulador com Android 8.0 (API 26) ou superior.
4. Execute o módulo `app`.
5. Conceda microfone ao tocar em **Ouvir comando**. O acesso a contatos só é solicitado ao usar um comando que precise localizar uma pessoa.

O projeto usa `compileSdk 35`, `targetSdk 35`, Java 17 e Android Gradle Plugin 8.7.3. A inferência neural não adiciona biblioteca externa ao APK. O pacote-fonte não inclui binários do Gradle Wrapper; se a IDE solicitar a distribuição, use Gradle 8.9.

## Limite de “todos os aplicativos”

O Android isola aplicativos. Este MVP consegue descobrir e abrir apps com atividade de inicialização visível. Ações internas dependem do que cada app publica como `Intent`, deep link ou API. O manifesto usa uma consulta direcionada a atividades `MAIN/LAUNCHER`; não solicita a permissão ampla e sensível `QUERY_ALL_PACKAGES`.

O modo de navegação usa `AccessibilityService` para agir somente sobre elementos expostos pelo aplicativo. Interfaces sem rótulos acessíveis, telas protegidas ou componentes desenhados fora da árvore de acessibilidade podem não funcionar. Não existe garantia técnica de controle de absolutamente todos os aplicativos.

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
