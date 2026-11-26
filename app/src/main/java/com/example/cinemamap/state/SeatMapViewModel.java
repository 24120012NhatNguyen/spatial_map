package com.example.cinemamap.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.example.cinemamap.data.CinemaLayout;
import com.example.cinemamap.data.MockCinemaRepository;
import com.example.cinemamap.data.Seat;
import com.example.cinemamap.data.SeatType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Nguồn trạng thái duy nhất cho màn hình sơ đồ. Canvas, bộ lọc và Bottom Sheet đều đọc
 * trạng thái từ {@link #getSelection()} / {@link #getFilterState()} và gửi thao tác ngược về đây
 * (luồng dữ liệu một chiều).
 *
 * <ul>
 *   <li>ViewModel sống qua thay đổi cấu hình (xoay màn hình) → không mất ghế đang chọn, bộ lọc.</li>
 *   <li>Mã ghế và bộ lọc còn được ghi vào {@link SavedStateHandle} → khôi phục được cả khi
 *       tiến trình bị hệ điều hành thu hồi.</li>
 * </ul>
 */
public class SeatMapViewModel extends ViewModel {

    static final String KEY_SELECTED_IDS = "selected_seat_ids";
    static final String KEY_FILTER_TYPES = "filter_types";
    static final String KEY_FILTER_AVAILABLE_ONLY = "filter_available_only";
    static final String KEY_FILTER_QUERY = "filter_query";
    public static final int MAX_SELECTION = 10;

    private final SavedStateHandle savedState;
    private final CinemaLayout layout;
    private final SeatSelection selection;
    private final MutableLiveData<SelectionSnapshot> selectionLiveData = new MutableLiveData<>();
    private final MutableLiveData<FilterState> filterLiveData = new MutableLiveData<>();
    private FilterState filterState;

    public SeatMapViewModel(@NonNull SavedStateHandle savedState) {
        this.savedState = savedState;
        this.layout = MockCinemaRepository.createLayout();
        this.selection = new SeatSelection(layout, MAX_SELECTION);
        ArrayList<String> savedIds = savedState.get(KEY_SELECTED_IDS);
        selection.restore(savedIds);
        filterState = FilterState.compute(layout, restoreFilter(savedState));
        filterLiveData.setValue(filterState);
        publishSelection();
    }

    public CinemaLayout getLayout() {
        return layout;
    }

    public LiveData<SelectionSnapshot> getSelection() {
        return selectionLiveData;
    }

    public LiveData<FilterState> getFilterState() {
        return filterLiveData;
    }

    @NonNull
    public FilterState getCurrentFilterState() {
        return filterState;
    }

    // region Selection

    /**
     * Người dùng chạm vào ghế trên Canvas. Ghế đang bị bộ lọc làm mờ thì không chọn được,
     * nhưng ghế đã chọn luôn bỏ chọn được (ghế đã chọn không bao giờ bị làm mờ).
     */
    public SeatSelection.Result onSeatTapped(String seatId) {
        Seat seat = layout.findSeat(seatId);
        if (seat != null && !selection.isSelected(seatId) && !filterState.matches(seat.getIndex())) {
            return SeatSelection.Result.FILTERED_OUT;
        }
        SeatSelection.Result result = selection.toggle(seatId);
        if (result == SeatSelection.Result.SELECTED || result == SeatSelection.Result.DESELECTED) {
            publishSelection();
        }
        return result;
    }

    /** Xóa một ghế khỏi danh sách đã chọn (từ Bottom Sheet). */
    public void removeSeat(String seatId) {
        if (selection.remove(seatId)) {
            publishSelection();
        }
    }

    /** @return true nếu có ghế bị bỏ chọn (để chỉ báo "đã bỏ chọn" khi thật sự có thay đổi). */
    public boolean clearSelection() {
        if (selection.size() == 0) {
            return false;
        }
        selection.clear();
        publishSelection();
        return true;
    }

    private void publishSelection() {
        savedState.set(KEY_SELECTED_IDS, selection.snapshotIds());
        selectionLiveData.setValue(new SelectionSnapshot(selection.getSelectedSeats(), selection.getTotalPrice()));
    }

    // endregion

    // region Filter

    public void setTypeFilter(@NonNull SeatType type, boolean enabled) {
        applyFilter(filterState.getFilter().withType(type, enabled));
    }

    public void setAvailableOnly(boolean availableOnly) {
        applyFilter(filterState.getFilter().withAvailableOnly(availableOnly));
    }

    public void setQuery(@Nullable String rawQuery) {
        applyFilter(filterState.getFilter().withQuery(rawQuery));
    }

    /** Xóa mọi điều kiện lọc – không ảnh hưởng tới danh sách ghế đang chọn. */
    public void clearFilter() {
        applyFilter(SeatFilter.NONE);
    }

    private void applyFilter(@NonNull SeatFilter filter) {
        if (filter.equals(filterState.getFilter())) {
            return; // Không phát lại khi điều kiện không đổi (tránh vẽ lại thừa).
        }
        filterState = FilterState.compute(layout, filter);
        ArrayList<String> types = new ArrayList<>();
        for (SeatType type : filter.getTypes()) {
            types.add(type.name());
        }
        savedState.set(KEY_FILTER_TYPES, types);
        savedState.set(KEY_FILTER_AVAILABLE_ONLY, filter.isAvailableOnly());
        savedState.set(KEY_FILTER_QUERY, filter.getQuery());
        filterLiveData.setValue(filterState);
    }

    @NonNull
    private static SeatFilter restoreFilter(@NonNull SavedStateHandle state) {
        EnumSet<SeatType> types = EnumSet.noneOf(SeatType.class);
        List<String> names = state.get(KEY_FILTER_TYPES);
        if (names != null) {
            for (String name : names) {
                for (SeatType type : SeatType.values()) {
                    if (type.name().equals(name)) {
                        types.add(type);
                    }
                }
            }
        }
        Boolean availableOnly = state.get(KEY_FILTER_AVAILABLE_ONLY);
        String query = state.get(KEY_FILTER_QUERY);
        return SeatFilter.of(types, availableOnly != null && availableOnly, query);
    }

    // endregion
}
