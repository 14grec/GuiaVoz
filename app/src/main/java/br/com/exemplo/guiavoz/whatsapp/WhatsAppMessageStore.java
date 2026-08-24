package br.com.exemplo.guiavoz.whatsapp;

import android.content.Context;
import android.content.SharedPreferences;

import br.com.exemplo.guiavoz.assistant.TextNormalizer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/** Histórico local e estado assistivo das mensagens observadas nas notificações. */
public final class WhatsAppMessageStore {
    private static final String PREFS = "whatsapp_messages";
    private static final String KEY = "recent_v2";
    private static final String LEGACY_KEY = "recent";
    private static final int MAX_MESSAGES = 80;

    public enum State { NEW, ANNOUNCED, PLAY_REQUESTED, HEARD }

    public static final class Message {
        public final String id;
        public final String sender;
        public final String text;
        public final long timestamp;
        public final boolean audio;
        public final State state;

        Message(String sender, String text, long timestamp, boolean audio) {
            this(createId(sender, text, timestamp), sender, text, timestamp, audio, State.NEW);
        }

        Message(String id, String sender, String text, long timestamp, boolean audio, State state) {
            this.id = id;
            this.sender = sender;
            this.text = text;
            this.timestamp = timestamp;
            this.audio = audio;
            this.state = state;
        }

        Message withState(State next) {
            return new Message(id, sender, text, timestamp, audio, next);
        }
    }

    private WhatsAppMessageStore() {}

    public static synchronized void add(Context context, Message message) {
        List<Message> messages = read(context);
        for (Message item : messages) {
            if (item.id.equals(message.id)) return;
        }
        messages.add(0, message);
        while (messages.size() > MAX_MESSAGES) messages.remove(messages.size() - 1);
        save(context, messages);
    }

    public static synchronized List<Message> read(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String saved = preferences.getString(KEY, "");
        if (saved == null || saved.isEmpty()) {
            return readLegacy(preferences.getString(LEGACY_KEY, ""));
        }
        List<Message> messages = new ArrayList<>();
        for (String line : saved.split("\\n")) {
            String[] parts = line.split("\\|", -1);
            if (parts.length != 6) continue;
            try {
                messages.add(new Message(decode(parts[0]), decode(parts[1]), decode(parts[2]),
                        Long.parseLong(parts[3]), Boolean.parseBoolean(parts[4]),
                        State.valueOf(parts[5])));
            } catch (RuntimeException ignored) {}
        }
        return messages;
    }

    public static synchronized List<Message> readNew(Context context) {
        List<Message> result = new ArrayList<>();
        for (Message message : read(context)) {
            if (message.state == State.NEW) result.add(message);
        }
        return result;
    }

    public static synchronized Message nextUnheardAudio(Context context) {
        List<Message> messages = read(context);
        // A interface do WhatsApp expõe com maior confiabilidade o último botão de áudio.
        for (Message message : messages) {
            if (message.audio && message.state != State.HEARD) return message;
        }
        return null;
    }

    public static synchronized Message lastHeardAudio(Context context) {
        for (Message message : read(context)) {
            if (message.audio && message.state == State.HEARD) return message;
        }
        return null;
    }

    public static synchronized String latestSender(Context context) {
        List<Message> messages = read(context);
        return messages.isEmpty() ? "" : messages.get(0).sender;
    }

    public static synchronized void markAnnounced(Context context, List<String> ids) {
        updateStates(context, ids, State.ANNOUNCED, true);
    }

    public static synchronized void markAudioRequested(Context context, String id) {
        updateStates(context, Collections.singletonList(id), State.PLAY_REQUESTED, true);
    }

    public static synchronized void markHeard(Context context, String id) {
        updateStates(context, Collections.singletonList(id), State.HEARD, true);
    }

    private static void updateStates(Context context, List<String> ids, State state,
                                     boolean includeAudio) {
        if (ids == null || ids.isEmpty()) return;
        List<Message> messages = read(context);
        boolean changed = false;
        for (int index = 0; index < messages.size(); index++) {
            Message item = messages.get(index);
            if (ids.contains(item.id) && (includeAudio || !item.audio)) {
                messages.set(index, item.withState(state));
                changed = true;
            }
        }
        if (changed) save(context, messages);
    }

    private static void save(Context context, List<Message> messages) {
        StringBuilder saved = new StringBuilder();
        for (Message item : messages) {
            if (saved.length() > 0) saved.append('\n');
            saved.append(encode(item.id)).append('|')
                    .append(encode(item.sender)).append('|')
                    .append(encode(item.text)).append('|')
                    .append(item.timestamp).append('|').append(item.audio).append('|')
                    .append(item.state.name());
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, saved.toString()).remove(LEGACY_KEY).apply();
    }

    private static List<Message> readLegacy(String saved) {
        List<Message> messages = new ArrayList<>();
        if (saved == null || saved.isEmpty()) return messages;
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

    private static String createId(String sender, String text, long timestamp) {
        String value = TextNormalizer.normalize(sender) + "|" + timestamp + "|"
                + TextNormalizer.normalize(text);
        return Long.toHexString(timestamp) + "-" + Integer.toHexString(value.hashCode());
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
