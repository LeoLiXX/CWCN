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
    private static final List<String> REPORT_TOKEN_VARIANTS = Arrays.asList("5NN", "599", "ENN");
    private static final List<String> CONTROL_TOKEN_VARIANTS = Arrays.asList("BK", "KN", "K");
    private static final List<String> COMPOUND_KEYWORDS = Arrays.asList(
            "CALLSIGN", "CALL", "QRZ", "CQ", "DE", "5NN", "ENN", "599", "AGN", "PSE", "PLS", "TNX", "TU", "UR", "BK", "KN", "73"
    );
    private static final List<String> COMPOUND_BRIDGE_KEYWORDS = Arrays.asList(
            "DE", "QRZ", "UR", "TNX", "TU", "BK", "KN", "73", "5NN", "ENN", "599", "AGN", "PSE", "PLS", "CALLSIGN", "CALL", "CQ"
    );
    private static final Pattern CALLSIGN_PATTERN =
            Pattern.compile("^(?=.{3,})(?=.*[A-Z])(?=.*\\d)[A-Z0-9?/]{3,}$");
    private static final Set<String> NON_CALLSIGN_TOKENS = new LinkedHashSet<>(Arrays.asList(
            "CQ", "DE", "QRZ", "K", "KN", "BK", "PSE", "PLS", "AGN", "R", "UR", "TU", "TNX", "599", "5NN", "ENN",
            "PLEASE", "CALL", "CALLSIGN", "AGAIN", "73"
    ));

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

    public synchronized void process(CwDecodeEvent decodeEvent) {
        if (decodeEvent.type() != CwDecodeEvent.Type.CHARACTER_DECODED
                && decodeEvent.type() != CwDecodeEvent.Type.WORD_BREAK) {
            return;
        }

        rawText = decodeEvent.outputText().trim();
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
        ArrayList<CwInterpretedToken> parsedTokens = new ArrayList<>();
        ArrayList<String> rawCallsignCandidates = new ArrayList<>();
        LinkedHashSet<String> hintSet = new LinkedHashSet<>();
        ArrayList<String> normalizedParts = new ArrayList<>();

        if (!rawText.isEmpty()) {
            String semanticRawText = rawText.toUpperCase(Locale.US).replace(CwDecoder.UNKNOWN_CHARACTER, "?");
            List<String> rawParts = normalizeRawParts(expandCompoundText(semanticRawText).split("\\s+"));
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
        callsignCandidates = buildRecoveredCallsignCandidates(rawCallsignCandidates, contextualCallsigns);
        primaryCallsignCandidate = choosePrimaryCallsignCandidate(parsedTokens, callsignCandidates);
        if (primaryCallsignCandidate != null) {
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
        if (containsNormalizedToken(parsedTokens, "599")) {
            hintSet.add("Report exchange");
        }
        if (containsTokenSequence(parsedTokens, "UR", "599")) {
            hintSet.add("Directed report to called station");
        }
        if (containsTokenSequence(parsedTokens, "R", "599")) {
            hintSet.add("Report acknowledgement / return report");
        }
        if (containsNormalizedToken(parsedTokens, "AGAIN")
                || containsNormalizedToken(parsedTokens, "PLEASE")
                || containsNormalizedToken(parsedTokens, "CALL")
                || containsNormalizedToken(parsedTokens, "CALLSIGN")
                || normalizedText.contains("?")) {
            hintSet.add("Repeat / clarification request");
        }
        if (hasUncertainCallsign(rawCallsignCandidates)) {
            hintSet.add("Partial callsign / uncertain copy");
        }
        if (hasPartialToFullUpgrade(rawCallsignCandidates, callsignCandidates)) {
            hintSet.add("Partial callsign resolved");
        }
        if (primaryCallsignCandidate != null
                && (containsNormalizedToken(parsedTokens, "CALL")
                || containsNormalizedToken(parsedTokens, "CALLSIGN")
                || containsNormalizedToken(parsedTokens, "AGAIN"))) {
            hintSet.add("Callsign confirmation cycle");
        }
        if (containsNormalizedToken(parsedTokens, "BK")
                || containsNormalizedToken(parsedTokens, "KN")
                || containsNormalizedToken(parsedTokens, "K")) {
            hintSet.add("Turn handoff / over");
        }
        if (containsNormalizedToken(parsedTokens, "THANKS")) {
            hintSet.add("Closing / acknowledgement");
        }
        if (containsNormalizedToken(parsedTokens, "73")) {
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

    private String normalizeToken(String rawToken) {
        String cleanedToken = trimKnownTrailingPunctuation(rawToken);
        String normalizedClosingResidue = normalizeClosingResidueToken(cleanedToken);
        if (normalizedClosingResidue != null) {
            return normalizedClosingResidue;
        }
        switch (cleanedToken) {
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
            case "5NN":
            case "ENN":
                return "599";
            default:
                return cleanedToken;
        }
    }

    private String normalizeToken(String rawToken, List<String> rawParts, int index) {
        String normalizedToken = normalizeToken(rawToken);
        if (!normalizedToken.equals(rawToken)) {
            return normalizedToken;
        }
        if (isLikelyDamagedReportToken(rawToken) && isReportResidueContext(rawParts, index)) {
            return "599";
        }
        String damagedControlNormalization = normalizeDamagedControlToken(rawToken, rawParts, index);
        if (damagedControlNormalization != null) {
            return damagedControlNormalization;
        }
        return normalizedToken;
    }

    private CwInterpretedToken.Type classifyToken(
            String rawToken,
            String normalizedToken,
            List<String> normalizedParts,
            int index
    ) {
        if ("CQ".equals(normalizedToken)) {
            return CwInterpretedToken.Type.CQ;
        }
        if ("DE".equals(normalizedToken)) {
            return CwInterpretedToken.Type.DE;
        }
        if ("QRZ".equals(normalizedToken)) {
            return CwInterpretedToken.Type.QRZ;
        }
        if ("THANKS".equals(normalizedToken)) {
            return CwInterpretedToken.Type.THANKS;
        }
        if ("AGAIN".equals(normalizedToken)) {
            return CwInterpretedToken.Type.AGAIN;
        }
        if ("R".equals(normalizedToken)) {
            return CwInterpretedToken.Type.ACK;
        }
        if (isRequestToken(normalizedToken)) {
            return CwInterpretedToken.Type.REQUEST;
        }
        if (isReportToken(rawToken, normalizedToken)) {
            return CwInterpretedToken.Type.REPORT;
        }
        if (isLikelyReportResidueToken(normalizedToken, normalizedParts, index)) {
            return CwInterpretedToken.Type.REPORT;
        }
        if (isControlToken(normalizedToken)) {
            return CwInterpretedToken.Type.CONTROL;
        }
        if (isLikelyControlResidueToken(normalizedToken)) {
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
        String previous = index > 0 ? normalizeToken(normalizedParts.get(index - 1)) : "";
        String next = index + 1 < normalizedParts.size() ? normalizeToken(normalizedParts.get(index + 1)) : "";
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
        String previous = normalizeToken(previousRaw);
        String next = normalizeToken(nextRaw);
        return "599".equals(previous)
                || "UR".equals(previous)
                || "R".equals(previous)
                || isLikelyDamagedReportToken(previousRaw)
                || "THANKS".equals(previous)
                || "73".equals(previous)
                || "THANKS".equals(next)
                || "73".equals(next)
                || "BK".equals(next)
                || "KN".equals(next)
                || "K".equals(next)
                || bestEffortControlNormalization(nextRaw) != null;
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

    private String normalizeDamagedControlToken(String rawToken, List<String> rawParts, int index) {
        if (rawToken == null || rawToken.isEmpty() || !isControlResidueContext(rawParts, index)) {
            return null;
        }
        return bestEffortControlNormalization(rawToken);
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
        if (NON_CALLSIGN_TOKENS.contains(token)) {
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
        String previous = index > 0 ? normalizeToken(normalizedParts.get(index - 1)) : "";
        String next = index + 1 < normalizedParts.size() ? normalizeToken(normalizedParts.get(index + 1)) : "";
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

    private boolean containsNormalizedToken(List<CwInterpretedToken> parsedTokens, String normalizedToken) {
        for (CwInterpretedToken token : parsedTokens) {
            if (normalizedToken.equals(token.normalizedText())) {
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
        return mergeAndRankCallsignCandidates(recoveredCandidates);
    }

    private List<String> deriveCallsignRepairVariants(String rawCandidate) {
        ArrayList<String> variants = new ArrayList<>();
        if (rawCandidate == null || rawCandidate.isEmpty()) {
            return variants;
        }
        variants.add(rawCandidate);
        addTrimmedRepairVariants(variants, rawCandidate);
        for (String keyword : COMPOUND_BRIDGE_KEYWORDS) {
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

    private void addTrimmedRepairVariants(List<String> variants, String rawCandidate) {
        if (rawCandidate == null || rawCandidate.length() < 5) {
            return;
        }
        int maxLeadingTrim = Math.min(3, rawCandidate.length() - 4);
        int maxTrailingTrim = Math.min(3, rawCandidate.length() - 4);
        for (int leadingTrim = 1; leadingTrim <= maxLeadingTrim; leadingTrim++) {
            addRepairedVariant(variants, rawCandidate.substring(leadingTrim));
        }
        for (int trailingTrim = 1; trailingTrim <= maxTrailingTrim; trailingTrim++) {
            addRepairedVariant(variants, rawCandidate.substring(0, rawCandidate.length() - trailingTrim));
        }
        for (int leadingTrim = 1; leadingTrim <= maxLeadingTrim; leadingTrim++) {
            for (int trailingTrim = 1; trailingTrim <= maxTrailingTrim; trailingTrim++) {
                if (leadingTrim + trailingTrim >= rawCandidate.length() - 3) {
                    continue;
                }
                addRepairedVariant(
                        variants,
                        rawCandidate.substring(leadingTrim, rawCandidate.length() - trailingTrim)
                );
            }
        }
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
            String merged = mergeAgainstRememberedCallsign(rememberedCandidate, rawCandidate);
            if (merged != null && !recoveredCandidates.contains(merged)) {
                recoveredCandidates.add(merged);
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
        if (isContaminatedWrapOfRememberedCallsign(rememberedCandidate, rawCandidate)) {
            return rememberedCandidate;
        }
        if (isShortAnchoredRememberedFragment(rememberedCandidate, rawCandidate)) {
            return rememberedCandidate;
        }
        return null;
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
                && "DEHKBSTUIMNR".indexOf(contamination) >= 0;
    }

    private String choosePrimaryCallsignCandidate(
            List<CwInterpretedToken> parsedTokens,
            List<String> rankedCandidates
    ) {
        if (rankedCandidates.isEmpty()) {
            return null;
        }

        ContextualCallsigns contextualCallsigns = extractContextualCallsigns(parsedTokens);
        if (contextualCallsigns.speakerCallsign != null) {
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
                return contextualUpgrade;
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
        String upgradedAddressed = upgradeRememberedCallsign(contextualCallsigns.addressedCallsign, rankedCandidates);
        if (upgradedAddressed != null) {
            rememberedAddressedCallsign = upgradedAddressed;
        }
        String upgradedSpeaker = upgradeRememberedCallsign(contextualCallsigns.speakerCallsign, rankedCandidates);
        if (upgradedSpeaker != null) {
            rememberedSpeakerCallsign = upgradedSpeaker;
        }
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
        return isSingleEdgeRememberedContaminationWrap(shorter, longer) ? shorter : null;
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

    private String normalizeClosingResidueToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        if (token.matches("[7?]3[BK?]")) {
            return "73";
        }
        if (token.matches("[7?]3")) {
            return "73";
        }
        return null;
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
            return false;
        }
        return prefixAligned
                ? shorter.endsWith(cleanSegment)
                : shorter.startsWith(cleanSegment);
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
            return Arrays.asList("DE");
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
        if (token.length() > 8) {
            return true;
        }
        return index + 1 < rawParts.size() && isShortCallsignFragment(rawParts.get(index + 1));
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
        if (NON_CALLSIGN_TOKENS.contains(token) || isKnownKeywordLikeToken(token)) {
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
        for (int unitLength = 3; unitLength <= 10; unitLength++) {
            if (mergedRun.length() % unitLength != 0) {
                continue;
            }
            String base = mergedRun.substring(0, unitLength);
            if (!isCallsignCandidate(base)) {
                continue;
            }
            int repetitions = mergedRun.length() / unitLength;
            boolean allSame = true;
            for (int index = 1; index < repetitions; index++) {
                String chunk = mergedRun.substring(index * unitLength, (index + 1) * unitLength);
                if (!base.equals(chunk)) {
                    allSame = false;
                    break;
                }
            }
            if (allSame && repetitions >= 2) {
                appendRepeatedToken(candidates, base, repetitions);
                return candidates;
            }
        }
        return candidates;
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
        for (String keyword : COMPOUND_KEYWORDS) {
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
        for (String keyword : COMPOUND_BRIDGE_KEYWORDS) {
            int searchFrom = 1;
            while (searchFrom < token.length()) {
                int splitIndex = token.indexOf(keyword, searchFrom);
                if (splitIndex <= 0 || splitIndex >= token.length() - keyword.length()) {
                    break;
                }
                String left = token.substring(0, splitIndex);
                String right = token.substring(splitIndex + keyword.length());
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
