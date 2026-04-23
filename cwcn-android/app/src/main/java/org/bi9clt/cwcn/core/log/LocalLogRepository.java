package org.bi9clt.cwcn.core.log;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.bi9clt.cwcn.core.eval.CwFixtureEvaluationResult;
import org.bi9clt.cwcn.core.qso.QsoDraftSnapshot;
import org.bi9clt.cwcn.core.qso.QsoPhase;
import org.bi9clt.cwcn.core.qso.QsoStateEvent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class LocalLogRepository {
    private static final String PREFS_NAME = "cwcn_logs";
    private static final String KEY_CURRENT_DRAFT = "current_draft";
    private static final String KEY_CONFIRMED_LOGS = "confirmed_logs";
    private static final String KEY_CONFIRMED_LOGS_MIGRATED = "confirmed_logs_migrated_v1";
    private static final String KEY_ACTIVE_DRAFT_MIGRATED = "active_draft_migrated_v2";
    private static final long ACTIVE_DRAFT_ID = 1L;

    private final SharedPreferences preferences;
    private final ConfirmedLogDatabaseHelper databaseHelper;

    public LocalLogRepository(Context context) {
        Context appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        databaseHelper = new ConfirmedLogDatabaseHelper(appContext);
        migrateLegacyConfirmedLogsIfNeeded();
        migrateLegacyDraftIfNeeded();
    }

    public synchronized void saveDraft(QsoDraftSnapshot snapshot) {
        if (snapshot == null) {
            clearDraft();
            return;
        }

        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        db.insertWithOnConflict(
                ConfirmedLogDatabaseHelper.TABLE_ACTIVE_DRAFT,
                null,
                toDraftContentValues(snapshot),
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    public synchronized QsoDraftSnapshot loadDraft() {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        try (Cursor cursor = db.query(
                ConfirmedLogDatabaseHelper.TABLE_ACTIVE_DRAFT,
                null,
                ConfirmedLogDatabaseHelper.COLUMN_ID + " = ?",
                new String[]{String.valueOf(ACTIVE_DRAFT_ID)},
                null,
                null,
                null
        )) {
            if (cursor.moveToFirst()) {
                return readDraft(cursor);
            }
        }
        return null;
    }

    public synchronized void clearDraft() {
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        db.delete(
                ConfirmedLogDatabaseHelper.TABLE_ACTIVE_DRAFT,
                ConfirmedLogDatabaseHelper.COLUMN_ID + " = ?",
                new String[]{String.valueOf(ACTIVE_DRAFT_ID)}
        );
    }

    public synchronized ConfirmedQsoLog confirmDraft(QsoDraftSnapshot snapshot, long confirmedAtEpochMs) {
        ConfirmedQsoLog log = ConfirmedQsoLog.fromDraft(snapshot, confirmedAtEpochMs);
        long id = insertConfirmedLog(log);
        return withId(log, id);
    }

    public synchronized List<ConfirmedQsoLog> loadConfirmedLogs() {
        return queryConfirmedLogs(ConfirmedLogQuery.defaultQuery());
    }

    public synchronized ConfirmedQsoLog loadConfirmedLogById(long id) {
        if (id <= 0L) {
            return null;
        }

        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        try (Cursor cursor = db.query(
                ConfirmedLogDatabaseHelper.TABLE_CONFIRMED_LOGS,
                null,
                ConfirmedLogDatabaseHelper.COLUMN_ID + " = ?",
                new String[]{String.valueOf(id)},
                null,
                null,
                null,
                "1"
        )) {
            if (cursor.moveToFirst()) {
                return readConfirmedLog(cursor);
            }
        }
        return null;
    }

    public synchronized List<ConfirmedQsoLog> queryConfirmedLogs(ConfirmedLogQuery query) {
        ArrayList<ConfirmedQsoLog> logs = new ArrayList<>();
        ConfirmedLogQuery safeQuery = query == null ? ConfirmedLogQuery.defaultQuery() : query;
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        QueryParts queryParts = buildConfirmedLogQueryParts(safeQuery);
        try (Cursor cursor = db.query(
                ConfirmedLogDatabaseHelper.TABLE_CONFIRMED_LOGS,
                null,
                queryParts.selection,
                queryParts.selectionArgs,
                null,
                null,
                queryParts.orderBy
        )) {
            while (cursor.moveToNext()) {
                logs.add(readConfirmedLog(cursor));
            }
        }
        return logs;
    }

    public synchronized AppOverviewSnapshot loadOverview() {
        QsoDraftSnapshot activeDraft = loadDraft();
        List<ConfirmedQsoLog> latestLogs = queryConfirmedLogs(
                new ConfirmedLogQuery(null, null, null, null, ConfirmedLogQuery.SortOrder.CONFIRMED_AT_DESC)
        );
        ConfirmedQsoLog latestConfirmedLog = latestLogs.isEmpty() ? null : latestLogs.get(0);
        int confirmedLogCount = countConfirmedLogs();
        int manualReviewLogCount = countConfirmedLogs(Boolean.TRUE);
        return new AppOverviewSnapshot(activeDraft, confirmedLogCount, manualReviewLogCount, latestConfirmedLog);
    }

    public synchronized boolean updateConfirmedLog(long id, ConfirmedQsoLog updatedLog) {
        if (id <= 0L || updatedLog == null) {
            return false;
        }

        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        int rowsUpdated = db.update(
                ConfirmedLogDatabaseHelper.TABLE_CONFIRMED_LOGS,
                toConfirmedLogContentValues(withId(updatedLog, id)),
                ConfirmedLogDatabaseHelper.COLUMN_ID + " = ?",
                new String[]{String.valueOf(id)}
        );
        return rowsUpdated > 0;
    }

    public synchronized boolean deleteConfirmedLog(long id) {
        if (id <= 0L) {
            return false;
        }

        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        int rowsDeleted = db.delete(
                ConfirmedLogDatabaseHelper.TABLE_CONFIRMED_LOGS,
                ConfirmedLogDatabaseHelper.COLUMN_ID + " = ?",
                new String[]{String.valueOf(id)}
        );
        return rowsDeleted > 0;
    }

    public synchronized void saveFixtureEvaluation(CwFixtureEvaluationResult evaluationResult) {
        if (evaluationResult == null) {
            return;
        }
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        db.insert(
                ConfirmedLogDatabaseHelper.TABLE_FIXTURE_EVALUATIONS,
                null,
                toFixtureEvaluationContentValues(evaluationResult)
        );
    }

    public synchronized List<CwFixtureEvaluationResult> loadRecentFixtureEvaluations(int limit) {
        ArrayList<CwFixtureEvaluationResult> evaluations = new ArrayList<>();
        int safeLimit = Math.max(1, limit);
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        try (Cursor cursor = db.query(
                ConfirmedLogDatabaseHelper.TABLE_FIXTURE_EVALUATIONS,
                null,
                null,
                null,
                null,
                null,
                ConfirmedLogDatabaseHelper.COLUMN_EVALUATED_AT_EPOCH_MS + " DESC",
                String.valueOf(safeLimit)
        )) {
            while (cursor.moveToNext()) {
                evaluations.add(readFixtureEvaluation(cursor));
            }
        }
        return evaluations;
    }

    private long insertConfirmedLog(ConfirmedQsoLog log) {
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        return db.insert(
                ConfirmedLogDatabaseHelper.TABLE_CONFIRMED_LOGS,
                null,
                toConfirmedLogContentValues(log)
        );
    }

    private int countConfirmedLogs() {
        return countConfirmedLogs(null);
    }

    private int countConfirmedLogs(Boolean reviewOnly) {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        String sql = "SELECT COUNT(*) FROM " + ConfirmedLogDatabaseHelper.TABLE_CONFIRMED_LOGS;
        String[] args = null;
        if (reviewOnly != null) {
            sql += " WHERE " + ConfirmedLogDatabaseHelper.COLUMN_NEED_MANUAL_REVIEW + " = ?";
            args = new String[]{reviewOnly ? "1" : "0"};
        }
        try (Cursor cursor = db.rawQuery(sql, args)) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        }
        return 0;
    }

    private ContentValues toConfirmedLogContentValues(ConfirmedQsoLog log) {
        ContentValues values = new ContentValues();
        if (log.id() > 0L) {
            values.put(ConfirmedLogDatabaseHelper.COLUMN_ID, log.id());
        }
        values.put(ConfirmedLogDatabaseHelper.COLUMN_REMOTE_CALLSIGN, log.remoteCallsign());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_QSO_DATE_UTC, log.qsoDateUtc());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_TIME_ON_UTC, log.timeOnUtc());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_MODE, log.mode());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_RST_SENT, log.rstSent());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_RST_RCVD, log.rstRcvd());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_NAME, log.name());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_QTH, log.qth());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_STATION_CALLSIGN, log.stationCallsign());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_PHASE, log.phase());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_NORMALIZED_TEXT, log.normalizedText());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_NEED_MANUAL_REVIEW, log.needManualReview() ? 1 : 0);
        values.put(ConfirmedLogDatabaseHelper.COLUMN_CONFIRMED_AT_EPOCH_MS, log.confirmedAtEpochMs());
        return values;
    }

    private ContentValues toDraftContentValues(QsoDraftSnapshot snapshot) {
        ContentValues values = new ContentValues();
        values.put(ConfirmedLogDatabaseHelper.COLUMN_ID, ACTIVE_DRAFT_ID);
        values.put(ConfirmedLogDatabaseHelper.COLUMN_PHASE, snapshot.phase().name());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_STATION_CALLSIGN_USED, snapshot.stationCallsignUsed());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_REMOTE_CALLSIGN_CANDIDATE, snapshot.remoteCallsignCandidate());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_RST_SENT_CANDIDATE, snapshot.rstSentCandidate());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_RST_RCVD_CANDIDATE, snapshot.rstRcvdCandidate());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_NAME_CANDIDATE, snapshot.nameCandidate());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_QTH_CANDIDATE, snapshot.qthCandidate());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_STATION_CALLSIGN_MANUALLY_SET, snapshot.stationCallsignManuallySet() ? 1 : 0);
        values.put(ConfirmedLogDatabaseHelper.COLUMN_REMOTE_CALLSIGN_MANUALLY_SET, snapshot.remoteCallsignManuallySet() ? 1 : 0);
        values.put(ConfirmedLogDatabaseHelper.COLUMN_RST_SENT_MANUALLY_SET, snapshot.rstSentManuallySet() ? 1 : 0);
        values.put(ConfirmedLogDatabaseHelper.COLUMN_RST_RCVD_MANUALLY_SET, snapshot.rstRcvdManuallySet() ? 1 : 0);
        values.put(ConfirmedLogDatabaseHelper.COLUMN_NAME_MANUALLY_SET, snapshot.nameManuallySet() ? 1 : 0);
        values.put(ConfirmedLogDatabaseHelper.COLUMN_QTH_MANUALLY_SET, snapshot.qthManuallySet() ? 1 : 0);
        values.put(ConfirmedLogDatabaseHelper.COLUMN_NORMALIZED_TEXT, snapshot.normalizedText());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_HINTS_JSON, serializeHints(snapshot.hints()));
        values.put(ConfirmedLogDatabaseHelper.COLUMN_READY_FOR_DRAFT_CONFIRMATION, snapshot.readyForDraftConfirmation() ? 1 : 0);
        values.put(ConfirmedLogDatabaseHelper.COLUMN_NEED_MANUAL_REVIEW, snapshot.needManualReview() ? 1 : 0);
        values.put(ConfirmedLogDatabaseHelper.COLUMN_UPDATED_AT_EPOCH_MS, snapshot.updatedAtEpochMs());
        if (snapshot.lastEvent() != null) {
            values.put(ConfirmedLogDatabaseHelper.COLUMN_LAST_EVENT_TIMESTAMP_MS, snapshot.lastEvent().timestampMs());
            values.put(ConfirmedLogDatabaseHelper.COLUMN_LAST_EVENT_PHASE, snapshot.lastEvent().phase().name());
            values.put(ConfirmedLogDatabaseHelper.COLUMN_LAST_EVENT_SUMMARY, snapshot.lastEvent().summary());
        } else {
            values.put(ConfirmedLogDatabaseHelper.COLUMN_LAST_EVENT_TIMESTAMP_MS, 0L);
            values.putNull(ConfirmedLogDatabaseHelper.COLUMN_LAST_EVENT_PHASE);
            values.putNull(ConfirmedLogDatabaseHelper.COLUMN_LAST_EVENT_SUMMARY);
        }
        return values;
    }

    private ContentValues toFixtureEvaluationContentValues(CwFixtureEvaluationResult result) {
        ContentValues values = new ContentValues();
        values.put(ConfirmedLogDatabaseHelper.COLUMN_SCENARIO_ID, result.scenarioId());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_SCENARIO_DISPLAY_NAME, result.scenarioDisplayName());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_EVALUATED_AT_EPOCH_MS, result.evaluatedAtEpochMs());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_COMPLETED, result.completed() ? 1 : 0);
        values.put(ConfirmedLogDatabaseHelper.COLUMN_PASSED, result.passed() ? 1 : 0);
        values.put(ConfirmedLogDatabaseHelper.COLUMN_EXACT_TEXT_MATCH, result.exactTextMatch() ? 1 : 0);
        values.put(ConfirmedLogDatabaseHelper.COLUMN_PRIMARY_CALLSIGN_SCORE, result.primaryCallsignScore());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_TEXT_TOKEN_RECALL, result.textTokenRecall());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_CALLSIGN_RECALL, result.callsignRecall());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_HINT_RECALL, result.hintRecall());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_QSO_SEMANTIC_SCORE, result.qsoSemanticScore());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_EXPECTED_NORMALIZED_TEXT, result.expectedNormalizedText());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_ACTUAL_NORMALIZED_TEXT, result.actualNormalizedText());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_EXPECTED_PHASE, result.expectedPhase());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_ACTUAL_PHASE, result.actualPhase());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_EXPECTED_RST_SENT, result.expectedRstSent());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_ACTUAL_RST_SENT, result.actualRstSent());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_EXPECTED_RST_RCVD, result.expectedRstRcvd());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_ACTUAL_RST_RCVD, result.actualRstRcvd());
        values.put(ConfirmedLogDatabaseHelper.COLUMN_ACTUAL_CALLSIGNS_JSON, serializeStringList(result.actualCallsigns()));
        values.put(ConfirmedLogDatabaseHelper.COLUMN_ACTUAL_HINTS_JSON, serializeStringList(result.actualHints()));
        values.put(ConfirmedLogDatabaseHelper.COLUMN_MISSING_TEXT_TOKENS_JSON, serializeStringList(result.missingTextTokens()));
        values.put(ConfirmedLogDatabaseHelper.COLUMN_MISSING_CALLSIGNS_JSON, serializeStringList(result.missingCallsigns()));
        values.put(ConfirmedLogDatabaseHelper.COLUMN_MISSING_HINTS_JSON, serializeStringList(result.missingHints()));
        values.put(ConfirmedLogDatabaseHelper.COLUMN_FAILURE_REASONS_JSON, serializeStringList(result.failureReasons()));
        return values;
    }

    private QsoDraftSnapshot readDraft(Cursor cursor) {
        return new QsoDraftSnapshot(
                safePhase(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_PHASE))),
                emptyToNull(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_STATION_CALLSIGN_USED))),
                emptyToNull(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_REMOTE_CALLSIGN_CANDIDATE))),
                emptyToNull(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_RST_SENT_CANDIDATE))),
                emptyToNull(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_RST_RCVD_CANDIDATE))),
                emptyToNull(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_NAME_CANDIDATE))),
                emptyToNull(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_QTH_CANDIDATE))),
                cursor.getInt(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_STATION_CALLSIGN_MANUALLY_SET)) != 0,
                cursor.getInt(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_REMOTE_CALLSIGN_MANUALLY_SET)) != 0,
                cursor.getInt(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_RST_SENT_MANUALLY_SET)) != 0,
                cursor.getInt(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_RST_RCVD_MANUALLY_SET)) != 0,
                cursor.getInt(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_NAME_MANUALLY_SET)) != 0,
                cursor.getInt(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_QTH_MANUALLY_SET)) != 0,
                emptyToNullAsEmpty(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_NORMALIZED_TEXT))),
                deserializeHints(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_HINTS_JSON))),
                cursor.getInt(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_READY_FOR_DRAFT_CONFIRMATION)) != 0,
                cursor.getInt(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_NEED_MANUAL_REVIEW)) != 0,
                cursor.getLong(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_UPDATED_AT_EPOCH_MS)),
                readDraftLastEvent(cursor)
        );
    }

    private QsoStateEvent readDraftLastEvent(Cursor cursor) {
        long timestampMs = cursor.getLong(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_LAST_EVENT_TIMESTAMP_MS));
        String phase = cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_LAST_EVENT_PHASE));
        String summary = cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_LAST_EVENT_SUMMARY));
        if (timestampMs <= 0L && (phase == null || phase.isEmpty()) && (summary == null || summary.isEmpty())) {
            return null;
        }
        return new QsoStateEvent(timestampMs, safePhase(phase), emptyToNull(summary));
    }

    private ConfirmedQsoLog readConfirmedLog(Cursor cursor) {
        return new ConfirmedQsoLog(
                cursor.getLong(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_ID)),
                emptyToNull(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_REMOTE_CALLSIGN))),
                emptyToNull(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_QSO_DATE_UTC))),
                emptyToNull(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_TIME_ON_UTC))),
                emptyToNull(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_MODE))),
                emptyToNull(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_RST_SENT))),
                emptyToNull(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_RST_RCVD))),
                emptyToNull(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_NAME))),
                emptyToNull(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_QTH))),
                emptyToNull(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_STATION_CALLSIGN))),
                emptyToNull(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_PHASE))),
                emptyToNull(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_NORMALIZED_TEXT))),
                cursor.getInt(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_NEED_MANUAL_REVIEW)) != 0,
                cursor.getLong(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_CONFIRMED_AT_EPOCH_MS))
        );
    }

    private CwFixtureEvaluationResult readFixtureEvaluation(Cursor cursor) {
        return new CwFixtureEvaluationResult(
                emptyToNullAsEmpty(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_SCENARIO_ID))),
                emptyToNullAsEmpty(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_SCENARIO_DISPLAY_NAME))),
                cursor.getLong(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_EVALUATED_AT_EPOCH_MS)),
                cursor.getInt(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_COMPLETED)) != 0,
                cursor.getInt(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_PASSED)) != 0,
                cursor.getInt(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_EXACT_TEXT_MATCH)) != 0,
                cursor.getDouble(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_PRIMARY_CALLSIGN_SCORE)),
                cursor.getDouble(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_TEXT_TOKEN_RECALL)),
                cursor.getDouble(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_CALLSIGN_RECALL)),
                cursor.getDouble(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_HINT_RECALL)),
                cursor.getDouble(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_QSO_SEMANTIC_SCORE)),
                emptyToNullAsEmpty(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_EXPECTED_NORMALIZED_TEXT))),
                emptyToNullAsEmpty(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_ACTUAL_NORMALIZED_TEXT))),
                emptyToNull(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_EXPECTED_PHASE))),
                emptyToNull(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_ACTUAL_PHASE))),
                emptyToNull(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_EXPECTED_RST_SENT))),
                emptyToNull(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_ACTUAL_RST_SENT))),
                emptyToNull(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_EXPECTED_RST_RCVD))),
                emptyToNull(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_ACTUAL_RST_RCVD))),
                deserializeStringList(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_ACTUAL_CALLSIGNS_JSON))),
                deserializeStringList(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_ACTUAL_HINTS_JSON))),
                deserializeStringList(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_MISSING_TEXT_TOKENS_JSON))),
                deserializeStringList(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_MISSING_CALLSIGNS_JSON))),
                deserializeStringList(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_MISSING_HINTS_JSON))),
                deserializeStringList(cursor.getString(cursor.getColumnIndexOrThrow(ConfirmedLogDatabaseHelper.COLUMN_FAILURE_REASONS_JSON)))
        );
    }

    private void migrateLegacyConfirmedLogsIfNeeded() {
        boolean alreadyMigrated = preferences.getBoolean(KEY_CONFIRMED_LOGS_MIGRATED, false);
        if (alreadyMigrated) {
            return;
        }

        String raw = preferences.getString(KEY_CONFIRMED_LOGS, null);
        if (raw == null || raw.isEmpty()) {
            preferences.edit()
                    .putBoolean(KEY_CONFIRMED_LOGS_MIGRATED, true)
                    .remove(KEY_CONFIRMED_LOGS)
                    .apply();
            return;
        }

        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object != null) {
                    ConfirmedQsoLog log = deserializeLegacyLog(object);
                    db.insert(
                            ConfirmedLogDatabaseHelper.TABLE_CONFIRMED_LOGS,
                            null,
                            toConfirmedLogContentValues(log)
                    );
                }
            }
            db.setTransactionSuccessful();
            preferences.edit()
                    .putBoolean(KEY_CONFIRMED_LOGS_MIGRATED, true)
                    .remove(KEY_CONFIRMED_LOGS)
                    .apply();
        } catch (JSONException exception) {
            preferences.edit().remove(KEY_CONFIRMED_LOGS).apply();
        } finally {
            db.endTransaction();
        }
    }

    private void migrateLegacyDraftIfNeeded() {
        boolean alreadyMigrated = preferences.getBoolean(KEY_ACTIVE_DRAFT_MIGRATED, false);
        if (alreadyMigrated) {
            return;
        }

        String raw = preferences.getString(KEY_CURRENT_DRAFT, null);
        if (raw == null || raw.isEmpty()) {
            preferences.edit()
                    .putBoolean(KEY_ACTIVE_DRAFT_MIGRATED, true)
                    .remove(KEY_CURRENT_DRAFT)
                    .apply();
            return;
        }

        try {
            QsoDraftSnapshot snapshot = deserializeDraftJson(new JSONObject(raw));
            saveDraft(snapshot);
            preferences.edit()
                    .putBoolean(KEY_ACTIVE_DRAFT_MIGRATED, true)
                    .remove(KEY_CURRENT_DRAFT)
                    .apply();
        } catch (JSONException exception) {
            preferences.edit().remove(KEY_CURRENT_DRAFT).apply();
        }
    }

    private QsoDraftSnapshot deserializeDraftJson(JSONObject object) {
        JSONArray hintsArray = object.optJSONArray("hints");
        ArrayList<String> hints = new ArrayList<>();
        if (hintsArray != null) {
            for (int i = 0; i < hintsArray.length(); i++) {
                String hint = hintsArray.optString(i, null);
                if (hint != null && !hint.isEmpty()) {
                    hints.add(hint);
                }
            }
        }

        QsoStateEvent lastEvent = null;
        JSONObject eventObject = object.optJSONObject("lastEvent");
        if (eventObject != null) {
            lastEvent = new QsoStateEvent(
                    eventObject.optLong("timestampMs", 0L),
                    safePhase(eventObject.optString("phase", QsoPhase.IDLE.name())),
                    emptyToNull(eventObject.optString("summary", null))
            );
        }

        return new QsoDraftSnapshot(
                safePhase(object.optString("phase", QsoPhase.IDLE.name())),
                emptyToNull(object.optString("stationCallsignUsed", null)),
                emptyToNull(object.optString("remoteCallsignCandidate", null)),
                emptyToNull(object.optString("rstSentCandidate", null)),
                emptyToNull(object.optString("rstRcvdCandidate", null)),
                emptyToNull(object.optString("nameCandidate", null)),
                emptyToNull(object.optString("qthCandidate", null)),
                object.optBoolean("stationCallsignManuallySet", false),
                object.optBoolean("remoteCallsignManuallySet", false),
                object.optBoolean("rstSentManuallySet", false),
                object.optBoolean("rstRcvdManuallySet", false),
                object.optBoolean("nameManuallySet", false),
                object.optBoolean("qthManuallySet", false),
                object.optString("normalizedText", ""),
                hints,
                object.optBoolean("readyForDraftConfirmation", false),
                object.optBoolean("needManualReview", false),
                object.optLong("updatedAtEpochMs", 0L),
                lastEvent
        );
    }

    private ConfirmedQsoLog deserializeLegacyLog(JSONObject object) {
        return new ConfirmedQsoLog(
                0L,
                emptyToNull(object.optString("remoteCallsign", null)),
                emptyToNull(object.optString("qsoDateUtc", null)),
                emptyToNull(object.optString("timeOnUtc", null)),
                emptyToNull(object.optString("mode", "CW")),
                emptyToNull(object.optString("rstSent", null)),
                emptyToNull(object.optString("rstRcvd", null)),
                emptyToNull(object.optString("name", null)),
                emptyToNull(object.optString("qth", null)),
                emptyToNull(object.optString("stationCallsign", null)),
                emptyToNull(object.optString("phase", null)),
                emptyToNull(object.optString("normalizedText", null)),
                object.optBoolean("needManualReview", false),
                object.optLong("confirmedAtEpochMs", 0L)
        );
    }

    private String serializeHints(List<String> hints) {
        JSONArray array = new JSONArray();
        if (hints != null) {
            for (String hint : hints) {
                array.put(hint);
            }
        }
        return array.toString();
    }

    private String serializeStringList(List<String> values) {
        JSONArray array = new JSONArray();
        if (values != null) {
            for (String value : values) {
                array.put(value);
            }
        }
        return array.toString();
    }

    private List<String> deserializeHints(String rawHints) {
        ArrayList<String> hints = new ArrayList<>();
        if (rawHints == null || rawHints.isEmpty()) {
            return hints;
        }
        try {
            JSONArray array = new JSONArray(rawHints);
            for (int i = 0; i < array.length(); i++) {
                String hint = array.optString(i, null);
                if (hint != null && !hint.isEmpty()) {
                    hints.add(hint);
                }
            }
        } catch (JSONException ignored) {
            // Keep draft readable even if hint JSON is malformed.
        }
        return hints;
    }

    private List<String> deserializeStringList(String rawValues) {
        ArrayList<String> values = new ArrayList<>();
        if (rawValues == null || rawValues.isEmpty()) {
            return values;
        }
        try {
            JSONArray array = new JSONArray(rawValues);
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, null);
                if (value != null && !value.isEmpty()) {
                    values.add(value);
                }
            }
        } catch (JSONException ignored) {
            // Keep evaluation history readable even if one JSON blob is malformed.
        }
        return values;
    }

    private ConfirmedQsoLog withId(ConfirmedQsoLog log, long id) {
        return new ConfirmedQsoLog(
                id,
                log.remoteCallsign(),
                log.qsoDateUtc(),
                log.timeOnUtc(),
                log.mode(),
                log.rstSent(),
                log.rstRcvd(),
                log.name(),
                log.qth(),
                log.stationCallsign(),
                log.phase(),
                log.normalizedText(),
                log.needManualReview(),
                log.confirmedAtEpochMs()
        );
    }

    private QueryParts buildConfirmedLogQueryParts(ConfirmedLogQuery query) {
        ArrayList<String> clauses = new ArrayList<>();
        ArrayList<String> args = new ArrayList<>();

        String callsignFilter = normalizeFilter(query.callsignFilter());
        if (callsignFilter != null) {
            clauses.add("UPPER(" + ConfirmedLogDatabaseHelper.COLUMN_REMOTE_CALLSIGN + ") LIKE ?");
            args.add("%" + callsignFilter.toUpperCase() + "%");
        }

        if (query.reviewOnly() != null) {
            clauses.add(ConfirmedLogDatabaseHelper.COLUMN_NEED_MANUAL_REVIEW + " = ?");
            args.add(query.reviewOnly() ? "1" : "0");
        }

        String fromQsoDateUtc = normalizeFilter(query.fromQsoDateUtc());
        if (fromQsoDateUtc != null) {
            clauses.add(ConfirmedLogDatabaseHelper.COLUMN_QSO_DATE_UTC + " >= ?");
            args.add(fromQsoDateUtc);
        }

        String toQsoDateUtc = normalizeFilter(query.toQsoDateUtc());
        if (toQsoDateUtc != null) {
            clauses.add(ConfirmedLogDatabaseHelper.COLUMN_QSO_DATE_UTC + " <= ?");
            args.add(toQsoDateUtc);
        }

        String selection = clauses.isEmpty() ? null : String.join(" AND ", clauses);
        String[] selectionArgs = args.isEmpty() ? null : args.toArray(new String[0]);
        return new QueryParts(selection, selectionArgs, mapSortOrder(query.sortOrder()));
    }

    private String mapSortOrder(ConfirmedLogQuery.SortOrder sortOrder) {
        ConfirmedLogQuery.SortOrder safeSort = sortOrder == null
                ? ConfirmedLogQuery.SortOrder.CONFIRMED_AT_DESC
                : sortOrder;
        switch (safeSort) {
            case CONFIRMED_AT_ASC:
                return ConfirmedLogDatabaseHelper.COLUMN_CONFIRMED_AT_EPOCH_MS + " ASC";
            case CALLSIGN_ASC:
                return ConfirmedLogDatabaseHelper.COLUMN_REMOTE_CALLSIGN + " COLLATE NOCASE ASC, "
                        + ConfirmedLogDatabaseHelper.COLUMN_CONFIRMED_AT_EPOCH_MS + " DESC";
            case CALLSIGN_DESC:
                return ConfirmedLogDatabaseHelper.COLUMN_REMOTE_CALLSIGN + " COLLATE NOCASE DESC, "
                        + ConfirmedLogDatabaseHelper.COLUMN_CONFIRMED_AT_EPOCH_MS + " DESC";
            case CONFIRMED_AT_DESC:
            default:
                return ConfirmedLogDatabaseHelper.COLUMN_CONFIRMED_AT_EPOCH_MS + " DESC";
        }
    }

    private String normalizeFilter(String rawFilter) {
        if (rawFilter == null) {
            return null;
        }
        String trimmed = rawFilter.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String emptyToNull(String value) {
        if (value == null || value.isEmpty() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }

    private String emptyToNullAsEmpty(String value) {
        return value == null ? "" : value;
    }

    private QsoPhase safePhase(String raw) {
        try {
            return QsoPhase.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return QsoPhase.IDLE;
        }
    }

    private static final class QueryParts {
        private final String selection;
        private final String[] selectionArgs;
        private final String orderBy;

        private QueryParts(String selection, String[] selectionArgs, String orderBy) {
            this.selection = selection;
            this.selectionArgs = selectionArgs;
            this.orderBy = orderBy;
        }
    }
}
