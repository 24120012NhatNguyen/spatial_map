package com.example.cinemamap.ui.map;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.example.cinemamap.R;
import com.example.cinemamap.data.MockCinemaRepository;
import com.example.cinemamap.data.SeatStatus;
import com.example.cinemamap.data.SeatType;

import java.util.ArrayList;
import java.util.List;

/**
 * Bảng chú thích gọn: mỗi mục là một ký hiệu ghế + nhãn, tự xuống dòng theo bề rộng.
 * Ký hiệu được vẽ bằng chính {@link SeatRenderer} của sơ đồ nên luôn khớp với Canvas.
 */
public class LegendView extends View {

    private static final float GLYPH_HEIGHT_DP = 14f;
    private static final float GLYPH_TEXT_GAP_DP = 5f;
    private static final float ITEM_GAP_DP = 14f;
    private static final float LINE_GAP_DP = 6f;
    private static final float TEXT_SIZE_SP = 12f;

    private static final class Item {
        final SeatType type;
        final SeatStatus status;
        final boolean selected;
        final boolean dimmed;
        /** Vẽ thêm vòng sáng "đang định vị". */
        final boolean highlight;
        final RectF glyph;
        final String label;
        float x;
        float y;
        float width;

        Item(SeatType type, SeatStatus status, boolean selected, boolean dimmed, boolean highlight,
             RectF glyph, String label) {
            this.type = type;
            this.status = status;
            this.selected = selected;
            this.dimmed = dimmed;
            this.highlight = highlight;
            this.glyph = glyph;
            this.label = label;
        }
    }

    private final SeatRenderer renderer;
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Item> items = new ArrayList<>();
    private final float glyphScale;
    private final float glyphHeight;
    private final float glyphTextGap;
    private final float itemGap;
    private final float lineGap;
    private final float lineHeight;
    private final float textBaseline;
    private final float density;
    /** Khoảng chừa quanh ký hiệu cho vòng sáng (px). */
    private final float ringInset;
    /** Bề rộng dòng dài nhất của lần xếp gần nhất. */
    private float widestLine;

    public LegendView(Context context) {
        this(context, null);
    }

    public LegendView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        ringInset = 3f * density;
        renderer = new SeatRenderer(context);
        textPaint.setColor(ContextCompat.getColor(context, R.color.on_surface_muted));
        textPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP,
                getResources().getDisplayMetrics()));
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textHeight = fm.descent - fm.ascent;

        glyphHeight = GLYPH_HEIGHT_DP * density;
        glyphScale = glyphHeight / MockCinemaRepository.SEAT_SIZE;
        glyphTextGap = GLYPH_TEXT_GAP_DP * density;
        itemGap = ITEM_GAP_DP * density;
        lineGap = LINE_GAP_DP * density;
        lineHeight = Math.max(glyphHeight, textHeight);
        textBaseline = lineHeight * 0.5f - (fm.ascent + fm.descent) * 0.5f;

        float seat = MockCinemaRepository.SEAT_SIZE;
        RectF single = new RectF(0f, 0f, seat, seat);
        RectF couple = new RectF(0f, 0f, seat * 2f + MockCinemaRepository.SEAT_GAP, seat);
        add(SeatType.STANDARD, SeatStatus.AVAILABLE, false, false, false, single, R.string.legend_standard);
        add(SeatType.VIP, SeatStatus.AVAILABLE, false, false, false, single, R.string.legend_vip);
        add(SeatType.COUPLE, SeatStatus.AVAILABLE, false, false, false, couple, R.string.legend_couple);
        add(SeatType.STANDARD, SeatStatus.AVAILABLE, true, false, false, single, R.string.legend_selected);
        add(SeatType.STANDARD, SeatStatus.BOOKED, false, false, false, single, R.string.legend_booked);
        add(SeatType.STANDARD, SeatStatus.LOCKED, false, false, false, single, R.string.legend_locked);
        add(SeatType.STANDARD, SeatStatus.AVAILABLE, false, true, false, single, R.string.legend_dimmed);
        add(SeatType.STANDARD, SeatStatus.AVAILABLE, false, false, true, single, R.string.legend_located);

        List<String> labels = new ArrayList<>(items.size());
        for (Item item : items) {
            labels.add(item.label);
        }
        setContentDescription(context.getString(R.string.legend_description, TextUtils.join(", ", labels)));
    }

    private void add(SeatType type, SeatStatus status, boolean selected, boolean dimmed, boolean highlight,
                     RectF glyph, @StringRes int label) {
        Item item = new Item(type, status, selected, dimmed, highlight, glyph, getContext().getString(label));
        float ring = highlight ? 2f * ringInset : 0f;
        item.width = ring + glyph.width() * glyphScale + glyphTextGap + textPaint.measureText(item.label);
        items.add(item);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        float maxLine;
        if (widthMode == MeasureSpec.UNSPECIFIED) {
            maxLine = Float.MAX_VALUE;
        } else {
            maxLine = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        }
        int lines = layoutItems(maxLine);
        int desiredWidth = Math.round(widestLine) + getPaddingLeft() + getPaddingRight();
        int desiredHeight = Math.round(lines * lineHeight + (lines - 1) * lineGap)
                + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec));
    }

    /** Xếp các mục thành dòng (vị trí tương đối trong vùng nội dung); trả về số dòng. */
    private int layoutItems(float maxLine) {
        int lines = 1;
        float x = 0f;
        float y = 0f;
        widestLine = 0f;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (x > 0f && x + item.width > maxLine) {
                widestLine = Math.max(widestLine, x - itemGap);
                lines++;
                x = 0f;
                y += lineHeight + lineGap;
            }
            item.x = x;
            item.y = y;
            x += item.width + itemGap;
        }
        widestLine = Math.max(widestLine, x - itemGap);
        return lines;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutItems(w - getPaddingLeft() - getPaddingRight());
        centerLines(w - getPaddingLeft() - getPaddingRight());
    }

    /** Căn giữa từng dòng theo chiều ngang. */
    private void centerLines(float available) {
        int start = 0;
        while (start < items.size()) {
            float lineY = items.get(start).y;
            int end = start;
            while (end + 1 < items.size() && items.get(end + 1).y == lineY) {
                end++;
            }
            Item last = items.get(end);
            float lineWidth = last.x + last.width;
            float offset = Math.max(0f, (available - lineWidth) * 0.5f);
            for (int i = start; i <= end; i++) {
                items.get(i).x += offset;
            }
            start = end + 1;
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float left = getPaddingLeft();
        float top = getPaddingTop();
        float glyphTop = (lineHeight - glyphHeight) * 0.5f;
        for (int i = 0, n = items.size(); i < n; i++) {
            Item item = items.get(i);
            float ring = item.highlight ? ringInset : 0f;
            float x = left + item.x + ring;
            float y = top + item.y;
            int save = canvas.save();
            canvas.translate(x, y + glyphTop);
            canvas.scale(glyphScale, glyphScale);
            renderer.drawSeatGlyph(canvas, item.glyph, item.type, item.status, item.selected, item.dimmed, null);
            if (item.highlight) {
                // Vòng sáng tĩnh, cùng hình với vòng nhấp nháy trên sơ đồ (độ dày quy đổi về dp).
                renderer.drawHighlightRing(canvas, item.glyph, 0f, glyphScale * 2.2f, density);
            }
            canvas.restoreToCount(save);
            canvas.drawText(item.label, x + item.glyph.width() * glyphScale + ring + glyphTextGap,
                    y + textBaseline, textPaint);
        }
    }
}
