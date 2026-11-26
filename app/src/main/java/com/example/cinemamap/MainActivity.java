package com.example.cinemamap;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.cinemamap.data.CinemaLayout;
import com.example.cinemamap.data.Seat;
import com.example.cinemamap.data.WorldRect;
import com.example.cinemamap.state.FilterState;
import com.example.cinemamap.state.SeatMapViewModel;
import com.example.cinemamap.state.SeatSelection;
import com.example.cinemamap.state.SelectionSnapshot;
import com.example.cinemamap.ui.filter.FilterBarController;
import com.example.cinemamap.ui.map.SeatMapView;
import com.example.cinemamap.ui.sheet.SelectionSheetController;
import com.google.android.material.appbar.MaterialToolbar;

/**
 * Màn hình chính: kết nối ViewModel (dữ liệu + trạng thái) với các thành phần hiển thị
 * (SeatMapView, thanh lọc, Bottom Sheet). Activity không chứa logic chọn/lọc ghế –
 * chỉ chuyển sự kiện vào ViewModel và phân phát trạng thái mới ra các thành phần.
 */
public class MainActivity extends AppCompatActivity {

    private static final float SIDE_PANEL_MAX_FRACTION = 0.4f;

    private SeatMapViewModel viewModel;
    private SeatMapView seatMapView;
    private FilterBarController filterBar;
    private SelectionSheetController sheet;
    @Nullable
    private Toast toast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        applySystemBarInsets(findViewById(R.id.root));
        fitSidePanel();

        viewModel = new ViewModelProvider(this).get(SeatMapViewModel.class);
        CinemaLayout layout = viewModel.getLayout();

        setupToolbar(layout);

        seatMapView = findViewById(R.id.seat_map);
        seatMapView.setCinemaLayout(layout);
        seatMapView.setOnSeatClickListener(this::onSeatClick);

        filterBar = new FilterBarController(this, viewModel, this::onSearchSubmitted);
        sheet = new SelectionSheetController(this, new SelectionSheetController.Callbacks() {
            @Override
            public void onLocateSeat(@NonNull Seat seat) {
                seatMapView.focusSeat(seat.getId());
            }

            @Override
            public void onRemoveSeat(@NonNull Seat seat) {
                viewModel.removeSeat(seat.getId());
            }

            @Override
            public void onClearAll() {
                if (viewModel.clearSelection()) {
                    showToast(getString(R.string.selection_cleared));
                }
            }
        });

        viewModel.getSelection().observe(this, this::renderSelection);
        viewModel.getFilterState().observe(this, this::renderFilter);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        sheet.saveState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState); // View tự khôi phục trước...
        sheet.restoreState(savedInstanceState);           // ...rồi mới áp trạng thái sheet đã yêu cầu.
    }

    /**
     * Màn hình ngang: cột phải rộng tối đa 320dp nhưng không quá 40% bề rộng cửa sổ, để sơ đồ
     * vẫn đủ rộng khi cửa sổ hẹp (chia đôi màn hình...).
     */
    private void fitSidePanel() {
        if (getResources().getBoolean(R.bool.sheet_overlaps_map)) {
            return; // Màn hình dọc không có cột bên.
        }
        View panel = findViewById(R.id.coordinator);
        float windowWidth = getResources().getConfiguration().screenWidthDp * getResources().getDisplayMetrics().density;
        ViewGroup.LayoutParams params = panel.getLayoutParams();
        params.width = Math.round(Math.min(getResources().getDimension(R.dimen.side_panel_width),
                windowWidth * SIDE_PANEL_MAX_FRACTION));
        panel.setLayoutParams(params);
    }

    private void setupToolbar(@NonNull CinemaLayout layout) {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        String details = getString(R.string.toolbar_subtitle, layout.getRoomName(), layout.getShowtime());
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // Màn hình ngang thấp: gộp vào một dòng để dành chiều cao cho sơ đồ.
            toolbar.setTitle(getString(R.string.toolbar_subtitle, layout.getMovieTitle(), details));
        } else {
            toolbar.setTitle(layout.getMovieTitle());
            toolbar.setSubtitle(details);
        }
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_reset_view) {
                seatMapView.resetView(); // Chỉ đặt lại góc nhìn; bộ lọc và ghế đã chọn giữ nguyên.
                return true;
            }
            return false;
        });
    }

    private void renderSelection(@NonNull SelectionSnapshot snapshot) {
        seatMapView.setSelectedSeatIds(snapshot.getIds());
        sheet.setSelection(snapshot);
    }

    private void renderFilter(@NonNull FilterState state) {
        seatMapView.setFilterMatches(state.isActive() ? state.copyMatches() : null);
        filterBar.render(state);
        sheet.setFilterState(state);
    }

    private void onSeatClick(@NonNull Seat seat) {
        SeatSelection.Result result = viewModel.onSeatTapped(seat.getId());
        switch (result) {
            case BOOKED:
                showToast(getString(R.string.seat_booked, seat.getId()));
                break;
            case LOCKED:
                showToast(getString(R.string.seat_locked, seat.getId()));
                break;
            case LIMIT_REACHED:
                showToast(getString(R.string.seat_limit_reached, SeatMapViewModel.MAX_SELECTION));
                break;
            case FILTERED_OUT:
                showToast(getString(R.string.seat_filtered_out, seat.getId()));
                break;
            default:
                // SELECTED / DESELECTED: Canvas và Bottom Sheet được cập nhật qua LiveData.
                break;
        }
    }

    /** Bấm "Tìm": một kết quả → định vị ghế đó; nhiều kết quả → đưa cả nhóm vào khung nhìn. */
    private void onSearchSubmitted() {
        FilterState state = viewModel.getCurrentFilterState();
        Seat single = state.getSingleMatch();
        WorldRect bounds = state.getMatchBounds();
        if (single != null || (bounds != null && state.isActive())) {
            sheet.collapseIfCoveringMap(); // Kết quả không bị Bottom Sheet mở rộng che.
        }
        if (single != null) {
            seatMapView.focusSeat(single.getId());
        } else if (bounds != null && state.isActive()) {
            seatMapView.showWorldArea(bounds);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            filterBar.dismissKeyboardIfOutside(event.getRawX(), event.getRawY());
        }
        return super.dispatchTouchEvent(event);
    }

    private void showToast(@NonNull String message) {
        if (toast != null) {
            toast.cancel();
        }
        toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        toast.show();
    }

    private static void applySystemBarInsets(@NonNull View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
