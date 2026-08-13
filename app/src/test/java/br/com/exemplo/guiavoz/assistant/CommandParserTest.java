package br.com.exemplo.guiavoz.assistant;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class CommandParserTest {
    private final CommandParser parser = new CommandParser();

    @Test
    public void parsesAppCommandIgnoringAccentsAndCase() {
        AssistantCommand command = parser.parse("Abrir Aplicativo Câmera");
        assertEquals(AssistantCommand.Type.OPEN_APP, command.getType());
        assertEquals("Câmera", command.getTarget());
    }

    @Test
    public void parsesSmsWithMessage() {
        AssistantCommand command = parser.parse(
                "Enviar mensagem para João dizendo estou chegando");
        assertEquals(AssistantCommand.Type.SMS, command.getType());
        assertEquals("João", command.getTarget());
        assertEquals("estou chegando", command.getMessage());
    }

    @Test
    public void parsesContactDial() {
        AssistantCommand command = parser.parse("Ligue para Maria da Silva");
        assertEquals(AssistantCommand.Type.DIAL, command.getType());
        assertEquals("Maria da Silva", command.getTarget());
    }

    @Test
    public void parsesMapBeforeGenericOpen() {
        AssistantCommand command = parser.parse("Abrir mapa para Praça da Sé");
        assertEquals(AssistantCommand.Type.MAP, command.getType());
        assertEquals("Praça da Sé", command.getTarget());
    }

    @Test
    public void convertsSpokenDigits() {
        assertEquals("11987654321",
                PhoneTextParser.extract("um um nove oito sete seis cinco quatro três dois um"));
    }
}
