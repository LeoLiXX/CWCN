package org.bi9clt.cwcn.core.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

public final class TxTemplateStore {
    public static final String TEMPLATE_CQ = "CQ";
    public static final String TEMPLATE_REPLY = "应答";
    public static final String TEMPLATE_QRZ = "QRZ";
    public static final String TEMPLATE_TU73 = "TU73";

    public static final String DEFAULT_CQ = "CQ CQ DE <MYCALL> K";
    public static final String DEFAULT_REPLY = "<CALL> DE <MYCALL> TNX FER CALL UR RST <RST_SENT> NAME <NAME> QTH <QTH> BK";
    public static final String DEFAULT_QRZ = "RIG <RIG> ANT <ANT> WX <WX> BK";
    public static final String DEFAULT_TU73 = "TU FER QSO 73 DE <MYCALL> SK";

    private static final String PREFS_NAME = "cwcn_tx_templates";
    private static final String KEY_CQ = "template_cq";
    private static final String KEY_CQ_DX = "template_cq_dx";
    private static final String KEY_QRZ = "template_qrz";
    private static final String KEY_TU73 = "template_tu73";

    private final SharedPreferences preferences;

    public TxTemplateStore(Context context) {
        Context appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String cqTemplate() {
        return normalizeOrDefault(preferences.getString(KEY_CQ, null), DEFAULT_CQ);
    }

    public String cqDxTemplate() {
        return normalizeOrDefault(preferences.getString(KEY_CQ_DX, null), DEFAULT_REPLY);
    }

    public String qrzTemplate() {
        return normalizeOrDefault(preferences.getString(KEY_QRZ, null), DEFAULT_QRZ);
    }

    public String tu73Template() {
        return normalizeOrDefault(preferences.getString(KEY_TU73, null), DEFAULT_TU73);
    }

    public void save(
            @Nullable String cqTemplate,
            @Nullable String cqDxTemplate,
            @Nullable String qrzTemplate,
            @Nullable String tu73Template
    ) {
        SharedPreferences.Editor editor = preferences.edit();
        putRequiredString(editor, KEY_CQ, cqTemplate, DEFAULT_CQ);
        putRequiredString(editor, KEY_CQ_DX, cqDxTemplate, DEFAULT_REPLY);
        putRequiredString(editor, KEY_QRZ, qrzTemplate, DEFAULT_QRZ);
        putRequiredString(editor, KEY_TU73, tu73Template, DEFAULT_TU73);
        editor.apply();
    }

    public String resolveTemplate(String templateLabel) {
        if (TEMPLATE_REPLY.equals(templateLabel)) {
            return cqDxTemplate();
        }
        if (TEMPLATE_QRZ.equals(templateLabel)) {
            return qrzTemplate();
        }
        if (TEMPLATE_TU73.equals(templateLabel)) {
            return tu73Template();
        }
        return cqTemplate();
    }

    private void putRequiredString(
            SharedPreferences.Editor editor,
            String key,
            @Nullable String value,
            String fallback
    ) {
        editor.putString(key, normalizeOrDefault(value, fallback));
    }

    private String normalizeOrDefault(@Nullable String value, String fallback) {
        String normalized = normalize(value);
        return normalized == null ? fallback : normalized;
    }

    @Nullable
    private String normalize(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
