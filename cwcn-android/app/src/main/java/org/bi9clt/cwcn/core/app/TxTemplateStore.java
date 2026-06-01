package org.bi9clt.cwcn.core.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class TxTemplateStore {
    public static final String TEMPLATE_CQ = "CQ";
    public static final String TEMPLATE_CQ_X2 = "CQ ×2";
    public static final String TEMPLATE_QRZ = "QRZ";
    public static final String TEMPLATE_RESPONSE_CQ = "Response CQ";
    public static final String TEMPLATE_RESPONSE_CALL = "Response CALL";
    public static final String TEMPLATE_QTH = "QTH";
    public static final String TEMPLATE_RIG = "RIG";
    public static final String TEMPLATE_QSO_73 = "QSO & 73";

    private static final String LEGACY_TEMPLATE_REPLY = "应答";
    private static final String LEGACY_TEMPLATE_QRZ = "设备";
    private static final String LEGACY_TEMPLATE_QRZ_VERBOSE = "设备信息";
    private static final String LEGACY_TEMPLATE_TU73 = "73";
    private static final String LEGACY_TEMPLATE_TU73_VERBOSE = "73 收尾";
    private static final String LEGACY_TEMPLATE_REPLY_VERBOSE = "基本应答";

    public static final String DEFAULT_CQ =
            "CQ CQ CQ DE <MYCALL> <MYCALL> <MYCALL> PSE K.";
    public static final String DEFAULT_CQ_X2 =
            "CQ CQ DE <MYCALL> <MYCALL>\n"
                    + "CQ CQ DE <MYCALL> <MYCALL>\n"
                    + "PSE K.";
    public static final String DEFAULT_QRZ = "QRZ? DE <MYCALL> <MYCALL> BK.";
    public static final String DEFAULT_RESPONSE_CQ =
            "<CALL> <CALL> <CALL> DE <MYCALL> <MYCALL> <MYCALL> UR <RST> <RST> BK.";
    public static final String DEFAULT_RESPONSE_CALL =
            "R R <CALL> DE <MYCALL> <MYCALL> TU UR <RST> <RST> BK.";
    public static final String DEFAULT_QTH =
            "<CALL> DE <MYCALL> QTH <QTH> GRID <GRID> WX <WX> BK.";
    public static final String DEFAULT_RIG =
            "<CALL> DE <MYCALL> RIG <RIG> ANT <ANT> BK.";
    public static final String DEFAULT_QSO_73 =
            "<CALL> TU QSO 73 DE <MYCALL> SK.";

    public static final String TEMPLATE_ID_CQ = "builtin_cq";
    public static final String TEMPLATE_ID_CQ_X2 = "builtin_cq_x2";
    public static final String TEMPLATE_ID_QRZ = "builtin_qrz";
    public static final String TEMPLATE_ID_RESPONSE_CQ = "builtin_response_cq";
    public static final String TEMPLATE_ID_RESPONSE_CALL = "builtin_response_call";
    public static final String TEMPLATE_ID_QTH = "builtin_qth";
    public static final String TEMPLATE_ID_RIG = "builtin_rig";
    public static final String TEMPLATE_ID_QSO_73 = "builtin_qso_73";

    private static final String PREFS_NAME = "cwcn_tx_templates";
    private static final String KEY_CQ = "template_cq";
    private static final String KEY_CQ_DX = "template_cq_dx";
    private static final String KEY_QRZ = "template_qrz";
    private static final String KEY_TU73 = "template_tu73";
    private static final String KEY_TEMPLATES_JSON = "templates_json";
    private static final String BACKUP_FORMAT = "cwcn_tx_templates";
    private static final int BACKUP_VERSION = 1;

    public enum ImportMode {
        APPEND,
        REPLACE_SAME_NAME
    }

    public static final class ImportResult {
        private final int importedCount;
        private final int createdCount;
        private final int updatedCount;
        private final int renamedCount;

        private ImportResult(
                int importedCount,
                int createdCount,
                int updatedCount,
                int renamedCount
        ) {
            this.importedCount = Math.max(0, importedCount);
            this.createdCount = Math.max(0, createdCount);
            this.updatedCount = Math.max(0, updatedCount);
            this.renamedCount = Math.max(0, renamedCount);
        }

        public int importedCount() {
            return importedCount;
        }

        public int createdCount() {
            return createdCount;
        }

        public int updatedCount() {
            return updatedCount;
        }

        public int renamedCount() {
            return renamedCount;
        }
    }

    private final SharedPreferences preferences;

    public TxTemplateStore(Context context) {
        Context appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        ensureTemplateCatalog();
    }

    public List<TxTemplateEntry> loadTemplates() {
        ensureTemplateCatalog();
        String rawJson = preferences.getString(KEY_TEMPLATES_JSON, null);
        ArrayList<TxTemplateEntry> results = deserializeTemplates(rawJson);
        if (results.isEmpty()) {
            results.addAll(defaultTemplates());
        }
        return Collections.unmodifiableList(results);
    }

    @Nullable
    public TxTemplateEntry findTemplateById(@Nullable String templateId) {
        String normalizedId = normalize(templateId);
        if (normalizedId == null) {
            return null;
        }
        List<TxTemplateEntry> entries = loadTemplates();
        for (TxTemplateEntry entry : entries) {
            if (normalizedId.equals(entry.id())) {
                return entry;
            }
        }
        return null;
    }

    @Nullable
    public TxTemplateEntry findTemplateByName(@Nullable String templateName) {
        String normalizedName = normalize(templateName);
        if (normalizedName == null) {
            return null;
        }
        List<TxTemplateEntry> entries = loadTemplates();
        for (TxTemplateEntry entry : entries) {
            if (normalizedName.equalsIgnoreCase(entry.name())) {
                return entry;
            }
        }
        return null;
    }

    public TxTemplateEntry saveTemplate(
            @Nullable String templateId,
            @Nullable String templateName,
            @Nullable String templateBody
    ) {
        String normalizedName = normalizeOrDefault(templateName, "新模板");
        String normalizedBody = normalizeOrDefault(templateBody, DEFAULT_CQ);
        String normalizedId = normalize(templateId);

        ArrayList<TxTemplateEntry> entries = new ArrayList<>(loadTemplates());
        TxTemplateEntry savedEntry = new TxTemplateEntry(
                normalizedId == null ? UUID.randomUUID().toString() : normalizedId,
                normalizedName,
                normalizedBody
        );
        boolean replaced = false;
        for (int i = 0; i < entries.size(); i++) {
            if (savedEntry.id().equals(entries.get(i).id())) {
                entries.set(i, savedEntry);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            entries.add(savedEntry);
        }
        persistTemplates(entries);
        return savedEntry;
    }

    public boolean deleteTemplate(@Nullable String templateId) {
        String normalizedId = normalize(templateId);
        if (normalizedId == null) {
            return false;
        }
        ArrayList<TxTemplateEntry> entries = new ArrayList<>(loadTemplates());
        if (entries.size() <= 1) {
            return false;
        }
        boolean removed = entries.removeIf(entry -> normalizedId.equals(entry.id()));
        if (!removed) {
            return false;
        }
        persistTemplates(entries);
        return true;
    }

    public String defaultTemplateId() {
        List<TxTemplateEntry> entries = loadTemplates();
        return entries.isEmpty() ? TEMPLATE_ID_CQ : entries.get(0).id();
    }

    public List<String> supportedPlaceholders() {
        ArrayList<String> values = new ArrayList<>();
        values.add("<MYCALL>");
        values.add("<CALL>");
        values.add("<RST>");
        values.add("<RST_SENT>");
        values.add("<RST_RCVD>");
        values.add("<NAME>");
        values.add("<QTH>");
        values.add("<GRID>");
        values.add("<RIG>");
        values.add("<ANT>");
        values.add("<WX>");
        return Collections.unmodifiableList(values);
    }

    public String cqTemplate() {
        TxTemplateEntry entry = findTemplateById(TEMPLATE_ID_CQ);
        return entry == null ? DEFAULT_CQ : entry.body();
    }

    public String cqX2Template() {
        TxTemplateEntry entry = findTemplateById(TEMPLATE_ID_CQ_X2);
        return entry == null ? DEFAULT_CQ_X2 : entry.body();
    }

    public String qrzTemplate() {
        TxTemplateEntry entry = findTemplateById(TEMPLATE_ID_QRZ);
        return entry == null ? DEFAULT_QRZ : entry.body();
    }

    public String responseCqTemplate() {
        TxTemplateEntry entry = findTemplateById(TEMPLATE_ID_RESPONSE_CQ);
        return entry == null ? DEFAULT_RESPONSE_CQ : entry.body();
    }

    public String responseCallTemplate() {
        TxTemplateEntry entry = findTemplateById(TEMPLATE_ID_RESPONSE_CALL);
        return entry == null ? DEFAULT_RESPONSE_CALL : entry.body();
    }

    public String qthTemplate() {
        TxTemplateEntry entry = findTemplateById(TEMPLATE_ID_QTH);
        return entry == null ? DEFAULT_QTH : entry.body();
    }

    public String rigTemplate() {
        TxTemplateEntry entry = findTemplateById(TEMPLATE_ID_RIG);
        return entry == null ? DEFAULT_RIG : entry.body();
    }

    public String qso73Template() {
        TxTemplateEntry entry = findTemplateById(TEMPLATE_ID_QSO_73);
        return entry == null ? DEFAULT_QSO_73 : entry.body();
    }

    public String resolveTemplate(String templateLabel) {
        TxTemplateEntry byId = findTemplateById(templateLabel);
        if (byId != null) {
            return byId.body();
        }
        if (TEMPLATE_CQ_X2.equals(templateLabel)) {
            return cqX2Template();
        }
        if (TEMPLATE_QRZ.equals(templateLabel)) {
            return qrzTemplate();
        }
        if (TEMPLATE_RESPONSE_CQ.equals(templateLabel)
                || LEGACY_TEMPLATE_REPLY.equals(templateLabel)
                || LEGACY_TEMPLATE_REPLY_VERBOSE.equals(templateLabel)) {
            return responseCqTemplate();
        }
        if (TEMPLATE_RESPONSE_CALL.equals(templateLabel)) {
            return responseCallTemplate();
        }
        if (TEMPLATE_QTH.equals(templateLabel)) {
            return qthTemplate();
        }
        if (TEMPLATE_RIG.equals(templateLabel)
                || LEGACY_TEMPLATE_QRZ.equals(templateLabel)
                || LEGACY_TEMPLATE_QRZ_VERBOSE.equals(templateLabel)) {
            return rigTemplate();
        }
        if (TEMPLATE_QSO_73.equals(templateLabel)
                || LEGACY_TEMPLATE_TU73.equals(templateLabel)
                || LEGACY_TEMPLATE_TU73_VERBOSE.equals(templateLabel)) {
            return qso73Template();
        }
        TxTemplateEntry byName = findTemplateByName(templateLabel);
        if (byName != null) {
            return byName.body();
        }
        return cqTemplate();
    }

    public String exportBackupJson() {
        JSONObject root = new JSONObject();
        try {
            root.put("format", BACKUP_FORMAT);
            root.put("version", BACKUP_VERSION);
            root.put("exportedAtEpochMs", System.currentTimeMillis());
            root.put("templates", buildTemplateArray(loadTemplates()));
        } catch (JSONException ignored) {
        }
        try {
            return root.toString(2);
        } catch (JSONException ignored) {
            return root.toString();
        }
    }

    public int countImportableTemplates(@Nullable String rawJson) {
        return deserializeTemplatesFromImport(rawJson).size();
    }

    public ImportResult importTemplates(
            @Nullable String rawJson,
            @Nullable ImportMode importMode
    ) {
        ArrayList<TxTemplateEntry> importedEntries = deserializeTemplatesFromImport(rawJson);
        if (importedEntries.isEmpty()) {
            return new ImportResult(0, 0, 0, 0);
        }
        ImportMode safeImportMode = importMode == null ? ImportMode.APPEND : importMode;
        ArrayList<TxTemplateEntry> currentEntries = new ArrayList<>(loadTemplates());
        int createdCount = 0;
        int updatedCount = 0;
        int renamedCount = 0;
        for (TxTemplateEntry importedEntry : importedEntries) {
            if (importedEntry == null) {
                continue;
            }
            if (safeImportMode == ImportMode.REPLACE_SAME_NAME) {
                int existingIndex = findTemplateIndexById(currentEntries, importedEntry.id());
                if (existingIndex < 0) {
                    existingIndex = findTemplateIndexByName(currentEntries, importedEntry.name(), -1);
                }
                if (existingIndex >= 0) {
                    TxTemplateEntry existingEntry = currentEntries.get(existingIndex);
                    String mergedName = uniqueTemplateName(
                            currentEntries,
                            importedEntry.name(),
                            existingIndex
                    );
                    if (!mergedName.equals(importedEntry.name())) {
                        renamedCount++;
                    }
                    currentEntries.set(
                            existingIndex,
                            new TxTemplateEntry(
                                    existingEntry.id(),
                                    mergedName,
                                    importedEntry.body()
                            )
                    );
                    updatedCount++;
                    continue;
                }
            }
            String uniqueId = uniqueTemplateId(currentEntries, importedEntry.id());
            String uniqueName = uniqueTemplateName(currentEntries, importedEntry.name(), -1);
            if (!uniqueName.equals(importedEntry.name())) {
                renamedCount++;
            }
            currentEntries.add(new TxTemplateEntry(uniqueId, uniqueName, importedEntry.body()));
            createdCount++;
        }
        persistTemplates(currentEntries);
        return new ImportResult(importedEntries.size(), createdCount, updatedCount, renamedCount);
    }

    private void ensureTemplateCatalog() {
        String rawJson = preferences.getString(KEY_TEMPLATES_JSON, null);
        if (normalize(rawJson) != null && !deserializeTemplates(rawJson).isEmpty()) {
            return;
        }
        ArrayList<TxTemplateEntry> migratedEntries = migrateLegacyTemplates();
        if (migratedEntries.isEmpty()) {
            migratedEntries.addAll(defaultTemplates());
        }
        persistTemplates(migratedEntries);
    }

    private ArrayList<TxTemplateEntry> migrateLegacyTemplates() {
        ArrayList<TxTemplateEntry> migrated = new ArrayList<>();
        migrated.add(new TxTemplateEntry(
                TEMPLATE_ID_CQ,
                TEMPLATE_CQ,
                normalizeOrDefault(preferences.getString(KEY_CQ, null), DEFAULT_CQ)
        ));
        migrated.add(new TxTemplateEntry(
                TEMPLATE_ID_CQ_X2,
                TEMPLATE_CQ_X2,
                DEFAULT_CQ_X2
        ));
        migrated.add(new TxTemplateEntry(
                TEMPLATE_ID_QRZ,
                TEMPLATE_QRZ,
                DEFAULT_QRZ
        ));
        migrated.add(new TxTemplateEntry(
                TEMPLATE_ID_RESPONSE_CQ,
                TEMPLATE_RESPONSE_CQ,
                normalizeOrDefault(preferences.getString(KEY_CQ_DX, null), DEFAULT_RESPONSE_CQ)
        ));
        migrated.add(new TxTemplateEntry(
                TEMPLATE_ID_RESPONSE_CALL,
                TEMPLATE_RESPONSE_CALL,
                DEFAULT_RESPONSE_CALL
        ));
        migrated.add(new TxTemplateEntry(
                TEMPLATE_ID_QTH,
                TEMPLATE_QTH,
                DEFAULT_QTH
        ));
        migrated.add(new TxTemplateEntry(
                TEMPLATE_ID_RIG,
                TEMPLATE_RIG,
                normalizeOrDefault(preferences.getString(KEY_QRZ, null), DEFAULT_RIG)
        ));
        migrated.add(new TxTemplateEntry(
                TEMPLATE_ID_QSO_73,
                TEMPLATE_QSO_73,
                normalizeOrDefault(preferences.getString(KEY_TU73, null), DEFAULT_QSO_73)
        ));
        return migrated;
    }

    private List<TxTemplateEntry> defaultTemplates() {
        ArrayList<TxTemplateEntry> defaults = new ArrayList<>();
        defaults.add(new TxTemplateEntry(TEMPLATE_ID_CQ, TEMPLATE_CQ, DEFAULT_CQ));
        defaults.add(new TxTemplateEntry(TEMPLATE_ID_CQ_X2, TEMPLATE_CQ_X2, DEFAULT_CQ_X2));
        defaults.add(new TxTemplateEntry(TEMPLATE_ID_QRZ, TEMPLATE_QRZ, DEFAULT_QRZ));
        defaults.add(new TxTemplateEntry(
                TEMPLATE_ID_RESPONSE_CQ,
                TEMPLATE_RESPONSE_CQ,
                DEFAULT_RESPONSE_CQ
        ));
        defaults.add(new TxTemplateEntry(
                TEMPLATE_ID_RESPONSE_CALL,
                TEMPLATE_RESPONSE_CALL,
                DEFAULT_RESPONSE_CALL
        ));
        defaults.add(new TxTemplateEntry(TEMPLATE_ID_QTH, TEMPLATE_QTH, DEFAULT_QTH));
        defaults.add(new TxTemplateEntry(TEMPLATE_ID_RIG, TEMPLATE_RIG, DEFAULT_RIG));
        defaults.add(new TxTemplateEntry(TEMPLATE_ID_QSO_73, TEMPLATE_QSO_73, DEFAULT_QSO_73));
        return defaults;
    }

    private void persistTemplates(List<TxTemplateEntry> entries) {
        JSONArray array = buildTemplateArray(entries);
        if (array.length() == 0) {
            array = buildTemplateArray(defaultTemplates());
        }
        preferences.edit().putString(KEY_TEMPLATES_JSON, array.toString()).apply();
    }

    private ArrayList<TxTemplateEntry> deserializeTemplates(@Nullable String rawJson) {
        ArrayList<TxTemplateEntry> entries = new ArrayList<>();
        String normalizedJson = normalize(rawJson);
        if (normalizedJson == null) {
            return entries;
        }
        try {
            JSONArray array = new JSONArray(normalizedJson);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                String id = normalize(object.optString("id", null));
                String name = normalize(object.optString("name", null));
                String body = normalize(object.optString("body", null));
                if (id == null || name == null || body == null) {
                    continue;
                }
                entries.add(new TxTemplateEntry(id, name, body));
            }
        } catch (JSONException ignored) {
            entries.clear();
        }
        return entries;
    }

    private JSONArray buildTemplateArray(List<TxTemplateEntry> entries) {
        JSONArray array = new JSONArray();
        if (entries == null) {
            return array;
        }
        for (TxTemplateEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            String id = normalize(entry.id());
            String name = normalize(entry.name());
            String body = normalize(entry.body());
            if (id == null || name == null || body == null) {
                continue;
            }
            JSONObject object = new JSONObject();
            try {
                object.put("id", id);
                object.put("name", name);
                object.put("body", body);
                array.put(object);
            } catch (JSONException ignored) {
            }
        }
        return array;
    }

    private ArrayList<TxTemplateEntry> deserializeTemplatesFromImport(@Nullable String rawJson) {
        ArrayList<TxTemplateEntry> entries = new ArrayList<>();
        String normalizedJson = normalize(rawJson);
        if (normalizedJson == null) {
            return entries;
        }
        try {
            Object root = new JSONTokener(normalizedJson).nextValue();
            JSONArray array = null;
            if (root instanceof JSONObject) {
                array = ((JSONObject) root).optJSONArray("templates");
            } else if (root instanceof JSONArray) {
                array = (JSONArray) root;
            }
            if (array == null) {
                return entries;
            }
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                String id = normalize(object.optString("id", null));
                String name = normalize(object.optString("name", null));
                String body = normalize(object.optString("body", null));
                if (name == null || body == null) {
                    continue;
                }
                entries.add(new TxTemplateEntry(
                        id == null ? UUID.randomUUID().toString() : id,
                        name,
                        body
                ));
            }
        } catch (JSONException ignored) {
            entries.clear();
        }
        return entries;
    }

    private int findTemplateIndexById(List<TxTemplateEntry> entries, @Nullable String templateId) {
        String normalizedId = normalize(templateId);
        if (normalizedId == null || entries == null) {
            return -1;
        }
        for (int i = 0; i < entries.size(); i++) {
            TxTemplateEntry entry = entries.get(i);
            if (entry != null && normalizedId.equals(entry.id())) {
                return i;
            }
        }
        return -1;
    }

    private int findTemplateIndexByName(
            List<TxTemplateEntry> entries,
            @Nullable String templateName,
            int ignoreIndex
    ) {
        String normalizedName = normalize(templateName);
        if (normalizedName == null || entries == null) {
            return -1;
        }
        for (int i = 0; i < entries.size(); i++) {
            if (i == ignoreIndex) {
                continue;
            }
            TxTemplateEntry entry = entries.get(i);
            if (entry != null && normalizedName.equalsIgnoreCase(entry.name())) {
                return i;
            }
        }
        return -1;
    }

    private String uniqueTemplateId(List<TxTemplateEntry> entries, @Nullable String preferredId) {
        String normalizedPreferredId = normalize(preferredId);
        if (normalizedPreferredId != null && findTemplateIndexById(entries, normalizedPreferredId) < 0) {
            return normalizedPreferredId;
        }
        return UUID.randomUUID().toString();
    }

    private String uniqueTemplateName(
            List<TxTemplateEntry> entries,
            @Nullable String preferredName,
            int ignoreIndex
    ) {
        String baseName = normalizeOrDefault(preferredName, "新模板");
        if (findTemplateIndexByName(entries, baseName, ignoreIndex) < 0) {
            return baseName;
        }
        int suffix = 2;
        while (true) {
            String candidate = baseName + " (" + suffix + ")";
            if (findTemplateIndexByName(entries, candidate, ignoreIndex) < 0) {
                return candidate;
            }
            suffix++;
        }
    }

    @Nullable
    private String normalize(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeOrDefault(@Nullable String value, String fallback) {
        String normalized = normalize(value);
        return normalized == null ? fallback : normalized;
    }
}
