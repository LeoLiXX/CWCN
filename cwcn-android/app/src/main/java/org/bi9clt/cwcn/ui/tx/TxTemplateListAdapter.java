package org.bi9clt.cwcn.ui.tx;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.bi9clt.cwcn.R;
import org.bi9clt.cwcn.core.app.TxTemplateEntry;
import org.bi9clt.cwcn.databinding.ItemTxTemplateCardBinding;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TxTemplateListAdapter extends RecyclerView.Adapter<TxTemplateListAdapter.TemplateViewHolder> {
    interface Callbacks {
        void onTemplateClicked(TxTemplateEntry entry);

        void onTemplateLongPressed(View anchorView, TxTemplateEntry entry);
    }

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("<[A-Z_]+>");

    private final Callbacks callbacks;
    private final ArrayList<TxTemplateEntry> items = new ArrayList<>();

    TxTemplateListAdapter(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    void submit(List<TxTemplateEntry> templates) {
        items.clear();
        if (templates != null) {
            items.addAll(templates);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TemplateViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemTxTemplateCardBinding binding = ItemTxTemplateCardBinding.inflate(inflater, parent, false);
        return new TemplateViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull TemplateViewHolder holder, int position) {
        holder.bind(items.get(position), position, callbacks);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class TemplateViewHolder extends RecyclerView.ViewHolder {
        private final ItemTxTemplateCardBinding binding;

        TemplateViewHolder(ItemTxTemplateCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(TxTemplateEntry entry, int position, Callbacks callbacks) {
            binding.cardRoot.setBackgroundResource(position % 2 == 0
                    ? R.drawable.logbook_card_even_background
                    : R.drawable.logbook_card_odd_background);
            binding.templateNameText.setText(safe(entry.name()));
            binding.templateMetaText.setText(renderMeta(entry.body()));
            binding.templatePreviewText.setText(renderPreview(entry.body()));
            binding.templatePlaceholderText.setText(renderPlaceholders(entry.body()));
            binding.getRoot().setOnClickListener(view -> callbacks.onTemplateClicked(entry));
            binding.getRoot().setOnLongClickListener(view -> {
                callbacks.onTemplateLongPressed(view, entry);
                return true;
            });
        }

        private String renderMeta(String body) {
            int length = body == null ? 0 : body.trim().length();
            int placeholderCount = extractPlaceholders(body).size();
            return binding.getRoot().getContext().getString(
                    R.string.tx_template_card_meta,
                    length,
                    placeholderCount
            );
        }

        private String renderPreview(String body) {
            String normalized = safe(body).replace('\n', ' ');
            return normalized.isEmpty() ? "-" : normalized;
        }

        private String renderPlaceholders(String body) {
            List<String> placeholders = extractPlaceholders(body);
            if (placeholders.isEmpty()) {
                return binding.getRoot().getContext().getString(R.string.tx_template_card_placeholders_none);
            }
            return binding.getRoot().getContext().getString(
                    R.string.tx_template_card_placeholders,
                    String.join(" ", placeholders)
            );
        }

        private List<String> extractPlaceholders(String body) {
            String normalized = safe(body).toUpperCase(Locale.US);
            LinkedHashSet<String> values = new LinkedHashSet<>();
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(normalized);
            while (matcher.find()) {
                values.add(matcher.group());
            }
            return new ArrayList<>(values);
        }

        private String safe(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
