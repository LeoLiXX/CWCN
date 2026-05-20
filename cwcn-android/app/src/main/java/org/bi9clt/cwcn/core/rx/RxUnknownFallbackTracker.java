package org.bi9clt.cwcn.core.rx;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.decoder.CwDecoder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class RxUnknownFallbackTracker {
    private final List<Entry> entries = new ArrayList<>();

    public synchronized void process(CwDecodeEvent decodeEvent) {
        if (decodeEvent == null) {
            return;
        }
        if (decodeEvent.type() == CwDecodeEvent.Type.CHARACTER_DECODED) {
            appendCharacter(decodeEvent);
        } else if (decodeEvent.type() == CwDecodeEvent.Type.WORD_BREAK) {
            appendWordBreak();
        }
    }

    public synchronized void reset() {
        entries.clear();
    }

    public synchronized RxUnknownFallbackSuggestion snapshot() {
        String rawText = buildText(false);
        String suggestedText = buildText(true);
        ArrayList<String> notes = buildNotes();
        if (suggestedText.equals(rawText) && notes.isEmpty()) {
            return RxUnknownFallbackSuggestion.none(rawText);
        }
        return new RxUnknownFallbackSuggestion(
                rawText,
                suggestedText,
                countUnknown(rawText),
                notes
        );
    }

    private void appendCharacter(CwDecodeEvent decodeEvent) {
        String rawCharacter = decodeEvent.emittedValue();
        if (CwDecoder.UNKNOWN_CHARACTER.equals(rawCharacter)) {
            rawCharacter = "?";
        }
        if (rawCharacter == null || rawCharacter.isEmpty()) {
            return;
        }

        String replacementText = null;
        String bestEffortReplacementText = null;
        if (decodeEvent.unknownCharacter()) {
            replacementText = RxUnknownFallbackResolver.resolveUnknownSequence(
                    decodeEvent.sourceSequence()
            );
            bestEffortReplacementText = RxUnknownFallbackResolver.resolveUnknownSequenceBestEffort(
                    decodeEvent.sourceSequence()
            );
        }
        entries.add(new Entry(
                rawCharacter,
                replacementText,
                bestEffortReplacementText,
                decodeEvent.sourceSequence()
        ));
    }

    private void appendWordBreak() {
        if (!entries.isEmpty() && " ".equals(entries.get(entries.size() - 1).rawText)) {
            return;
        }
        entries.add(new Entry(" ", null, null, ""));
    }

    private String buildText(boolean useReplacement) {
        StringBuilder builder = new StringBuilder();
        for (Entry entry : entries) {
            if (useReplacement && entry.replacementText != null && !entry.replacementText.isEmpty()) {
                builder.append(entry.replacementText);
            } else {
                builder.append(entry.rawText);
            }
        }
        return stripTrailingWhitespace(builder.toString());
    }

    private ArrayList<String> buildNotes() {
        LinkedHashSet<String> notes = new LinkedHashSet<>();
        for (Entry entry : entries) {
            if (entry.bestEffortReplacementText == null
                    || entry.bestEffortReplacementText.isEmpty()
                    || entry.bestEffortReplacementText.equals(entry.rawText)
                    || entry.bestEffortReplacementText.equals(entry.replacementText)) {
                continue;
            }
            notes.add(formatNote(entry.sourceSequence, entry.bestEffortReplacementText));
        }
        return new ArrayList<>(notes);
    }

    private static String formatNote(String sourceSequence, String bestEffortReplacementText) {
        String normalizedSequence = sourceSequence == null ? "" : sourceSequence.trim();
        if (normalizedSequence.isEmpty()) {
            return "? -> " + bestEffortReplacementText;
        }
        return "?(" + normalizedSequence + ") -> " + bestEffortReplacementText;
    }

    private static String stripTrailingWhitespace(String value) {
        int end = value.length();
        while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return end == value.length() ? value : value.substring(0, end);
    }

    private static int countUnknown(String text) {
        int count = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == '?') {
                count++;
            }
        }
        return count;
    }

    private static final class Entry {
        private final String rawText;
        private final String replacementText;
        private final String bestEffortReplacementText;
        private final String sourceSequence;

        private Entry(
                String rawText,
                String replacementText,
                String bestEffortReplacementText,
                String sourceSequence
        ) {
            this.rawText = rawText;
            this.replacementText = replacementText;
            this.bestEffortReplacementText = bestEffortReplacementText;
            this.sourceSequence = sourceSequence == null ? "" : sourceSequence;
        }
    }
}
