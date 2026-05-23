package org.bi9clt.cwcn.core.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class TxTemplateStore {
    public static final String TEMPLATE_CQ = "CQ";
    public static final String TEMPLATE_REPLY = "基本应答";
    public static final String TEMPLATE_QRZ = "设备信息";
    public static final String TEMPLATE_TU73 = "73 收尾";

    private static final String LEGACY_TEMPLATE_REPLY = "应答";
    private static final String LEGACY_TEMPLATE_QRZ = "设备";
    private static final String LEGACY_TEMPLATE_TU73 = "73";

    public static final String DEFAULT_CQ = "CQ CQ DE <MYCALL> K";
    public static final String DEFAULT_REPLY = "<CALL> DE <MYCALL> TNX FER CALL UR RST <RST_SENT> NAME <NAME> QTH <QTH> BK";
    public static final String DEFAULT_QRZ = "RIG <RIG> ANT <ANT> WX <WX> BK";
    public static final String DEFAULT_TU73 = "TU FER QSO 73 DE <MYCALL> SK";

    public static final String TEMPLATE_ID_CQ = "builtin_cq";
    public static final String TEMPLATE_ID_REPLY = "builtin_reply";
    public static final String TEMPLATE_ID_QRZ = "builtin_qrz";
    public static final String TEMPLATE_ID_TU73 = "builtin_tu73";

    private static final String PREFS_NAME = "cwcn_tx_templates";
    private static final String KEY_CQ = "template_cq";
    private static final String KEY_CQ_DX = "template_cq_dx";
    private static final String KEY_QRZ = "template_qrz";
    private static final String KEY_TU73 = "template_tu73";
    private static final String KEY_TEMPLATES_JSON = "templates_json";

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

    public String cqDxTemplate() {
        TxTemplateEntry entry = findTemplateById(TEMPLATE_ID_REPLY);
        return entry == null ? DEFAULT_REPLY : entry.body();
    }

    public String qrzTemplate() {
        TxTemplateEntry entry = findTemplateById(TEMPLATE_ID_QRZ);
        return entry == null ? DEFAULT_QRZ : entry.body();
    }

    public String tu73Template() {
        TxTemplateEntry entry = findTemplateById(TEMPLATE_ID_TU73);
        return entry == null ? DEFAULT_TU73 : entry.body();
    }

    public String resolveTemplate(String templateLabel) {
        TxTemplateEntry byId = findTemplateById(templateLabel);
        if (byId != null) {
            return byId.body();
        }
        if (TEMPLATE_REPLY.equals(templateLabel) || LEGACY_TEMPLATE_REPLY.equals(templateLabel)) {
            return cqDxTemplate();
        }
        if (TEMPLATE_QRZ.equals(templateLabel) || LEGACY_TEMPLATE_QRZ.equals(templateLabel)) {
            return qrzTemplate();
        }
        if (TEMPLATE_TU73.equals(templateLabel) || LEGACY_TEMPLATE_TU73.equals(templateLabel)) {
            return tu73Template();
        }
        TxTemplateEntry byName = findTemplateByName(templateLabel);
        if (byName != null) {
            return byName.body();
        }
        return cqTemplate();
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
                TEMPLATE_ID_REPLY,
                TEMPLATE_REPLY,
                normalizeOrDefault(preferences.getString(KEY_CQ_DX, null), DEFAULT_REPLY)
        ));
        migrated.add(new TxTemplateEntry(
                TEMPLATE_ID_QRZ,
                TEMPLATE_QRZ,
                normalizeOrDefault(preferences.getString(KEY_QRZ, null), DEFAULT_QRZ)
        ));
        migrated.add(new TxTemplateEntry(
                TEMPLATE_ID_TU73,
                TEMPLATE_TU73,
                normalizeOrDefault(preferences.getString(KEY_TU73, null), DEFAULT_TU73)
        ));
        return migrated;
    }

    private List<TxTemplateEntry> defaultTemplates() {
        ArrayList<TxTemplateEntry> defaults = new ArrayList<>();
        defaults.add(new TxTemplateEntry(TEMPLATE_ID_CQ, TEMPLATE_CQ, DEFAULT_CQ));
        defaults.add(new TxTemplateEntry(TEMPLATE_ID_REPLY, TEMPLATE_REPLY, DEFAULT_REPLY));
        defaults.add(new TxTemplateEntry(TEMPLATE_ID_QRZ, TEMPLATE_QRZ, DEFAULT_QRZ));
        defaults.add(new TxTemplateEntry(TEMPLATE_ID_TU73, TEMPLATE_TU73, DEFAULT_TU73));
        return defaults;
    }

    private void persistTemplates(List<TxTemplateEntry> entries) {
        JSONArray array = new JSONArray();
        if (entries != null) {
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
        }
        if (array.length() == 0) {
            for (TxTemplateEntry entry : defaultTemplates()) {
                JSONObject object = new JSONObject();
                try {
                    object.put("id", entry.id());
                    object.put("name", entry.name());
                    object.put("body", entry.body());
                    array.put(object);
                } catch (JSONException ignored) {
                }
            }
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
