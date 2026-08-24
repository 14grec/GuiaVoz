import br.com.exemplo.guiavoz.assistant.AssistantCommand;
import br.com.exemplo.guiavoz.assistant.CommandParser;
import br.com.exemplo.guiavoz.assistant.PhoneTextParser;
import br.com.exemplo.guiavoz.assistant.NeuralIntentModel;

import java.io.FileInputStream;

public final class ParserSmokeTest {
    public static void main(String[] args) throws Exception {
        CommandParser parser = new CommandParser();

        assertCommand(parser, "Abrir Aplicativo Câmera",
                AssistantCommand.Type.OPEN_APP, "Câmera", "");
        assertCommand(parser, "Enviar SMS para João dizendo estou chegando",
                AssistantCommand.Type.SMS, "João", "estou chegando");
        assertCommand(parser, "Enviar mensagem para João dizendo estou chegando",
                AssistantCommand.Type.WHATSAPP_SEND_MESSAGE, "João", "estou chegando");
        assertCommand(parser, "Responda ao Carlos no WhatsApp dizendo tudo bem",
                AssistantCommand.Type.WHATSAPP_REPLY_MESSAGE, "Carlos", "tudo bem");
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
        NeuralIntentModel model = NeuralIntentModel.load(new FileInputStream(
                "app/src/main/assets/guiavoz_brain.bin"));
        NeuralIntentModel.Prediction send = model.predict(
                "Quero mandar uma mensagem para Pedro no WhatsApp");
        if (!"SEND".equals(send.action) || !"MESSAGE".equals(send.object)
                || !"WHATSAPP".equals(send.channel)) {
            throw new AssertionError("Saída hierárquica inesperada: " + send.semanticKey());
        }
        AssistantCommand neural = new CommandParser(model).parse(
                "Quero mandar uma mensagem para Pedro no WhatsApp");
        if (neural.getType() != AssistantCommand.Type.WHATSAPP_SEND_MESSAGE) {
            throw new AssertionError("Decodificação neural inesperada: " + neural.getType());
        }
        NeuralIntentModel.Prediction audio = model.predict(
                "Coloque para tocar o que recebi em áudio");
        if (!"PLAY_WHATSAPP_AUDIO".equals(audio.label) || audio.confidence <= 0.58f) {
            throw new AssertionError("Confiança neural de áudio insuficiente: "
                    + audio.label + " / " + audio.confidence);
        }
        AssistantCommand navigation = new CommandParser(model).parse(
                "Pressione onde estiver escrito pesquisar");
        if (navigation.getType() != AssistantCommand.Type.TAP_ELEMENT
                || !"neural".equals(navigation.getSource())) {
            throw new AssertionError("Navegação neural inesperada: " + navigation.getType()
                    + " / " + navigation.getSource());
        }
        System.out.println("ParserSmokeTest: 19 cenários aprovados.");
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
            throw new AssertionError("Falha ao interpretar: " + phrase + " => "
                    + actual.getType() + " / " + actual.getTarget() + " / "
                    + actual.getMessage());
        }
    }
}
