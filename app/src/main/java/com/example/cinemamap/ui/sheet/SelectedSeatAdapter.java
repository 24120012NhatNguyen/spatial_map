package com.example.cinemamap.ui.sheet;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cinemamap.R;
import com.example.cinemamap.data.PriceFormatter;
import com.example.cinemamap.data.Seat;
import com.example.cinemamap.ui.SeatTypeColors;

/**
 * Danh sách ghế đã chọn trong Bottom Sheet. DiffUtil so khớp theo mã ghế nên danh sách
 * không bao giờ có mục trùng lặp và chỉ dòng thay đổi mới được vẽ lại.
 */
final class SelectedSeatAdapter extends ListAdapter<SelectedSeatItem, SelectedSeatAdapter.Holder> {

    interface Listener {
        void onLocate(@NonNull Seat seat);

        void onRemove(@NonNull Seat seat);
    }

    private static final DiffUtil.ItemCallback<SelectedSeatItem> DIFF = new DiffUtil.ItemCallback<SelectedSeatItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull SelectedSeatItem a, @NonNull SelectedSeatItem b) {
            return a.seat.getId().equals(b.seat.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull SelectedSeatItem a, @NonNull SelectedSeatItem b) {
            return a.matchesFilter == b.matchesFilter; // Seat là bất biến.
        }
    };

    private final Listener listener;

    SelectedSeatAdapter(@NonNull Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_selected_seat, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(getItem(position));
    }

    final class Holder extends RecyclerView.ViewHolder {
        private final View swatch;
        private final TextView title;
        private final TextView detail;
        private final TextView filteredTag;
        private final ImageButton remove;

        Holder(@NonNull View itemView) {
            super(itemView);
            swatch = itemView.findViewById(R.id.seat_swatch);
            title = itemView.findViewById(R.id.seat_title);
            detail = itemView.findViewById(R.id.seat_detail);
            filteredTag = itemView.findViewById(R.id.seat_filtered_tag);
            remove = itemView.findViewById(R.id.seat_remove);
            // Đọc vị trí lúc chạm (không giữ Seat trong lambda) để luôn đúng sau khi danh sách đổi.
            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onLocate(getItem(pos).seat);
                }
            });
            remove.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onRemove(getItem(pos).seat);
                }
            });
        }

        void bind(@NonNull SelectedSeatItem item) {
            Seat seat = item.seat;
            ViewCompat.setBackgroundTintList(swatch, ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.getContext(), SeatTypeColors.of(seat.getType()))));
            title.setText(itemView.getContext().getString(R.string.seat_item_title,
                    seat.getId(), seat.getType().getDisplayName()));
            detail.setText(itemView.getContext().getString(R.string.seat_item_detail,
                    seat.getRow(), seat.getNumber(), PriceFormatter.format(seat.getPrice())));
            filteredTag.setVisibility(item.matchesFilter ? View.GONE : View.VISIBLE);
            remove.setContentDescription(itemView.getContext().getString(R.string.seat_item_remove, seat.getId()));
            itemView.setContentDescription(itemView.getContext().getString(R.string.seat_item_locate, seat.getId()));
        }
    }
}
