package br.com.exemplo.guiavoz.whatsapp;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class WhatsAppMessageStore {
    private static final String PREFS = "whatsapp_messages";
    private static final String KEY = "recent";
    private static final int MAX_MESSAGES = 20;

    public static final class Message {
        public final String sender;
        public final String text;
        public final long timestamp;
        public final boolean audio;

        Message(String sender, String text, long timestamp, boolean audio) {
            this.sender = sender;
            this.text = text;
            this.timestamp = timestamp;
            this.audio = audio;
        }
    }

    private WhatsAppMessageStore() {}

    public static synchronized void add(Context context, Message message) {
        List<Message> messages = read(context);
        if (!messages.isEmpty()) {
            Message latest = messages.get(0);
            if (latest.sender.equals(message.sender) && latest.text.equals(message.text)
                    && Math.abs(latest.timestamp - message.timestamp) < 2000) return;
        }
        messages.add(0, message);
        while (messages.size() > MAX_MESSAGES) messages.remove(messages.size() - 1);
        StringBuilder saved = new StringBuilder();
        for (Message item : messages) {
            if (saved.length() > 0) saved.append('\n');
            saved.append(encode(item.sender)).append('|')
                    .append(encode(item.text)).append('|')
                    .append(item.timestamp).append('|').append(item.audio);
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, saved.toString()).apply();
    }

    public static synchronized List<Message> read(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String saved = preferences.getString(KEY, "");
        if (saved == null || saved.isEmpty()) return new ArrayList<>();
        List<Message> messages = new ArrayList<>();
        for (String line : saved.split("\\n")) {
            String[] parts = line.split("\\|", -1);
            if (parts.length != 4) continue;
            try {
                messages.add(new Message(decode(parts[0]), decode(parts[1]),
                        Long.parseLong(parts[2]), Boolean.parseBoolean(parts[3])));
            } catch (RuntimeException ignored) {}
        }
        return messages;
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
