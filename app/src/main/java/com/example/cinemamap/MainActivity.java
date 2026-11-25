package com.example.cinemamap;

import android.content.res.Configuration;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
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
import com.example.cinemamap.data.PriceFormatter;
import com.example.cinemamap.data.Seat;
import com.example.cinemamap.state.SeatMapViewModel;
import com.example.cinemamap.state.SeatSelection;
import com.example.cinemamap.state.SelectionSnapshot;
import com.example.cinemamap.ui.map.SeatMapView;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

/**
 * Màn hình chính: kết nối ViewModel (dữ liệu + trạng thái) với SeatMapView (lớp vẽ).
 * Activity không chứa logic chọn ghế – chỉ chuyển sự kiện và hiển thị kết quả.
 */
public class MainActivity extends AppCompatActivity {

    private SeatMapViewModel viewModel;
    private SeatMapView seatMapView;
    private TextView summaryTitle;
    private TextView summaryDetail;
    @Nullable
    private Toast toast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        applySystemBarInsets(findViewById(R.id.root));

        viewModel = new ViewModelProvider(this).get(SeatMapViewModel.class);
        CinemaLayout layout = viewModel.getLayout();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        String details = getString(R.string.toolbar_subtitle, layout.getRoomName(), layout.getShowtime());
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // Màn hình ngang thấp: gộp vào một dòng để dành chiều cao cho sơ đồ.
            toolbar.setTitle(getString(R.string.toolbar_subtitle, layout.getMovieTitle(), details));
        } else {
            toolbar.setTitle(layout.getMovieTitle());
            toolbar.setSubtitle(details);
        }

        seatMapView = findViewById(R.id.seat_map);
        seatMapView.setCinemaLayout(layout);
        seatMapView.setOnSeatClickListener(this::onSeatClick);

        summaryTitle = findViewById(R.id.summary_title);
        summaryDetail = findViewById(R.id.summary_detail);

        viewModel.getSelection().observe(this, this::renderSelection);
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
            default:
                // SELECTED / DESELECTED: giao diện được cập nhật qua LiveData.
                break;
        }
    }

    private void renderSelection(@NonNull SelectionSnapshot snapshot) {
        seatMapView.setSelectedSeatIds(snapshot.getIds());
        if (snapshot.isEmpty()) {
            summaryTitle.setText(R.string.summary_empty_title);
            summaryDetail.setText(getString(R.string.summary_empty_detail, SeatMapViewModel.MAX_SELECTION));
            return;
        }
        summaryTitle.setText(getString(R.string.summary_title, snapshot.getCount(),
                SeatMapViewModel.MAX_SELECTION, PriceFormatter.format(snapshot.getTotalPrice())));
        List<String> labels = new ArrayList<>(snapshot.getCount());
        for (Seat seat : snapshot.getSeats()) {
            labels.add(seat.getId() + " (" + seat.getType().getDisplayName() + ")");
        }
        summaryDetail.setText(TextUtils.join(", ", labels));
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
