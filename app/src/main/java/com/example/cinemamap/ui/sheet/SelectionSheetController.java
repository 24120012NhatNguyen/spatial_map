package com.example.cinemamap.ui.sheet;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cinemamap.R;
import com.example.cinemamap.data.PriceFormatter;
import com.example.cinemamap.data.Seat;
import com.example.cinemamap.state.FilterState;
import com.example.cinemamap.state.SeatMapViewModel;
import com.example.cinemamap.state.SelectionSnapshot;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Điều khiển Expandable Bottom Sheet. Không giữ trạng thái chọn riêng: mọi thứ hiển thị đều
 * dựng lại từ {@link SelectionSnapshot} và {@link FilterState} của ViewModel, còn thao tác của
 * người dùng (định vị, bỏ chọn, bỏ chọn tất cả) được báo ra ngoài qua {@link Callbacks}.
 */
public final class SelectionSheetController {

    public interface Callbacks {
        void onLocateSeat(@NonNull Seat seat);

        void onRemoveSeat(@NonNull Seat seat);

        void onClearAll();
    }

    private static final String KEY_REQUESTED_STATE = "selection_sheet_state";
    /** Màn hình dọc: khi sheet mở rộng, sơ đồ luôn còn lộ ra ít nhất chừng này (dp)... */
    private static final float MIN_VISIBLE_MAP_DP = 160f;
    /** ...và ít nhất tỷ lệ này của vùng sơ đồ. */
    private static final float MIN_VISIBLE_MAP_FRACTION = 0.4f;
    /** Cửa sổ thấp: sheet mở rộng vẫn đủ chỗ cho khoảng 2 dòng danh sách... */
    private static final float MIN_EXPANDED_LIST_DP = 120f;
    /** ...nhưng không bao giờ che hết sơ đồ: luôn chừa một dải tối thiểu. */
    private static final float MIN_MAP_STRIP_DP = 48f;

    private final AppCompatActivity activity;
    private final View sheet;
    private final View coordinator;
    private final BottomSheetBehavior<View> behavior;
    private final TextView summaryTitle;
    private final TextView summaryDetail;
    private final ImageView chevron;
    private final View clearAll;
    private final TextView emptyText;
    private final RecyclerView list;
    private final SelectedSeatAdapter adapter;
    private final boolean overlapsMap;
    private final View mapContainer;
    private final int peekHeight;
    private final float minVisibleMapPx;
    private final float minExpandedListPx;
    private final float minMapStripPx;
    private final OnBackPressedCallback collapseOnBack;
    /**
     * Trạng thái ổn định (mở rộng / thu gọn) người dùng yêu cầu gần nhất. Material khôi phục trạng
     * thái "đang trượt" thành thu gọn, nên tự lưu giá trị này để xoay giữa chừng không mất yêu cầu.
     */
    private int requestedState = BottomSheetBehavior.STATE_COLLAPSED;
    /** true khi sheet đang trượt do người dùng kéo tay (đích đến suy ra từ hướng trượt). */
    private boolean settlingFromDrag;
    private float lastSlideOffset;
    private boolean slidingUp;
    /** Danh sách đã gửi cho adapter gần nhất (có thể chưa áp dụng xong vì DiffUtil chạy nền). */
    private List<SelectedSeatItem> lastSubmitted = new ArrayList<>();

    @Nullable
    private SelectionSnapshot selection;
    @Nullable
    private FilterState filterState;
    private Set<String> previousIds = new HashSet<>();

    public SelectionSheetController(@NonNull AppCompatActivity activity, @NonNull Callbacks callbacks) {
        this.activity = activity;
        sheet = activity.findViewById(R.id.selection_sheet);
        coordinator = activity.findViewById(R.id.coordinator);
        behavior = BottomSheetBehavior.from(sheet);
        summaryTitle = activity.findViewById(R.id.summary_title);
        summaryDetail = activity.findViewById(R.id.summary_detail);
        chevron = activity.findViewById(R.id.sheet_chevron);
        clearAll = activity.findViewById(R.id.sheet_clear_all);
        emptyText = activity.findViewById(R.id.sheet_empty);
        list = activity.findViewById(R.id.selection_list);
        overlapsMap = activity.getResources().getBoolean(R.bool.sheet_overlaps_map);
        mapContainer = activity.findViewById(R.id.map_container);
        peekHeight = activity.getResources().getDimensionPixelSize(R.dimen.sheet_peek_height);
        float density = activity.getResources().getDisplayMetrics().density;
        minVisibleMapPx = MIN_VISIBLE_MAP_DP * density;
        minExpandedListPx = MIN_EXPANDED_LIST_DP * density;
        minMapStripPx = MIN_MAP_STRIP_DP * density;

        adapter = new SelectedSeatAdapter(new SelectedSeatAdapter.Listener() {
            @Override
            public void onLocate(@NonNull Seat seat) {
                // Màn hình dọc: thu gọn sheet để ghế được định vị không bị chính sheet che.
                collapseIfCoveringMap();
                callbacks.onLocateSeat(seat);
            }

            @Override
            public void onRemove(@NonNull Seat seat) {
                callbacks.onRemoveSeat(seat);
            }
        });
        list.setLayoutManager(new LinearLayoutManager(activity));
        list.setAdapter(adapter);
        clearAll.setOnClickListener(v -> callbacks.onClearAll());

        activity.findViewById(R.id.sheet_header).setOnClickListener(v -> toggle());
        collapseOnBack = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                setRequestedState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        };
        activity.getOnBackPressedDispatcher().addCallback(activity, collapseOnBack);

        behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_DRAGGING) {
                    settlingFromDrag = true;
                } else if (newState == BottomSheetBehavior.STATE_EXPANDED || newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    requestedState = newState; // Người dùng có thể kéo tay tới trạng thái này.
                    settlingFromDrag = false;
                }
                syncWithState();
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                if (slideOffset != lastSlideOffset) {
                    slidingUp = slideOffset > lastSlideOffset;
                    lastSlideOffset = slideOffset;
                }
                chevron.setRotation(180f * Math.max(0f, Math.min(1f, slideOffset)));
            }
        });
        // Trạng thái sheet được CoordinatorLayout tự khôi phục sau khi xoay (không gọi callback),
        // nên đồng bộ lại mỗi lần bố cục thay đổi; đồng thời cập nhật chiều cao tối đa.
        coordinator.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            updateMaxHeight();
            syncWithState();
        });
    }

    public void saveState(@NonNull Bundle out) {
        out.putInt(KEY_REQUESTED_STATE, intendedState());
    }

    /** Đích đến hiện tại của sheet, kể cả khi đang kéo tay hoặc đang trượt. */
    private int intendedState() {
        int state = behavior.getState();
        if (state == BottomSheetBehavior.STATE_DRAGGING) {
            return lastSlideOffset >= 0.5f ? BottomSheetBehavior.STATE_EXPANDED : BottomSheetBehavior.STATE_COLLAPSED;
        }
        if (state == BottomSheetBehavior.STATE_SETTLING && settlingFromDrag) {
            return slidingUp ? BottomSheetBehavior.STATE_EXPANDED : BottomSheetBehavior.STATE_COLLAPSED;
        }
        return requestedState;
    }

    /** Thu gọn sheet nếu nó đang che sơ đồ (màn hình dọc) – trước khi đưa camera tới một ghế. */
    public void collapseIfCoveringMap() {
        if (overlapsMap && intendedState() != BottomSheetBehavior.STATE_COLLAPSED) {
            setRequestedState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    /** Gọi sau khi View hierarchy đã tự khôi phục (Activity.onRestoreInstanceState). */
    public void restoreState(@NonNull Bundle in) {
        int state = in.getInt(KEY_REQUESTED_STATE, BottomSheetBehavior.STATE_COLLAPSED);
        if (state == BottomSheetBehavior.STATE_EXPANDED || state == BottomSheetBehavior.STATE_COLLAPSED) {
            setRequestedState(state);
            syncWithState();
        }
    }

    private void setRequestedState(int state) {
        requestedState = state;
        settlingFromDrag = false;
        behavior.setState(state);
    }

    public void setSelection(@NonNull SelectionSnapshot snapshot) {
        selection = snapshot;
        rebuild();
    }

    public void setFilterState(@NonNull FilterState state) {
        filterState = state;
        rebuild();
    }

    private void rebuild() {
        if (selection == null || filterState == null) {
            return;
        }
        renderSummary(selection);
        List<SelectedSeatItem> items = new ArrayList<>(selection.getCount());
        String added = null;
        for (Seat seat : selection.getSeats()) {
            items.add(new SelectedSeatItem(seat, filterState.matches(seat.getIndex())));
            if (!previousIds.contains(seat.getId())) {
                added = seat.getId();
            }
        }
        boolean singleAddition = selection.getCount() == previousIds.size() + 1 && added != null;
        previousIds = new HashSet<>(selection.getIds());
        emptyText.setVisibility(selection.isEmpty() ? View.VISIBLE : View.GONE);
        clearAll.setEnabled(!selection.isEmpty());
        // So với danh sách GỬI gần nhất (không phải danh sách adapter đã áp dụng): khi một diff
        // còn đang chạy nền, so với danh sách cũ có thể bỏ sót thay đổi mới nhất.
        if (sameRows(items, lastSubmitted)) {
            return; // Ví dụ gõ thêm ký tự mà kết quả lọc không đổi: không dựng lại danh sách.
        }
        lastSubmitted = items;
        final String reveal = singleAddition ? added : null;
        adapter.submitList(items, () -> {
            if (reveal != null) {
                scrollToSeat(reveal); // Ghế vừa chọn trên Canvas → cuộn danh sách tới ghế đó.
            }
        });
    }

    private static boolean sameRows(@NonNull List<SelectedSeatItem> a, @NonNull List<SelectedSeatItem> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).seat != b.get(i).seat || a.get(i).matchesFilter != b.get(i).matchesFilter) {
                return false;
            }
        }
        return true;
    }

    private void renderSummary(@NonNull SelectionSnapshot snapshot) {
        if (snapshot.isEmpty()) {
            summaryTitle.setText(R.string.summary_empty_title);
            summaryDetail.setText(activity.getString(R.string.summary_empty_detail, SeatMapViewModel.MAX_SELECTION));
            return;
        }
        summaryTitle.setText(activity.getString(R.string.summary_title, snapshot.getCount(),
                SeatMapViewModel.MAX_SELECTION, PriceFormatter.format(snapshot.getTotalPrice())));
        List<String> ids = new ArrayList<>(snapshot.getCount());
        for (Seat seat : snapshot.getSeats()) {
            ids.add(seat.getId());
        }
        summaryDetail.setText(TextUtils.join(", ", ids));
    }

    private void scrollToSeat(@NonNull String seatId) {
        List<SelectedSeatItem> current = adapter.getCurrentList();
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i).seat.getId().equals(seatId)) {
                list.smoothScrollToPosition(i);
                return;
            }
        }
    }

    private void toggle() {
        int state = behavior.getState();
        setRequestedState(state == BottomSheetBehavior.STATE_EXPANDED
                ? BottomSheetBehavior.STATE_COLLAPSED : BottomSheetBehavior.STATE_EXPANDED);
    }

    private void syncWithState() {
        int state = behavior.getState();
        boolean expanded = state == BottomSheetBehavior.STATE_EXPANDED;
        if (state == BottomSheetBehavior.STATE_EXPANDED || state == BottomSheetBehavior.STATE_COLLAPSED) {
            chevron.setRotation(expanded ? 180f : 0f);
        }
        chevron.setContentDescription(activity.getString(expanded ? R.string.sheet_collapse : R.string.sheet_expand));
        collapseOnBack.setEnabled(state != BottomSheetBehavior.STATE_COLLAPSED);
    }

    /**
     * Màn hình dọc: sheet mở rộng không bao giờ che quá phần dưới của vùng sơ đồ – luôn chừa lại
     * ít nhất max(40% vùng sơ đồ, min(160dp, 60% vùng sơ đồ)). Màn hình ngang: sheet nằm trong cột
     * riêng nên được mở hết cột.
     */
    private void updateMaxHeight() {
        int containerHeight = coordinator.getHeight();
        if (containerHeight <= 0) {
            return;
        }
        int maxHeight = containerHeight;
        if (overlapsMap && mapContainer != null && mapContainer.getHeight() > 0) {
            int mapTop = topWithin(mapContainer, coordinator);
            float mapHeight = mapContainer.getHeight();
            float keepVisible = Math.max(MIN_VISIBLE_MAP_FRACTION * mapHeight, Math.min(minVisibleMapPx, 0.6f * mapHeight));
            float preferred = containerHeight - mapTop - keepVisible;
            // Cửa sổ thấp: ưu tiên để danh sách vẫn dùng được, nhưng luôn chừa một dải sơ đồ.
            float usableFloor = Math.min(peekHeight + headerExtraHeight() + minExpandedListPx,
                    containerHeight - mapTop - minMapStripPx);
            maxHeight = Math.max(peekHeight, Math.round(Math.max(preferred, usableFloor)));
        }
        if (maxHeight != behavior.getMaxHeight()) {
            behavior.setMaxHeight(maxHeight);
            sheet.post(sheet::requestLayout);
        }
    }

    /** Phần cố định giữa phần tóm tắt và danh sách (đường kẻ + hàng "Bỏ chọn tất cả"). */
    private int headerExtraHeight() {
        View actions = (View) clearAll.getParent();
        return actions.getHeight() + 1;
    }

    /** Tọa độ y của {@code child} trong hệ tọa độ của {@code ancestor}. */
    private static int topWithin(@NonNull View child, @NonNull View ancestor) {
        int top = 0;
        View v = child;
        while (v != null && v != ancestor) {
            top += v.getTop();
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        return top;
    }
}
