package org.bi9clt.cwcn.ui.qso;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.bi9clt.cwcn.R;
import org.bi9clt.cwcn.core.callsign.CallsignMetadata;
import org.bi9clt.cwcn.core.callsign.CallsignMetadataResolver;
import org.bi9clt.cwcn.core.log.ConfirmedQsoLog;
import org.bi9clt.cwcn.core.log.LogDisplayFormatter;
import org.bi9clt.cwcn.databinding.ItemQsoLogbookCardBinding;

import java.util.ArrayList;
import java.util.List;
final class QsoLogbookAdapter extends RecyclerView.Adapter<QsoLogbookAdapter.LogViewHolder> {
    interface Callbacks {
        void onLogClicked(ConfirmedQsoLog log);

        void onLogLongPressed(View anchorView, ConfirmedQsoLog log);
    }

    enum ViewMode {
        DETAILED,
        COMPACT
    }

    private final Callbacks callbacks;
    private final CallsignMetadataResolver callsignMetadataResolver;
    private final ArrayList<ConfirmedQsoLog> items = new ArrayList<>();
    private ViewMode viewMode = ViewMode.DETAILED;

    QsoLogbookAdapter(Callbacks callbacks, CallsignMetadataResolver callsignMetadataResolver) {
        this.callbacks = callbacks;
        this.callsignMetadataResolver = callsignMetadataResolver;
    }

