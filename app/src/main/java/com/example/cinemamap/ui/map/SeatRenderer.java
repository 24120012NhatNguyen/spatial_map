package com.example.cinemamap.ui.map;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import androidx.core.content.ContextCompat;

import com.example.cinemamap.R;
import com.example.cinemamap.data.CinemaLayout;
import com.example.cinemamap.data.RowLabel;
import com.example.cinemamap.data.Seat;
import com.example.cinemamap.data.SeatType;
import com.example.cinemamap.data.WorldRect;
import com.example.cinemamap.data.Zone;

import java.util.List;

/**
 * Lớp vẽ sơ đồ. Toàn bộ Paint/Path/RectF được tạo một lần (constructor và {@link #prepare})
 * và tái sử dụng; {@link #draw} không cấp phát đối tượng mới. Mọi kích thước tính theo
 * đơn vị world – Canvas đã được scale/translate bởi {@link SeatMapView}.
 */
final class SeatRenderer {

    private static final float SEAT_TEXT_SIZE = 13f;
    private static final float ZONE_LABEL_SIZE = 16f;
    private static final float ROW_LABEL_SIZE = 14f;
    private static final float SMALL_LABEL_SIZE = 11f;
    private static final int ZONE_FILL_ALPHA = 0x16;
    private static final int ZONE_STROKE_ALPHA = 0x66;

    private final int standardColor;
    private final int vipColor;
    private final int coupleColor;
    private final int selectedColor;
    private final int selectedStrokeColor;
    private final int bookedColor;
    private final int bookedMarkColor;
    private final int lockedColor;
    private final int screenColor;

    private final Paint seatFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint seatStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lockedStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint seatTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zoneFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zoneStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zoneLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint aislePaint = new Paint();
    private final Paint aisleLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rowLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint screenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint screenGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float seatTextBaseline;
    private final float zoneLabelBaseline;
    private final float rowLabelBaseline;
    private final float smallLabelBaseline;

    private final RectF tmpRect = new RectF();
    private final Path screenPath = new Path();
    private final Path screenGlowPath = new Path();

    // Hình học được tính sẵn một lần cho mỗi layout.
    private RectF[] seatRects = new RectF[0];
    private RectF[] zoneRects = new RectF[0];
    private RectF[] aisleRects = new RectF[0];
    private float screenLabelX;
    private float screenLabelY;

