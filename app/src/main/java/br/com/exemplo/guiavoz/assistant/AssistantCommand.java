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
        UNKNOWN
    }

    private final Type type;
    private final String target;
    private final String message;
    private final String original;

    public AssistantCommand(Type type, String target, String message, String original) {
        this.type = Objects.requireNonNull(type);
        this.target = target == null ? "" : target.trim();
        this.message = message == null ? "" : message.trim();
        this.original = original == null ? "" : original.trim();
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
}
