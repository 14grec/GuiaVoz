package br.com.exemplo.guiavoz.assistant;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Registro privado, local e exportável para formar o próximo corpus de treino. */
public final class MisunderstoodCommandStore {
    private static final String FILE_NAME = "misunderstood_commands.jsonl";

    private MisunderstoodCommandStore() {}

    public static synchronized void record(Context context, AssistantCommand command, String reason) {
        if (command == null || command.getOriginal().trim().isEmpty()) return;
        try {
            JSONObject row = new JSONObject();
            row.put("timestamp", System.currentTimeMillis());
            row.put("text", command.getOriginal());
            row.put("predicted_intent", command.getType().name());
            row.put("confidence", command.getConfidence());
            row.put("source", command.getSource());
            row.put("reason", reason == null ? "unknown" : reason);
            byte[] encoded = (row + "\n").getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream output = context.openFileOutput(FILE_NAME, Context.MODE_APPEND)) {
                output.write(encoded);
            }
        } catch (Exception ignored) {
            // O registro de melhoria nunca pode interromper o assistente.
        }
    }

    public static File file(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }
}
