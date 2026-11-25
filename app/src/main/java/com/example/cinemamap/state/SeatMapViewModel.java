package com.example.cinemamap.state;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.example.cinemamap.data.CinemaLayout;
import com.example.cinemamap.data.MockCinemaRepository;

import java.util.ArrayList;

/**
 * Nguồn trạng thái duy nhất cho màn hình sơ đồ. Canvas, bộ lọc và Bottom Sheet đều đọc
 * lựa chọn từ {@link #getSelection()} và gửi thao tác ngược về đây (luồng dữ liệu một chiều).
 *
 * <ul>
 *   <li>ViewModel sống qua thay đổi cấu hình (xoay màn hình) → không mất ghế đang chọn.</li>
 *   <li>Mã ghế còn được ghi vào {@link SavedStateHandle} → khôi phục được cả khi tiến trình
 *       bị hệ điều hành thu hồi.</li>
 * </ul>
 */
public class SeatMapViewModel extends ViewModel {

    static final String KEY_SELECTED_IDS = "selected_seat_ids";
    public static final int MAX_SELECTION = 10;

    private final SavedStateHandle savedState;
    private final CinemaLayout layout;
    private final SeatSelection selection;
    private final MutableLiveData<SelectionSnapshot> selectionLiveData = new MutableLiveData<>();

    public SeatMapViewModel(@NonNull SavedStateHandle savedState) {
        this.savedState = savedState;
        this.layout = MockCinemaRepository.createLayout();
        this.selection = new SeatSelection(layout, MAX_SELECTION);
        ArrayList<String> savedIds = savedState.get(KEY_SELECTED_IDS);
        selection.restore(savedIds);
        publish();
    }

    public CinemaLayout getLayout() {
        return layout;
    }

    public LiveData<SelectionSnapshot> getSelection() {
        return selectionLiveData;
    }

    /** Người dùng chạm vào ghế trên Canvas. */
    public SeatSelection.Result onSeatTapped(String seatId) {
        SeatSelection.Result result = selection.toggle(seatId);
        if (result == SeatSelection.Result.SELECTED || result == SeatSelection.Result.DESELECTED) {
            publish();
        }
        return result;
    }

    /** Xóa một ghế khỏi danh sách đã chọn (ví dụ từ Bottom Sheet). */
    public void removeSeat(String seatId) {
        if (selection.remove(seatId)) {
            publish();
        }
    }

    public void clearSelection() {
        if (selection.size() > 0) {
            selection.clear();
            publish();
        }
    }

    private void publish() {
        savedState.set(KEY_SELECTED_IDS, selection.snapshotIds());
        selectionLiveData.setValue(new SelectionSnapshot(selection.getSelectedSeats(), selection.getTotalPrice()));
    }
}
