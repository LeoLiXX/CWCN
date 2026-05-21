package org.bi9clt.cwcn.core.log;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public final class ConfirmedLogDatabaseHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "cwcn_logs.db";
    public static final int DATABASE_VERSION = 6;

    public static final String TABLE_CONFIRMED_LOGS = "confirmed_logs";
    public static final String TABLE_ACTIVE_DRAFT = "active_draft";
    public static final String TABLE_FIXTURE_EVALUATIONS = "fixture_evaluations";

    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_REMOTE_CALLSIGN = "remote_callsign";
    public static final String COLUMN_STATION_CALLSIGN = "station_callsign";
    public static final String COLUMN_QSO_TIME_UTC_EPOCH_MS = "qso_time_utc_epoch_ms";
    public static final String COLUMN_FREQUENCY_HZ = "frequency_hz";
    public static final String COLUMN_RST_SENT = "rst_sent";
    public static final String COLUMN_RST_RCVD = "rst_rcvd";
    public static final String COLUMN_REMOTE_GRID = "remote_grid";
    public static final String COLUMN_STATION_GRID = "station_grid";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_QTH = "qth";
    public static final String COLUMN_COMMENT = "comment";
    public static final String COLUMN_MANUAL_CONFIRMED = "manual_confirmed";
    public static final String COLUMN_MODE = "mode";
    public static final String COLUMN_PHASE = "phase";
    public static final String COLUMN_NORMALIZED_TEXT = "normalized_text";
    public static final String COLUMN_NEED_MANUAL_REVIEW = "need_manual_review";
    public static final String COLUMN_CONFIRMED_AT_EPOCH_MS = "confirmed_at_epoch_ms";
    public static final String COLUMN_QSO_DATE_UTC = "qso_date_utc";
    public static final String COLUMN_TIME_ON_UTC = "time_on_utc";

    public static final String COLUMN_STATION_CALLSIGN_USED = "station_callsign_used";
    public static final String COLUMN_REMOTE_CALLSIGN_CANDIDATE = "remote_callsign_candidate";
    public static final String COLUMN_RST_SENT_CANDIDATE = "rst_sent_candidate";
    public static final String COLUMN_RST_RCVD_CANDIDATE = "rst_rcvd_candidate";
    public static final String COLUMN_NAME_CANDIDATE = "name_candidate";
    public static final String COLUMN_QTH_CANDIDATE = "qth_candidate";
    public static final String COLUMN_STATION_CALLSIGN_MANUALLY_SET = "station_callsign_manually_set";
    public static final String COLUMN_REMOTE_CALLSIGN_MANUALLY_SET = "remote_callsign_manually_set";
    public static final String COLUMN_RST_SENT_MANUALLY_SET = "rst_sent_manually_set";
    public static final String COLUMN_RST_RCVD_MANUALLY_SET = "rst_rcvd_manually_set";
    public static final String COLUMN_NAME_MANUALLY_SET = "name_manually_set";
    public static final String COLUMN_QTH_MANUALLY_SET = "qth_manually_set";
    public static final String COLUMN_HINTS_JSON = "hints_json";
    public static final String COLUMN_READY_FOR_DRAFT_CONFIRMATION = "ready_for_draft_confirmation";
    public static final String COLUMN_UPDATED_AT_EPOCH_MS = "updated_at_epoch_ms";
    public static final String COLUMN_LAST_EVENT_TIMESTAMP_MS = "last_event_timestamp_ms";
    public static final String COLUMN_LAST_EVENT_PHASE = "last_event_phase";
    public static final String COLUMN_LAST_EVENT_SUMMARY = "last_event_summary";

    public static final String COLUMN_SCENARIO_ID = "scenario_id";
    public static final String COLUMN_SCENARIO_DISPLAY_NAME = "scenario_display_name";
    public static final String COLUMN_EVALUATED_AT_EPOCH_MS = "evaluated_at_epoch_ms";
    public static final String COLUMN_COMPLETED = "completed";
    public static final String COLUMN_PASSED = "passed";
    public static final String COLUMN_EXACT_TEXT_MATCH = "exact_text_match";
    public static final String COLUMN_PRIMARY_CALLSIGN_SCORE = "primary_callsign_score";
    public static final String COLUMN_TEXT_TOKEN_RECALL = "text_token_recall";
    public static final String COLUMN_CALLSIGN_RECALL = "callsign_recall";
    public static final String COLUMN_HINT_RECALL = "hint_recall";
    public static final String COLUMN_QSO_SEMANTIC_SCORE = "qso_semantic_score";
    public static final String COLUMN_EXPECTED_NORMALIZED_TEXT = "expected_normalized_text";
    public static final String COLUMN_ACTUAL_NORMALIZED_TEXT = "actual_normalized_text";
    public static final String COLUMN_EXPECTED_PHASE = "expected_phase";
    public static final String COLUMN_ACTUAL_PHASE = "actual_phase";
    public static final String COLUMN_EXPECTED_RST_SENT = "expected_rst_sent";
    public static final String COLUMN_ACTUAL_RST_SENT = "actual_rst_sent";
    public static final String COLUMN_EXPECTED_RST_RCVD = "expected_rst_rcvd";
    public static final String COLUMN_ACTUAL_RST_RCVD = "actual_rst_rcvd";
    public static final String COLUMN_ACTUAL_CALLSIGNS_JSON = "actual_callsigns_json";
    public static final String COLUMN_ACTUAL_HINTS_JSON = "actual_hints_json";
    public static final String COLUMN_MISSING_TEXT_TOKENS_JSON = "missing_text_tokens_json";
    public static final String COLUMN_MISSING_CALLSIGNS_JSON = "missing_callsigns_json";
    public static final String COLUMN_MISSING_HINTS_JSON = "missing_hints_json";
    public static final String COLUMN_FAILURE_REASONS_JSON = "failure_reasons_json";

    private static final String CREATE_CONFIRMED_LOGS_TABLE =
            "CREATE TABLE " + TABLE_CONFIRMED_LOGS + " ("
                    + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COLUMN_REMOTE_CALLSIGN + " TEXT, "
                    + COLUMN_STATION_CALLSIGN + " TEXT, "
                    + COLUMN_QSO_TIME_UTC_EPOCH_MS + " INTEGER NOT NULL DEFAULT 0, "
                    + COLUMN_FREQUENCY_HZ + " INTEGER NOT NULL DEFAULT 0, "
                    + COLUMN_RST_SENT + " TEXT, "
                    + COLUMN_RST_RCVD + " TEXT, "
                    + COLUMN_REMOTE_GRID + " TEXT, "
                    + COLUMN_STATION_GRID + " TEXT, "
                    + COLUMN_NAME + " TEXT, "
                    + COLUMN_QTH + " TEXT, "
                    + COLUMN_COMMENT + " TEXT, "
                    + COLUMN_MANUAL_CONFIRMED + " INTEGER NOT NULL DEFAULT 0, "
                    + COLUMN_MODE + " TEXT, "
                    + COLUMN_PHASE + " TEXT, "
                    + COLUMN_NORMALIZED_TEXT + " TEXT, "
                    + COLUMN_NEED_MANUAL_REVIEW + " INTEGER NOT NULL DEFAULT 0, "
                    + COLUMN_CONFIRMED_AT_EPOCH_MS + " INTEGER NOT NULL DEFAULT 0, "
                    + COLUMN_QSO_DATE_UTC + " TEXT, "
                    + COLUMN_TIME_ON_UTC + " TEXT"
                    + ")";

    private static final String CREATE_ACTIVE_DRAFT_TABLE =
            "CREATE TABLE " + TABLE_ACTIVE_DRAFT + " ("
                    + COLUMN_ID + " INTEGER PRIMARY KEY, "
                    + COLUMN_PHASE + " TEXT, "
                    + COLUMN_STATION_CALLSIGN_USED + " TEXT, "
                    + COLUMN_REMOTE_CALLSIGN_CANDIDATE + " TEXT, "
                    + COLUMN_RST_SENT_CANDIDATE + " TEXT, "
                    + COLUMN_RST_RCVD_CANDIDATE + " TEXT, "
                    + COLUMN_NAME_CANDIDATE + " TEXT, "
                    + COLUMN_QTH_CANDIDATE + " TEXT, "
                    + COLUMN_STATION_CALLSIGN_MANUALLY_SET + " INTEGER NOT NULL DEFAULT 0, "
                    + COLUMN_REMOTE_CALLSIGN_MANUALLY_SET + " INTEGER NOT NULL DEFAULT 0, "
                    + COLUMN_RST_SENT_MANUALLY_SET + " INTEGER NOT NULL DEFAULT 0, "
                    + COLUMN_RST_RCVD_MANUALLY_SET + " INTEGER NOT NULL DEFAULT 0, "
                    + COLUMN_NAME_MANUALLY_SET + " INTEGER NOT NULL DEFAULT 0, "
                    + COLUMN_QTH_MANUALLY_SET + " INTEGER NOT NULL DEFAULT 0, "
                    + COLUMN_NORMALIZED_TEXT + " TEXT, "
                    + COLUMN_HINTS_JSON + " TEXT, "
                    + COLUMN_READY_FOR_DRAFT_CONFIRMATION + " INTEGER NOT NULL DEFAULT 0, "
                    + COLUMN_NEED_MANUAL_REVIEW + " INTEGER NOT NULL DEFAULT 0, "
                    + COLUMN_UPDATED_AT_EPOCH_MS + " INTEGER NOT NULL DEFAULT 0, "
                    + COLUMN_LAST_EVENT_TIMESTAMP_MS + " INTEGER NOT NULL DEFAULT 0, "
                    + COLUMN_LAST_EVENT_PHASE + " TEXT, "
                    + COLUMN_LAST_EVENT_SUMMARY + " TEXT"
                    + ")";

    private static final String CREATE_FIXTURE_EVALUATIONS_TABLE =
            "CREATE TABLE " + TABLE_FIXTURE_EVALUATIONS + " ("
                    + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COLUMN_SCENARIO_ID + " TEXT NOT NULL, "
                    + COLUMN_SCENARIO_DISPLAY_NAME + " TEXT NOT NULL, "
                    + COLUMN_EVALUATED_AT_EPOCH_MS + " INTEGER NOT NULL DEFAULT 0, "
                    + COLUMN_COMPLETED + " INTEGER NOT NULL DEFAULT 0, "
                    + COLUMN_PASSED + " INTEGER NOT NULL DEFAULT 0, "
                    + COLUMN_EXACT_TEXT_MATCH + " INTEGER NOT NULL DEFAULT 0, "
                    + COLUMN_PRIMARY_CALLSIGN_SCORE + " REAL NOT NULL DEFAULT 0, "
                    + COLUMN_TEXT_TOKEN_RECALL + " REAL NOT NULL DEFAULT 0, "
                    + COLUMN_CALLSIGN_RECALL + " REAL NOT NULL DEFAULT 0, "
                    + COLUMN_HINT_RECALL + " REAL NOT NULL DEFAULT 0, "
                    + COLUMN_QSO_SEMANTIC_SCORE + " REAL NOT NULL DEFAULT 0, "
                    + COLUMN_EXPECTED_NORMALIZED_TEXT + " TEXT, "
                    + COLUMN_ACTUAL_NORMALIZED_TEXT + " TEXT, "
                    + COLUMN_EXPECTED_PHASE + " TEXT, "
                    + COLUMN_ACTUAL_PHASE + " TEXT, "
                    + COLUMN_EXPECTED_RST_SENT + " TEXT, "
                    + COLUMN_ACTUAL_RST_SENT + " TEXT, "
                    + COLUMN_EXPECTED_RST_RCVD + " TEXT, "
                    + COLUMN_ACTUAL_RST_RCVD + " TEXT, "
                    + COLUMN_ACTUAL_CALLSIGNS_JSON + " TEXT, "
                    + COLUMN_ACTUAL_HINTS_JSON + " TEXT, "
                    + COLUMN_MISSING_TEXT_TOKENS_JSON + " TEXT, "
                    + COLUMN_MISSING_CALLSIGNS_JSON + " TEXT, "
                    + COLUMN_MISSING_HINTS_JSON + " TEXT, "
                    + COLUMN_FAILURE_REASONS_JSON + " TEXT"
                    + ")";

    public ConfirmedLogDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_CONFIRMED_LOGS_TABLE);
        db.execSQL(CREATE_ACTIVE_DRAFT_TABLE);
        db.execSQL(CREATE_FIXTURE_EVALUATIONS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL(CREATE_ACTIVE_DRAFT_TABLE);
        }
        if (oldVersion < 3) {
            db.execSQL(CREATE_FIXTURE_EVALUATIONS_TABLE);
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE " + TABLE_FIXTURE_EVALUATIONS
                    + " ADD COLUMN " + COLUMN_PRIMARY_CALLSIGN_SCORE + " REAL NOT NULL DEFAULT 0");
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE " + TABLE_FIXTURE_EVALUATIONS
                    + " ADD COLUMN " + COLUMN_QSO_SEMANTIC_SCORE + " REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE_FIXTURE_EVALUATIONS
                    + " ADD COLUMN " + COLUMN_EXPECTED_PHASE + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE_FIXTURE_EVALUATIONS
                    + " ADD COLUMN " + COLUMN_ACTUAL_PHASE + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE_FIXTURE_EVALUATIONS
                    + " ADD COLUMN " + COLUMN_EXPECTED_RST_SENT + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE_FIXTURE_EVALUATIONS
                    + " ADD COLUMN " + COLUMN_ACTUAL_RST_SENT + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE_FIXTURE_EVALUATIONS
                    + " ADD COLUMN " + COLUMN_EXPECTED_RST_RCVD + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE_FIXTURE_EVALUATIONS
                    + " ADD COLUMN " + COLUMN_ACTUAL_RST_RCVD + " TEXT");
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE " + TABLE_CONFIRMED_LOGS
                    + " ADD COLUMN " + COLUMN_QSO_TIME_UTC_EPOCH_MS + " INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE_CONFIRMED_LOGS
                    + " ADD COLUMN " + COLUMN_FREQUENCY_HZ + " INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE_CONFIRMED_LOGS
                    + " ADD COLUMN " + COLUMN_REMOTE_GRID + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE_CONFIRMED_LOGS
                    + " ADD COLUMN " + COLUMN_STATION_GRID + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE_CONFIRMED_LOGS
                    + " ADD COLUMN " + COLUMN_COMMENT + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE_CONFIRMED_LOGS
                    + " ADD COLUMN " + COLUMN_MANUAL_CONFIRMED + " INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE " + TABLE_CONFIRMED_LOGS
                    + " SET " + COLUMN_QSO_TIME_UTC_EPOCH_MS + " = " + COLUMN_CONFIRMED_AT_EPOCH_MS
                    + " WHERE " + COLUMN_QSO_TIME_UTC_EPOCH_MS + " = 0");
        }
    }
}
