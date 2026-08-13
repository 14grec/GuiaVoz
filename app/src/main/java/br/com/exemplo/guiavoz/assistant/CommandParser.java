package br.com.exemplo.guiavoz.assistant;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommandParser {
    private static final List<String> SMS_PREFIXES = Arrays.asList(
            "enviar mensagem para ", "mandar mensagem para ",
            "mensagem para ", "sms para "
    );
    private static final List<String> DIAL_PREFIXES = Arrays.asList(
            "ligar para ", "ligue para ", "discar para ", "telefonar para "
    );
    private static final List<String> MAP_PREFIXES = Arrays.asList(
            "abrir mapa para ", "mapa para ", "navegar para ",
            "como chegar em ", "como chegar a "
    );
    private static final List<String> OPEN_PREFIXES = Arrays.asList(
            "abrir aplicativo ", "abrir app ", "abrir ", "abra ",
            "iniciar ", "inicie "
    );

    public AssistantCommand parse(String spokenText) {
        String original = spokenText == null ? "" : spokenText.trim();
        String normalized = TextNormalizer.normalize(original);

        if (normalized.isEmpty()) return command(AssistantCommand.Type.UNKNOWN, "", "", original);

        if (normalized.equals("ajuda") || normalized.equals("comandos")
                || normalized.contains("o que voce pode fazer")) {
            return command(AssistantCommand.Type.HELP, "", "", original);
        }

        if (normalized.contains("que horas") || normalized.equals("horas")
                || normalized.equals("horas agora")) {
            return command(AssistantCommand.Type.TIME, "", "", original);
        }

        if (normalized.contains("ajustes de acessibilidade")
                || normalized.equals("abrir acessibilidade")
                || normalized.equals("acessibilidade")) {
            return command(AssistantCommand.Type.ACCESSIBILITY_SETTINGS, "", "", original);
        }

        if (normalized.contains("quais aplicativos") || normalized.contains("listar aplicativos")
                || normalized.contains("quais apps") || normalized.contains("listar apps")) {
            return command(AssistantCommand.Type.LIST_APPS, "", "", original);
        }

        if (normalized.equals("leia minhas mensagens do whatsapp")
                || normalized.equals("ler minhas mensagens do whatsapp")
                || normalized.equals("mensagens do whatsapp")) {
            return command(AssistantCommand.Type.WHATSAPP_MESSAGES, "", "", original);
        }

        if (normalized.equals("executar o audio do whatsapp")
                || normalized.equals("tocar o audio do whatsapp")
                || normalized.equals("ouvir o audio do whatsapp")
                || normalized.equals("reproduzir audio do whatsapp")) {
            return command(AssistantCommand.Type.PLAY_WHATSAPP_AUDIO, "", "", original);
        }

        for (String prefix : Arrays.asList(
                "ligar via whatsapp para ", "ligue via whatsapp para ",
                "chamar no whatsapp ", "ligacao do whatsapp para ")) {
            if (normalized.startsWith(prefix)) {
                return command(AssistantCommand.Type.WHATSAPP_CALL,
                        stripLeadingWords(original, prefix), "", original);
            }
        }

        for (String prefix : SMS_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                String remainder = stripLeadingWords(original, prefix);
                Matcher divider = Pattern.compile(
                        "(?iu)\\s+(dizendo|com\\s+a\\s+mensagem)\\s+").matcher(remainder);
                if (divider.find()) {
                    return command(
                            AssistantCommand.Type.SMS,
                            remainder.substring(0, divider.start()),
                            remainder.substring(divider.end()),
                            original
                    );
                }
                return command(AssistantCommand.Type.SMS, remainder, "", original);
            }
        }

        for (String prefix : DIAL_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                return command(AssistantCommand.Type.DIAL,
                        stripLeadingWords(original, prefix), "", original);
            }
        }

        for (String prefix : MAP_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                return command(AssistantCommand.Type.MAP,
                        stripLeadingWords(original, prefix), "", original);
            }
        }

        for (String prefix : OPEN_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                return command(AssistantCommand.Type.OPEN_APP,
                        stripLeadingWords(original, prefix), "", original);
            }
        }

        return command(AssistantCommand.Type.UNKNOWN, "", "", original);
    }

    private AssistantCommand command(
            AssistantCommand.Type type,
            String target,
            String message,
            String original
    ) {
        return new AssistantCommand(type, target, message, original);
    }

    private String stripLeadingWords(String original, String normalizedPrefix) {
        int wordCount = normalizedPrefix.trim().split("\\s+").length;
        String[] pieces = original.trim().split("\\s+", wordCount + 1);
        return pieces.length > wordCount ? pieces[wordCount].trim() : "";
    }
}
