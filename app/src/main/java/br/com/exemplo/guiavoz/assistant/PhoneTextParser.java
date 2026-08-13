package br.com.exemplo.guiavoz.assistant;

import java.util.HashMap;
import java.util.Map;

public final class PhoneTextParser {
    private static final Map<String, String> DIGITS = new HashMap<>();

    static {
        DIGITS.put("zero", "0");
        DIGITS.put("um", "1");
        DIGITS.put("uma", "1");
        DIGITS.put("dois", "2");
        DIGITS.put("duas", "2");
        DIGITS.put("tres", "3");
        DIGITS.put("quatro", "4");
        DIGITS.put("cinco", "5");
        DIGITS.put("seis", "6");
        DIGITS.put("meia", "6");
        DIGITS.put("sete", "7");
        DIGITS.put("oito", "8");
        DIGITS.put("nove", "9");
    }

    private PhoneTextParser() {}

    public static String extract(String value) {
        if (value == null) return "";
        String normalized = TextNormalizer.normalize(value);
        boolean hasNumericDigit = normalized.matches(".*\\d.*");
        if (hasNumericDigit) {
            String prefix = normalized.trim().startsWith("+") ? "+" : "";
            return prefix + normalized.replaceAll("\\D", "");
        }

        StringBuilder result = new StringBuilder();
        int recognizedWords = 0;
        for (String word : normalized.split(" ")) {
            String digit = DIGITS.get(word);
            if (digit != null) {
                result.append(digit);
                recognizedWords++;
            }
        }
        return recognizedWords >= 3 ? result.toString() : "";
    }
}
