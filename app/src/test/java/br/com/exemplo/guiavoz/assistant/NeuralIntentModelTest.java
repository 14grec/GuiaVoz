package br.com.exemplo.guiavoz.assistant;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class NeuralIntentModelTest {
    private NeuralIntentModel load() throws Exception {
        File model = new File("app/src/main/assets/guiavoz_brain.bin");
        if (!model.isFile()) model = new File("src/main/assets/guiavoz_brain.bin");
        return NeuralIntentModel.load(new FileInputStream(model));
    }

    @Test
    public void recognizesUnseenAudioPhrase() throws Exception {
        NeuralIntentModel.Prediction prediction = load().predict(
                "Coloque para tocar o que recebi em áudio");
        assertEquals("PLAY_WHATSAPP_AUDIO", prediction.label);
        assertTrue(prediction.confidence > 0.58f);
    }

    @Test
    public void parserUsesNeuralModelForNaturalNavigationPhrase() throws Exception {
        AssistantCommand command = new CommandParser(load()).parse(
                "Pressione onde estiver escrito pesquisar");
        assertEquals(AssistantCommand.Type.TAP_ELEMENT, command.getType());
        assertEquals("neural", command.getSource());
        assertTrue(command.getTarget().toLowerCase().contains("pesquisar"));
    }
}