    SeatRenderer(Context context) {
        standardColor = ContextCompat.getColor(context, R.color.seat_standard);
        vipColor = ContextCompat.getColor(context, R.color.seat_vip);
        coupleColor = ContextCompat.getColor(context, R.color.seat_couple);
        selectedColor = ContextCompat.getColor(context, R.color.seat_selected);
        selectedStrokeColor = ContextCompat.getColor(context, R.color.seat_selected_stroke);
        bookedColor = ContextCompat.getColor(context, R.color.seat_booked);
        bookedMarkColor = ContextCompat.getColor(context, R.color.seat_booked_mark);
        lockedColor = ContextCompat.getColor(context, R.color.seat_locked);
        screenColor = ContextCompat.getColor(context, R.color.screen);

        seatFillPaint.setStyle(Paint.Style.FILL);

        seatStrokePaint.setStyle(Paint.Style.STROKE);
        seatStrokePaint.setStrokeWidth(3f);
        seatStrokePaint.setColor(selectedStrokeColor);

        lockedStrokePaint.setStyle(Paint.Style.STROKE);
        lockedStrokePaint.setStrokeWidth(2f);
        lockedStrokePaint.setColor(lockedColor);
        lockedStrokePaint.setPathEffect(new DashPathEffect(new float[]{5f, 4f}, 0f));

        markPaint.setStyle(Paint.Style.STROKE);
        markPaint.setStrokeCap(Paint.Cap.ROUND);
        markPaint.setStrokeJoin(Paint.Join.ROUND);

        seatTextPaint.setColor(0xFFFFFFFF);
        seatTextPaint.setTextAlign(Paint.Align.CENTER);
        seatTextPaint.setTextSize(SEAT_TEXT_SIZE);
        seatTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
        seatTextBaseline = centeredBaseline(seatTextPaint);

        zoneFillPaint.setStyle(Paint.Style.FILL);
        zoneStrokePaint.setStyle(Paint.Style.STROKE);
        zoneStrokePaint.setStrokeWidth(1.5f);

        zoneLabelPaint.setTextAlign(Paint.Align.CENTER);
        zoneLabelPaint.setTextSize(ZONE_LABEL_SIZE);
        zoneLabelPaint.setTypeface(Typeface.DEFAULT_BOLD);
        zoneLabelPaint.setLetterSpacing(0.08f);
        zoneLabelBaseline = centeredBaseline(zoneLabelPaint);

        aislePaint.setColor(ContextCompat.getColor(context, R.color.aisle));
        aisleLinePaint.setColor(ContextCompat.getColor(context, R.color.aisle_line));
        aisleLinePaint.setStrokeWidth(2f);
        aisleLinePaint.setPathEffect(new DashPathEffect(new float[]{10f, 8f}, 0f));

        rowLabelPaint.setColor(ContextCompat.getColor(context, R.color.row_label));
        rowLabelPaint.setTextAlign(Paint.Align.CENTER);
        rowLabelPaint.setTextSize(ROW_LABEL_SIZE);
        rowLabelPaint.setTypeface(Typeface.DEFAULT_BOLD);
        rowLabelBaseline = centeredBaseline(rowLabelPaint);

        smallLabelPaint.setColor(ContextCompat.getColor(context, R.color.on_surface_muted));
        smallLabelPaint.setTextAlign(Paint.Align.CENTER);
        smallLabelPaint.setTextSize(SMALL_LABEL_SIZE);
        smallLabelPaint.setLetterSpacing(0.15f);
        smallLabelBaseline = centeredBaseline(smallLabelPaint);

        screenPaint.setStyle(Paint.Style.STROKE);
        screenPaint.setStrokeWidth(6f);
        screenPaint.setStrokeCap(Paint.Cap.ROUND);
        screenPaint.setColor(screenColor);
        screenGlowPaint.setStyle(Paint.Style.FILL);
    }

    /** Tính sẵn hình học (RectF, Path, Shader) cho layout. Gọi một lần khi đổi layout. */
    void prepare(CinemaLayout layout) {
        List<Seat> seats = layout.getSeats();
        seatRects = new RectF[seats.size()];
        for (int i = 0; i < seatRects.length; i++) {
            seatRects[i] = toRectF(seats.get(i).getBounds());
        }
        List<Zone> zones = layout.getZones();
        zoneRects = new RectF[zones.size()];
        for (int i = 0; i < zoneRects.length; i++) {
            zoneRects[i] = toRectF(zones.get(i).getBounds());
        }
        List<WorldRect> aisles = layout.getAisles();
        aisleRects = new RectF[aisles.size()];
        for (int i = 0; i < aisleRects.length; i++) {
            aisleRects[i] = toRectF(aisles.get(i));
        }

        WorldRect screen = layout.getScreenBounds();
        float curveDepth = screen.height() * 0.6f;
        screenPath.reset();
        screenPath.moveTo(screen.left, screen.bottom);
        screenPath.quadTo(screen.centerX(), screen.top - curveDepth, screen.right, screen.bottom);

        float glowBottom = screen.bottom + 34f;
        screenGlowPath.reset();
        screenGlowPath.moveTo(screen.left, screen.bottom);
        screenGlowPath.quadTo(screen.centerX(), screen.top - curveDepth, screen.right, screen.bottom);
        screenGlowPath.lineTo(screen.right + 24f, glowBottom);
        screenGlowPath.lineTo(screen.left - 24f, glowBottom);
        screenGlowPath.close();
        screenGlowPaint.setShader(new LinearGradient(0f, screen.top, 0f, glowBottom,
                (screenColor & 0x00FFFFFF) | 0x40000000, screenColor & 0x00FFFFFF, Shader.TileMode.CLAMP));

        screenLabelX = screen.centerX();
        screenLabelY = screen.bottom + 18f;
    }

