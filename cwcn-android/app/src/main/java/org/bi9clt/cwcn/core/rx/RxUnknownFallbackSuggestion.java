package org.bi9clt.cwcn.core.rx;

import java.util.ArrayList;
import java.util.List;

public final class RxUnknownFallbackSuggestion {
    private final String rawText;
    private final String suggestedText;
    private final int unknownCount;
    private final List<String> notes;

    public RxUnknownFallbackSuggestion(
            String rawText,
            String suggestedText,
            int unknownCount,
            List<String> notes
    ) {
        this.rawText = safeText(rawText);
        this.suggestedText = safeText(suggestedText);
        this.unknownCount = Math.max(0, unknownCount);
        this.notes = notes == null ? new ArrayList<>() : new ArrayList<>(notes);
    }

    public static RxUnknownFallbackSuggestion none(String rawText) {
        return new RxUnknownFallbackSuggestion(rawText, "", countUnknown(rawText), new ArrayList<>());
    }

    public String rawText() {
        return rawText;
    }

    public String suggestedText() {
        return suggestedText;
    }

    public int unknownCount() {
        return unknownCount;
    }

    public List<String> notes() {
        return new ArrayList<>(notes);
    }

    public boolean hasSuggestion() {
        return !suggestedText.isEmpty() && !suggestedText.equals(rawText);
    }

    public boolean hasNotes() {
        return !notes.isEmpty();
    }

    public String notesText() {
        if (notes.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String note : notes) {
            if (note == null || note.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(note.trim());
        }
        return builder.toString();
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }

    private static int countUnknown(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == '?') {
                count++;
            }
        }
        return count;
    }
}
