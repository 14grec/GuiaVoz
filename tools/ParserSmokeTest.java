import br.com.exemplo.guiavoz.assistant.AssistantCommand;
import br.com.exemplo.guiavoz.assistant.CommandParser;
import br.com.exemplo.guiavoz.assistant.PhoneTextParser;

public final class ParserSmokeTest {
    public static void main(String[] args) {
        CommandParser parser = new CommandParser();

        assertCommand(parser, "Abrir Aplicativo Câmera",
                AssistantCommand.Type.OPEN_APP, "Câmera", "");
        assertCommand(parser, "Enviar mensagem para João dizendo estou chegando",
                AssistantCommand.Type.SMS, "João", "estou chegando");
        assertCommand(parser, "Ligue para Maria da Silva",
                AssistantCommand.Type.DIAL, "Maria da Silva", "");
        assertCommand(parser, "Abrir mapa para Praça da Sé",
                AssistantCommand.Type.MAP, "Praça da Sé", "");
        assertCommand(parser, "Leia minhas mensagens do WhatsApp",
                AssistantCommand.Type.WHATSAPP_MESSAGES, "", "");
        assertCommand(parser, "Ligar via WhatsApp para Maria",
                AssistantCommand.Type.WHATSAPP_CALL, "Maria", "");
        assertCommand(parser, "Executar o áudio do WhatsApp",
                AssistantCommand.Type.PLAY_WHATSAPP_AUDIO, "", "");
        assertCommand(parser, "Reproduzir áudios do WhatsApp",
                AssistantCommand.Type.PLAY_WHATSAPP_AUDIO, "", "");
        assertCommand(parser, "Ouvir minhas mensagens do Whats",
                AssistantCommand.Type.WHATSAPP_LISTEN_MESSAGES, "", "");
        assertCommand(parser, "Ligar para Maria no WhatsApp",
                AssistantCommand.Type.WHATSAPP_CALL, "Maria", "");
        assertCommand(parser, "Pausar música",
                AssistantCommand.Type.MEDIA_PAUSE, "", "");
        assertCommand(parser, "Próxima faixa",
                AssistantCommand.Type.MEDIA_NEXT, "", "");

        String number = PhoneTextParser.extract(
                "um um nove oito sete seis cinco quatro três dois um");
        if (!"11987654321".equals(number)) {
            throw new AssertionError("Número inesperado: " + number);
        }
        System.out.println("ParserSmokeTest: 13 cenários aprovados.");
    }

    private static void assertCommand(
            CommandParser parser,
            String phrase,
            AssistantCommand.Type type,
            String target,
            String message
    ) {
        AssistantCommand actual = parser.parse(phrase);
        if (actual.getType() != type
                || !actual.getTarget().equals(target)
                || !actual.getMessage().equals(message)) {
            throw new AssertionError("Falha ao interpretar: " + phrase);
        }
    }
}
