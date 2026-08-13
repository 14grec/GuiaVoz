package br.com.exemplo.guiavoz.data;

import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.ContactsContract;

import br.com.exemplo.guiavoz.assistant.TextNormalizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class ContactRepository {
    public static final class Match {
        public enum Status { FOUND, NOT_FOUND, AMBIGUOUS }

        public final Status status;
        public final String displayName;
        public final String phoneNumber;
        public final List<String> alternatives;

        private Match(Status status, String displayName, String phoneNumber, List<String> alternatives) {
            this.status = status;
            this.displayName = displayName;
            this.phoneNumber = phoneNumber;
            this.alternatives = alternatives;
        }

        public static Match found(String name, String phone) {
            return new Match(Status.FOUND, name, phone, Collections.emptyList());
        }

        public static Match notFound() {
            return new Match(Status.NOT_FOUND, "", "", Collections.emptyList());
        }

        public static Match ambiguous(List<String> names) {
            return new Match(Status.AMBIGUOUS, "", "", names);
        }
    }

    private static final class Candidate {
        final String name;
        final String phone;
        final int score;

        Candidate(String name, String phone, int score) {
            this.name = name;
            this.phone = phone;
            this.score = score;
        }
    }

    private final ContentResolver resolver;

    public ContactRepository(ContentResolver resolver) {
        this.resolver = resolver;
    }

    public Match findByName(String requestedName) {
        String wanted = TextNormalizer.normalize(requestedName);
        if (wanted.isEmpty()) return Match.notFound();

        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };
        List<Candidate> candidates = new ArrayList<>();

        try (Cursor cursor = resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )) {
            if (cursor == null) return Match.notFound();
            int nameColumn = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
            int phoneColumn = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.NUMBER);

            while (cursor.moveToNext()) {
                String name = cursor.getString(nameColumn);
                String phone = cursor.getString(phoneColumn);
                String normalizedName = TextNormalizer.normalize(name);
                int score = score(normalizedName, wanted);
                if (score > 0 && phone != null && !phone.trim().isEmpty()) {
                    candidates.add(new Candidate(name, phone, score));
                }
            }
        }

        if (candidates.isEmpty()) return Match.notFound();
        candidates.sort(Comparator.comparingInt((Candidate item) -> item.score).reversed());
        int bestScore = candidates.get(0).score;
        List<Candidate> best = candidates.stream()
                .filter(item -> item.score == bestScore)
                .collect(Collectors.toList());

        String firstNormalizedName = TextNormalizer.normalize(best.get(0).name);
        boolean samePerson = best.stream().allMatch(
                item -> TextNormalizer.normalize(item.name).equals(firstNormalizedName));
        if (samePerson) return Match.found(best.get(0).name, best.get(0).phone);

        List<String> names = best.stream().map(item -> item.name).distinct().limit(4)
                .collect(Collectors.toList());
        return Match.ambiguous(names);
    }

    private int score(String candidate, String wanted) {
        if (candidate.equals(wanted)) return 100;
        if (candidate.startsWith(wanted + " ")) return 80;
        if (candidate.contains(" " + wanted + " ") || candidate.endsWith(" " + wanted)) return 60;
        if (candidate.contains(wanted)) return 40;
        return 0;
    }
}
