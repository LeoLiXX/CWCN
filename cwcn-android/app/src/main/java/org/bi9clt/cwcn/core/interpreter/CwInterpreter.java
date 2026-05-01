package org.bi9clt.cwcn.core.interpreter;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.decoder.CwDecoder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class CwInterpreter {
    public enum RecoveryMode {
        SEMANTIC_RECOVERY,
        RAW_COPY_FOCUS
    }

    private static final List<String> REPORT_TOKEN_VARIANTS = Arrays.asList("5NN", "599", "ENN");
    private static final List<String> CONTROL_TOKEN_VARIANTS = Arrays.asList("BK", "KN", "K");
    private static final List<String> PREFIX_SPLIT_KEYWORDS = Arrays.asList(
            "QRZ", "CQ", "DE", "5NN", "ENN", "599", "UR", "BK", "KN", "TNX", "TU", "73"
    );
    private static final List<String> BRIDGE_SPLIT_KEYWORDS = Arrays.asList(
            "DE", "QRZ", "UR", "TNX", "TU", "BK", "KN", "73", "5NN", "ENN", "599", "CQ"
    );
    private static final Pattern CALLSIGN_PATTERN =
            Pattern.compile("^(?=.{3,})(?=.*[A-Z])(?=.*\\d)[A-Z0-9?/]{3,}$");
    private static final Set<String> NON_CALLSIGN_TOKENS = new LinkedHashSet<>(Arrays.asList(
            "CQ", "DE", "QRZ", "K", "KN", "BK", "PSE", "PLS", "AGN", "R", "UR", "TU", "TNX", "599", "5NN", "ENN",
            "PLEASE", "CALL", "CALLSIGN", "AGAIN", "73", "FB", "SK"
    ));
    private static final Set<String> MERGEABLE_SPLIT_TOKENS = new LinkedHashSet<>(Arrays.asList(
            "DE", "BK", "KN", "TU", "TNX", "AGN", "PSE", "PLS", "5NN", "ENN", "599", "73"
    ));

    private final RecoveryMode recoveryMode;

    private String rawText = "";
    private String normalizedText = "";
    private List<CwInterpretedToken> tokens = new ArrayList<>();
    private String rememberedPrimaryCallsign;
    private String rememberedAddressedCallsign;
    private String rememberedSpeakerCallsign;
    private String primaryCallsignCandidate;
    private List<String> callsignCandidates = new ArrayList<>();
    private List<String> phraseHints = new ArrayList<>();
    private CwInterpretationEvent lastEvent;

    public CwInterpreter() {
        this(RecoveryMode.SEMANTIC_RECOVERY);
    }

    public CwInterpreter(RecoveryMode recoveryMode) {
        this.recoveryMode = recoveryMode == null
                ? RecoveryMode.SEMANTIC_RECOVERY
                : recoveryMode;
    }

    public synchronized void process(CwDecodeEvent decodeEvent) {
        if (decodeEvent.type() != CwDecodeEvent.Type.CHARACTER_DECODED
                && decodeEvent.type() != CwDecodeEvent.Type.WORD_BREAK) {
            return;
        }

        // RAW must stay as a faithful view of the observed decode stream.
        // Human-oriented cleanup and structural recovery belong to Normalize,
        // not to the RAW channel itself.
        rawText = stabilizeVisibleRawText(decodeEvent.outputText());
        parseCurrentText(decodeEvent.timestampMs());
    }

    public synchronized void reset() {
        rawText = "";
        normalizedText = "";
        tokens = new ArrayList<>();
        rememberedPrimaryCallsign = null;
        rememberedAddressedCallsign = null;
        rememberedSpeakerCallsign = null;
        primaryCallsignCandidate = null;
        callsignCandidates = new ArrayList<>();
        phraseHints = new ArrayList<>();
        lastEvent = null;
    }

    public synchronized CwInterpreterSnapshot snapshot() {
        return new CwInterpreterSnapshot(
                rawText,
                normalizedText,
                new ArrayList<>(tokens),
                primaryCallsignCandidate,
                new ArrayList<>(callsignCandidates),
                new ArrayList<>(phraseHints),
                lastEvent
        );
    }

    private void parseCurrentText(long timestampMs) {
        if (recoveryMode == RecoveryMode.RAW_COPY_FOCUS) {
            parseRawCopyFocusText(timestampMs);
            return;
        }

        ArrayList<CwInterpretedToken> parsedTokens = new ArrayList<>();
        ArrayList<String> rawCallsignCandidates = new ArrayList<>();
        LinkedHashSet<String> hintSet = new LinkedHashSet<>();
        ArrayList<String> normalizedParts = new ArrayList<>();

        if (!rawText.isEmpty()) {
            List<String> rawParts = normalizeRawParts(expandCompoundText(rawText).split("\\s+"));
            for (int index = 0; index < rawParts.size(); index++) {
                String rawPart = rawParts.get(index);
                if (rawPart.isEmpty()) {
                    continue;
                }

                String normalized = normalizeToken(rawPart, rawParts, index);
                CwInterpretedToken.Type type = classifyToken(rawPart, normalized, rawParts, index);
                parsedTokens.add(new CwInterpretedToken(rawPart, normalized, type));
                normalizedParts.add(normalized);

                if (type == CwInterpretedToken.Type.CALLSIGN_CANDIDATE) {
                    rawCallsignCandidates.add(normalized);
                }
            }
        }

        normalizedText = String.join(" ", normalizedParts).trim();
        tokens = parsedTokens;
        ContextualCallsigns contextualCallsigns = extractContextualCallsigns(parsedTokens);
        String repeatedCleanCallsign = chooseRepeatedTrustedCleanCallsign(rawCallsignCandidates);
        callsignCandidates = buildRecoveredCallsignCandidates(rawCallsignCandidates, contextualCallsigns);
        callsignCandidates = preferRepeatedCleanCallsign(callsignCandidates, repeatedCleanCallsign);
        primaryCallsignCandidate = choosePrimaryCallsignCandidate(parsedTokens, callsignCandidates, repeatedCleanCallsign);
        if (isTrustedCleanCallsign(primaryCallsignCandidate)) {
            rememberedPrimaryCallsign = primaryCallsignCandidate;
        }
        updateRememberedContextualCallsigns(contextualCallsigns, callsignCandidates);

        if (containsNormalizedToken(parsedTokens, "CQ")) {
            hintSet.add("CQ / calling flow");
        }
        if (containsNormalizedToken(parsedTokens, "QRZ")) {
            hintSet.add("QRZ / next caller flow");
        }
        if (containsNormalizedToken(parsedTokens, "DE") && !callsignCandidates.isEmpty()) {
            hintSet.add("Station identification / callsign exchange");
        }
        if (containsReportValueToken(parsedTokens)) {
            hintSet.add("Report exchange");
        }
        if (containsReportLeadSequence(parsedTokens, "UR")) {
            hintSet.add("Directed report to called station");
        }
        if (containsReportLeadSequence(parsedTokens, "R")) {
            hintSet.add("Report acknowledgement / return report");
        }
        if (hasRepeatClarificationHint(parsedTokens)) {
            hintSet.add("Repeat / clarification request");
        }
        if (hasUncertainCallsign(rawCallsignCandidates)) {
            hintSet.add("Partial callsign / uncertain copy");
        }
        if (hasPartialToFullUpgrade(rawCallsignCandidates, callsignCandidates)) {
            hintSet.add("Partial callsign resolved");
        }
        if (primaryCallsignCandidate != null
                && (containsSemanticToken(parsedTokens, "CALL")
                || containsSemanticToken(parsedTokens, "CALLSIGN")
                || containsSemanticToken(parsedTokens, "AGAIN"))) {
            hintSet.add("Callsign confirmation cycle");
        }
        if (hasTurnHandoffHint(parsedTokens)) {
            hintSet.add("Turn handoff / over");
        }
        if (hasShortTailEndingHint(parsedTokens, callsignCandidates)) {
            hintSet.add("Short-tail ending");
        }
        if (containsSemanticToken(parsedTokens, "THANKS")) {
            hintSet.add("Closing / acknowledgement");
        }
        if (containsSemanticToken(parsedTokens, "73")) {
            hintSet.add("73 closing");
        }

        phraseHints = new ArrayList<>(hintSet);

        String latestTokenSummary = parsedTokens.isEmpty()
                ? "(none)"
                : formatTokenSummary(parsedTokens.get(parsedTokens.size() - 1));
        lastEvent = new CwInterpretationEvent(
                timestampMs,
                rawText,
                normalizedText,
                latestTokenSummary
        );
    }

    private String stabilizeVisibleRawText(String candidateText) {
        if (candidateText == null) {
            return "";
        }
        String visibleRawText = candidateText.replace(CwDecoder.UNKNOWN_CHARACTER, "?");
        if (visibleRawText.trim().isEmpty()) {
            return "";
        }
        return visibleRawText.stripTrailing();
    }

    private List<String> normalizeRawCopyParts(String[] rawParts) {
        ArrayList<String> normalizedParts = new ArrayList<>();
        for (int index = 0; index < rawParts.length; index++) {
            String token = rawParts[index];
            if (token == null || token.isEmpty()) {
                continue;
            }
            String mergedFixedToken = mergeSplitFixedToken(rawParts, index);
            if (mergedFixedToken != null) {
                normalizedParts.add(mergedFixedToken);
                index += countMergedSplitTokens(rawParts, index, mergedFixedToken.length()) - 1;
                continue;
            }
            normalizedParts.add(token);
        }
        return expandRawCopyReadableParts(normalizedParts);
    }

    private List<String> expandRawCopyReadableParts(List<String> rawParts) {
        ArrayList<String> readableParts = new ArrayList<>();
        for (String token : rawParts) {
            if (token == null || token.isEmpty()) {
                continue;
            }
            List<String> repeatedCallsignSplit = splitRepeatedCallsignRun(token);
            if (!repeatedCallsignSplit.isEmpty()) {
                readableParts.addAll(repeatedCallsignSplit);
                continue;
            }
            readableParts.add(token);
        }
        return readableParts;
    }

    private void parseRawCopyFocusText(long timestampMs) {
        ArrayList<CwInterpretedToken> parsedTokens = new ArrayList<>();
        ArrayList<String> normalizedParts = new ArrayList<>();
        LinkedHashSet<String> callsignSet = new LinkedHashSet<>();

        if (!rawText.isEmpty()) {
            String semanticRawText = rawText.toUpperCase(Locale.US).replace(CwDecoder.UNKNOWN_CHARACTER, "?");
            List<String> rawParts = normalizeRawCopyParts(semanticRawText.split("\\s+"));
            for (int index = 0; index < rawParts.size(); index++) {
                String rawPart = rawParts.get(index);
                if (rawPart.isEmpty()) {
                    continue;
                }
                String visibleToken = normalizeRawCopyFocusToken(rawPart);
                String classificationToken = rawCopyFocusClassificationToken(rawPart);
                CwInterpretedToken.Type type = classifyRawCopyFocusToken(rawPart, classificationToken);
                parsedTokens.add(new CwInterpretedToken(rawPart, visibleToken, type));
                normalizedParts.add(visibleToken);
                if (type == CwInterpretedToken.Type.CALLSIGN_CANDIDATE) {
                    callsignSet.add(visibleToken);
                }
            }
        }

        normalizedText = String.join(" ", normalizedParts).trim();
        tokens = parsedTokens;
        callsignCandidates = new ArrayList<>(callsignSet);
        primaryCallsignCandidate = callsignCandidates.isEmpty() ? null : callsignCandidates.get(0);
        phraseHints = new ArrayList<>();

        String latestTokenSummary = parsedTokens.isEmpty()
                ? "(none)"
                : formatTokenSummary(parsedTokens.get(parsedTokens.size() - 1));
        lastEvent = new CwInterpretationEvent(
                timestampMs,
                rawText,
                normalizedText,
                latestTokenSummary
        );
    }

    private CwInterpretedToken.Type classifyRawCopyFocusToken(String rawToken, String classificationToken) {
        String semanticToken = semanticToken(classificationToken);
        if ("CQ".equals(semanticToken)) {
            return CwInterpretedToken.Type.CQ;
        }
        if ("DE".equals(semanticToken)) {
            return CwInterpretedToken.Type.DE;
        }
        if ("QRZ".equals(semanticToken)) {
            return CwInterpretedToken.Type.QRZ;
        }
        if ("THANKS".equals(semanticToken)) {
            return CwInterpretedToken.Type.THANKS;
        }
        if ("AGAIN".equals(semanticToken)) {
            return CwInterpretedToken.Type.AGAIN;
        }
        if ("R".equals(semanticToken)) {
            return CwInterpretedToken.Type.ACK;
        }
        if (isRequestToken(semanticToken)) {
            return CwInterpretedToken.Type.REQUEST;
        }
        if (isReportToken(rawToken, classificationToken)) {
            return CwInterpretedToken.Type.REPORT;
        }
        if (isControlToken(classificationToken)) {
            return CwInterpretedToken.Type.CONTROL;
        }
        if (isRawCopyFocusCallsignCandidate(classificationToken)) {
            return CwInterpretedToken.Type.CALLSIGN_CANDIDATE;
        }
        return CwInterpretedToken.Type.FREE_TEXT;
    }

    private boolean isRawCopyFocusCallsignCandidate(String token) {
        if (!isCallsignCandidate(token)) {
            return false;
        }
        if (token.length() < 4 || token.length() > 10) {
            return false;
        }
        if (!containsLetter(token) || !containsDigit(token)) {
            return false;
        }
        return countLetters(token) >= 2;
    }

    private String normalizeRawCopyFocusToken(String rawToken) {
        return trimKnownTrailingPunctuation(rawToken);
    }

    private String rawCopyFocusClassificationToken(String rawToken) {
        return trimKnownTrailingPunctuation(rawToken);
    }

    private String normalizeToken(String rawToken) {
        String cleanedToken = trimKnownTrailingPunctuation(rawToken);
        return cleanedToken;
    }

    private String semanticToken(String token) {
        String cleanedToken = trimKnownTrailingPunctuation(token);
        switch (cleanedToken) {
            case "5NN":
            case "ENN":
                return "599";
            case "TU":
            case "TNX":
                return "THANKS";
            case "AGN":
                return "AGAIN";
            case "PSE":
            case "PLS":
                return "PLEASE";
            case "CALLING":
                return "CALL";
            case "CALL-SIGN":
                return "CALLSIGN";
            default:
                if (cleanedToken.matches("[7?]3[BK?]") || cleanedToken.matches("[7?]3")) {
                    return "73";
                }
                return cleanedToken;
        }
    }

    private String normalizeToken(String rawToken, List<String> rawParts, int index) {
        String normalizedToken = normalizeToken(rawToken);
        if (!normalizedToken.equals(rawToken)) {
            return normalizedToken;
        }
        return normalizedToken;
    }

    private CwInterpretedToken.Type classifyToken(
            String rawToken,
            String normalizedToken,
            List<String> normalizedParts,
            int index
    ) {
        String semanticToken = semanticToken(normalizedToken);
        if ("CQ".equals(semanticToken)) {
            return CwInterpretedToken.Type.CQ;
        }
        if ("DE".equals(semanticToken)) {
            return CwInterpretedToken.Type.DE;
        }
        if ("QRZ".equals(semanticToken)) {
            return CwInterpretedToken.Type.QRZ;
        }
        if ("THANKS".equals(semanticToken)) {
            return CwInterpretedToken.Type.THANKS;
        }
        if ("AGAIN".equals(semanticToken)) {
            return CwInterpretedToken.Type.AGAIN;
        }
        if ("R".equals(semanticToken)) {
            return CwInterpretedToken.Type.ACK;
        }
        if (isRequestToken(semanticToken)) {
            return CwInterpretedToken.Type.REQUEST;
        }
        if (isReportToken(rawToken, normalizedToken)) {
            return CwInterpretedToken.Type.REPORT;
        }
        if (isLikelyDamagedReportToken(rawToken) && isReportResidueContext(normalizedParts, index)) {
            return CwInterpretedToken.Type.REPORT;
        }
        if (isLikelyReportResidueToken(normalizedToken, normalizedParts, index)) {
            return CwInterpretedToken.Type.REPORT;
        }
        if (isControlToken(normalizedToken)) {
            return CwInterpretedToken.Type.CONTROL;
        }
        if (isDamagedControlResidueToken(rawToken, normalizedParts, index)
                || isLikelyControlResidueToken(normalizedToken)) {
            return CwInterpretedToken.Type.CONTROL;
        }
        if (isCallsignCandidate(normalizedToken)
                || isLikelyContextualPartialCallsign(normalizedToken, normalizedParts, index)) {
            return CwInterpretedToken.Type.CALLSIGN_CANDIDATE;
        }
        return CwInterpretedToken.Type.FREE_TEXT;
    }

    private boolean isReportToken(String rawToken, String normalizedToken) {
        return "599".equals(normalizedToken)
                || "UR".equals(normalizedToken)
                || "RST".equals(normalizedToken)
                || "R".equals(normalizedToken)
                || "5NN".equals(rawToken)
                || "ENN".equals(rawToken);
    }

    private boolean isControlToken(String normalizedToken) {
        return "K".equals(normalizedToken)
                || "KN".equals(normalizedToken)
                || "BK".equals(normalizedToken);
    }

    private boolean isLikelyReportResidueToken(
            String normalizedToken,
            List<String> normalizedParts,
            int index
    ) {
        if (normalizedToken == null || normalizedToken.length() < 3 || normalizedToken.length() > 4) {
            return false;
        }
        if (normalizedToken.contains("?")) {
            return false;
        }
        if (!normalizedToken.matches("[59EN][A-Z]{2,3}")) {
            return false;
        }
        if (countDigits(normalizedToken) > 1) {
            return false;
        }
        return isReportResidueContext(normalizedParts, index);
    }

    private boolean isReportResidueContext(List<String> normalizedParts, int index) {
        String previous = index > 0 ? semanticToken(normalizeToken(normalizedParts.get(index - 1))) : "";
        String next = index + 1 < normalizedParts.size() ? semanticToken(normalizeToken(normalizedParts.get(index + 1))) : "";
        return "UR".equals(previous)
                || "R".equals(previous)
                || "RST".equals(previous)
                || "599".equals(previous)
                || "BK".equals(next)
                || "KN".equals(next)
                || "K".equals(next)
                || isLikelyControlResidueToken(next)
                || "THANKS".equals(next)
                || "73".equals(next);
    }

    private boolean isControlResidueContext(List<String> normalizedParts, int index) {
        String previousRaw = index > 0 ? normalizedParts.get(index - 1) : "";
        String nextRaw = index + 1 < normalizedParts.size() ? normalizedParts.get(index + 1) : "";
        String previous = semanticToken(normalizeToken(previousRaw));
        String next = semanticToken(normalizeToken(nextRaw));
        return "599".equals(previous)
                || "UR".equals(previous)
                || "R".equals(previous)
                || isLikelyDamagedReportToken(previousRaw)
                || "THANKS".equals(previous)
                || "73".equals(previous)
                || "THANKS".equals(next)
                || "73".equals(next);
    }

    private boolean matchesFuzzyToken(String rawToken, String canonicalToken, int maxMismatches) {
        if (rawToken == null || canonicalToken == null || rawToken.length() != canonicalToken.length()) {
            return false;
        }
        int mismatches = 0;
        for (int index = 0; index < rawToken.length(); index++) {
            char rawChar = rawToken.charAt(index);
            char canonicalChar = canonicalToken.charAt(index);
            if (rawChar == canonicalChar || rawChar == '?') {
                continue;
            }
            mismatches += 1;
            if (mismatches > maxMismatches) {
                return false;
            }
        }
        return true;
    }

    private boolean isLikelyControlResidueToken(String normalizedToken) {
        return matchesControlResidue(normalizedToken, "BK")
                || matchesControlResidue(normalizedToken, "KN")
                || matchesControlResidue(normalizedToken, "K");
    }

    private boolean isDamagedControlResidueToken(String rawToken, List<String> normalizedParts, int index) {
        return rawToken != null
                && bestEffortControlNormalization(rawToken) != null
                && isControlResidueContext(normalizedParts, index);
    }

    private boolean isLikelyDamagedReportToken(String token) {
        if (token == null || token.length() != 3 || !token.matches("[59EN?]{3}")) {
            return false;
        }
        for (String canonicalToken : REPORT_TOKEN_VARIANTS) {
            if (matchesFuzzyToken(token, canonicalToken, 1)) {
                return true;
            }
        }
        return false;
    }

    private String bestEffortControlNormalization(String token) {
        if (!isPotentialControlResidueToken(token)) {
            return null;
        }
        if ("B".equals(token) || "?".equals(token)) {
            return "BK";
        }
        if (token.length() == 2 && token.endsWith("B")) {
            return "BK";
        }
        for (String canonicalToken : CONTROL_TOKEN_VARIANTS) {
            int allowedMismatches = canonicalToken.length() == 1 ? 0 : 1;
            if (matchesFuzzyToken(token, canonicalToken, allowedMismatches)) {
                return canonicalToken;
            }
        }
        return null;
    }

    private boolean isPotentialControlResidueToken(String token) {
        return token != null
                && !token.isEmpty()
                && token.length() <= 2
                && token.matches("[BKEN?]+");
    }

    private boolean matchesControlResidue(String token, String controlToken) {
        if (token == null || token.length() <= controlToken.length()) {
            return false;
        }
        if (!token.contains("?")) {
            return false;
        }
        if (token.startsWith(controlToken)) {
            return !containsDigit(token.substring(controlToken.length()));
        }
        if (token.endsWith(controlToken)) {
            return !containsDigit(token.substring(0, token.length() - controlToken.length()));
        }
        return false;
    }

    private String trimKnownTrailingPunctuation(String rawToken) {
        if (rawToken == null || rawToken.isEmpty()) {
            return "";
        }
        String cleaned = rawToken;
        while (cleaned.length() > 1 && endsWithKnownTrailingPunctuation(cleaned)) {
            String candidate = cleaned.substring(0, cleaned.length() - 1);
            if (!isKnownKeywordLikeToken(candidate)) {
                break;
            }
            cleaned = candidate;
        }
        return cleaned;
    }

    private boolean endsWithKnownTrailingPunctuation(String value) {
        return value.endsWith("?")
                || value.endsWith(".")
                || value.endsWith(",");
    }

    private boolean isKnownKeywordLikeToken(String token) {
        switch (token) {
            case "CQ":
            case "DE":
            case "QRZ":
            case "K":
            case "KN":
            case "BK":
            case "PSE":
            case "PLS":
            case "AGN":
            case "R":
            case "UR":
            case "TU":
            case "TNX":
            case "RST":
            case "599":
            case "5NN":
            case "ENN":
            case "CALL":
            case "CALLING":
            case "CALLSIGN":
            case "CALL-SIGN":
            case "NAME":
            case "QTH":
            case "RIG":
            case "WX":
            case "ANT":
            case "73":
            case "EE":
            case "GL":
                return true;
            default:
                return false;
        }
    }

    private boolean isRequestToken(String normalizedToken) {
        return "PLEASE".equals(normalizedToken)
                || "CALL".equals(normalizedToken)
                || "CALLSIGN".equals(normalizedToken);
    }

    private boolean isCallsignCandidate(String token) {
        if (NON_CALLSIGN_TOKENS.contains(token)
                || NON_CALLSIGN_TOKENS.contains(semanticToken(token))) {
            return false;
        }
        return CALLSIGN_PATTERN.matcher(token).matches();
    }

    private boolean isLikelyContextualPartialCallsign(
            String token,
            List<String> normalizedParts,
            int index
    ) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        if (isCallsignCandidate(token)
                || NON_CALLSIGN_TOKENS.contains(token)
                || isKnownKeywordLikeToken(token)) {
            return false;
        }
        if (!token.matches("[A-Z0-9?]{3,}")) {
            return false;
        }
        if (token.length() < 4 && !token.contains("?")) {
            return false;
        }
        if (!containsLetter(token)) {
            return false;
        }
        if (!containsDigit(token) && !token.contains("?")) {
            return false;
        }
        return isContextualCallsignSlot(normalizedParts, index);
    }

    private boolean isContextualCallsignSlot(List<String> normalizedParts, int index) {
        String previous = index > 0 ? semanticToken(normalizeToken(normalizedParts.get(index - 1))) : "";
        String next = index + 1 < normalizedParts.size() ? semanticToken(normalizeToken(normalizedParts.get(index + 1))) : "";
        return "DE".equals(previous)
                || "DE".equals(next)
                || "BK".equals(previous)
                || "KN".equals(previous)
                || "K".equals(previous)
                || "AGAIN".equals(next)
                || "PLEASE".equals(next)
                || "CALL".equals(next)
                || "CALLSIGN".equals(next)
                || "UR".equals(next)
                || "R".equals(next)
                || "THANKS".equals(next)
                || "73".equals(next)
                || "BK".equals(next)
                || "KN".equals(next)
                || "K".equals(next);
    }

    private boolean containsDigit(String token) {
        for (int index = 0; index < token.length(); index++) {
            if (Character.isDigit(token.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private int countDigits(String token) {
        int digits = 0;
        for (int index = 0; index < token.length(); index++) {
            if (Character.isDigit(token.charAt(index))) {
                digits += 1;
            }
        }
        return digits;
    }

    private boolean containsLetter(String token) {
        for (int index = 0; index < token.length(); index++) {
            if (Character.isLetter(token.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private int countLetters(String token) {
        int letters = 0;
        for (int index = 0; index < token.length(); index++) {
            if (Character.isLetter(token.charAt(index))) {
                letters += 1;
            }
        }
        return letters;
    }

    private boolean containsNormalizedToken(List<CwInterpretedToken> parsedTokens, String normalizedToken) {
        for (CwInterpretedToken token : parsedTokens) {
            if (normalizedToken.equals(token.normalizedText())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsSemanticToken(List<CwInterpretedToken> parsedTokens, String semanticToken) {
        for (CwInterpretedToken token : parsedTokens) {
            if (semanticToken.equals(semanticToken(token.normalizedText()))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsTokenSequence(
            List<CwInterpretedToken> parsedTokens,
            String firstNormalizedToken,
            String secondNormalizedToken
    ) {
        for (int index = 0; index < parsedTokens.size() - 1; index++) {
            CwInterpretedToken first = parsedTokens.get(index);
            CwInterpretedToken second = parsedTokens.get(index + 1);
            if (firstNormalizedToken.equals(first.normalizedText())
                    && secondNormalizedToken.equals(second.normalizedText())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsSemanticTokenSequence(
            List<CwInterpretedToken> parsedTokens,
            String firstSemanticToken,
            String secondSemanticToken
    ) {
        for (int index = 0; index < parsedTokens.size() - 1; index++) {
            CwInterpretedToken first = parsedTokens.get(index);
            CwInterpretedToken second = parsedTokens.get(index + 1);
            if (firstSemanticToken.equals(semanticToken(first.normalizedText()))
                    && secondSemanticToken.equals(semanticToken(second.normalizedText()))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsReportValueToken(List<CwInterpretedToken> parsedTokens) {
        for (CwInterpretedToken token : parsedTokens) {
            if (isReportValueToken(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsReportLeadSequence(List<CwInterpretedToken> parsedTokens, String reportLeadToken) {
        for (int index = 0; index < parsedTokens.size() - 1; index++) {
            CwInterpretedToken first = parsedTokens.get(index);
            CwInterpretedToken second = parsedTokens.get(index + 1);
            if (reportLeadToken.equals(semanticToken(first.normalizedText()))
                    && isReportValueToken(second)) {
                return true;
            }
        }
        return false;
    }

    private boolean isReportValueToken(CwInterpretedToken token) {
        if (token == null || token.type() != CwInterpretedToken.Type.REPORT) {
            return false;
        }
        String normalized = token.normalizedText();
        if (normalized == null || normalized.isEmpty()) {
            return false;
        }
        String semantic = semanticToken(normalized);
        if ("599".equals(semantic)) {
            return true;
        }
        return isLikelyDamagedReportToken(trimKnownTrailingPunctuation(normalized));
    }

    private boolean hasTurnHandoffHint(List<CwInterpretedToken> parsedTokens) {
        for (int index = 0; index < parsedTokens.size(); index++) {
            String canonicalControl = canonicalControlToken(parsedTokens.get(index));
            if ("BK".equals(canonicalControl) || "KN".equals(canonicalControl)) {
                return true;
            }
            if ("K".equals(canonicalControl) && hasContextualPlainKUsage(parsedTokens, index)) {
                return true;
            }
        }
        return false;
    }

    private String canonicalControlToken(CwInterpretedToken token) {
        if (token == null || token.type() != CwInterpretedToken.Type.CONTROL) {
            return null;
        }
        String normalized = trimKnownTrailingPunctuation(token.normalizedText());
        if (isControlToken(normalized)) {
            return normalized;
        }
        return bestEffortControlNormalization(normalized);
    }

    private boolean hasRepeatClarificationHint(List<CwInterpretedToken> parsedTokens) {
        int requestSignalCount = 0;
        boolean hasUncertainty = false;
        for (CwInterpretedToken token : parsedTokens) {
            if (isRepeatRequestSignal(token.normalizedText())) {
                requestSignalCount += 1;
            }
            if (token.normalizedText() != null && token.normalizedText().contains("?")) {
                hasUncertainty = true;
            }
        }
        return requestSignalCount >= 2 || (hasUncertainty && requestSignalCount >= 1);
    }

    private boolean hasShortTailEndingHint(
            List<CwInterpretedToken> parsedTokens,
            List<String> recoveredCallsignCandidates
    ) {
        if (parsedTokens == null || parsedTokens.isEmpty()) {
            return false;
        }
        boolean hasQrz = false;
        boolean hasCq = false;
        boolean hasDe = false;
        boolean hasCallsign = recoveredCallsignCandidates != null && !recoveredCallsignCandidates.isEmpty();
        boolean hasReport = false;
        boolean hasClosing = false;
        boolean hasUncertainty = false;
        String lastNormalized = null;
        for (CwInterpretedToken token : parsedTokens) {
            if (token == null || token.normalizedText() == null) {
                continue;
            }
            String normalized = token.normalizedText();
            String semantic = semanticToken(normalized);
            lastNormalized = normalized;
            if (normalized.contains("?")
                    || (token.rawText() != null && token.rawText().contains("?"))) {
                hasUncertainty = true;
            }
            if ("QRZ".equals(semantic)) {
                hasQrz = true;
            } else if ("CQ".equals(semantic)) {
                hasCq = true;
            } else if ("DE".equals(semantic)) {
                hasDe = true;
            } else if ("THANKS".equals(semantic) || "73".equals(semantic)) {
                hasClosing = true;
            }
            if (token.type() == CwInterpretedToken.Type.REPORT || "RST".equals(normalized)) {
                hasReport = true;
            }
        }
        return hasQrz
                && !hasCq
                && hasDe
                && hasCallsign
                && !hasReport
                && !hasClosing
                && !hasUncertainty
                && "KN".equals(lastNormalized);
    }

    private boolean isRepeatRequestSignal(String normalizedToken) {
        String semantic = semanticToken(normalizedToken);
        return "AGAIN".equals(semantic)
                || "PLEASE".equals(semantic)
                || "CALL".equals(semantic)
                || "CALLSIGN".equals(semantic);
    }

    private boolean hasContextualPlainKUsage(List<CwInterpretedToken> parsedTokens, int index) {
        if (index < 0 || index >= parsedTokens.size()) {
            return false;
        }
        CwInterpretedToken previous = index > 0 ? parsedTokens.get(index - 1) : null;
        CwInterpretedToken next = index + 1 < parsedTokens.size() ? parsedTokens.get(index + 1) : null;
        return isPlainKSupportToken(previous) || isPlainKSupportToken(next);
    }

    private boolean isPlainKSupportToken(CwInterpretedToken token) {
        if (token == null) {
            return false;
        }
        switch (token.type()) {
            case CALLSIGN_CANDIDATE:
            case CQ:
            case DE:
            case QRZ:
            case REPORT:
            case ACK:
            case REQUEST:
            case THANKS:
            case AGAIN:
                return true;
            case CONTROL:
            case FREE_TEXT:
            default:
                return false;
        }
    }

    private boolean hasUncertainCallsign(List<String> callsigns) {
        for (String callsign : callsigns) {
            if (callsign.contains("?")) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPartialToFullUpgrade(List<String> rawCandidates, List<String> mergedCandidates) {
        boolean hasPartial = false;
        for (String candidate : rawCandidates) {
            if (candidate.contains("?")) {
                hasPartial = true;
                break;
            }
        }
        if (!hasPartial) {
            return false;
        }
        for (String candidate : mergedCandidates) {
            if (!candidate.contains("?")) {
                return true;
            }
        }
        return false;
    }

    private List<String> buildRecoveredCallsignCandidates(
            List<String> rawCandidates,
            ContextualCallsigns contextualCallsigns
    ) {
        ArrayList<String> repairedRawCandidates = new ArrayList<>();
        if (rawCandidates != null) {
            for (String rawCandidate : rawCandidates) {
                for (String variant : deriveCallsignRepairVariants(rawCandidate)) {
                    if (!repairedRawCandidates.contains(variant)) {
                        repairedRawCandidates.add(variant);
                    }
                }
            }
        }

        ArrayList<String> recoveredCandidates = new ArrayList<>(mergeAndRankCallsignCandidates(repairedRawCandidates));
        recoverRememberedCandidates(recoveredCandidates, repairedRawCandidates, rememberedAddressedCallsign);
        recoverRememberedCandidates(recoveredCandidates, repairedRawCandidates, rememberedSpeakerCallsign);
        recoverRememberedCandidates(recoveredCandidates, repairedRawCandidates, rememberedPrimaryCallsign);
        recoverContextualCandidate(
                recoveredCandidates,
                contextualCallsigns == null ? null : contextualCallsigns.addressedCallsign,
                rememberedAddressedCallsign
        );
        recoverContextualCandidate(
                recoveredCandidates,
                contextualCallsigns == null ? null : contextualCallsigns.speakerCallsign,
                rememberedSpeakerCallsign
        );
        recoverContextualCandidate(
                recoveredCandidates,
                contextualCallsigns == null ? null : contextualCallsigns.speakerCallsign,
                rememberedPrimaryCallsign
        );
        ArrayList<String> rankedCandidates = new ArrayList<>(mergeAndRankCallsignCandidates(recoveredCandidates));
        restoreConflictingObservedCandidates(rankedCandidates, rawCandidates);
        restoreObservedContextualCandidates(rankedCandidates, rawCandidates, contextualCallsigns);
        return rankedCandidates;
    }

    private void restoreConflictingObservedCandidates(
            List<String> rankedCandidates,
            List<String> rawCandidates
    ) {
        if (rankedCandidates == null || rawCandidates == null || rawCandidates.isEmpty()) {
            return;
        }
        ArrayList<String> preservedCandidates = new ArrayList<>();
        for (String rawCandidate : rawCandidates) {
            if (rawCandidate == null || rawCandidate.isEmpty() || rankedCandidates.contains(rawCandidate)) {
                continue;
            }
            if (shouldPreserveObservedCandidateForConflictingCleanMatches(
                    rawCandidate,
                    rankedCandidates,
                    rememberedAddressedCallsign,
                    rememberedSpeakerCallsign,
                    rememberedPrimaryCallsign
            )) {
                preservedCandidates.add(rawCandidate);
            }
        }
        for (int index = preservedCandidates.size() - 1; index >= 0; index--) {
            String candidate = preservedCandidates.get(index);
            if (!rankedCandidates.contains(candidate)) {
                rankedCandidates.add(0, candidate);
            }
        }
    }

    private void restoreObservedContextualCandidates(
            List<String> rankedCandidates,
            List<String> rawCandidates,
            ContextualCallsigns contextualCallsigns
    ) {
        if (rankedCandidates == null || rawCandidates == null || contextualCallsigns == null) {
            return;
        }
        restoreObservedContextualCandidate(
                rankedCandidates,
                rawCandidates,
                contextualCallsigns.addressedCallsign,
                true
        );
        restoreObservedContextualCandidate(
                rankedCandidates,
                rawCandidates,
                contextualCallsigns.speakerCallsign,
                false
        );
    }

    private void restoreObservedContextualCandidate(
            List<String> rankedCandidates,
            List<String> rawCandidates,
            String observedCandidate,
            boolean addressedContext
    ) {
        if (!isTrustedCleanCallsign(observedCandidate)
                || rankedCandidates.contains(observedCandidate)
                || !rawCandidates.contains(observedCandidate)
                || hasCompatibleRememberedTrustedCleanCandidate(observedCandidate)) {
            return;
        }
        ArrayList<String> derivedTrimmedCandidates = new ArrayList<>();
        for (String rankedCandidate : rankedCandidates) {
            if (!isDerivedShorterTrimmedCandidateOfObservedCallsign(
                    rankedCandidate,
                    observedCandidate,
                    rawCandidates
            )) {
                continue;
            }
            derivedTrimmedCandidates.add(rankedCandidate);
        }
        if (derivedTrimmedCandidates.isEmpty()
                || isLikelyDeBoundaryContaminatedContextualCandidate(
                observedCandidate,
                derivedTrimmedCandidates,
                addressedContext
        )) {
            return;
        }
        rankedCandidates.removeAll(derivedTrimmedCandidates);
        rankedCandidates.add(0, observedCandidate);
    }

    private boolean hasCompatibleRememberedTrustedCleanCandidate(String observedCandidate) {
        return isBlockingRememberedTrustedCleanCandidate(observedCandidate, rememberedAddressedCallsign)
                || isBlockingRememberedTrustedCleanCandidate(observedCandidate, rememberedSpeakerCallsign)
                || isBlockingRememberedTrustedCleanCandidate(observedCandidate, rememberedPrimaryCallsign);
    }

    private boolean isBlockingRememberedTrustedCleanCandidate(
            String observedCandidate,
            String rememberedCandidate
    ) {
        return isCompatibleTrustedCleanUpgrade(observedCandidate, rememberedCandidate)
                && !isProtectedShortTrimOfObservedCallsign(rememberedCandidate, observedCandidate);
    }

    private boolean isDerivedShorterTrimmedCandidateOfObservedCallsign(
            String rankedCandidate,
            String observedCandidate,
            List<String> rawCandidates
    ) {
        if (!isTrustedCleanCallsign(rankedCandidate)
                || rankedCandidate.length() >= observedCandidate.length()
                || rawCandidates.contains(rankedCandidate)) {
            return false;
        }
        return isProtectedShortTrimOfObservedCallsign(rankedCandidate, observedCandidate);
    }

    private boolean isProtectedShortTrimOfObservedCallsign(
            String shorterCandidate,
            String observedCandidate
    ) {
        if (!isTrustedCleanCallsign(shorterCandidate)
                || shorterCandidate.length() >= observedCandidate.length()) {
            return false;
        }
        if (anchoredCallsignMatch(observedCandidate, shorterCandidate, true)) {
            String contamination = observedCandidate.substring(shorterCandidate.length());
            if (!isProtectedOperatorEdgeResidue(contamination)) {
                return false;
            }
            return shorterCandidate.equals(
                    chooseAnchoredMergeWinner(observedCandidate, shorterCandidate, true)
            );
        }
        if (anchoredCallsignMatch(observedCandidate, shorterCandidate, false)) {
            String contamination = observedCandidate.substring(0, observedCandidate.length() - shorterCandidate.length());
            if (!isProtectedOperatorEdgeResidue(contamination)) {
                return false;
            }
            return shorterCandidate.equals(
                    chooseAnchoredMergeWinner(observedCandidate, shorterCandidate, false)
            );
        }
        return false;
    }

    private boolean isProtectedOperatorEdgeResidue(String contamination) {
        return "K".equals(contamination)
                || "KN".equals(contamination)
                || "BK".equals(contamination)
                || "TU".equals(contamination)
                || "TNX".equals(contamination)
                || "73".equals(contamination)
                || "AGN".equals(contamination)
                || "PSE".equals(contamination)
                || "PLS".equals(contamination);
    }

    private boolean isLikelyDeBoundaryContaminatedContextualCandidate(
            String observedCandidate,
            List<String> derivedTrimmedCandidates,
            boolean addressedContext
    ) {
        if (observedCandidate == null || derivedTrimmedCandidates == null || derivedTrimmedCandidates.isEmpty()) {
            return false;
        }
        for (String candidate : derivedTrimmedCandidates) {
            if (candidate == null || candidate.length() >= observedCandidate.length()) {
                continue;
            }
            String contamination = addressedContext
                    ? observedCandidate.substring(candidate.length())
                    : observedCandidate.substring(0, observedCandidate.length() - candidate.length());
            if ("D".equals(contamination) || "DE".equals(contamination)) {
                return true;
            }
        }
        return false;
    }

    private List<String> deriveCallsignRepairVariants(String rawCandidate) {
        ArrayList<String> variants = new ArrayList<>();
        if (rawCandidate == null || rawCandidate.isEmpty()) {
            return variants;
        }
        variants.add(rawCandidate);
        addTrimmedRepairVariants(variants, rawCandidate);
        addRepeatedPartialCallsignRepairVariants(variants, rawCandidate);
        for (String keyword : BRIDGE_SPLIT_KEYWORDS) {
            int searchFrom = 1;
            while (searchFrom < rawCandidate.length()) {
                int splitIndex = rawCandidate.indexOf(keyword, searchFrom);
                if (splitIndex <= 0 || splitIndex >= rawCandidate.length() - keyword.length()) {
                    break;
                }
                String left = rawCandidate.substring(0, splitIndex);
                String right = rawCandidate.substring(splitIndex + keyword.length());
                addRepairedVariant(variants, left);
                addRepairedVariant(variants, right);
                addTrimmedRepairVariants(variants, left);
                addTrimmedRepairVariants(variants, right);
                searchFrom = splitIndex + 1;
            }
        }
        return variants;
    }

    private void addRepeatedPartialCallsignRepairVariants(List<String> variants, String rawCandidate) {
        if (rawCandidate == null || rawCandidate.length() < 7) {
            return;
        }
        for (int unitLength = 3; unitLength <= 10 && unitLength < rawCandidate.length(); unitLength++) {
            String base = rawCandidate.substring(0, unitLength);
            if (!isTrustedCleanCallsign(base)) {
                continue;
            }
            int index = unitLength;
            while (index + unitLength <= rawCandidate.length()
                    && rawCandidate.substring(index, index + unitLength).equals(base)) {
                index += unitLength;
            }
            String trailingFragment = rawCandidate.substring(index);
            if (trailingFragment.length() < 3 || trailingFragment.length() > Math.max(3, base.length() - 2)) {
                continue;
            }
            if (base.startsWith(trailingFragment)) {
                addRepairedVariant(variants, base);
            }
        }
    }

    private void addTrimmedRepairVariants(List<String> variants, String rawCandidate) {
        if (rawCandidate == null || rawCandidate.length() < 5) {
            return;
        }
        int maxLeadingTrim = Math.min(3, rawCandidate.length() - 4);
        int maxTrailingTrim = Math.min(3, rawCandidate.length() - 4);
        for (int leadingTrim = 1; leadingTrim <= maxLeadingTrim; leadingTrim++) {
            addRepairedVariantPreservingUncertainty(
                    variants,
                    rawCandidate,
                    rawCandidate.substring(leadingTrim)
            );
        }
        for (int trailingTrim = 1; trailingTrim <= maxTrailingTrim; trailingTrim++) {
            addRepairedVariantPreservingUncertainty(
                    variants,
                    rawCandidate,
                    rawCandidate.substring(0, rawCandidate.length() - trailingTrim)
            );
        }
        for (int leadingTrim = 1; leadingTrim <= maxLeadingTrim; leadingTrim++) {
            for (int trailingTrim = 1; trailingTrim <= maxTrailingTrim; trailingTrim++) {
                if (leadingTrim + trailingTrim >= rawCandidate.length() - 3) {
                    continue;
                }
                addRepairedVariantPreservingUncertainty(
                        variants,
                        rawCandidate,
                        rawCandidate.substring(leadingTrim, rawCandidate.length() - trailingTrim)
                );
            }
        }
    }

    private void addRepairedVariantPreservingUncertainty(
            List<String> variants,
            String rawCandidate,
            String candidate
    ) {
        if (rawCandidate != null && rawCandidate.contains("?") && candidate != null && !candidate.contains("?")) {
            return;
        }
        addRepairedVariant(variants, candidate);
    }

    private void addRepairedVariant(List<String> variants, String candidate) {
        if (!isPotentialRepairedCallsignFragment(candidate)) {
            return;
        }
        if (!variants.contains(candidate)) {
            variants.add(candidate);
        }
    }

    private boolean isPotentialRepairedCallsignFragment(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        if (NON_CALLSIGN_TOKENS.contains(token) || isKnownKeywordLikeToken(token)) {
            return false;
        }
        if (!token.matches("[A-Z0-9?]{4,}")) {
            return false;
        }
        if (!containsLetter(token)) {
            return false;
        }
        return containsDigit(token) || token.contains("?");
    }

    private void recoverRememberedCandidates(
            List<String> recoveredCandidates,
            List<String> rawCandidates,
            String rememberedCandidate
    ) {
        if (rememberedCandidate == null || rawCandidates == null || rawCandidates.isEmpty()) {
            return;
        }
        for (String rawCandidate : rawCandidates) {
            if (shouldPreserveObservedCandidateForConflictingCleanMatches(
                    rawCandidate,
                    recoveredCandidates,
                    rememberedCandidate
            )) {
                continue;
            }
            String merged = mergeAgainstRememberedCallsign(rememberedCandidate, rawCandidate);
            if (merged != null && !recoveredCandidates.contains(merged)) {
                recoveredCandidates.add(merged);
            }
            if (merged != null && !merged.equals(rawCandidate)) {
                recoveredCandidates.remove(rawCandidate);
            }
            String recovered = recoverRememberedCandidateFromFragment(rememberedCandidate, rawCandidate);
            if (recovered != null) {
                if (!recoveredCandidates.contains(recovered)) {
                    recoveredCandidates.add(recovered);
                }
                if (!recovered.equals(rawCandidate)) {
                    recoveredCandidates.remove(rawCandidate);
                }
            }
        }
    }

    private void recoverContextualCandidate(
            List<String> recoveredCandidates,
            String contextualCandidate,
            String rememberedCandidate
    ) {
        if (contextualCandidate == null || rememberedCandidate == null) {
            return;
        }
        if (shouldPreserveObservedCandidateForConflictingCleanMatches(
                contextualCandidate,
                recoveredCandidates,
                rememberedCandidate
        )) {
            return;
        }
        String merged = mergeAgainstRememberedCallsign(rememberedCandidate, contextualCandidate);
        if (merged != null) {
            if (!recoveredCandidates.contains(merged)) {
                recoveredCandidates.add(merged);
            }
            if (!merged.equals(contextualCandidate)) {
                recoveredCandidates.remove(contextualCandidate);
            }
        }
    }

    private String recoverRememberedCandidateFromFragment(String rememberedCandidate, String rawCandidate) {
        if (!isTrustedCleanCallsign(rememberedCandidate) || rawCandidate == null || rawCandidate.isEmpty()) {
            return null;
        }
        if (rememberedCandidate.equals(rawCandidate)) {
            return rememberedCandidate;
        }
        if (shouldPreferCurrentCleanKeywordTrimOverRememberedCandidate(rememberedCandidate, rawCandidate)) {
            return rawCandidate;
        }
        if (isShortAnchoredRememberedFragment(rememberedCandidate, rawCandidate)) {
            return rememberedCandidate;
        }
        if (isTrustedCleanCallsign(rawCandidate)
                && isProtectedShortTrimOfObservedCallsign(rememberedCandidate, rawCandidate)) {
            return rawCandidate;
        }
        if (isTrustedCleanCallsign(rawCandidate)
                && isKeywordResidueWrap(rawCandidate, rememberedCandidate)) {
            return rawCandidate;
        }
        if (isContaminatedWrapOfRememberedCallsign(rememberedCandidate, rawCandidate)) {
            return rememberedCandidate;
        }
        return null;
    }

    private boolean shouldPreferCurrentCleanKeywordTrimOverRememberedCandidate(
            String rememberedCandidate,
            String rawCandidate
    ) {
        return isTrustedCleanCallsign(rawCandidate)
                && rawCandidate.length() >= 6
                && isKeywordResidueWrap(rawCandidate, rememberedCandidate);
    }

    private String mergeAgainstRememberedCallsign(String rememberedCandidate, String observedCandidate) {
        String recovered = recoverRememberedCandidateFromFragment(rememberedCandidate, observedCandidate);
        if (recovered != null) {
            return recovered;
        }
        return mergeCompatibleCallsigns(rememberedCandidate, observedCandidate);
    }

    private boolean isContaminatedWrapOfRememberedCallsign(String rememberedCandidate, String rawCandidate) {
        if (rawCandidate.length() <= rememberedCandidate.length()) {
            return false;
        }
        if (anchoredCallsignMatch(rawCandidate, rememberedCandidate, true)) {
            return isLikelyRememberedEdgeContamination(
                    rawCandidate.substring(rememberedCandidate.length())
            );
        }
        if (anchoredCallsignMatch(rawCandidate, rememberedCandidate, false)) {
            return isLikelyRememberedEdgeContamination(
                    rawCandidate.substring(0, rawCandidate.length() - rememberedCandidate.length())
            );
        }
        return false;
    }

    private boolean isKeywordResidueWrap(String cleanCandidate, String pollutedCandidate) {
        if (cleanCandidate == null || pollutedCandidate == null || pollutedCandidate.length() <= cleanCandidate.length()) {
            return false;
        }
        int extraLength = pollutedCandidate.length() - cleanCandidate.length();
        if (extraLength > 2) {
            return false;
        }
        if (anchoredCallsignMatch(pollutedCandidate, cleanCandidate, true)) {
            return isLikelyKeywordEdgeResidue(
                    pollutedCandidate.substring(cleanCandidate.length())
            );
        }
        if (anchoredCallsignMatch(pollutedCandidate, cleanCandidate, false)) {
            return isLikelyKeywordEdgeResidue(
                    pollutedCandidate.substring(0, pollutedCandidate.length() - cleanCandidate.length())
            );
        }
        return false;
    }

    private boolean isLikelyKeywordEdgeResidue(String token) {
        if (token == null || token.isEmpty() || token.length() > 2 || containsDigit(token)) {
            return false;
        }
        for (String mergeableToken : MERGEABLE_SPLIT_TOKENS) {
            if (mergeableToken.startsWith(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean isShortAnchoredRememberedFragment(String rememberedCandidate, String rawCandidate) {
        if (rawCandidate.length() < 3 || rawCandidate.length() >= rememberedCandidate.length()) {
            return false;
        }
        if (rememberedCandidate.length() - rawCandidate.length() > 3) {
            return false;
        }
        if (certaintyScore(rawCandidate) < 2) {
            return false;
        }
        return anchoredCallsignMatch(rememberedCandidate, rawCandidate, true)
                || anchoredCallsignMatch(rememberedCandidate, rawCandidate, false);
    }

    private boolean isLikelyRememberedEdgeContamination(String token) {
        if (isLikelyContaminationSegment(token)) {
            return true;
        }
        if (token == null || token.length() != 1) {
            return false;
        }
        char contamination = token.charAt(0);
        return !Character.isDigit(contamination)
                && "DEHKBSUIMNR".indexOf(contamination) >= 0;
    }

    private String choosePrimaryCallsignCandidate(
            List<CwInterpretedToken> parsedTokens,
            List<String> rankedCandidates,
            String repeatedCleanCallsign
    ) {
        if (rankedCandidates.isEmpty()) {
            return null;
        }
        if (repeatedCleanCallsign != null && rankedCandidates.contains(repeatedCleanCallsign)) {
            return repeatedCleanCallsign;
        }

        ContextualCallsigns contextualCallsigns = extractContextualCallsigns(parsedTokens);
        if (contextualCallsigns.speakerCallsign != null) {
            if (shouldPreserveObservedCandidateForConflictingCleanMatches(
                    contextualCallsigns.speakerCallsign,
                    rankedCandidates,
                    rememberedPrimaryCallsign,
                    rememberedSpeakerCallsign
            )) {
                return contextualCallsigns.speakerCallsign;
            }
            if (rememberedPrimaryCallsign != null) {
                String recoveredRememberedPrimary = recoverRememberedCandidateFromFragment(
                        rememberedPrimaryCallsign,
                        contextualCallsigns.speakerCallsign
                );
                if (recoveredRememberedPrimary != null) {
                    return recoveredRememberedPrimary;
                }
            }
            if (rememberedSpeakerCallsign != null) {
                String recoveredRememberedSpeaker = recoverRememberedCandidateFromFragment(
                        rememberedSpeakerCallsign,
                        contextualCallsigns.speakerCallsign
                );
                if (recoveredRememberedSpeaker != null) {
                    return recoveredRememberedSpeaker;
                }
            }
            String contextualUpgrade = upgradeRememberedCallsign(contextualCallsigns.speakerCallsign, rankedCandidates);
            if (rememberedPrimaryCallsign != null) {
                String rememberedUpgrade = mergeCompatibleCallsigns(rememberedPrimaryCallsign, contextualUpgrade);
                if (rememberedUpgrade != null) {
                    return rememberedUpgrade;
                }
                rememberedUpgrade = mergeCompatibleCallsigns(rememberedPrimaryCallsign, contextualCallsigns.speakerCallsign);
                if (rememberedUpgrade != null) {
                    return rememberedUpgrade;
                }
            }
            if (contextualUpgrade != null) {
                return preferCleanRepeatedPartialCallsign(contextualUpgrade, rankedCandidates);
            }
        }

        if (rememberedPrimaryCallsign != null) {
            String upgraded = upgradeRememberedCallsign(rememberedPrimaryCallsign, rankedCandidates);
            if (upgraded != null) {
                return upgraded;
            }
        }

        return rankedCandidates.get(0);
    }

    private List<String> preferRepeatedCleanCallsign(List<String> rankedCandidates, String repeatedCleanCallsign) {
        if (rankedCandidates == null || rankedCandidates.isEmpty() || repeatedCleanCallsign == null) {
            return rankedCandidates;
        }
        ArrayList<String> preferred = new ArrayList<>();
        preferred.add(repeatedCleanCallsign);
        for (String candidate : rankedCandidates) {
            if (candidate == null
                    || repeatedCleanCallsign.equals(candidate)
                    || isLikelyPollutedRepeatOfCleanCallsign(repeatedCleanCallsign, candidate)) {
                continue;
            }
            preferred.add(candidate);
        }
        return preferred;
    }

    private String chooseRepeatedTrustedCleanCallsign(List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        String bestCandidate = null;
        int bestCount = 1;
        for (String candidate : candidates) {
            if (!isTrustedCleanCallsign(candidate)) {
                continue;
            }
            int count = 0;
            for (String otherCandidate : candidates) {
                if (candidate.equals(otherCandidate)) {
                    count += 1;
                }
            }
            if (count > bestCount
                    || (count == bestCount
                    && bestCandidate != null
                    && candidate.compareTo(bestCandidate) < 0)) {
                bestCandidate = candidate;
                bestCount = count;
            }
        }
        return bestCount >= 2 ? bestCandidate : null;
    }

    private boolean isLikelyPollutedRepeatOfCleanCallsign(String cleanCallsign, String candidate) {
        if (!isTrustedCleanCallsign(cleanCallsign)
                || candidate == null
                || candidate.length() <= cleanCallsign.length()) {
            return false;
        }
        if (candidate.contains(cleanCallsign)) {
            return true;
        }
        if (candidate.length() < cleanCallsign.length() + 3) {
            return false;
        }
        String prefix = candidate.substring(0, cleanCallsign.length());
        String tail = candidate.substring(cleanCallsign.length());
        int tailCompareLength = Math.min(cleanCallsign.length(), tail.length());
        return hammingDistance(prefix, cleanCallsign) <= 1
                && tailCompareLength >= 3
                && hammingDistance(tail.substring(0, tailCompareLength), cleanCallsign.substring(0, tailCompareLength)) <= 1;
    }

    private String preferCleanRepeatedPartialCallsign(String candidate, List<String> rankedCandidates) {
        if (candidate == null || rankedCandidates == null || rankedCandidates.isEmpty()) {
            return candidate;
        }
        for (String rankedCandidate : rankedCandidates) {
            if (rankedCandidate == null || rankedCandidate.equals(candidate) || !isTrustedCleanCallsign(rankedCandidate)) {
                continue;
            }
            String merged = mergeCompatibleCallsigns(candidate, rankedCandidate);
            if (rankedCandidate.equals(merged)) {
                return rankedCandidate;
            }
        }
        return candidate;
    }

    private String upgradeRememberedCallsign(String preferredCallsign, List<String> rankedCandidates) {
        if (preferredCallsign == null || preferredCallsign.isEmpty()) {
            return null;
        }
        for (String candidate : rankedCandidates) {
            String merged = mergeAgainstRememberedCallsign(preferredCallsign, candidate);
            if (merged != null) {
                return merged;
            }
            if (preferredCallsign.equals(candidate)) {
                return candidate;
            }
        }
        return rankedCandidates.contains(preferredCallsign) ? preferredCallsign : null;
    }

    private void updateRememberedContextualCallsigns(
            ContextualCallsigns contextualCallsigns,
            List<String> rankedCandidates
    ) {
        if (contextualCallsigns == null) {
            return;
        }
        String upgradedAddressed = shouldPreserveObservedCandidateForConflictingCleanMatches(
                contextualCallsigns.addressedCallsign,
                rankedCandidates,
                rememberedAddressedCallsign
        )
                ? null
                : upgradeRememberedCallsign(contextualCallsigns.addressedCallsign, rankedCandidates);
        if (shouldUpdateRememberedTrustedCallsign(rememberedAddressedCallsign, upgradedAddressed)) {
            rememberedAddressedCallsign = upgradedAddressed;
        }
        String upgradedSpeaker = shouldPreserveObservedCandidateForConflictingCleanMatches(
                contextualCallsigns.speakerCallsign,
                rankedCandidates,
                rememberedPrimaryCallsign,
                rememberedSpeakerCallsign
        )
                ? null
                : upgradeRememberedCallsign(contextualCallsigns.speakerCallsign, rankedCandidates);
        if (shouldUpdateRememberedTrustedCallsign(rememberedSpeakerCallsign, upgradedSpeaker)) {
            rememberedSpeakerCallsign = upgradedSpeaker;
        }
    }

    private boolean shouldUpdateRememberedTrustedCallsign(
            String existingRememberedCallsign,
            String replacementCandidate
    ) {
        if (!isTrustedCleanCallsign(replacementCandidate)) {
            return false;
        }
        if (!isTrustedCleanCallsign(existingRememberedCallsign)) {
            return true;
        }
        if (isShortAnchoredRememberedFragment(existingRememberedCallsign, replacementCandidate)) {
            return false;
        }
        String mergedWithExisting = mergeAgainstRememberedCallsign(existingRememberedCallsign, replacementCandidate);
        return mergedWithExisting == null || !existingRememberedCallsign.equals(mergedWithExisting);
    }

    private boolean shouldPreserveObservedCandidateForConflictingCleanMatches(
            String observedCandidate,
            List<String> rankedCandidates,
            String... extraCleanCandidates
    ) {
        if (observedCandidate == null || observedCandidate.isEmpty() || isTrustedCleanCallsign(observedCandidate)) {
            return false;
        }
        return countDistinctCompatibleTrustedCleanCandidates(
                observedCandidate,
                rankedCandidates,
                extraCleanCandidates
        ) > 1;
    }

    private int countDistinctCompatibleTrustedCleanCandidates(
            String observedCandidate,
            List<String> rankedCandidates,
            String... extraCleanCandidates
    ) {
        LinkedHashSet<String> compatibleCleanCandidates = new LinkedHashSet<>();
        if (rankedCandidates != null) {
            for (String candidate : rankedCandidates) {
                if (isCompatibleTrustedCleanUpgrade(observedCandidate, candidate)) {
                    compatibleCleanCandidates.add(candidate);
                }
            }
        }
        if (extraCleanCandidates != null) {
            for (String candidate : extraCleanCandidates) {
                if (isCompatibleTrustedCleanUpgrade(observedCandidate, candidate)) {
                    compatibleCleanCandidates.add(candidate);
                }
            }
        }
        return compatibleCleanCandidates.size();
    }

    private boolean isCompatibleTrustedCleanUpgrade(String observedCandidate, String cleanCandidate) {
        if (observedCandidate == null
                || observedCandidate.isEmpty()
                || observedCandidate.equals(cleanCandidate)
                || !isTrustedCleanCallsign(cleanCandidate)) {
            return false;
        }
        return cleanCandidate.equals(mergeAgainstRememberedCallsign(cleanCandidate, observedCandidate));
    }

    private List<String> mergeAndRankCallsignCandidates(List<String> rawCandidates) {
        ArrayList<RankedCallsign> merged = new ArrayList<>();
        for (int index = 0; index < rawCandidates.size(); index++) {
            String candidate = rawCandidates.get(index);
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }

            boolean mergedIntoExisting = false;
            for (int existingIndex = 0; existingIndex < merged.size(); existingIndex++) {
                RankedCallsign existing = merged.get(existingIndex);
                String preferredCleanCandidate = choosePreferredRecoveredCleanCandidate(existing.callsign, candidate);
                if (preferredCleanCandidate != null) {
                    merged.set(existingIndex, new RankedCallsign(
                            preferredCleanCandidate,
                            Math.max(existing.lastSeenOrder, index)
                    ));
                    mergedIntoExisting = true;
                    break;
                }
                String mergedCandidate = mergeCompatibleCallsigns(existing.callsign, candidate);
                if (mergedCandidate != null) {
                    merged.set(existingIndex, new RankedCallsign(
                            mergedCandidate,
                            Math.max(existing.lastSeenOrder, index)
                    ));
                    mergedIntoExisting = true;
                    break;
                }
            }

            if (!mergedIntoExisting) {
                merged.add(new RankedCallsign(candidate, index));
            }
        }

        merged.sort((left, right) -> {
            int certaintyCompare = Integer.compare(right.certaintyScore(), left.certaintyScore());
            if (certaintyCompare != 0) {
                return certaintyCompare;
            }
            int recencyCompare = Integer.compare(right.lastSeenOrder(), left.lastSeenOrder());
            if (recencyCompare != 0) {
                return recencyCompare;
            }
            return left.callsign().compareTo(right.callsign());
        });

        ArrayList<String> rankedCandidates = new ArrayList<>();
        for (RankedCallsign candidate : merged) {
            rankedCandidates.add(candidate.callsign);
        }
        return suppressContainedCleanFragmentCandidates(rankedCandidates);
    }

    private List<String> suppressContainedCleanFragmentCandidates(List<String> rankedCandidates) {
        ArrayList<String> filtered = new ArrayList<>();
        for (String candidate : rankedCandidates) {
            if (candidate == null || candidate.contains("?")) {
                filtered.add(candidate);
                continue;
            }
            boolean suppressed = false;
            for (String otherCandidate : rankedCandidates) {
                if (otherCandidate == null || otherCandidate.equals(candidate)) {
                    continue;
                }
                if (!isTrustedCleanCallsign(otherCandidate) || otherCandidate.length() <= candidate.length()) {
                    continue;
                }
                int lengthDelta = otherCandidate.length() - candidate.length();
                if (lengthDelta > 2) {
                    continue;
                }
                if (otherCandidate.contains(candidate)) {
                    suppressed = true;
                    break;
                }
            }
            if (!suppressed) {
                filtered.add(candidate);
            }
        }
        return filtered;
    }

    private String choosePreferredRecoveredCleanCandidate(String left, String right) {
        if (left == null || right == null || left.equals(right)) {
            return null;
        }
        String shorter = left.length() <= right.length() ? left : right;
        String longer = shorter == left ? right : left;
        if (!isTrustedCleanCallsign(shorter) || shorter.length() < 6) {
            return null;
        }
        return isSingleEdgeRememberedContaminationWrap(shorter, longer)
                || isKeywordResidueWrap(shorter, longer)
                ? shorter
                : null;
    }

    private boolean isSingleEdgeRememberedContaminationWrap(String rememberedCandidate, String rawCandidate) {
        if (rawCandidate.length() != rememberedCandidate.length() + 1) {
            return false;
        }
        if (anchoredCallsignMatch(rawCandidate, rememberedCandidate, true)) {
            return isLikelyRememberedEdgeContamination(
                    rawCandidate.substring(rememberedCandidate.length())
            );
        }
        if (anchoredCallsignMatch(rawCandidate, rememberedCandidate, false)) {
            return isLikelyRememberedEdgeContamination(
                    rawCandidate.substring(0, rawCandidate.length() - rememberedCandidate.length())
            );
        }
        return false;
    }

    private String mergeCompatibleCallsigns(String left, String right) {
        if (left == null || right == null) {
            return null;
        }
        if (left.length() == right.length()) {
            return mergeEqualLengthCallsigns(left, right);
        }
        String longer = left.length() >= right.length() ? left : right;
        String shorter = longer == left ? right : left;
        if (shorter.length() < 4 || certaintyScore(shorter) < 3) {
            return null;
        }
        if (anchoredCallsignMatch(longer, shorter, true)) {
            return chooseAnchoredMergeWinner(longer, shorter, true);
        }
        if (anchoredCallsignMatch(longer, shorter, false)) {
            return chooseAnchoredMergeWinner(longer, shorter, false);
        }
        return null;
    }

    private String mergeEqualLengthCallsigns(String left, String right) {
        if (left == null || right == null || left.length() != right.length()) {
            return null;
        }

        StringBuilder merged = new StringBuilder(left.length());
        for (int index = 0; index < left.length(); index++) {
            char leftChar = left.charAt(index);
            char rightChar = right.charAt(index);
            if (leftChar == rightChar) {
                merged.append(leftChar);
            } else if (leftChar == '?') {
                merged.append(rightChar);
            } else if (rightChar == '?') {
                merged.append(leftChar);
            } else {
                return null;
            }
        }
        return merged.toString();
    }

    private boolean anchoredCallsignMatch(String longer, String shorter, boolean prefixAligned) {
        int offset = prefixAligned ? 0 : longer.length() - shorter.length();
        for (int index = 0; index < shorter.length(); index++) {
            char longerChar = longer.charAt(index + offset);
            char shorterChar = shorter.charAt(index);
            if (longerChar == shorterChar) {
                continue;
            }
            if (longerChar == '?' || shorterChar == '?') {
                continue;
            }
            return false;
        }
        return true;
    }

    private String chooseAnchoredMergeWinner(String longer, String shorter, boolean prefixAligned) {
        String contaminationSegment = prefixAligned
                ? longer.substring(shorter.length())
                : longer.substring(0, longer.length() - shorter.length());
        if (shouldPreferShorterAnchoredCandidate(longer, shorter, prefixAligned, contaminationSegment)) {
            return shorter;
        }
        return longer;
    }

    private boolean shouldPreferShorterAnchoredCandidate(
            String longer,
            String shorter,
            boolean prefixAligned,
            String contaminationSegment
    ) {
        if (!isTrustedCleanCallsign(shorter)) {
            return false;
        }
        if (isLikelyLegitimateRepeatedCallsignEdge(longer, shorter, prefixAligned, contaminationSegment)) {
            return false;
        }
        if (isLikelyContaminationSegment(contaminationSegment)) {
            return true;
        }
        return isLikelyRepeatedAnchorEdge(contaminationSegment, shorter, prefixAligned);
    }

    private boolean isTrustedCleanCallsign(String candidate) {
        return candidate != null
                && !candidate.contains("?")
                && isCallsignCandidate(candidate)
                && certaintyScore(candidate) >= 5;
    }

    private boolean isLikelyContaminationSegment(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        if (token.length() <= 2 && token.contains("?")) {
            return true;
        }
        if ("K".equals(token)
                || "KN".equals(token)
                || "BK".equals(token)
                || "DE".equals(token)
                || "UR".equals(token)
                || "R".equals(token)
                || "TU".equals(token)
                || "TNX".equals(token)
                || "73".equals(token)) {
            return true;
        }
        if (token.matches("R[59EN]{1,3}")) {
            return true;
        }
        return token.matches("[59EN]{1,3}");
    }

    private boolean isLikelyRepeatedAnchorEdge(
            String contaminationSegment,
            String shorter,
            boolean prefixAligned
    ) {
        if (contaminationSegment == null || contaminationSegment.isEmpty()) {
            return false;
        }
        String cleanSegment = contaminationSegment.replace("?", "");
        if (cleanSegment.isEmpty() || cleanSegment.length() > 2) {
            return isTrustedCleanCallsign(shorter)
                    && cleanSegment.length() <= Math.max(2, shorter.length() - 2)
                    && (prefixAligned
                    ? shorter.startsWith(cleanSegment)
                    : shorter.endsWith(cleanSegment));
        }
        return prefixAligned
                ? shorter.endsWith(cleanSegment)
                : shorter.startsWith(cleanSegment);
    }

    private boolean isLikelyLegitimateRepeatedCallsignEdge(
            String longer,
            String shorter,
            boolean prefixAligned,
            String contaminationSegment
    ) {
        if (!isTrustedCleanCallsign(longer)
                || contaminationSegment == null
                || contaminationSegment.length() != 1) {
            return false;
        }
        char repeated = contaminationSegment.charAt(0);
        if (!Character.isLetter(repeated)) {
            return false;
        }
        int repeatedRunLength = 0;
        if (prefixAligned) {
            for (int index = longer.length() - 1; index >= 0 && longer.charAt(index) == repeated; index--) {
                repeatedRunLength += 1;
            }
            return repeatedRunLength >= 3 && shorter.endsWith(String.valueOf(repeated));
        }
        for (int index = 0; index < longer.length() && longer.charAt(index) == repeated; index++) {
            repeatedRunLength += 1;
        }
        return repeatedRunLength >= 3 && shorter.startsWith(String.valueOf(repeated));
    }

    private int certaintyScore(String candidate) {
        int score = 0;
        if (candidate == null) {
            return score;
        }
        for (int index = 0; index < candidate.length(); index++) {
            if (candidate.charAt(index) != '?') {
                score += 1;
            }
        }
        return score;
    }

    private int hammingDistance(String left, String right) {
        if (left == null || right == null || left.length() != right.length()) {
            return Integer.MAX_VALUE;
        }
        int distance = 0;
        for (int index = 0; index < left.length(); index++) {
            if (left.charAt(index) != right.charAt(index)) {
                distance += 1;
            }
        }
        return distance;
    }

    private ContextualCallsigns extractContextualCallsigns(List<CwInterpretedToken> parsedTokens) {
        String addressedCallsign = null;
        String speakerCallsign = null;
        for (int index = 0; index < parsedTokens.size(); index++) {
            CwInterpretedToken token = parsedTokens.get(index);
            if (token.type() != CwInterpretedToken.Type.DE) {
                continue;
            }
            String before = nearestCallsignBefore(parsedTokens, index);
            String after = nearestCallsignAfter(parsedTokens, index);
            if (before != null) {
                addressedCallsign = before;
            }
            if (after != null) {
                speakerCallsign = after;
            }
        }
        return new ContextualCallsigns(addressedCallsign, speakerCallsign);
    }

    private String nearestCallsignBefore(List<CwInterpretedToken> tokens, int indexExclusive) {
        for (int index = indexExclusive - 1; index >= 0 && index >= indexExclusive - 2; index--) {
            CwInterpretedToken token = tokens.get(index);
            if (token.type() == CwInterpretedToken.Type.CALLSIGN_CANDIDATE) {
                return token.normalizedText();
            }
        }
        return null;
    }

    private String nearestCallsignAfter(List<CwInterpretedToken> tokens, int indexExclusive) {
        for (int index = indexExclusive + 1; index < tokens.size() && index <= indexExclusive + 2; index++) {
            CwInterpretedToken token = tokens.get(index);
            if (token.type() == CwInterpretedToken.Type.CALLSIGN_CANDIDATE) {
                return token.normalizedText();
            }
        }
        return null;
    }

    private List<String> normalizeRawParts(String[] rawParts) {
        ArrayList<String> normalizedParts = new ArrayList<>();
        for (int index = 0; index < rawParts.length; index++) {
            String token = rawParts[index];
            if (token == null || token.isEmpty()) {
                continue;
            }
            String mergedFixedToken = mergeSplitFixedToken(rawParts, index);
            if (mergedFixedToken != null) {
                normalizedParts.add(mergedFixedToken);
                index += countMergedSplitTokens(rawParts, index, mergedFixedToken.length()) - 1;
                continue;
            }
            List<String> trailingDeSplit = splitTrailingDeWithFollowingCallsign(
                    token,
                    index + 1 < rawParts.length ? rawParts[index + 1] : null
            );
            if (trailingDeSplit != null && !trailingDeSplit.isEmpty()) {
                normalizedParts.addAll(trailingDeSplit);
                continue;
            }
            if (index + 1 < rawParts.length && isCallToken(token) && "SIGN".equals(rawParts[index + 1])) {
                normalizedParts.add("CALLSIGN");
                index += 1;
                continue;
            }
            normalizedParts.add(token);
        }
        return normalizeCallsignRuns(normalizedParts);
    }

    private String mergeSplitFixedToken(String[] rawParts, int startIndex) {
        if (startIndex < 0 || startIndex >= rawParts.length) {
            return null;
        }
        String first = rawParts[startIndex];
        if (!isShortMergeableFragment(first)) {
            return null;
        }
        String bestCandidate = null;
        int maxParts = Math.min(3, rawParts.length - startIndex);
        for (int partCount = 2; partCount <= maxParts; partCount++) {
            String merged = joinRawParts(rawParts, startIndex, startIndex + partCount);
            if (MERGEABLE_SPLIT_TOKENS.contains(merged)
                    && shouldMergeSplitFixedToken(rawParts, startIndex, partCount, merged)) {
                bestCandidate = merged;
            }
        }
        return bestCandidate;
    }

    private boolean shouldMergeSplitFixedToken(
            String[] rawParts,
            int startIndex,
            int partCount,
            String mergedToken
    ) {
        String previousRaw = previousNonEmptyRawPart(rawParts, startIndex - 1);
        String nextRaw = nextNonEmptyRawPart(rawParts, startIndex + partCount);
        String previous = semanticToken(normalizeToken(previousRaw == null ? "" : previousRaw));
        String next = semanticToken(normalizeToken(nextRaw == null ? "" : nextRaw));

        switch (mergedToken) {
            case "DE":
                return hasPotentialCallsignContextBefore(rawParts, startIndex)
                        || hasPotentialCallsignContextAfter(rawParts, startIndex + partCount)
                        || "CQ".equals(previous)
                        || "QRZ".equals(previous);
            case "BK":
            case "KN":
                return hasPotentialReportContextBefore(rawParts, startIndex)
                        || hasPotentialClosingContextBefore(rawParts, startIndex)
                        || hasPotentialCallsignContextBefore(rawParts, startIndex)
                        || hasPotentialCallsignContextAfter(rawParts, startIndex + partCount)
                        || "CQ".equals(next)
                        || "QRZ".equals(next);
            case "TU":
            case "TNX":
                return hasPotentialReportContextBefore(rawParts, startIndex)
                        || hasPotentialCallsignContextBefore(rawParts, startIndex)
                        || isPotentialControlFollowToken(next)
                        || "73".equals(next);
            case "AGN":
            case "PSE":
            case "PLS":
                return hasPotentialCallsignContextBefore(rawParts, startIndex)
                        || hasPotentialCallsignContextAfter(rawParts, startIndex + partCount)
                        || isPotentialControlFollowToken(next)
                        || isPotentialRequestFollowToken(next);
            case "73":
                return hasPotentialClosingContextBefore(rawParts, startIndex)
                        || hasPotentialCallsignContextBefore(rawParts, startIndex)
                        || isPotentialControlFollowToken(next)
                        || "SK".equals(next);
            case "5NN":
            case "599":
            case "ENN":
                return hasPotentialReportLeadBefore(rawParts, startIndex)
                        || isPotentialControlFollowToken(next)
                        || "THANKS".equals(next)
                        || "73".equals(next);
            default:
                return false;
        }
    }

    private boolean hasPotentialCallsignContextBefore(String[] rawParts, int exclusiveEndIndex) {
        return hasPotentialJoinedContext(rawParts, Math.max(0, exclusiveEndIndex - 3), exclusiveEndIndex, true);
    }

    private boolean hasPotentialCallsignContextAfter(String[] rawParts, int startIndex) {
        return hasPotentialJoinedContext(rawParts, startIndex, Math.min(rawParts.length, startIndex + 3), false);
    }

    private boolean hasPotentialJoinedContext(
            String[] rawParts,
            int startInclusive,
            int endExclusive,
            boolean suffixAligned
    ) {
        if (rawParts == null || startInclusive < 0 || endExclusive > rawParts.length || startInclusive >= endExclusive) {
            return false;
        }
        if (suffixAligned) {
            for (int index = endExclusive - 1; index >= startInclusive; index--) {
                if (isPotentialCallsignContextToken(joinRawParts(rawParts, index, endExclusive))) {
                    return true;
                }
            }
            return false;
        }
        for (int index = startInclusive + 1; index <= endExclusive; index++) {
            if (isPotentialCallsignContextToken(joinRawParts(rawParts, startInclusive, index))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPotentialReportLeadBefore(String[] rawParts, int exclusiveEndIndex) {
        if (rawParts == null) {
            return false;
        }
        int startInclusive = Math.max(0, exclusiveEndIndex - 3);
        for (int index = exclusiveEndIndex - 1; index >= startInclusive; index--) {
            if (isPotentialReportLeadToken(normalizeToken(joinRawParts(rawParts, index, exclusiveEndIndex)))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPotentialReportContextBefore(String[] rawParts, int exclusiveEndIndex) {
        if (hasPotentialReportLeadBefore(rawParts, exclusiveEndIndex)) {
            return true;
        }
        if (rawParts == null) {
            return false;
        }
        int startInclusive = Math.max(0, exclusiveEndIndex - 3);
        for (int index = exclusiveEndIndex - 1; index >= startInclusive; index--) {
            String candidate = joinRawParts(rawParts, index, exclusiveEndIndex);
            String normalizedCandidate = normalizeToken(candidate);
            if ("599".equals(semanticToken(normalizedCandidate)) || isLikelyDamagedReportToken(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPotentialClosingContextBefore(String[] rawParts, int exclusiveEndIndex) {
        if (rawParts == null) {
            return false;
        }
        int startInclusive = Math.max(0, exclusiveEndIndex - 3);
        for (int index = exclusiveEndIndex - 1; index >= startInclusive; index--) {
            if (isPotentialClosingLeadToken(normalizeToken(joinRawParts(rawParts, index, exclusiveEndIndex)))) {
                return true;
            }
        }
        return false;
    }

    private String previousNonEmptyRawPart(String[] rawParts, int startIndex) {
        for (int index = startIndex; index >= 0; index--) {
            String token = rawParts[index];
            if (token != null && !token.isEmpty()) {
                return token;
            }
        }
        return null;
    }

    private String nextNonEmptyRawPart(String[] rawParts, int startIndex) {
        for (int index = startIndex; index < rawParts.length; index++) {
            String token = rawParts[index];
            if (token != null && !token.isEmpty()) {
                return token;
            }
        }
        return null;
    }

    private boolean isPotentialCallsignContextToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        String normalizedToken = normalizeToken(token);
        if (isCallsignCandidate(normalizedToken) || isPotentialRepairedCallsignFragment(normalizedToken)) {
            return true;
        }
        return normalizedToken.matches("[A-Z0-9?]{3,}")
                && containsLetter(normalizedToken)
                && (containsDigit(normalizedToken) || normalizedToken.contains("?"));
    }

    private boolean isPotentialReportLeadToken(String normalizedToken) {
        String semantic = semanticToken(normalizedToken);
        return "UR".equals(semantic)
                || "R".equals(semantic)
                || "RST".equals(semantic)
                || "599".equals(semantic);
    }

    private boolean isPotentialClosingLeadToken(String normalizedToken) {
        String semantic = semanticToken(normalizedToken);
        return "THANKS".equals(semantic)
                || "73".equals(semantic);
    }

    private boolean isPotentialControlFollowToken(String normalizedToken) {
        return "BK".equals(normalizedToken)
                || "KN".equals(normalizedToken)
                || "K".equals(normalizedToken);
    }

    private boolean isPotentialRequestFollowToken(String normalizedToken) {
        String semantic = semanticToken(normalizedToken);
        return "PLEASE".equals(semantic)
                || "CALL".equals(semantic)
                || "CALLSIGN".equals(semantic)
                || "AGAIN".equals(semantic);
    }

    private int countMergedSplitTokens(String[] rawParts, int startIndex, int mergedLength) {
        int consumedLength = 0;
        int partCount = 0;
        for (int index = startIndex; index < rawParts.length && partCount < 3; index++) {
            String token = rawParts[index];
            if (!isCountableMergedFragment(token)) {
                break;
            }
            consumedLength += token.length();
            partCount += 1;
            if (consumedLength == mergedLength) {
                return partCount;
            }
            if (consumedLength > mergedLength) {
                break;
            }
        }
        return 1;
    }

    private boolean isCountableMergedFragment(String token) {
        return token != null
                && !token.isEmpty()
                && token.length() <= 2
                && token.matches("[A-Z0-9?]+");
    }

    private String joinRawParts(String[] rawParts, int startInclusive, int endExclusive) {
        StringBuilder builder = new StringBuilder();
        for (int index = startInclusive; index < endExclusive && index < rawParts.length; index++) {
            if (rawParts[index] == null || rawParts[index].isEmpty()) {
                return "";
            }
            builder.append(rawParts[index]);
        }
        return builder.toString();
    }

    private boolean isShortMergeableFragment(String token) {
        return token != null
                && !token.isEmpty()
                && token.length() <= 2
                && token.matches("[A-Z0-9?]+")
                && !isKnownKeywordLikeToken(token)
                && !"R".equals(token)
                && !"K".equals(token);
    }

    private List<String> splitTrailingDeWithFollowingCallsign(String token, String nextToken) {
        if (token == null || nextToken == null || token.length() <= 2 || !token.endsWith("DE")) {
            return null;
        }
        String prefix = token.substring(0, token.length() - 2);
        if (!isPotentialDeFollowingToken(nextToken)) {
            return null;
        }

        List<String> controlPrefixedParts = splitControlPrefixedCallsign(prefix);
        if (controlPrefixedParts != null && !controlPrefixedParts.isEmpty()) {
            ArrayList<String> result = new ArrayList<>(controlPrefixedParts);
            result.add("DE");
            return result;
        }
        List<String> prefixParts = expandCompoundSide(prefix);
        if (prefixParts != null && !prefixParts.isEmpty()) {
            ArrayList<String> result = new ArrayList<>(prefixParts);
            result.add("DE");
            return result;
        }
        if (isLikelyLeadingEdgeNoise(prefix)) {
            return Arrays.asList(prefix, "DE");
        }
        return null;
    }

    private boolean isPotentialDeFollowingToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        if (isCallsignCandidate(token)) {
            return true;
        }
        if (token.matches("[A-Z0-9?]{3,}")) {
            return containsDigit(token) || token.contains("?");
        }
        return false;
    }

    private List<String> splitControlPrefixedCallsign(String token) {
        if (token == null || token.length() < 5) {
            return null;
        }
        for (String controlToken : Arrays.asList("BK", "KN", "K")) {
            if (!token.startsWith(controlToken) || token.length() <= controlToken.length()) {
                continue;
            }
            String suffix = token.substring(controlToken.length());
            if (!isCallsignCandidate(suffix) && !isPotentialRepairedCallsignFragment(suffix)) {
                continue;
            }
            return Arrays.asList(controlToken, suffix);
        }
        return null;
    }

    private boolean isLikelyLeadingEdgeNoise(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        if (token.length() <= 2 && token.contains("?")) {
            return true;
        }
        return token.matches("[?]{1,2}");
    }

    private List<String> normalizeCallsignRuns(List<String> rawParts) {
        ArrayList<String> normalizedParts = new ArrayList<>();
        for (int index = 0; index < rawParts.size(); index++) {
            String token = rawParts.get(index);
            String adjacentMergedCallsign = mergeAdjacentCallsignFragments(rawParts, index);
            if (adjacentMergedCallsign != null) {
                normalizedParts.add(adjacentMergedCallsign);
                index += 1;
                continue;
            }
            if (!isPotentialCallsignRunStart(rawParts, index)) {
                normalizedParts.add(token);
                continue;
            }

            int runEndExclusive = findCallsignRunEnd(rawParts, index);
            String mergedRun = joinTokens(rawParts, index, runEndExclusive);
            List<String> splitCandidates = splitRepeatedCallsignRun(mergedRun);
            if (!splitCandidates.isEmpty()) {
                normalizedParts.addAll(splitCandidates);
                index = runEndExclusive - 1;
                continue;
            }
            if (isCallsignCandidate(mergedRun)) {
                normalizedParts.add(mergedRun);
                index = runEndExclusive - 1;
                continue;
            }

            normalizedParts.add(token);
        }
        return normalizedParts;
    }

    private boolean isPotentialCallsignRunStart(List<String> rawParts, int index) {
        String token = rawParts.get(index);
        if (!isCallsignFragmentToken(token)) {
            return false;
        }
        if (index + 1 < rawParts.size() && mergeSplitFixedToken(rawParts.toArray(new String[0]), index + 1) != null) {
            return false;
        }
        if (token.length() > 8) {
            return true;
        }
        return index + 1 < rawParts.size() && isShortCallsignFragment(rawParts.get(index + 1));
    }

    private String mergeAdjacentCallsignFragments(List<String> rawParts, int index) {
        if (index < 0 || index + 1 >= rawParts.size()) {
            return null;
        }
        String left = rawParts.get(index);
        String right = rawParts.get(index + 1);
        if (!isCallsignFragmentToken(left) || !isCallsignFragmentToken(right)) {
            return null;
        }
        boolean contextualSingleCharacterEdge = isContextualSingleCharacterCallsignEdge(rawParts, index, left, right, left + right);
        if (!contextualSingleCharacterEdge && (!containsLetter(left) || !containsLetter(right))) {
            return null;
        }
        if (left.length() > 3 && right.length() > 4) {
            return null;
        }
        String merged = left + right;
        if (!contextualSingleCharacterEdge && looksLikeReportOrControlResidue(left, right, merged)) {
            return null;
        }
        if ((left.length() > 8 || right.length() > 8) && !splitRepeatedCallsignRun(merged).isEmpty()) {
            return null;
        }
        if (isCallsignCandidate(merged) || isPotentialRepairedCallsignFragment(merged)) {
            return merged;
        }
        return null;
    }

    private boolean isContextualSingleCharacterCallsignEdge(
            List<String> rawParts,
            int index,
            String left,
            String right,
            String merged
    ) {
        if (rawParts == null
                || merged == null
                || (!isCallsignCandidate(merged) && !isPotentialRepairedCallsignFragment(merged))) {
            return false;
        }
        if (!isSingleCharacterCallsignEdgeFragment(left) && !isSingleCharacterCallsignEdgeFragment(right)) {
            return false;
        }
        if (isSingleCharacterCallsignEdgeFragment(left) && !isExpandableCallsignBody(right)) {
            return false;
        }
        if (isSingleCharacterCallsignEdgeFragment(right) && !isExpandableCallsignBody(left)) {
            return false;
        }
        return isContextualCallsignSlot(rawParts, index)
                || isContextualCallsignSlot(rawParts, index + 1);
    }

    private boolean isSingleCharacterCallsignEdgeFragment(String token) {
        return token != null
                && token.length() == 1
                && token.matches("[A-Z?]")
                && !isKnownKeywordLikeToken(token)
                && !NON_CALLSIGN_TOKENS.contains(token);
    }

    private boolean isExpandableCallsignBody(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        if (isCallsignCandidate(token)) {
            return true;
        }
        return isPotentialRepairedCallsignFragment(token)
                && (containsDigit(token) || token.contains("?"));
    }

    private boolean looksLikeReportOrControlResidue(String left, String right, String merged) {
        if (left == null || right == null || merged == null) {
            return false;
        }
        if (isLikelyDamagedReportToken(left)
                || isLikelyDamagedReportToken(right)
                || isPotentialControlResidueToken(left)
                || isPotentialControlResidueToken(right)) {
            return true;
        }
        if (merged.matches("UR[59EN?BK]+")
                || merged.matches("R[59EN?BK]+")
                || merged.matches("[59EN?]{2,}[BK?]{1,2}")) {
            return true;
        }
        return false;
    }

    private int findCallsignRunEnd(List<String> rawParts, int startIndex) {
        int index = startIndex + 1;
        while (index < rawParts.size() && isCallsignFragmentToken(rawParts.get(index))) {
            if (rawParts.get(index).length() > 6 && index > startIndex) {
                break;
            }
            index += 1;
        }
        return index;
    }

    private boolean isCallsignFragmentToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        if (NON_CALLSIGN_TOKENS.contains(token)
                || isKnownKeywordLikeToken(token)
                || isQuestionWrappedKeywordLikeToken(token)) {
            return false;
        }
        return token.matches("[A-Z0-9?]+");
    }

    private boolean isShortCallsignFragment(String token) {
        return isCallsignFragmentToken(token) && token.length() <= 3;
    }

    private String joinTokens(List<String> tokens, int startInclusive, int endExclusive) {
        StringBuilder builder = new StringBuilder();
        for (int index = startInclusive; index < endExclusive; index++) {
            builder.append(tokens.get(index));
        }
        return builder.toString();
    }

    private List<String> splitRepeatedCallsignRun(String mergedRun) {
        ArrayList<String> candidates = new ArrayList<>();
        if (mergedRun == null || mergedRun.length() < 6) {
            return candidates;
        }
        for (int unitLength = 3; unitLength <= 10 && unitLength <= mergedRun.length(); unitLength++) {
            String base = mergedRun.substring(0, unitLength);
            if (!isCallsignCandidate(base)) {
                continue;
            }
            int repetitions = mergedRun.length() / unitLength;
            int remainderLength = mergedRun.length() % unitLength;
            if (repetitions < 2) {
                continue;
            }
            boolean allSame = true;
            for (int index = 1; index < repetitions; index++) {
                String chunk = mergedRun.substring(index * unitLength, (index + 1) * unitLength);
                if (!base.equals(chunk)) {
                    allSame = false;
                    break;
                }
            }
            if (!allSame) {
                continue;
            }
            if (remainderLength == 0) {
                appendRepeatedToken(candidates, base, repetitions);
                return candidates;
            }
            String trailingFragment = mergedRun.substring(repetitions * unitLength);
            if (trailingFragment.length() >= 3 && base.startsWith(trailingFragment)) {
                appendRepeatedToken(candidates, base, repetitions + 1);
                return candidates;
            }
        }
        return candidates;
    }

    private boolean isQuestionWrappedKeywordLikeToken(String token) {
        if (token == null || token.isEmpty() || token.indexOf('?') < 0) {
            return false;
        }
        String stripped = token.replace("?", "");
        if (stripped.isEmpty()) {
            return false;
        }
        return NON_CALLSIGN_TOKENS.contains(stripped) || isKnownKeywordLikeToken(stripped);
    }

    private boolean isCallToken(String token) {
        return "CALL".equals(token)
                || "CALLING".equals(token)
                || "CALLSIGN".equals(token)
                || "CALL-SIGN".equals(token);
    }

    private String expandCompoundText(String rawTextUppercase) {
        if (rawTextUppercase == null || rawTextUppercase.isEmpty()) {
            return "";
        }
        String[] rawParts = rawTextUppercase.split("\\s+");
        ArrayList<String> expandedParts = new ArrayList<>();
        for (String rawPart : rawParts) {
            if (rawPart == null || rawPart.isEmpty()) {
                continue;
            }
            List<String> splitParts = splitCompoundToken(rawPart);
            if (splitParts != null && !splitParts.isEmpty()) {
                expandedParts.addAll(splitParts);
            } else {
                expandedParts.add(rawPart);
            }
        }
        return String.join(" ", expandedParts);
    }

    private List<String> splitCompoundToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        List<String> ackChainSplit = splitAckCompoundToken(token);
        if (ackChainSplit != null) {
            return ackChainSplit;
        }
        List<String> fuzzyShortChainSplit = splitFuzzyShortChainToken(token);
        if (fuzzyShortChainSplit != null) {
            return fuzzyShortChainSplit;
        }
        if (isRepeatedKeywordRun(token, "CQ")) {
            ArrayList<String> repeated = new ArrayList<>();
            appendRepeatedToken(repeated, "CQ", token.length() / 2);
            return repeated;
        }

        List<String> prefixedSplit = splitKeywordPrefixedToken(token);
        if (prefixedSplit != null) {
            return prefixedSplit;
        }

        return splitKeywordBridgedToken(token);
    }

    private List<String> splitFuzzyShortChainToken(String token) {
        if (token == null || token.length() < 3) {
            return null;
        }
        if (token.startsWith("UR") && token.length() > 2) {
            List<String> tailParts = splitFuzzyReportControlTail(token.substring(2));
            if (tailParts != null && !tailParts.isEmpty()) {
                ArrayList<String> result = new ArrayList<>();
                result.add("UR");
                result.addAll(tailParts);
                return result;
            }
        }
        if (token.startsWith("R") && token.length() > 1) {
            List<String> tailParts = splitFuzzyReportControlTail(token.substring(1));
            if (tailParts != null && !tailParts.isEmpty()) {
                ArrayList<String> result = new ArrayList<>();
                result.add("R");
                result.addAll(tailParts);
                return result;
            }
        }
        List<String> tailParts = splitFuzzyReportControlTail(token);
        if (tailParts != null && tailParts.size() >= 2) {
            return tailParts;
        }
        return null;
    }

    private List<String> splitFuzzyReportControlTail(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        ArrayList<String> result = new ArrayList<>();
        int index = 0;
        while (index < token.length()) {
            int remaining = token.length() - index;
            if (remaining >= 3) {
                String reportCandidate = token.substring(index, index + 3);
                if (isLikelyDamagedReportToken(reportCandidate) || REPORT_TOKEN_VARIANTS.contains(reportCandidate)) {
                    result.add(reportCandidate);
                    index += 3;
                    continue;
                }
            }
            if (remaining >= 2) {
                String controlCandidate = token.substring(index, index + 2);
                if (bestEffortControlNormalization(controlCandidate) != null) {
                    result.add(controlCandidate);
                    index += 2;
                    continue;
                }
            }
            String controlCandidate = token.substring(index, index + 1);
            if (bestEffortControlNormalization(controlCandidate) != null) {
                result.add(controlCandidate);
                index += 1;
                continue;
            }
            return null;
        }
        return result.isEmpty() ? null : result;
    }

    private List<String> splitAckCompoundToken(String token) {
        if (!token.startsWith("R") || token.length() <= 1) {
            return null;
        }
        String suffix = token.substring(1);
        String ackPrefix = ackChainPrefix(suffix);
        if (ackPrefix != null) {
            ArrayList<String> result = new ArrayList<>();
            result.add("R");
            result.add(ackPrefix);
            String remainder = suffix.substring(ackPrefix.length());
            if (!remainder.isEmpty()) {
                List<String> remainderParts = splitCompoundToken(remainder);
                if (remainderParts == null || remainderParts.isEmpty()) {
                    return null;
                }
                result.addAll(remainderParts);
            }
            return result;
        }
        List<String> suffixParts = splitCompoundToken(suffix);
        if (suffixParts == null || suffixParts.isEmpty()) {
            return null;
        }
        if (!startsWithAckChainToken(suffixParts.get(0))) {
            return null;
        }
        ArrayList<String> result = new ArrayList<>();
        result.add("R");
        result.addAll(suffixParts);
        return result;
    }

    private boolean startsWithAckChainToken(String token) {
        return "5NN".equals(token)
                || "ENN".equals(token)
                || "599".equals(token)
                || "TU".equals(token)
                || "TNX".equals(token)
                || "BK".equals(token)
                || "KN".equals(token)
                || "73".equals(token);
    }

    private String ackChainPrefix(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        for (String candidate : Arrays.asList("5NN", "599", "ENN", "TNX", "TU", "BK", "KN", "73")) {
            if (token.startsWith(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private List<String> splitKeywordPrefixedToken(String token) {
        for (String keyword : PREFIX_SPLIT_KEYWORDS) {
            if (!token.startsWith(keyword) || token.length() <= keyword.length()) {
                continue;
            }
            String suffix = token.substring(keyword.length());
            List<String> suffixParts = splitCompoundToken(suffix);
            if (suffixParts != null && !suffixParts.isEmpty()) {
                ArrayList<String> result = new ArrayList<>();
                result.add(keyword);
                result.addAll(suffixParts);
                return result;
            }
            if (isCompoundTokenTerminal(suffix)) {
                return Arrays.asList(keyword, suffix);
            }
        }
        return null;
    }

    private List<String> splitKeywordBridgedToken(String token) {
        for (String keyword : BRIDGE_SPLIT_KEYWORDS) {
            int searchFrom = 1;
            while (searchFrom < token.length()) {
                int splitIndex = token.indexOf(keyword, searchFrom);
                if (splitIndex <= 0 || splitIndex >= token.length() - keyword.length()) {
                    break;
                }
                String left = token.substring(0, splitIndex);
                String right = token.substring(splitIndex + keyword.length());
                if (shouldPreserveWholeTokenAgainstBridgeSplit(token, keyword, left, right)) {
                    searchFrom = splitIndex + 1;
                    continue;
                }
                List<String> leftParts = splitTrailingAckResidueFromCallsign(left, keyword);
                if (leftParts == null) {
                    leftParts = expandCompoundSide(left);
                }
                List<String> rightParts = expandCompoundSide(right);
                if (leftParts == null || rightParts == null) {
                    searchFrom = splitIndex + 1;
                    continue;
                }

                ArrayList<String> result = new ArrayList<>();
                result.addAll(leftParts);
                result.add(keyword);
                result.addAll(rightParts);
                return result;
            }
        }
        return null;
    }

    private boolean shouldPreserveWholeTokenAgainstBridgeSplit(
            String token,
            String keyword,
            String left,
            String right
    ) {
        if (!isCallsignCandidate(token)) {
            return false;
        }
        if (!"TU".equals(keyword)
                && !"TNX".equals(keyword)
                && !"BK".equals(keyword)
                && !"KN".equals(keyword)
                && !"73".equals(keyword)
                && !"AGN".equals(keyword)
                && !"PSE".equals(keyword)
                && !"PLS".equals(keyword)) {
            return false;
        }
        return left.length() < 4 || right.length() < 2;
    }

    private List<String> splitTrailingAckResidueFromCallsign(String leftToken, String bridgeKeyword) {
        if (leftToken == null || bridgeKeyword == null || leftToken.length() < 5) {
            return null;
        }
        if (!"5NN".equals(bridgeKeyword) && !"599".equals(bridgeKeyword) && !"ENN".equals(bridgeKeyword)) {
            return null;
        }
        if (!leftToken.endsWith("R")) {
            return null;
        }
        String callsign = leftToken.substring(0, leftToken.length() - 1);
        if (!isCallsignCandidate(callsign)) {
            return null;
        }
        return Arrays.asList(callsign, "R");
    }

    private List<String> expandCompoundSide(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        List<String> splitParts = splitCompoundToken(token);
        if (splitParts != null && !splitParts.isEmpty()) {
            return splitParts;
        }
        List<String> compactAckReportCluster = splitCompactAckReportCluster(token);
        if (compactAckReportCluster != null && !compactAckReportCluster.isEmpty()) {
            return compactAckReportCluster;
        }
        if (isCompoundTokenTerminal(token)) {
            return Arrays.asList(token);
        }
        return null;
    }

    private List<String> splitCompactAckReportCluster(String token) {
        if (token == null || token.length() < 7) {
            return null;
        }
        for (String reportToken : Arrays.asList("5NN", "599", "ENN")) {
            String suffix = "R" + reportToken;
            if (!token.endsWith(suffix) || token.length() <= suffix.length()) {
                continue;
            }
            String callsign = token.substring(0, token.length() - suffix.length());
            if (!isCallsignCandidate(callsign)) {
                continue;
            }
            return Arrays.asList(callsign, "R", reportToken);
        }
        return null;
    }

    private boolean isCompoundTokenTerminal(String token) {
        return isKnownKeywordLikeToken(token)
                || isKnownKeywordLikeToken(semanticToken(token))
                || isCallsignCandidate(token)
                || isLikelyContextualPartialCallsign(token, Arrays.asList(token), 0);
    }

    private boolean isRepeatedKeywordRun(String token, String keyword) {
        if (token == null || keyword == null || keyword.isEmpty()) {
            return false;
        }
        if (token.length() < keyword.length() * 2 || token.length() % keyword.length() != 0) {
            return false;
        }
        int repetitions = token.length() / keyword.length();
        StringBuilder builder = new StringBuilder(token.length());
        for (int index = 0; index < repetitions; index++) {
            builder.append(keyword);
        }
        return token.equals(builder.toString());
    }

    private void appendRepeatedToken(List<String> target, String token, int count) {
        for (int index = 0; index < count; index++) {
            target.add(token);
        }
    }

    private String formatTokenSummary(CwInterpretedToken token) {
        return token.type().name() + ": " + token.rawText() + " -> " + token.normalizedText();
    }

    private static final class RankedCallsign {
        private final String callsign;
        private final int lastSeenOrder;

        private RankedCallsign(String callsign, int lastSeenOrder) {
            this.callsign = callsign;
            this.lastSeenOrder = lastSeenOrder;
        }

        private int certaintyScore() {
            return certaintyScore(callsign);
        }

        private int certaintyScore(String candidate) {
            int score = 0;
            for (int index = 0; index < candidate.length(); index++) {
                if (candidate.charAt(index) != '?') {
                    score += 1;
                }
            }
            return score;
        }

        private int lastSeenOrder() {
            return lastSeenOrder;
        }

        private String callsign() {
            return callsign;
        }
    }

    private static final class ContextualCallsigns {
        private final String addressedCallsign;
        private final String speakerCallsign;

        private ContextualCallsigns(String addressedCallsign, String speakerCallsign) {
            this.addressedCallsign = addressedCallsign;
            this.speakerCallsign = speakerCallsign;
        }
    }
}
