package com.example.cinemamap.ui.filter;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.cinemamap.R;
import com.example.cinemamap.data.SeatType;
import com.example.cinemamap.state.FilterState;
import com.example.cinemamap.state.SeatFilter;
import com.example.cinemamap.state.SeatMapViewModel;
import com.google.android.material.chip.Chip;

/**
 * Thanh tìm kiếm + chip lọc + thông báo kết quả. Chỉ chuyển thao tác về ViewModel và hiển thị
 * lại {@link FilterState}; không tự giữ điều kiện lọc (ViewModel là nguồn duy nhất).
 */
public final class FilterBarController {

    public interface Callbacks {
        /** Người dùng bấm "Tìm" trên bàn phím – đưa camera tới kết quả. */
        void onSearchSubmitted();
    }

    private final SeatMapViewModel viewModel;
    private final EditText searchInput;
    private final Chip statusChip;
    private final Chip standardChip;
    private final Chip vipChip;
    private final Chip coupleChip;
    private final Chip availableChip;
    @Nullable
    private final HorizontalScrollView chipScroll;
    private final View noResultsCard;
    /** true khi đang đổ trạng thái từ ViewModel vào View – bỏ qua listener để không tạo vòng lặp. */
    private boolean binding;

    public FilterBarController(@NonNull Activity activity, @NonNull SeatMapViewModel viewModel,
                               @NonNull Callbacks callbacks) {
        this.viewModel = viewModel;
        searchInput = activity.findViewById(R.id.search_input);
        statusChip = activity.findViewById(R.id.chip_filter_status);
        standardChip = activity.findViewById(R.id.chip_standard);
        vipChip = activity.findViewById(R.id.chip_vip);
        coupleChip = activity.findViewById(R.id.chip_couple);
        availableChip = activity.findViewById(R.id.chip_available);
        chipScroll = activity.findViewById(R.id.chip_scroll); // Chỉ có ở màn hình dọc.
        noResultsCard = activity.findViewById(R.id.no_results_card);

        // Đổ trạng thái hiện tại trước khi gắn listener.
        render(viewModel.getCurrentFilterState());

        bindTypeChip(standardChip, SeatType.STANDARD);
        bindTypeChip(vipChip, SeatType.VIP);
        bindTypeChip(coupleChip, SeatType.COUPLE);
        availableChip.setOnCheckedChangeListener((chip, checked) -> {
            if (!binding) {
                viewModel.setAvailableOnly(checked);
            }
        });
        statusChip.setCloseIconContentDescription(activity.getString(R.string.filter_status_clear));
        statusChip.setOnClickListener(v -> viewModel.clearFilter());
        statusChip.setOnCloseIconClickListener(v -> viewModel.clearFilter());
        activity.findViewById(R.id.no_results_clear).setOnClickListener(v -> viewModel.clearFilter());

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!binding) {
                    viewModel.setQuery(s.toString()); // Lọc ngay khi gõ.
                }
            }
        });
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean enterKey = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (actionId != EditorInfo.IME_ACTION_SEARCH && !enterKey) {
                return false;
            }
            // Phím Enter của bàn phím cứng gửi cả DOWN và UP: chỉ xử lý một lần.
            if (!enterKey || event.getAction() == KeyEvent.ACTION_UP) {
                hideKeyboard();
                callbacks.onSearchSubmitted();
            }
            return true;
        });
    }

    private void bindTypeChip(@NonNull Chip chip, @NonNull SeatType type) {
        chip.setOnCheckedChangeListener((c, checked) -> {
            if (!binding) {
                viewModel.setTypeFilter(type, checked);
            }
        });
    }

    public void render(@NonNull FilterState state) {
        SeatFilter filter = state.getFilter();
        binding = true;
        standardChip.setChecked(filter.getTypes().contains(SeatType.STANDARD));
        vipChip.setChecked(filter.getTypes().contains(SeatType.VIP));
        coupleChip.setChecked(filter.getTypes().contains(SeatType.COUPLE));
        availableChip.setChecked(filter.isAvailableOnly());
        // Chỉ ghi đè ô tìm kiếm khi khác về nghĩa (ví dụ vừa "Xóa bộ lọc"), không làm nhảy con trỏ khi đang gõ.
        if (!SeatFilter.normalizeQuery(searchInput.getText().toString()).equals(filter.getQuery())) {
            searchInput.setText(filter.getQuery());
            searchInput.setSelection(searchInput.length());
        }
        binding = false;

        boolean wasVisible = statusChip.getVisibility() == View.VISIBLE;
        if (state.isActive()) {
            statusChip.setText(statusChip.getContext().getString(R.string.filter_status, state.getMatchCount()));
            statusChip.setVisibility(View.VISIBLE);
            if (!wasVisible && chipScroll != null) {
                chipScroll.post(() -> chipScroll.smoothScrollTo(0, 0)); // Cho người dùng thấy chip trạng thái.
            }
        } else {
            statusChip.setVisibility(View.GONE);
        }
        noResultsCard.setVisibility(state.hasNoResults() ? View.VISIBLE : View.GONE);
    }

    /** Đóng bàn phím khi người dùng chạm ra ngoài ô tìm kiếm. */
    public void dismissKeyboardIfOutside(float rawX, float rawY) {
        if (!searchInput.hasFocus()) {
            return;
        }
        int[] location = new int[2];
        searchInput.getLocationOnScreen(location);
        boolean inside = rawX >= location[0] && rawX <= location[0] + searchInput.getWidth()
                && rawY >= location[1] && rawY <= location[1] + searchInput.getHeight();
        if (!inside) {
            hideKeyboard();
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) searchInput.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }
        searchInput.clearFocus();
    }
}
