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

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.cinemamap.R;
import com.example.cinemamap.data.CinemaLayout;
import com.example.cinemamap.data.MockCinemaRepository;
import com.example.cinemamap.data.RowLabel;
import com.example.cinemamap.data.Seat;
import com.example.cinemamap.data.SeatStatus;
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

    private static final String SCREEN_LABEL = "MÀN HÌNH";
    private static final String ENTRANCE_LABEL = "LỐI VÀO";
    private static final float SEAT_TEXT_SIZE = 13f;
    private static final float ZONE_LABEL_SIZE = 16f;
    private static final float ROW_LABEL_SIZE = 14f;
    private static final float SMALL_LABEL_SIZE = 11f;
    /** Kích thước tối thiểu (dp trên màn hình) để nhãn phân khu/hàng vẫn đọc được khi thu nhỏ. */
    private static final float MIN_ZONE_LABEL_DP = 11f;
    private static final float MIN_ROW_LABEL_DP = 9f;
    private static final float MIN_SMALL_LABEL_DP = 8f;
    /** Cỡ chữ tối đa (world) cho nhãn "MÀN HÌNH"/"LỐI VÀO" để không đè lên ghế. */
    private static final float MAX_SMALL_LABEL_SIZE = 18f;
    private static final int ZONE_FILL_ALPHA = 0x16;
    private static final int ZONE_STROKE_ALPHA = 0x66;
    /** Độ trong suốt của ghế không khớp bộ lọc (bị làm mờ). */
    private static final int DIMMED_ALPHA = 0x3A;
    /** Ghế đang chọn nhưng không khớp bộ lọc: mờ nhẹ hơn để vẫn nhận ra dấu ✓. */
    private static final int DIMMED_SELECTED_ALPHA = 0x80;
    /** Vòng sáng định vị ghế: độ dày và khoảng nở tối đa (dp trên màn hình). */
    private static final float HIGHLIGHT_STROKE_DP = 3f;
    private static final float HIGHLIGHT_GAP_DP = 3f;
    private static final float HIGHLIGHT_PULSE_DP = 7f;

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
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float seatTextBaseline;
    /** Tỷ lệ baseline/cỡ chữ – dùng khi cỡ chữ nhãn thay đổi theo mức zoom. */
    private final float zoneLabelBaselineRatio;
    private final float rowLabelBaselineRatio;
    private final float smallLabelBaselineRatio;

    private final RectF tmpRect = new RectF();
    private final Path screenPath = new Path();
    private final Path screenGlowPath = new Path();

    // Hình học được tính sẵn một lần cho mỗi layout.
    private RectF[] seatRects = new RectF[0];
    private RectF[] zoneRects = new RectF[0];
    private int[] zoneColors = new int[0];
    /** Cỡ chữ nhãn phân khu đã tính cho {@link #cachedZoneMinSize}; chỉ đo lại khi mức zoom đổi. */
    private float[] zoneLabelSizes = new float[0];
    private float cachedZoneMinSize = Float.NaN;
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
        zoneLabelBaselineRatio = centeredBaseline(zoneLabelPaint) / ZONE_LABEL_SIZE;

        aislePaint.setColor(ContextCompat.getColor(context, R.color.aisle));
        aisleLinePaint.setColor(ContextCompat.getColor(context, R.color.aisle_line));
        aisleLinePaint.setStrokeWidth(2f);
        aisleLinePaint.setPathEffect(new DashPathEffect(new float[]{10f, 8f}, 0f));

        rowLabelPaint.setColor(ContextCompat.getColor(context, R.color.row_label));
        rowLabelPaint.setTextAlign(Paint.Align.CENTER);
        rowLabelPaint.setTextSize(ROW_LABEL_SIZE);
        rowLabelPaint.setTypeface(Typeface.DEFAULT_BOLD);
        rowLabelBaselineRatio = centeredBaseline(rowLabelPaint) / ROW_LABEL_SIZE;

        smallLabelPaint.setColor(ContextCompat.getColor(context, R.color.on_surface_muted));
        smallLabelPaint.setTextAlign(Paint.Align.CENTER);
        smallLabelPaint.setTextSize(SMALL_LABEL_SIZE);
        smallLabelPaint.setLetterSpacing(0.15f);
        smallLabelBaselineRatio = centeredBaseline(smallLabelPaint) / SMALL_LABEL_SIZE;

        screenPaint.setStyle(Paint.Style.STROKE);
        screenPaint.setStrokeWidth(6f);
        screenPaint.setStrokeCap(Paint.Cap.ROUND);
        screenPaint.setColor(screenColor);
        screenGlowPaint.setStyle(Paint.Style.FILL);

        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setColor(ContextCompat.getColor(context, R.color.seat_highlight));
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
        zoneColors = new int[zones.size()];
        zoneLabelSizes = new float[zones.size()];
        cachedZoneMinSize = Float.NaN;
        for (int i = 0; i < zoneRects.length; i++) {
            zoneRects[i] = toRectF(zones.get(i).getBounds());
            zoneColors[i] = typeColor(zones.get(i).getType());
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
     * @param dimmed       cờ "bị bộ lọc làm mờ" theo chỉ số ghế
     * @param visible      vùng world đang hiển thị [l, t, r, b] – ghế ngoài vùng này bị bỏ qua
     * @param scale        tỷ lệ hiện tại (px / đơn vị world)
     * @param density      mật độ màn hình (px / dp)
     * @param showNumbers  có vẽ số ghế hay không (ẩn khi thu nhỏ để sơ đồ gọn và vẽ nhanh hơn)
     */
    void draw(Canvas canvas, CinemaLayout layout, boolean[] selected, boolean[] dimmed, float[] visible,
              float scale, float density, boolean showNumbers) {
        float smallLabelSize = Math.min(Math.max(SMALL_LABEL_SIZE, MIN_SMALL_LABEL_DP * density / scale),
                MAX_SMALL_LABEL_SIZE);
        smallLabelPaint.setTextSize(smallLabelSize);
        float smallBaseline = smallLabelSize * smallLabelBaselineRatio;

        drawZones(canvas);
        drawAisles(canvas, smallBaseline);
        drawScreen(canvas, smallBaseline);
        drawZoneLabels(canvas, layout.getZones(), MIN_ZONE_LABEL_DP * density / scale);
        drawRowLabels(canvas, layout.getRowLabels(), MIN_ROW_LABEL_DP * density / scale, visible);

        List<Seat> seats = layout.getSeats();
        for (int i = 0, n = seats.size(); i < n; i++) {
            RectF r = seatRects[i];
            if (r.right < visible[0] || r.left > visible[2] || r.bottom < visible[1] || r.top > visible[3]) {
                continue; // Culling: không vẽ ghế nằm ngoài màn hình.
            }
            Seat seat = seats.get(i);
            boolean faded = dimmed[i];
            drawSeatGlyph(canvas, r, seat.getType(), seat.getStatus(), selected[i], faded,
                    showNumbers && !faded ? seat.getNumberLabel() : null);
        }
    }

    /**
     * Vòng sáng nhấp nháy quanh ghế vừa được định vị (từ Bottom Sheet hoặc tìm kiếm).
     *
     * @param progress tiến độ hiệu ứng 0..1 – vòng nở ra/thu vào rồi mờ dần
     */
    void drawHighlight(Canvas canvas, int seatIndex, float progress, float scale, float density) {
        if (seatIndex < 0 || seatIndex >= seatRects.length) {
            return;
        }
        drawHighlightRing(canvas, seatRects[seatIndex], progress, scale, density);
    }

    /** Vòng sáng quanh một hình chữ nhật world; dùng chung cho sơ đồ và bảng chú thích. */
    void drawHighlightRing(Canvas canvas, RectF r, float progress, float scale, float density) {
        float dpToWorld = density / scale;
        float pulse = (float) Math.abs(Math.sin(progress * Math.PI * 3.0));
        float outset = (HIGHLIGHT_GAP_DP + HIGHLIGHT_PULSE_DP * pulse) * dpToWorld;
        float fade = 1f - progress * progress * progress;
        highlightPaint.setStrokeWidth(HIGHLIGHT_STROKE_DP * dpToWorld);
        highlightPaint.setAlpha(Math.round(255 * fade));
        tmpRect.set(r.left - outset, r.top - outset, r.right + outset, r.bottom + outset);
        float radius = r.height() * 0.24f + outset;
        canvas.drawRoundRect(tmpRect, radius, radius, highlightPaint);
    }

    private void drawZones(Canvas canvas) {
        for (int i = 0; i < zoneRects.length; i++) {
            int color = zoneColors[i];
            zoneFillPaint.setColor((color & 0x00FFFFFF) | (ZONE_FILL_ALPHA << 24));
            zoneStrokePaint.setColor((color & 0x00FFFFFF) | (ZONE_STROKE_ALPHA << 24));
            canvas.drawRoundRect(zoneRects[i], 14f, 14f, zoneFillPaint);
            canvas.drawRoundRect(zoneRects[i], 14f, 14f, zoneStrokePaint);
        }
    }

    /**
     * Nhãn phân khu: cỡ chữ theo world nhưng không nhỏ hơn {@code minSize} (tương ứng một cỡ dp
     * tối thiểu trên màn hình) và không vượt quá vùng tiêu đề của phân khu.
     */
    private void drawZoneLabels(Canvas canvas, List<Zone> zones, float minSize) {
        if (minSize != cachedZoneMinSize) {
            computeZoneLabelSizes(zones, minSize);
        }
        for (int i = 0, n = zones.size(); i < n; i++) {
            Zone zone = zones.get(i);
            WorldRect header = zone.getHeaderBounds();
            float size = zoneLabelSizes[i];
            zoneLabelPaint.setTextSize(size);
            zoneLabelPaint.setColor(zoneColors[i]);
            canvas.drawText(zone.getLabel(), header.centerX(),
                    header.centerY() + size * zoneLabelBaselineRatio, zoneLabelPaint);
        }
    }

    private void drawAisles(Canvas canvas, float labelBaseline) {
        for (RectF aisle : aisleRects) {
            canvas.drawRect(aisle, aislePaint);
            float cx = aisle.centerX();
            canvas.drawLine(cx, aisle.top + 6f, cx, aisle.bottom - 30f, aisleLinePaint);
            canvas.drawText(ENTRANCE_LABEL, cx, aisle.bottom - 12f + labelBaseline, smallLabelPaint);
        }
    }

    private void drawScreen(Canvas canvas, float labelBaseline) {
        canvas.drawPath(screenGlowPath, screenGlowPaint);
        canvas.drawPath(screenPath, screenPaint);
        canvas.drawText(SCREEN_LABEL, screenLabelX, screenLabelY + labelBaseline, smallLabelPaint);
    }

    /** Đo lại cỡ chữ vừa vùng tiêu đề – chỉ chạy khi mức zoom (cỡ tối thiểu) thay đổi. */
    private void computeZoneLabelSizes(List<Zone> zones, float minSize) {
        for (int i = 0, n = zones.size(); i < n; i++) {
            Zone zone = zones.get(i);
            WorldRect header = zone.getHeaderBounds();
            float size = Math.min(Math.max(ZONE_LABEL_SIZE, minSize), header.height() * 0.66f);
            zoneLabelPaint.setTextSize(size);
            float width = zoneLabelPaint.measureText(zone.getLabel());
            float maxWidth = header.width() * 0.96f;
            if (width > maxWidth) {
                size *= maxWidth / width;
            }
            zoneLabelSizes[i] = size;
        }
        cachedZoneMinSize = minSize;
    }

    private void drawRowLabels(Canvas canvas, List<RowLabel> labels, float minSize, float[] visible) {
        float size = Math.min(Math.max(ROW_LABEL_SIZE, minSize), MockCinemaRepository.ROW_PITCH * 0.6f);
        rowLabelPaint.setTextSize(size);
        float baseline = size * rowLabelBaselineRatio;
        float half = size; // nửa bề rộng/chiều cao ước lượng của một ký tự nhãn hàng
        for (int i = 0, n = labels.size(); i < n; i++) {
            RowLabel label = labels.get(i);
            float x = label.getX();
            float y = label.getCenterY();
            if (x + half < visible[0] || x - half > visible[2] || y + half < visible[1] || y - half > visible[3]) {
                continue; // Culling nhãn nằm ngoài màn hình.
            }
            canvas.drawText(label.getText(), x, y + baseline, rowLabelPaint);
        }
    }

    /**
     * Vẽ một ghế theo loại/trạng thái trong hệ tọa độ world (cạnh ghế chuẩn = 32 đơn vị).
     * Dùng chung cho sơ đồ và bảng chú thích để ký hiệu luôn giống hệt nhau.
     *
     * @param label số ghế cần vẽ, hoặc null để ẩn
     */
    void drawSeatGlyph(Canvas canvas, RectF r, SeatType type, SeatStatus status,
                       boolean selected, boolean dimmed, @Nullable String label) {
        float radius = r.height() * 0.24f;
        int alpha = dimmed ? DIMMED_ALPHA : 0xFF;
        if (selected) {
            alpha = dimmed ? DIMMED_SELECTED_ALPHA : 0xFF;
            seatFillPaint.setColor(withAlpha(selectedColor, alpha));
            canvas.drawRoundRect(r, radius, radius, seatFillPaint);
            seatStrokePaint.setColor(withAlpha(selectedStrokeColor, alpha));
            tmpRect.set(r);
            tmpRect.inset(1.5f, 1.5f);
            canvas.drawRoundRect(tmpRect, radius, radius, seatStrokePaint);
            drawCheck(canvas, r, alpha);
            return;
        }
        switch (status) {
            case AVAILABLE:
                seatFillPaint.setColor(withAlpha(typeColor(type), alpha));
                canvas.drawRoundRect(r, radius, radius, seatFillPaint);
                if (label != null) {
                    canvas.drawText(label, r.centerX(), r.centerY() + seatTextBaseline, seatTextPaint);
                }
                break;
            case BOOKED:
                seatFillPaint.setColor(withAlpha(bookedColor, alpha));
                canvas.drawRoundRect(r, radius, radius, seatFillPaint);
                // Dải màu nhỏ phía trên giúp vẫn nhận ra loại ghế khi ghế đã được đặt.
                seatFillPaint.setColor(withAlpha(typeColor(type), alpha));
                tmpRect.set(r.left + radius, r.top + 3f, r.right - radius, r.top + 6f);
                canvas.drawRect(tmpRect, seatFillPaint);
                drawCross(canvas, r, alpha);
                break;
            case LOCKED:
            default:
                lockedStrokePaint.setColor(withAlpha(lockedColor, alpha));
                tmpRect.set(r);
                tmpRect.inset(1f, 1f);
                canvas.drawRoundRect(tmpRect, radius, radius, lockedStrokePaint);
                drawLock(canvas, r, alpha);
                break;
        }
    }

    private void drawCheck(Canvas canvas, RectF r, int alpha) {
        float s = r.height();
        float cx = r.centerX();
        float cy = r.centerY();
        markPaint.setColor(withAlpha(0xFFFFFFFF, alpha));
        markPaint.setStrokeWidth(s * 0.11f);
        canvas.drawLine(cx - s * 0.22f, cy + s * 0.01f, cx - s * 0.06f, cy + s * 0.17f, markPaint);
        canvas.drawLine(cx - s * 0.06f, cy + s * 0.17f, cx + s * 0.24f, cy - s * 0.15f, markPaint);
    }

    private void drawCross(Canvas canvas, RectF r, int alpha) {
        float h = r.height() * 0.17f;
        float cx = r.centerX();
        float cy = r.centerY() + r.height() * 0.05f;
        markPaint.setColor(withAlpha(bookedMarkColor, alpha));
        markPaint.setStrokeWidth(r.height() * 0.08f);
        canvas.drawLine(cx - h, cy - h, cx + h, cy + h, markPaint);
        canvas.drawLine(cx - h, cy + h, cx + h, cy - h, markPaint);
    }

    private void drawLock(Canvas canvas, RectF r, int alpha) {
        float s = r.height();
        float cx = r.centerX();
        float cy = r.centerY();
        // Thân khóa
        seatFillPaint.setColor(withAlpha(lockedColor, alpha));
        tmpRect.set(cx - s * 0.19f, cy - s * 0.04f, cx + s * 0.19f, cy + s * 0.24f);
        canvas.drawRoundRect(tmpRect, s * 0.05f, s * 0.05f, seatFillPaint);
        // Quai khóa
        markPaint.setColor(withAlpha(lockedColor, alpha));
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

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
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
