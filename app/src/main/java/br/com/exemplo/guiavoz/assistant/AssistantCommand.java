package br.com.exemplo.guiavoz.assistant;

import java.util.Objects;

public final class AssistantCommand {
    public enum Type {
        HELP,
        TIME,
        OPEN_APP,
        LIST_APPS,
        DIAL,
        SMS,
        MAP,
        ACCESSIBILITY_SETTINGS,
        WHATSAPP_MESSAGES,
        WHATSAPP_LISTEN_MESSAGES,
        WHATSAPP_CALL,
        WHATSAPP_SEND_MESSAGE,
        WHATSAPP_REPLY_MESSAGE,
        PLAY_WHATSAPP_AUDIO,
        PLAY_NEXT_AUDIO,
        REPEAT_AUDIO,
        CONTINUE_SESSION,
        MEDIA_PLAY,
        MEDIA_PAUSE,
        MEDIA_NEXT,
        READ_SCREEN,
        TAP_ELEMENT,
        TYPE_TEXT,
        SCROLL_DOWN,
        SCROLL_UP,
        BACK,
        UNKNOWN
    }

    private final Type type;
    private final String target;
    private final String message;
    private final String original;
    private final float confidence;
    private final String source;

    public AssistantCommand(Type type, String target, String message, String original) {
        this(type, target, message, original, 1f, "rules");
    }

    public AssistantCommand(Type type, String target, String message, String original,
                            float confidence, String source) {
        this.type = Objects.requireNonNull(type);
        this.target = target == null ? "" : target.trim();
        this.message = message == null ? "" : message.trim();
        this.original = original == null ? "" : original.trim();
        this.confidence = Math.max(0f, Math.min(1f, confidence));
        this.source = source == null ? "unknown" : source;
    }

    public Type getType() {
        return type;
    }

    public String getTarget() {
        return target;
    }

    public String getMessage() {
        return message;
    }

    public String getOriginal() {
        return original;
    }

    public float getConfidence() {
        return confidence;
    }

    public String getSource() {
        return source;
    }
}
