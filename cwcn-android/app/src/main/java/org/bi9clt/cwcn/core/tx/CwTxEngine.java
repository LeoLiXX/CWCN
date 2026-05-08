package org.bi9clt.cwcn.core.tx;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CwTxEngine {
    private static final int DEFAULT_WPM = 18;
    private static final int DEFAULT_TONE_FREQUENCY_HZ = 650;
    private static final Map<Character, String> MORSE_MAP = createMorseMap();

    public CwTxPlan buildPlan(String rawText) {
        return buildPlan(rawText, DEFAULT_WPM, DEFAULT_TONE_FREQUENCY_HZ);
    }

    public CwTxPlan buildPlan(String rawText, int wpm, int toneFrequencyHz) {
        int safeWpm = Math.max(5, wpm);
        int safeToneFrequencyHz = Math.max(200, toneFrequencyHz);
        int dotDurationMs = Math.max(20, (int) Math.round(1200.0d / safeWpm));
        String normalizedText = normalizeText(rawText);
        ArrayList<CwTxElement> elements = new ArrayList<>();
        String morsePreview = buildElements(normalizedText, dotDurationMs, elements);
        return new CwTxPlan(
                rawText,
                normalizedText,
                morsePreview,
                safeWpm,
                safeToneFrequencyHz,
                dotDurationMs,
                elements
        );
    }

    public String normalizeText(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return "";
        }
        String upper = rawText.toUpperCase(Locale.US);
        StringBuilder builder = new StringBuilder();
        boolean previousWasSpace = true;
        for (int index = 0; index < upper.length(); index++) {
            char current = upper.charAt(index);
            if (Character.isWhitespace(current)) {
                if (!previousWasSpace && builder.length() > 0) {
                    builder.append(' ');
                }
                previousWasSpace = true;
                continue;
            }
            if (!MORSE_MAP.containsKey(current)) {
                continue;
            }
            builder.append(current);
            previousWasSpace = false;
        }
        int length = builder.length();
        while (length > 0 && builder.charAt(length - 1) == ' ') {
            builder.deleteCharAt(length - 1);
            length -= 1;
        }
        return builder.toString();
    }

    private String buildElements(String normalizedText, int dotDurationMs, List<CwTxElement> elements) {
        if (normalizedText.isEmpty()) {
            return "";
        }

        StringBuilder morsePreview = new StringBuilder();
        for (int index = 0; index < normalizedText.length(); index++) {
            char current = normalizedText.charAt(index);
            if (current == ' ') {
                if (morsePreview.length() > 0 && morsePreview.charAt(morsePreview.length() - 1) != ' ') {
                    morsePreview.append(" / ");
                }
                appendElement(elements, CwTxElement.Kind.KEY_UP, dotDurationMs * 7, " ", index);
                continue;
            }

            String morse = MORSE_MAP.get(current);
            if (morse == null) {
                continue;
            }
            if (morsePreview.length() > 0 && morsePreview.charAt(morsePreview.length() - 1) != ' ') {
                morsePreview.append(' ');
            }
            morsePreview.append(morse);

            for (int symbolIndex = 0; symbolIndex < morse.length(); symbolIndex++) {
                char symbol = morse.charAt(symbolIndex);
                int keyDownDurationMs = symbol == '-' ? dotDurationMs * 3 : dotDurationMs;
                appendElement(elements, CwTxElement.Kind.KEY_DOWN, keyDownDurationMs, String.valueOf(current), index);
                if (symbolIndex < morse.length() - 1) {
                    appendElement(elements, CwTxElement.Kind.KEY_UP, dotDurationMs, String.valueOf(current), index);
                }
            }

            char next = index + 1 < normalizedText.length() ? normalizedText.charAt(index + 1) : '\0';
            if (next != '\0' && next != ' ') {
                appendElement(elements, CwTxElement.Kind.KEY_UP, dotDurationMs * 3, String.valueOf(current), index);
            }
        }

        return morsePreview.toString().trim();
    }

    private void appendElement(
            List<CwTxElement> elements,
            CwTxElement.Kind kind,
            int durationMs,
            String sourceSymbol,
            int sourceTextIndex
    ) {
        if (durationMs <= 0) {
            return;
        }
        if (!elements.isEmpty()) {
            CwTxElement previous = elements.get(elements.size() - 1);
            if (previous.kind() == kind) {
                elements.set(
                        elements.size() - 1,
                        new CwTxElement(
                                kind,
                                previous.durationMs() + durationMs,
                                previous.sourceSymbol(),
                                previous.sourceTextIndex()
                        )
                );
                return;
            }
        }
        elements.add(new CwTxElement(kind, durationMs, sourceSymbol, sourceTextIndex));
    }

    private static Map<Character, String> createMorseMap() {
        LinkedHashMap<Character, String> map = new LinkedHashMap<>();
        map.put('A', ".-");
        map.put('B', "-...");
        map.put('C', "-.-.");
        map.put('D', "-..");
        map.put('E', ".");
        map.put('F', "..-.");
        map.put('G', "--.");
        map.put('H', "....");
        map.put('I', "..");
        map.put('J', ".---");
        map.put('K', "-.-");
        map.put('L', ".-..");
        map.put('M', "--");
        map.put('N', "-.");
        map.put('O', "---");
        map.put('P', ".--.");
        map.put('Q', "--.-");
        map.put('R', ".-.");
        map.put('S', "...");
        map.put('T', "-");
        map.put('U', "..-");
        map.put('V', "...-");
        map.put('W', ".--");
        map.put('X', "-..-");
        map.put('Y', "-.--");
        map.put('Z', "--..");
        map.put('0', "-----");
        map.put('1', ".----");
        map.put('2', "..---");
        map.put('3', "...--");
        map.put('4', "....-");
        map.put('5', ".....");
        map.put('6', "-....");
        map.put('7', "--...");
        map.put('8', "---..");
        map.put('9', "----.");
        map.put('/', "-..-.");
        map.put('?', "..--..");
        map.put('.', ".-.-.-");
        map.put(',', "--..--");
        map.put('=', "-...-");
        map.put('+', ".-.-.");
        map.put('-', "-....-");
        return map;
    }
}
