# Arquitetura, acessibilidade e limites

## Fluxo

1. `VoiceController` inicia o reconhecimento e entrega a melhor transcrição.
2. `NeuralIntentModel` prevê ação, objeto, canal e intenção terminal; o
   `CommandParser` aplica decodificação restrita e extrai os campos do comando.
3. `MainActivity` mantém o diálogo e solicita a permissão mínima no momento de uso.
4. `ContactRepository` ou `InstalledAppRepository` resolve o alvo.
5. Mensagens e chamadas entram em estado pendente e exigem “sim” antes da execução.
6. `CapabilityRegistry` cruza pacote e versão dos apps inicializáveis com um
   catálogo declarativo embutido/online. Dados remotos não viram código executável.
7. O resultado visual pode ser detalhado, mas a resposta falada segue uma política curta.
8. `MediaActionController` controla sessões de mídia ativas sem abrir o aplicativo quando possível.

## Decisões de acessibilidade

- Uma ação principal grande e central para reduzir a precisão motora exigida.
- Alto contraste e textos grandes.
- Rótulos completos e descrições de conteúdo, sem duplicar o status pelo TalkBack e pelo TTS.
- Alternativa digitada para desenvolvimento, ambientes ruidosos e testes.
- Erros falados em uma frase curta e acionável.
- Nenhuma ação financeira, envio ou chamada é confirmada silenciosamente.

## Integração com terceiros

Há três níveis possíveis:

1. **Intent genérica:** discador, SMS, mapas, câmera e compartilhamento. É o caminho preferido.
2. **Deep link/API do app:** permite uma ação específica quando o fornecedor a documenta.
3. **Serviço de acessibilidade:** pode observar e acionar elementos da interface, mas é frágil, sensível e sujeito a regras estritas. Deve ser usado somente para uma necessidade assistiva que APIs mais restritas não atendem.

“Interagir com absolutamente todos os apps” não é uma garantia tecnicamente possível: apps podem não exportar ações, bloquear deep links, esconder atividades ou alterar a interface. O caminho sustentável é criar adaptadores explícitos por capacidade e um fallback que apenas abre o aplicativo.

## Estado e próximas fases

- Extrair o roteador de comandos da `MainActivity` para um serviço reutilizável.
- O GuiaVoz já se qualifica para `ROLE_ASSISTANT` por tratar `ACTION_ASSIST`. Uma
  sessão completa de `VoiceInteractionService` continua sendo uma evolução futura.
- Reconhecimento de voz offline quando disponível no dispositivo.
- Catálogo de adaptadores para deep links documentados.
- Comandos de câmera, calendário, alarmes e compartilhamento.
- Testes instrumentados com TalkBack e usuários reais.
- Política de privacidade e revisão das declarações da loja antes de distribuição.
- Avaliação isolada de `AccessibilityService`, com lista permitida de ações, indicadores permanentes, botão de parada e auditoria local.
