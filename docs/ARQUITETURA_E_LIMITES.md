# Arquitetura, acessibilidade e limites

## Fluxo

1. `VoiceController` inicia o reconhecimento e entrega a melhor transcrição.
2. `CommandParser` converte a frase em um comando tipado.
3. `MainActivity` solicita a permissão mínima no momento de uso.
4. `ContactRepository` ou `InstalledAppRepository` resolve o alvo.
5. Uma `Intent` abre o app responsável, mantendo a confirmação final com a pessoa.
6. O resultado e os erros são apresentados em texto grande e por voz.

## Decisões de acessibilidade

- Uma ação principal grande e central para reduzir a precisão motora exigida.
- Alto contraste e textos grandes.
- Rótulos completos, descrições de conteúdo e região de status anunciável.
- Alternativa digitada para desenvolvimento, ambientes ruidosos e testes.
- Erros falados com orientação de recuperação.
- Nenhuma ação financeira, envio ou chamada é confirmada silenciosamente.

## Integração com terceiros

Há três níveis possíveis:

1. **Intent genérica:** discador, SMS, mapas, câmera e compartilhamento. É o caminho preferido.
2. **Deep link/API do app:** permite uma ação específica quando o fornecedor a documenta.
3. **Serviço de acessibilidade:** pode observar e acionar elementos da interface, mas é frágil, sensível e sujeito a regras estritas. Deve ser usado somente para uma necessidade assistiva que APIs mais restritas não atendem.

“Interagir com absolutamente todos os apps” não é uma garantia tecnicamente possível: apps podem não exportar ações, bloquear deep links, esconder atividades ou alterar a interface. O caminho sustentável é criar adaptadores explícitos por capacidade e um fallback que apenas abre o aplicativo.

## Próximas fases recomendadas

- Confirmação conversacional para contatos ou apps ambíguos.
- Reconhecimento de voz offline quando disponível no dispositivo.
- Catálogo de adaptadores para deep links documentados.
- Comandos de câmera, calendário, alarmes e compartilhamento.
- Testes instrumentados com TalkBack e usuários reais.
- Política de privacidade e revisão das declarações da loja antes de distribuição.
- Avaliação isolada de `AccessibilityService`, com lista permitida de ações, indicadores permanentes, botão de parada e auditoria local.