    void submit(List<ConfirmedQsoLog> logs, ViewMode updatedViewMode) {
        items.clear();
        if (logs != null) {
            items.addAll(logs);
        }
        viewMode = updatedViewMode == null ? ViewMode.DETAILED : updatedViewMode;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemQsoLogbookCardBinding binding = ItemQsoLogbookCardBinding.inflate(inflater, parent, false);
        return new LogViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        holder.bind(items.get(position), position, viewMode, callbacks, callsignMetadataResolver);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class LogViewHolder extends RecyclerView.ViewHolder {
        private final ItemQsoLogbookCardBinding binding;

        LogViewHolder(ItemQsoLogbookCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(
                ConfirmedQsoLog log,
                int position,
                ViewMode viewMode,
                Callbacks callbacks,
                CallsignMetadataResolver callsignMetadataResolver
        ) {
            binding.cardRoot.setBackgroundResource(position % 2 == 0
                    ? R.drawable.logbook_card_even_background
                    : R.drawable.logbook_card_odd_background);

            CallsignMetadata metadata = callsignMetadataResolver.resolve(log.remoteCallsign());
            boolean detailed = viewMode == ViewMode.DETAILED;
            binding.detailedGroup.setVisibility(detailed ? View.VISIBLE : View.GONE);
            binding.compactGroup.setVisibility(detailed ? View.GONE : View.VISIBLE);

            bindDetailed(log, metadata);
            bindCompact(log, metadata);

            binding.getRoot().setOnClickListener(view -> callbacks.onLogClicked(log));
            binding.getRoot().setOnLongClickListener(view -> {
                callbacks.onLogLongPressed(view, log);
                return true;
            });
        }

        private void bindDetailed(ConfirmedQsoLog log, CallsignMetadata metadata) {
            binding.detailCallsignText.setText(safeText(log.remoteCallsign()));
            binding.detailRemoteGridText.setText(binding.getRoot().getContext().getString(
                    R.string.qso_logbook_card_grid,
                    safeField(log.remoteGrid())
            ));
            binding.detailStationCallsignText.setText(safeText(log.stationCallsign()));
            binding.detailStationGridText.setText(binding.getRoot().getContext().getString(
                    R.string.qso_logbook_card_grid,
                    safeField(log.stationGrid())
            ));
            bindConfirmation(binding.detailConfirmationText, log.manualConfirmed());

            binding.detailTimeText.setText(binding.getRoot().getContext().getString(
                    R.string.qso_logbook_card_time,
                    safeDateTime(log.qsoTimeUtcEpochMs())
            ));
            binding.detailMetaText.setText(renderDetailedMeta(metadata));
            binding.detailRstText.setText(binding.getRoot().getContext().getString(
                    R.string.qso_logbook_card_rst,
                    safeField(log.rstRcvd()),
                    safeField(log.rstSent())
            ));
            binding.detailModeText.setText(binding.getRoot().getContext().getString(
                    R.string.qso_logbook_card_mode,
                    safeField(log.mode())
            ));

            binding.detailBandText.setText(binding.getRoot().getContext().getString(
                    R.string.qso_logbook_card_band,
                    safeBand(log.frequencyHz())
            ));
            binding.detailFrequencyText.setText(binding.getRoot().getContext().getString(
                    R.string.qso_logbook_card_frequency,
                    safeFrequencyNoSpace(log.frequencyHz())
            ));
            binding.detailCountryText.setText(renderLocation(metadata, log.qth()));
            binding.detailCommentText.setText(renderDetailedComment(log));
        }

        private void bindCompact(ConfirmedQsoLog log, CallsignMetadata metadata) {
            binding.compactCallsignText.setText(safeText(log.remoteCallsign()));
            bindConfirmation(binding.compactConfirmationText, log.manualConfirmed());

            binding.compactRecentTimeText.setText(binding.getRoot().getContext().getString(
                    R.string.qso_logbook_card_recent_time,
                    renderCompactDate(log.qsoTimeUtcEpochMs())
            ));
            binding.compactModeText.setText(binding.getRoot().getContext().getString(
                    R.string.qso_logbook_card_mode_compact,
                    safeField(log.mode())
            ));
            binding.compactMetaText.setText(renderCompactMeta(metadata));

            binding.compactBandFrequencyText.setText(renderCompactBandFrequency(log.frequencyHz()));
            binding.compactGridDistanceText.setText(renderCompactGridDistance(log));
            binding.compactCountryText.setText(renderLocation(metadata, log.qth()));
        }

        private void bindConfirmation(TextView target, boolean confirmed) {
            target.setText(confirmed
                    ? R.string.qso_logbook_card_confirmed
                    : R.string.qso_logbook_card_unconfirmed);
            target.setTextColor(ContextCompat.getColor(
                    binding.getRoot().getContext(),
                    confirmed ? R.color.cwcn_accent : R.color.cwcn_warning
            ));
        }

        private String renderDetailedMeta(CallsignMetadata metadata) {
            String dxcc = metadata == null ? "-" : safeField(metadata.dxccPrefix());
            String itu = metadata != null && metadata.ituZone() > 0 ? String.valueOf(metadata.ituZone()) : "-";
            String cq = metadata != null && metadata.cqZone() > 0 ? String.valueOf(metadata.cqZone()) : "-";
            return binding.getRoot().getContext().getString(
                    R.string.qso_logbook_card_meta_detailed,
                    dxcc,
                    itu,
                    cq
            );
        }

        private String renderCompactMeta(CallsignMetadata metadata) {
            String dxcc = metadata == null ? "-" : safeField(metadata.dxccPrefix());
            String itu = metadata != null && metadata.ituZone() > 0 ? String.valueOf(metadata.ituZone()) : "-";
            String cq = metadata != null && metadata.cqZone() > 0 ? String.valueOf(metadata.cqZone()) : "-";
            return binding.getRoot().getContext().getString(
                    R.string.qso_logbook_card_meta_compact,
                    dxcc,
                    itu,
                    cq
            );
        }

        private String renderCompactBandFrequency(long frequencyHz) {
            String band = safeBand(frequencyHz);
            String frequency = safeFrequencyNoSpace(frequencyHz);
            if ("-".equals(band) && "-".equals(frequency)) {
                return "-";
            }
            if ("-".equals(frequency)) {
                return band;
            }
            if ("-".equals(band)) {
                return frequency;
            }
            return band + "(" + frequency + ")";
        }

        private String renderCompactGridDistance(ConfirmedQsoLog log) {
            String grid = trimToNull(log.remoteGrid());
            String distance = trimToNull(renderDistanceZh(log.distanceKm()));
            if (grid != null && distance != null) {
                return binding.getRoot().getContext().getString(
                        R.string.qso_logbook_card_grid_distance,
                        grid,
                        distance
                );
            }
            if (grid != null) {
                return binding.getRoot().getContext().getString(
                        R.string.qso_logbook_card_grid_only,
                        grid
                );
            }
            if (distance != null) {
                return distance;
            }
            return binding.getRoot().getContext().getString(R.string.qso_logbook_card_grid_empty);
        }

        private String renderLocation(CallsignMetadata metadata, String qthRaw) {
            String qth = trimToNull(qthRaw);
            if (qth != null) {
                return qth;
            }
            String country = metadata == null ? null : trimToNull(metadata.displayCountry());
            return country == null ? "-" : country;
        }

        private String renderDetailedComment(ConfirmedQsoLog log) {
            ArrayList<String> parts = new ArrayList<>();
            String distance = trimToNull(renderDistanceComment(log.distanceKm()));
            if (distance != null) {
                parts.add(distance);
            }
            addIfPresent(parts, log.comment());
            return parts.isEmpty() ? "-" : String.join("  |  ", parts);
        }

        private void addIfPresent(List<String> parts, String value) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                parts.add(trimmed);
            }
        }

        private String renderCompactDate(long epochMs) {
            String full = safeDateTime(epochMs);
            if (full.length() >= 10 && !"-".equals(full)) {
                return full.substring(0, 10);
            }
            return full;
        }

        private String safeBand(long frequencyHz) {
            String exact = trimToNull(LogDisplayFormatter.formatBand(frequencyHz));
            if (exact != null) {
                return exact;
            }
            if (frequencyHz <= 0L) {
                return "-";
            }
            int[] bands = new int[]{160, 80, 60, 40, 30, 20, 17, 15, 12, 10, 6, 2};
            double meters = 299_792_458d / (double) frequencyHz;
            int bestBand = bands[0];
            double bestDiff = Math.abs(meters - bands[0]);
            for (int candidate : bands) {
                double diff = Math.abs(meters - candidate);
                if (diff < bestDiff) {
                    bestDiff = diff;
                    bestBand = candidate;
                }
            }
            return bestDiff <= Math.max(1.0d, bestBand * 0.35d) ? bestBand + "m" : "-";
        }

        private String safeFrequencyNoSpace(long frequencyHz) {
            String frequency = LogDisplayFormatter.formatFrequency(frequencyHz);
            if (frequency == null) {
                return "-";
            }
            return safeField(frequency.replace(" MHz", "MHz"));
        }

        private String renderDistanceZh(Double distanceKm) {
            if (distanceKm == null || distanceKm <= 0.0d) {
                return "-";
            }
            return binding.getRoot().getContext().getString(
                    R.string.qso_logbook_distance_km,
                    distanceKm
            );
        }

        private String renderDistanceComment(Double distanceKm) {
            if (distanceKm == null || distanceKm <= 0.0d) {
                return null;
            }
            return binding.getRoot().getContext().getString(
                    R.string.qso_logbook_distance_comment,
                    distanceKm
            );
        }

        private String safeDateTime(long epochMs) {
            return safeField(LogDisplayFormatter.formatLocalDateTime(epochMs));
        }

        private String safeField(String value) {
            String trimmed = trimToNull(value);
            return trimmed == null ? "-" : trimmed;
        }

        private String trimToNull(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            if (trimmed.isEmpty() || "-".equals(trimmed)) {
                return null;
            }
            return trimmed;
        }

        private String safeText(String value) {
            return safeField(value);
        }
    }
}