    RectF getSeatRect(int index) {
        return seatRects[index];
    }

    /**
     * Vẽ toàn bộ sơ đồ.
     *
     * @param selected     cờ "đang chọn" theo chỉ số ghế
     * @param visible      vùng world đang hiển thị [l, t, r, b] – ghế ngoài vùng này bị bỏ qua
     * @param showNumbers  có vẽ số ghế hay không (ẩn khi thu nhỏ để sơ đồ gọn và vẽ nhanh hơn)
     */
    void draw(Canvas canvas, CinemaLayout layout, boolean[] selected, float[] visible, boolean showNumbers) {
        drawZones(canvas, layout.getZones());
        drawAisles(canvas);
        drawScreen(canvas);
        drawRowLabels(canvas, layout.getRowLabels());

        List<Seat> seats = layout.getSeats();
        for (int i = 0, n = seats.size(); i < n; i++) {
            RectF r = seatRects[i];
            if (r.right < visible[0] || r.left > visible[2] || r.bottom < visible[1] || r.top > visible[3]) {
                continue; // Culling: không vẽ ghế nằm ngoài màn hình.
            }
            drawSeat(canvas, seats.get(i), r, selected[i], showNumbers);
        }
    }

    private void drawZones(Canvas canvas, List<Zone> zones) {
        for (int i = 0; i < zoneRects.length; i++) {
            Zone zone = zones.get(i);
            int color = typeColor(zone.getType());
            zoneFillPaint.setColor((color & 0x00FFFFFF) | (ZONE_FILL_ALPHA << 24));
            zoneStrokePaint.setColor((color & 0x00FFFFFF) | (ZONE_STROKE_ALPHA << 24));
            canvas.drawRoundRect(zoneRects[i], 14f, 14f, zoneFillPaint);
            canvas.drawRoundRect(zoneRects[i], 14f, 14f, zoneStrokePaint);

            WorldRect header = zone.getHeaderBounds();
            zoneLabelPaint.setColor(color);
            canvas.drawText(zone.getLabel(), header.centerX(), header.centerY() + zoneLabelBaseline, zoneLabelPaint);
        }
    }

    private void drawAisles(Canvas canvas) {
        for (RectF aisle : aisleRects) {
            canvas.drawRect(aisle, aislePaint);
            float cx = aisle.centerX();
            canvas.drawLine(cx, aisle.top + 6f, cx, aisle.bottom - 30f, aisleLinePaint);
            canvas.drawText("LỐI VÀO", cx, aisle.bottom - 12f + smallLabelBaseline, smallLabelPaint);
        }
    }

    private void drawScreen(Canvas canvas) {
        canvas.drawPath(screenGlowPath, screenGlowPaint);
        canvas.drawPath(screenPath, screenPaint);
        canvas.drawText("MÀN HÌNH", screenLabelX, screenLabelY + smallLabelBaseline, smallLabelPaint);
    }

    private void drawRowLabels(Canvas canvas, List<RowLabel> labels) {
        for (int i = 0, n = labels.size(); i < n; i++) {
            RowLabel label = labels.get(i);
            canvas.drawText(label.getText(), label.getX(), label.getCenterY() + rowLabelBaseline, rowLabelPaint);
        }
    }

