package br.com.exemplo.guiavoz.assistant;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashSet;
import java.util.Set;

public final class CommandParser {
    private static final float NEURAL_THRESHOLD = 0.58f;
    private static final float SENSITIVE_NEURAL_THRESHOLD = 0.80f;
    private final NeuralIntentModel neuralModel;
    private static final Set<String> WHATSAPP_NAMES = new HashSet<>(Arrays.asList(
            "whatsapp", "whats", "zap"
    ));
    private static final Set<String> CALL_WORDS = new HashSet<>(Arrays.asList(
            "ligar", "ligue", "chamar", "chame", "telefonar", "telefone",
            "ligacao", "chamada"
    ));
    private static final Set<String> AUDIO_ACTIONS = new HashSet<>(Arrays.asList(
            "executar", "execute", "reproduzir", "reproduza", "tocar", "toque",
            "ouvir", "ouca", "escutar", "escute", "rodar", "rode"
    ));
    private static final Set<String> READ_ACTIONS = new HashSet<>(Arrays.asList(
            "ler", "leia", "listar", "liste", "mostrar", "mostre"
    ));
    private static final Set<String> LISTEN_ACTIONS = new HashSet<>(Arrays.asList(
            "ouvir", "ouca", "escutar", "escute"
    ));
    private static final Set<String> SEND_WORDS = new HashSet<>(Arrays.asList(
            "mandar", "mande", "manda", "enviar", "envie", "envia", "escrever",
            "escreva", "avise", "avisar"
    ));
    private static final Set<String> REPLY_WORDS = new HashSet<>(Arrays.asList(
            "responder", "responda", "responde", "retornar", "retorne", "resposta"
    ));
    private static final List<String> SMS_PREFIXES = Arrays.asList(
            "enviar mensagem para ", "mandar mensagem para ",
            "enviar sms para ", "mandar sms para ", "mensagem para ", "sms para "
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

    public CommandParser() {
        this(null);
    }

    public CommandParser(NeuralIntentModel neuralModel) {
        this.neuralModel = neuralModel;
    }

    public AssistantCommand parse(String spokenText) {
        String original = spokenText == null ? "" : spokenText.trim();
        if (original.isEmpty()) return command(AssistantCommand.Type.UNKNOWN, "", "", original);
        NeuralIntentModel.Prediction lowConfidence = null;
        if (neuralModel != null) {
            NeuralIntentModel.Prediction prediction = neuralModel.predict(original);
            lowConfidence = prediction;
            try {
                AssistantCommand.Type semanticType = typeFromSemantics(prediction);
                AssistantCommand.Type terminalType = AssistantCommand.Type.valueOf(prediction.label);
                AssistantCommand.Type type = semanticType;
                if (type == null || (type != terminalType && prediction.intentConfidence >= 0.70f)) {
                    type = terminalType;
                }
                float threshold = isSensitive(type)
                        ? SENSITIVE_NEURAL_THRESHOLD : NEURAL_THRESHOLD;
                if (prediction.confidence >= threshold) {
                    AssistantCommand learned = fromPrediction(type, original, prediction.confidence);
                    if (learned != null) return learned;
                }
            } catch (IllegalArgumentException ignored) {
                // Um modelo mais novo nunca pode derrubar uma versão antiga do executor.
            }
        }
        AssistantCommand rules = parseByRules(original);
        if (rules.getType() == AssistantCommand.Type.UNKNOWN && lowConfidence != null) {
            return new AssistantCommand(AssistantCommand.Type.UNKNOWN, "", "", original,
                    lowConfidence.confidence, "neural-low-confidence:" + lowConfidence.semanticKey());
        }
        return rules;
    }

    private AssistantCommand parseByRules(String spokenText) {
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

        boolean whatsapp = containsWord(normalized, WHATSAPP_NAMES);
        boolean audio = containsAny(normalized, "audio", "audios", "mensagem de voz", "mensagens de voz");
        boolean messages = containsAny(normalized, "mensagem", "mensagens", "conversa", "conversas");

        if (containsWord(normalized, REPLY_WORDS)
                && (whatsapp || messages || normalized.contains("ultimo contato"))) {
            String[] slots = extractWhatsAppMessageSlots(original, true);
            return command(AssistantCommand.Type.WHATSAPP_REPLY_MESSAGE,
                    slots[0], slots[1], original);
        }

        if (containsWord(normalized, SEND_WORDS)
                && (whatsapp || ((messages || normalized.contains("texto"))
                && !containsAny(normalized, "sms", "mensagem de texto")))) {
            String[] slots = extractWhatsAppMessageSlots(original, false);
            return command(AssistantCommand.Type.WHATSAPP_SEND_MESSAGE,
                    slots[0], slots[1], original);
        }

        if (audio && containsWord(normalized, AUDIO_ACTIONS)) {
            return command(AssistantCommand.Type.PLAY_WHATSAPP_AUDIO, "", "", original);
        }

        if (messages && containsWord(normalized, LISTEN_ACTIONS)) {
            return command(AssistantCommand.Type.WHATSAPP_LISTEN_MESSAGES, "", "", original);
        }

        if (messages && (containsWord(normalized, READ_ACTIONS)
                || (whatsapp && !containsWord(normalized, CALL_WORDS))
                || normalized.equals("mensagens"))) {
            return command(AssistantCommand.Type.WHATSAPP_MESSAGES, "", "", original);
        }

        if (whatsapp && containsWord(normalized, CALL_WORDS)) {
            return command(AssistantCommand.Type.WHATSAPP_CALL,
                    extractWhatsAppCallTarget(original), "", original);
        }

        if (containsAny(normalized, "pausar musica", "pause a musica", "parar musica",
                "pare a musica", "pausar audio", "pause o audio")) {
            return command(AssistantCommand.Type.MEDIA_PAUSE, "", "", original);
        }

        if (containsAny(normalized, "proxima musica", "proxima faixa", "avancar musica",
                "pular musica", "pule a musica")) {
            return command(AssistantCommand.Type.MEDIA_NEXT, "", "", original);
        }

        if (containsAny(normalized, "continuar musica", "continue a musica", "tocar musica",
                "reproduzir musica", "voltar a musica")) {
            return command(AssistantCommand.Type.MEDIA_PLAY, "", "", original);
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

    private AssistantCommand fromPrediction(AssistantCommand.Type type, String original,
                                            float confidence) {
        AssistantCommand rules = parseByRules(original);
        if (rules.getType() == type) {
            return neuralCommand(type, rules.getTarget(), rules.getMessage(), original, confidence);
        }
        return switch (type) {
            case WHATSAPP_CALL -> neuralCommand(type, extractWhatsAppCallTarget(original), "",
                    original, confidence);
            case WHATSAPP_SEND_MESSAGE, WHATSAPP_REPLY_MESSAGE -> {
                String[] slots = extractWhatsAppMessageSlots(original,
                        type == AssistantCommand.Type.WHATSAPP_REPLY_MESSAGE);
                yield neuralCommand(type, slots[0], slots[1], original, confidence);
            }
            case OPEN_APP -> withRequiredTarget(type,
                    stripIntentWords(original, "abrir", "abre", "abra", "iniciar", "inicie",
                            "aplicativo", "app", "programa", "quero", "usar", "entrar", "no", "na"),
                    "", original, confidence);
            case DIAL -> withRequiredTarget(type,
                    stripIntentWords(original, "ligar", "ligue", "telefone", "telefonar", "chamar",
                            "chame", "ligacao", "chamada", "normal", "comum", "para", "pelo", "use", "o"),
                    "", original, confidence);
            case MAP -> withRequiredTarget(type,
                    stripIntentWords(original, "mapa", "rota", "caminho", "direcoes", "localize",
                            "localizar", "mostre", "calcule", "encontre", "para", "ate", "a", "ao"),
                    "", original, confidence);
            case TAP_ELEMENT -> withRequiredTarget(type,
                    stripIntentWords(original, "toque", "tocar", "clique", "clicar", "aperte",
                            "pressione", "selecione", "escolha", "botao", "controle", "elemento",
                            "opcao", "onde", "esta", "estiver", "escrito", "diz", "chamado", "em", "no", "na"),
                    "", original, confidence);
            case TYPE_TEXT -> withRequiredTarget(type, "",
                    stripIntentWords(original, "digite", "digitar", "escreva", "escrever", "insira",
                            "inserir", "coloque", "preencha", "campo", "pesquisa", "com", "o", "a", "no", "na"),
                    original, confidence);
            case SMS -> rules.getType() == AssistantCommand.Type.UNKNOWN ? null
                    : neuralCommand(type, rules.getTarget(), rules.getMessage(), original, confidence);
            default -> neuralCommand(type, "", "", original, confidence);
        };
    }

    private AssistantCommand withRequiredTarget(AssistantCommand.Type type, String target,
                                                String message, String original, float confidence) {
        String required = type == AssistantCommand.Type.TYPE_TEXT ? message : target;
        if (required.trim().isEmpty()) return null;
        return neuralCommand(type, target, message, original, confidence);
    }

    private AssistantCommand neuralCommand(AssistantCommand.Type type, String target,
                                           String message, String original, float confidence) {
        return new AssistantCommand(type, target, message, original, confidence, "neural");
    }

    private boolean isSensitive(AssistantCommand.Type type) {
        return type == AssistantCommand.Type.DIAL
                || type == AssistantCommand.Type.SMS
                || type == AssistantCommand.Type.WHATSAPP_CALL
                || type == AssistantCommand.Type.WHATSAPP_SEND_MESSAGE
                || type == AssistantCommand.Type.WHATSAPP_REPLY_MESSAGE;
    }

    private AssistantCommand.Type typeFromSemantics(NeuralIntentModel.Prediction prediction) {
        String key = prediction.semanticKey();
        return switch (key) {
            case "SEND:MESSAGE:WHATSAPP" -> AssistantCommand.Type.WHATSAPP_SEND_MESSAGE;
            case "REPLY:MESSAGE:WHATSAPP" -> AssistantCommand.Type.WHATSAPP_REPLY_MESSAGE;
            case "READ:MESSAGE:WHATSAPP" -> AssistantCommand.Type.WHATSAPP_MESSAGES;
            case "LISTEN:MESSAGE:WHATSAPP" -> AssistantCommand.Type.WHATSAPP_LISTEN_MESSAGES;
            case "CALL:CONTACT:WHATSAPP" -> AssistantCommand.Type.WHATSAPP_CALL;
            case "CALL:CONTACT:PHONE" -> AssistantCommand.Type.DIAL;
            case "SEND:MESSAGE:SMS" -> AssistantCommand.Type.SMS;
            case "PLAY:AUDIO:WHATSAPP" -> AssistantCommand.Type.PLAY_WHATSAPP_AUDIO;
            case "NEXT:AUDIO:WHATSAPP" -> AssistantCommand.Type.PLAY_NEXT_AUDIO;
            case "REPEAT:AUDIO:WHATSAPP" -> AssistantCommand.Type.REPEAT_AUDIO;
            case "OPEN:APP:ANY" -> AssistantCommand.Type.OPEN_APP;
            case "LIST:APP:ANY" -> AssistantCommand.Type.LIST_APPS;
            case "READ:SCREEN:CURRENT" -> AssistantCommand.Type.READ_SCREEN;
            case "TAP:ELEMENT:CURRENT" -> AssistantCommand.Type.TAP_ELEMENT;
            case "TYPE:TEXT:CURRENT" -> AssistantCommand.Type.TYPE_TEXT;
            case "SCROLL_DOWN:SCREEN:CURRENT" -> AssistantCommand.Type.SCROLL_DOWN;
            case "SCROLL_UP:SCREEN:CURRENT" -> AssistantCommand.Type.SCROLL_UP;
            case "BACK:SCREEN:CURRENT" -> AssistantCommand.Type.BACK;
            case "PLAY:MEDIA:ANY" -> AssistantCommand.Type.MEDIA_PLAY;
            case "PAUSE:MEDIA:ANY" -> AssistantCommand.Type.MEDIA_PAUSE;
            case "NEXT:MEDIA:ANY" -> AssistantCommand.Type.MEDIA_NEXT;
            case "CONTINUE:SESSION:WHATSAPP" -> AssistantCommand.Type.CONTINUE_SESSION;
            case "GET:TIME:SYSTEM" -> AssistantCommand.Type.TIME;
            case "HELP:NONE:SYSTEM" -> AssistantCommand.Type.HELP;
            case "OPEN:MAP:SYSTEM" -> AssistantCommand.Type.MAP;
            case "OPEN:ACCESSIBILITY:SYSTEM" -> AssistantCommand.Type.ACCESSIBILITY_SETTINGS;
            default -> null;
        };
    }

    private String[] extractWhatsAppMessageSlots(String original, boolean reply) {
        String beforeContent = original.trim();
        String content = "";
        Matcher divider = Pattern.compile(
                "(?iu)\\s+(dizendo|falando|com\\s+a\\s+mensagem|com\\s+o\\s+texto|que)\\s+")
                .matcher(beforeContent);
        if (divider.find()) {
            content = beforeContent.substring(divider.end()).trim();
            beforeContent = beforeContent.substring(0, divider.start()).trim();
        }
        String target = stripIntentWords(beforeContent,
                "mandar", "mande", "manda", "enviar", "envie", "envia", "escrever",
                "escreva", "avise", "avisar", "fale", "responder", "responda", "responde",
                "retornar", "retorne", "resposta", "mensagem", "texto", "recado", "escrito",
                "whatsapp", "whats", "zap", "pelo", "pela", "via", "no", "na", "para",
                "pra", "pro", "ao", "a", "o", "uma", "um", "quero", "preciso", "ultima",
                "ultimo", "pessoa", "contato", "quem", "acabou", "falar", "comigo", "essa",
                "esta", "conversa");
        if (reply && target.equalsIgnoreCase("mensagem")) target = "";
        return new String[]{target, content};
    }

    private String stripIntentWords(String original, String... ignoredWords) {
        Set<String> ignored = new HashSet<>();
        ignored.addAll(Arrays.asList(ignoredWords));
        String[] originalWords = original.trim().split("\\s+");
        String[] normalizedWords = TextNormalizer.normalize(original).split(" ");
        StringBuilder result = new StringBuilder();
        int count = Math.min(originalWords.length, normalizedWords.length);
        for (int index = 0; index < count; index++) {
            if (ignored.contains(normalizedWords[index])) continue;
            if (result.length() > 0) result.append(' ');
            result.append(originalWords[index]);
        }
        return result.toString().trim();
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

    private boolean containsAny(String normalized, String... phrases) {
        for (String phrase : phrases) {
            if (normalized.equals(phrase) || normalized.contains(phrase)) return true;
        }
        return false;
    }

    private boolean containsWord(String normalized, Set<String> words) {
        for (String token : normalized.split(" ")) {
            if (words.contains(token)) return true;
        }
        return false;
    }

    private String extractWhatsAppCallTarget(String original) {
        String[] originalWords = original.trim().split("\\s+");
        String[] normalizedWords = TextNormalizer.normalize(original).split(" ");
        Set<String> ignored = new HashSet<>();
        ignored.addAll(WHATSAPP_NAMES);
        ignored.addAll(CALL_WORDS);
        ignored.addAll(Arrays.asList("para", "pra", "pro", "via", "pelo", "pela",
                "no", "na", "do", "da", "uma", "chamada", "ligacao"));
        StringBuilder target = new StringBuilder();
        int count = Math.min(originalWords.length, normalizedWords.length);
        for (int index = 0; index < count; index++) {
            if (ignored.contains(normalizedWords[index])) continue;
            if (target.length() > 0) target.append(' ');
            target.append(originalWords[index]);
        }
        return target.toString().trim();
    }
}
