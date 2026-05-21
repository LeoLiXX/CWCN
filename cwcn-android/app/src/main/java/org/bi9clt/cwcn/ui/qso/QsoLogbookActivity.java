package org.bi9clt.cwcn.ui.qso;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.bi9clt.cwcn.BuildConfig;
import org.bi9clt.cwcn.R;
import org.bi9clt.cwcn.core.callsign.CallsignMetadataResolver;
import org.bi9clt.cwcn.core.log.ConfirmedLogQuery;
import org.bi9clt.cwcn.core.log.ConfirmedQsoLog;
import org.bi9clt.cwcn.core.log.CwTextLogFileWriter;
import org.bi9clt.cwcn.core.log.LocalLogRepository;
import org.bi9clt.cwcn.core.log.LogbookExportRequest;
import org.bi9clt.cwcn.core.log.LogbookExportSupport;
import org.bi9clt.cwcn.core.log.LogbookLanExportServer;
import org.bi9clt.cwcn.databinding.ActivityQsoLogbookBinding;
import org.bi9clt.cwcn.databinding.DialogLogbookFilterBinding;
import org.bi9clt.cwcn.ui.navigation.FormalBottomNavStyler;
import org.bi9clt.cwcn.ui.operate.OperateActivity;
import org.bi9clt.cwcn.ui.settings.SettingsActivity;
import org.bi9clt.cwcn.ui.spectrum.SpectrumActivity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public final class QsoLogbookActivity extends AppCompatActivity implements QsoLogbookAdapter.Callbacks {
    private static final String PREFS_NAME = "logbook_ui";
    private static final String KEY_VIEW_MODE = "view_mode";
    private static final long SEARCH_DEBOUNCE_MS = 300L;

    private static final int MENU_LAN_START = 1;
    private static final int MENU_LAN_SHOW = 2;
    private static final int MENU_LAN_COPY = 3;
    private static final int MENU_LAN_STOP = 4;

    private ActivityQsoLogbookBinding binding;
    private LocalLogRepository localLogRepository;
    private CallsignMetadataResolver callsignMetadataResolver;
    private QsoLogbookAdapter adapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable searchCommitRunnable = this::commitSearchFilter;
    private final Paint swipeBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint swipeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private QsoLogbookAdapter.ViewMode viewMode = QsoLogbookAdapter.ViewMode.DETAILED;
    private LogbookExportRequest.ConfirmationScope confirmationScope =
            LogbookExportRequest.ConfirmationScope.ALL;
    private LogbookExportRequest.TimeRangeScope timeRangeScope =
            LogbookExportRequest.TimeRangeScope.ALL;
    private String searchFilter;
    private List<ConfirmedQsoLog> displayedLogs = Collections.emptyList();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityQsoLogbookBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        localLogRepository = new LocalLogRepository(this);
        callsignMetadataResolver = new CallsignMetadataResolver(this);
        viewMode = loadPersistedViewMode();
        adapter = new QsoLogbookAdapter(this, callsignMetadataResolver);
        initSwipePaint();
        setupRecyclerView();
        setupActions();
        binding.searchEditText.setText("");
        reloadLogs();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadLogs();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacks(searchCommitRunnable);
        super.onDestroy();
    }

    private void initSwipePaint() {
        swipeTextPaint.setTextSize(spToPx(11f));
        swipeTextPaint.setFakeBoldText(true);
    }

    private void setupRecyclerView() {
        binding.logRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.logRecyclerView.setAdapter(adapter);
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(
                new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
                    @Override
                    public boolean onMove(
                            @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder,
                            @NonNull RecyclerView.ViewHolder target
                    ) {
                        return false;
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                        int position = viewHolder.getBindingAdapterPosition();
                        if (position < 0 || position >= displayedLogs.size()) {
                            reloadLogs();
                            return;
                        }
                        ConfirmedQsoLog log = displayedLogs.get(position);
                        if (direction == ItemTouchHelper.RIGHT) {
                            toggleConfirmed(log);
                            return;
                        }
                        adapter.notifyItemChanged(position);
                        confirmDelete(log);
                    }

                    @Override
                    public void onChildDraw(
                            @NonNull Canvas canvas,
                            @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder,
                            float dX,
                            float dY,
                            int actionState,
                            boolean isCurrentlyActive
                    ) {
                        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX != 0f) {
                            drawSwipeFeedback(canvas, viewHolder.itemView, dX);
                        }
                        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                    }
                }
        );
        itemTouchHelper.attachToRecyclerView(binding.logRecyclerView);
    }

    private void setupActions() {
        binding.exportButton.setOnClickListener(this::showExportMenu);
        binding.shareButton.setOnClickListener(view -> showShareDialog());
        binding.newButton.setOnClickListener(view -> openNewEditor());
        binding.viewModeButton.setOnClickListener(view -> toggleViewMode());
        binding.filterButton.setOnClickListener(view -> showFilterDialog());
        binding.bottomNavView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_nav_operate) {
                startActivity(new Intent(this, OperateActivity.class));
                return true;
            }
            if (itemId == R.id.menu_nav_spectrum) {
                startActivity(new Intent(this, SpectrumActivity.class));
                return true;
            }
            if (itemId == R.id.menu_nav_logbook) {
                return true;
            }
            if (itemId == R.id.menu_nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });
        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                mainHandler.removeCallbacks(searchCommitRunnable);
                mainHandler.postDelayed(searchCommitRunnable, SEARCH_DEBOUNCE_MS);
            }
        });
    }

    private void commitSearchFilter() {
        String updatedFilter = normalizeFilter(binding.searchEditText.getText());
        if (equalsNullable(searchFilter, updatedFilter)) {
            return;
        }
        searchFilter = updatedFilter;
        reloadLogs();
    }

    private void toggleViewMode() {
        viewMode = viewMode == QsoLogbookAdapter.ViewMode.DETAILED
                ? QsoLogbookAdapter.ViewMode.COMPACT
                : QsoLogbookAdapter.ViewMode.DETAILED;
        persistViewMode(viewMode);
        adapter.submit(displayedLogs, viewMode);
        refreshChrome();
    }

    private void showFilterDialog() {
        DialogLogbookFilterBinding dialogBinding = DialogLogbookFilterBinding.inflate(getLayoutInflater());
        bindScopeSelection(dialogBinding, buildCurrentScopeRequest());
        new AlertDialog.Builder(this)
                .setTitle("高级筛选")
                .setView(dialogBinding.getRoot())
                .setNegativeButton("重置", (dialog, which) -> {
                    confirmationScope = LogbookExportRequest.ConfirmationScope.ALL;
                    timeRangeScope = LogbookExportRequest.TimeRangeScope.ALL;
                    reloadLogs();
                })
                .setNeutralButton("取消", null)
                .setPositiveButton("应用", (dialog, which) -> {
                    LogbookExportRequest request = readScopeSelection(dialogBinding);
                    confirmationScope = request.confirmationScope();
                    timeRangeScope = request.timeRangeScope();
                    reloadLogs();
                })
                .show();
    }

    private void showExportMenu(View anchorView) {
        PopupMenu popupMenu = new PopupMenu(this, anchorView);
        boolean running = LogbookLanExportServer.getInstance().isRunning();
        if (!running) {
            popupMenu.getMenu().add(0, MENU_LAN_START, 0, "启动局域网导出");
        } else {
            popupMenu.getMenu().add(0, MENU_LAN_SHOW, 0, "查看导出地址");
            popupMenu.getMenu().add(0, MENU_LAN_COPY, 1, "复制导出地址");
            popupMenu.getMenu().add(0, MENU_LAN_STOP, 2, "停止局域网导出");
        }
        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == MENU_LAN_START) {
                startLanExport();
                return true;
            }
            if (itemId == MENU_LAN_SHOW) {
                showLanAddressDialog();
                return true;
            }
            if (itemId == MENU_LAN_COPY) {
                copyLanAddressToClipboard();
                return true;
            }
            if (itemId == MENU_LAN_STOP) {
                stopLanExport();
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void showShareDialog() {
        showScopedActionDialog("分享文本", "分享", this::performTxtShare);
    }

    private void showScopedActionDialog(
            String title,
            String positiveLabel,
            ExportAction exportAction
    ) {
        DialogLogbookFilterBinding dialogBinding = DialogLogbookFilterBinding.inflate(getLayoutInflater());
        bindScopeSelection(dialogBinding, buildCurrentScopeRequest());
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(dialogBinding.getRoot())
                .setNegativeButton("取消", null)
                .setPositiveButton(positiveLabel, (dialog, which) ->
                        exportAction.run(readScopeSelection(dialogBinding)))
                .show();
    }

    private void reloadLogs() {
        List<ConfirmedQsoLog> queriedLogs = localLogRepository.queryConfirmedLogs(buildQuery());
        displayedLogs = applyLocalTimeRange(queriedLogs);
        adapter.submit(displayedLogs, viewMode);
        refreshChrome();
        FormalBottomNavStyler.apply(binding.bottomNavView, FormalBottomNavStyler.Page.LOGBOOK);
    }

    private void refreshChrome() {
        boolean filterActive = confirmationScope != LogbookExportRequest.ConfirmationScope.ALL
                || timeRangeScope != LogbookExportRequest.TimeRangeScope.ALL;
        boolean lanRunning = LogbookLanExportServer.getInstance().isRunning();
        binding.filterButton.setSelected(filterActive);
        binding.exportButton.setSelected(lanRunning);
        binding.viewModeButton.setSelected(viewMode == QsoLogbookAdapter.ViewMode.COMPACT);
        binding.viewModeButton.setImageResource(viewMode == QsoLogbookAdapter.ViewMode.DETAILED
                ? R.drawable.ic_ui_view_compact
                : R.drawable.ic_ui_view_detail);
        binding.viewModeButton.setContentDescription(viewMode == QsoLogbookAdapter.ViewMode.DETAILED
                ? "切换到简约模式"
                : "切换到详细模式");
        binding.emptyStateText.setVisibility(displayedLogs.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private ConfirmedLogQuery buildQuery() {
        return new ConfirmedLogQuery(
                searchFilter,
                null,
                null,
                null,
                mapConfirmationScope(confirmationScope),
                ConfirmedLogQuery.SortOrder.QSO_TIME_DESC
        );
    }

    private List<ConfirmedQsoLog> applyLocalTimeRange(List<ConfirmedQsoLog> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        if (timeRangeScope == LogbookExportRequest.TimeRangeScope.ALL) {
            return source;
        }

        long startEpochMs = timeRangeStartEpochMs(timeRangeScope);
        ArrayList<ConfirmedQsoLog> filtered = new ArrayList<>();
        for (ConfirmedQsoLog log : source) {
            if (log == null) {
                continue;
            }
            if (log.qsoTimeUtcEpochMs() >= startEpochMs) {
                filtered.add(log);
            }
        }
        return filtered;
    }

    private long timeRangeStartEpochMs(LogbookExportRequest.TimeRangeScope scope) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (scope == LogbookExportRequest.TimeRangeScope.THIS_MONTH) {
            calendar.set(Calendar.DAY_OF_MONTH, 1);
        }
        return calendar.getTimeInMillis();
    }

    private void performTxtShare(LogbookExportRequest request) {
        List<ConfirmedQsoLog> logs = loadLogsForRequest(request);
        if (logs.isEmpty()) {
            Toast.makeText(this, "没有可分享的 QSO 记录。", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File shareFile = CwTextLogFileWriter.export(
                    this,
                    logs,
                    LogbookExportSupport.buildRequestLabel(request)
            );
            Uri shareUri = FileProvider.getUriForFile(
                    this,
                    BuildConfig.APPLICATION_ID + ".fileprovider",
                    shareFile
            );
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "CWCN QSO 日志");
            shareIntent.putExtra(Intent.EXTRA_STREAM, shareUri);
            shareIntent.setClipData(ClipData.newUri(getContentResolver(), "CWCN QSO 日志", shareUri));
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "分享 QSO 记录"));
        } catch (IOException exception) {
            Toast.makeText(this, "文本分享失败。", Toast.LENGTH_SHORT).show();
        }
    }

    private void startLanExport() {
        try {
            LogbookLanExportServer.getInstance().start(getApplicationContext(), BuildConfig.VERSION_NAME);
            refreshChrome();
            showLanAddressDialog();
        } catch (IOException exception) {
            Toast.makeText(this, "无法启动局域网导出。", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopLanExport() {
        LogbookLanExportServer.getInstance().stop();
        refreshChrome();
        Toast.makeText(this, "已停止局域网导出。", Toast.LENGTH_SHORT).show();
    }

    private void showLanAddressDialog() {
        String baseUrl = LogbookLanExportServer.getInstance().getBaseUrl();
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            Toast.makeText(this, "局域网导出尚未启动。", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("局域网导出")
                .setMessage("在同一局域网浏览器打开下面地址下载 ADI：\n\n" + baseUrl)
                .setNegativeButton("关闭", null)
                .setPositiveButton("复制地址", (dialog, which) -> copyLanAddressToClipboard())
                .show();
    }

    private void copyLanAddressToClipboard() {
        String baseUrl = LogbookLanExportServer.getInstance().getBaseUrl();
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            Toast.makeText(this, "局域网导出尚未启动。", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("CWCN 局域网导出地址", baseUrl));
        }
        Toast.makeText(this, "导出地址已复制。", Toast.LENGTH_SHORT).show();
    }

    private List<ConfirmedQsoLog> loadLogsForRequest(LogbookExportRequest request) {
        return LogbookExportSupport.loadLogs(localLogRepository, request);
    }

    private void openNewEditor() {
        Intent intent = new Intent(this, QsoEditorActivity.class);
        intent.putExtra(QsoEditorActivity.EXTRA_START_FRESH, true);
        startActivity(intent);
    }

    @Override
    public void onLogClicked(ConfirmedQsoLog log) {
        openEditorForLog(log);
    }

    @Override
    public void onLogLongPressed(View anchorView, ConfirmedQsoLog log) {
        PopupMenu popupMenu = new PopupMenu(this, anchorView);
        popupMenu.getMenu().add(0, 1, 0, log.manualConfirmed() ? "标记未确认" : "标记已确认");
        popupMenu.getMenu().add(0, 2, 1, "编辑");
        popupMenu.getMenu().add(0, 3, 2, "删除");
        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == 1) {
                toggleConfirmed(log);
                return true;
            }
            if (itemId == 2) {
                openEditorForLog(log);
                return true;
            }
            if (itemId == 3) {
                confirmDelete(log);
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void toggleConfirmed(ConfirmedQsoLog log) {
        boolean updated = localLogRepository.updateConfirmedLog(
                log.id(),
                log.withManualConfirmed(!log.manualConfirmed())
        );
        if (!updated) {
            Toast.makeText(this, "无法更新确认状态。", Toast.LENGTH_SHORT).show();
            return;
        }
        reloadLogs();
        Toast.makeText(
                this,
                log.manualConfirmed() ? "已取消确认。" : "已标记为已确认。",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void confirmDelete(ConfirmedQsoLog log) {
        new AlertDialog.Builder(this)
                .setTitle("删除这条 QSO 记录？")
                .setMessage("确定删除 " + safeText(log.remoteCallsign()) + " 的记录吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> deleteLog(log))
                .show();
    }

    private void deleteLog(ConfirmedQsoLog log) {
        boolean deleted = localLogRepository.deleteConfirmedLog(log.id());
        if (!deleted) {
            Toast.makeText(this, "无法删除这条 QSO 记录。", Toast.LENGTH_SHORT).show();
            return;
        }
        reloadLogs();
        Toast.makeText(this, "已删除记录。", Toast.LENGTH_SHORT).show();
    }

    private void openEditorForLog(ConfirmedQsoLog log) {
        Intent intent = new Intent(this, QsoEditorActivity.class);
        intent.putExtra(QsoEditorActivity.EXTRA_CONFIRMED_LOG_ID, log.id());
        startActivity(intent);
    }

    private void bindScopeSelection(
            DialogLogbookFilterBinding dialogBinding,
            LogbookExportRequest request
    ) {
        switch (request.confirmationScope()) {
            case CONFIRMED:
                dialogBinding.confirmationConfirmedRadio.setChecked(true);
                break;
            case UNCONFIRMED:
                dialogBinding.confirmationUnconfirmedRadio.setChecked(true);
                break;
            case ALL:
            default:
                dialogBinding.confirmationAllRadio.setChecked(true);
                break;
        }

        switch (request.timeRangeScope()) {
            case TODAY:
                dialogBinding.timeRangeTodayRadio.setChecked(true);
                break;
            case THIS_MONTH:
                dialogBinding.timeRangeMonthRadio.setChecked(true);
                break;
            case ALL:
            default:
                dialogBinding.timeRangeAllRadio.setChecked(true);
                break;
        }
    }

    private LogbookExportRequest readScopeSelection(DialogLogbookFilterBinding dialogBinding) {
        return new LogbookExportRequest(
                readConfirmationScope(dialogBinding),
                readTimeRangeScope(dialogBinding)
        );
    }

    private LogbookExportRequest.ConfirmationScope readConfirmationScope(DialogLogbookFilterBinding dialogBinding) {
        int checkedId = dialogBinding.confirmationRadioGroup.getCheckedRadioButtonId();
        if (checkedId == R.id.confirmationConfirmedRadio) {
            return LogbookExportRequest.ConfirmationScope.CONFIRMED;
        }
        if (checkedId == R.id.confirmationUnconfirmedRadio) {
            return LogbookExportRequest.ConfirmationScope.UNCONFIRMED;
        }
        return LogbookExportRequest.ConfirmationScope.ALL;
    }

    private LogbookExportRequest.TimeRangeScope readTimeRangeScope(DialogLogbookFilterBinding dialogBinding) {
        int checkedId = dialogBinding.timeRangeRadioGroup.getCheckedRadioButtonId();
        if (checkedId == R.id.timeRangeTodayRadio) {
            return LogbookExportRequest.TimeRangeScope.TODAY;
        }
        if (checkedId == R.id.timeRangeMonthRadio) {
            return LogbookExportRequest.TimeRangeScope.THIS_MONTH;
        }
        return LogbookExportRequest.TimeRangeScope.ALL;
    }

    private ConfirmedLogQuery.ConfirmationFilter mapConfirmationScope(
            LogbookExportRequest.ConfirmationScope scope
    ) {
        switch (scope) {
            case CONFIRMED:
                return ConfirmedLogQuery.ConfirmationFilter.MANUALLY_CONFIRMED;
            case UNCONFIRMED:
                return ConfirmedLogQuery.ConfirmationFilter.UNCONFIRMED;
            case ALL:
            default:
                return ConfirmedLogQuery.ConfirmationFilter.ALL;
        }
    }

    private LogbookExportRequest buildCurrentScopeRequest() {
        return new LogbookExportRequest(confirmationScope, timeRangeScope);
    }

    private QsoLogbookAdapter.ViewMode loadPersistedViewMode() {
        String raw = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_VIEW_MODE, QsoLogbookAdapter.ViewMode.DETAILED.name());
        try {
            return QsoLogbookAdapter.ViewMode.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return QsoLogbookAdapter.ViewMode.DETAILED;
        }
    }

    private void persistViewMode(QsoLogbookAdapter.ViewMode updatedViewMode) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_VIEW_MODE, updatedViewMode.name())
                .apply();
    }

    @Nullable
    private String normalizeFilter(@Nullable Editable editable) {
        if (editable == null) {
            return null;
        }
        String value = editable.toString().trim();
        return value.isEmpty() ? null : value;
    }

    private boolean equalsNullable(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private void drawSwipeFeedback(Canvas canvas, View itemView, float dX) {
        boolean rightSwipe = dX > 0f;
        float threshold = itemView.getWidth() * 0.5f;
        boolean ready = Math.abs(dX) >= threshold;

        int backgroundColor = ContextCompat.getColor(
                this,
                rightSwipe
                        ? (ready ? R.color.cwcn_accent : R.color.cwcn_chip_active)
                        : (ready ? R.color.cwcn_warning : R.color.cwcn_secondary_variant)
        );
        int textColor = ContextCompat.getColor(
                this,
                ready ? R.color.cwcn_screen_background : R.color.cwcn_title
        );
        swipeBackgroundPaint.setColor(backgroundColor);
        swipeTextPaint.setColor(textColor);
        swipeTextPaint.setTextAlign(rightSwipe ? Paint.Align.LEFT : Paint.Align.RIGHT);

        RectF rect = rightSwipe
                ? new RectF(itemView.getLeft(), itemView.getTop(), itemView.getLeft() + dX, itemView.getBottom())
                : new RectF(itemView.getRight() + dX, itemView.getTop(), itemView.getRight(), itemView.getBottom());
        canvas.drawRoundRect(rect, dpToPx(5f), dpToPx(5f), swipeBackgroundPaint);

        String label = rightSwipe
                ? (ready ? "松手确认" : "右滑确认")
                : (ready ? "松手删除" : "左滑删除");
        float textX = rightSwipe
                ? itemView.getLeft() + dpToPx(14f)
                : itemView.getRight() - dpToPx(14f);
        float textY = itemView.getTop()
                + (itemView.getHeight() / 2f)
                - ((swipeTextPaint.descent() + swipeTextPaint.ascent()) / 2f);
        canvas.drawText(label, textX, textY, swipeTextPaint);
    }

    private float dpToPx(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private float spToPx(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }

    private String safeText(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value.trim();
    }

    private interface ExportAction {
        void run(LogbookExportRequest request);
    }
}