    private void drawSeat(Canvas canvas, Seat seat, RectF r, boolean selected, boolean showNumber) {
        float radius = r.height() * 0.24f;
        if (selected) {
            seatFillPaint.setColor(selectedColor);
            canvas.drawRoundRect(r, radius, radius, seatFillPaint);
            tmpRect.set(r);
            tmpRect.inset(1.5f, 1.5f);
            canvas.drawRoundRect(tmpRect, radius, radius, seatStrokePaint);
            drawCheck(canvas, r);
            return;
        }
        switch (seat.getStatus()) {
            case AVAILABLE:
                seatFillPaint.setColor(typeColor(seat.getType()));
                canvas.drawRoundRect(r, radius, radius, seatFillPaint);
                if (showNumber) {
                    canvas.drawText(seat.getNumberLabel(), r.centerX(), r.centerY() + seatTextBaseline, seatTextPaint);
                }
                break;
            case BOOKED:
                seatFillPaint.setColor(bookedColor);
                canvas.drawRoundRect(r, radius, radius, seatFillPaint);
                // Dải màu nhỏ phía trên giúp vẫn nhận ra loại ghế khi ghế đã được đặt.
                seatFillPaint.setColor(typeColor(seat.getType()));
                tmpRect.set(r.left + radius, r.top + 3f, r.right - radius, r.top + 6f);
                canvas.drawRect(tmpRect, seatFillPaint);
                drawCross(canvas, r);
                break;
            case LOCKED:
            default:
                tmpRect.set(r);
                tmpRect.inset(1f, 1f);
                canvas.drawRoundRect(tmpRect, radius, radius, lockedStrokePaint);
                drawLock(canvas, r);
                break;
        }
    }

    private void drawCheck(Canvas canvas, RectF r) {
        float s = r.height();
        float cx = r.centerX();
        float cy = r.centerY();
        markPaint.setColor(0xFFFFFFFF);
        markPaint.setStrokeWidth(s * 0.11f);
        canvas.drawLine(cx - s * 0.22f, cy + s * 0.01f, cx - s * 0.06f, cy + s * 0.17f, markPaint);
        canvas.drawLine(cx - s * 0.06f, cy + s * 0.17f, cx + s * 0.24f, cy - s * 0.15f, markPaint);
    }

    private void drawCross(Canvas canvas, RectF r) {
        float h = r.height() * 0.17f;
        float cx = r.centerX();
        float cy = r.centerY() + r.height() * 0.05f;
        markPaint.setColor(bookedMarkColor);
        markPaint.setStrokeWidth(r.height() * 0.08f);
        canvas.drawLine(cx - h, cy - h, cx + h, cy + h, markPaint);
        canvas.drawLine(cx - h, cy + h, cx + h, cy - h, markPaint);
    }

    private void drawLock(Canvas canvas, RectF r) {
        float s = r.height();
        float cx = r.centerX();
        float cy = r.centerY();
        // Thân khóa
        seatFillPaint.setColor(lockedColor);
        tmpRect.set(cx - s * 0.19f, cy - s * 0.04f, cx + s * 0.19f, cy + s * 0.24f);
        canvas.drawRoundRect(tmpRect, s * 0.05f, s * 0.05f, seatFillPaint);
        // Quai khóa
        markPaint.setColor(lockedColor);
        markPaint.setStrokeWidth(s * 0.07f);
        tmpRect.set(cx - s * 0.12f, cy - s * 0.27f, cx + s * 0.12f, cy + s * 0.02f);
        canvas.drawArc(tmpRect, 180f, 180f, false, markPaint);
    }

    int typeColor(SeatType type) {
        switch (type) {
            case VIP:
                return vipColor;
            case COUPLE:
                return coupleColor;
            case STANDARD:
            default:
                return standardColor;
        }
    }

    private static RectF toRectF(WorldRect rect) {
        return new RectF(rect.left, rect.top, rect.right, rect.bottom);
    }

    /** Độ lệch baseline để chữ nằm giữa theo chiều dọc quanh một tọa độ y. */
    private static float centeredBaseline(Paint paint) {
        Paint.FontMetrics fm = paint.getFontMetrics();
        return -(fm.ascent + fm.descent) * 0.5f;
    }
}
