package com.example.cinemamap.ui;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;

import com.example.cinemamap.R;
import com.example.cinemamap.data.SeatType;

/** Màu đại diện cho từng loại ghế – dùng chung cho Canvas, bảng chú thích và Bottom Sheet. */
public final class SeatTypeColors {

    private SeatTypeColors() {
    }

    @ColorRes
    public static int of(@NonNull SeatType type) {
        switch (type) {
            case VIP:
                return R.color.seat_vip;
            case COUPLE:
                return R.color.seat_couple;
            case STANDARD:
            default:
                return R.color.seat_standard;
        }
    }
}
