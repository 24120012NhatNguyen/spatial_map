package com.example.cinemamap.ui.map;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import com.example.cinemamap.data.CinemaLayout;
import com.example.cinemamap.data.MockCinemaRepository;
import com.example.cinemamap.data.PriceFormatter;
import com.example.cinemamap.data.Seat;
import com.example.cinemamap.data.WorldRect;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Custom View vẽ toàn bộ sơ đồ phòng chiếu trên MỘT Canvas duy nhất (không tạo View con cho ghế).
 *
 * <p>Trách nhiệm của lớp này chỉ gồm: nhận cử chỉ (Pan, Pinch-to-Zoom, Fling, Tap),
 * cập nhật {@link Viewport}, đổi điểm chạm sang tọa độ world để hit-test, và yêu cầu vẽ lại.
 * Dữ liệu ({@link CinemaLayout}) và trạng thái chọn (từ ViewModel) được truyền vào từ ngoài;
 * View không tự quyết định ghế nào được chọn – nó chỉ báo sự kiện qua {@link OnSeatClickListener}.</p>
 */
public class SeatMapView extends View {

    public interface OnSeatClickListener {
        void onSeatClick(@NonNull Seat seat);
    }

    private static final float FIT_PADDING_DP = 12f;
    private static final float TOUCH_TOLERANCE_DP = 10f;
    /** Không cho vùng dung sai chạm vượt quá ~40% cạnh ghế để tránh chọn nhầm ghế bên cạnh. */
    private static final float MAX_WORLD_TOLERANCE = MockCinemaRepository.SEAT_SIZE * 0.4f;
    /** Chỉ vẽ số ghế khi cạnh ghế trên màn hình đủ lớn (dp). */
    private static final float MIN_SEAT_DP_FOR_NUMBERS = 22f;
    /** Mức zoom tối đa luôn đủ để cạnh ghế đạt ít nhất chừng này (dp), kể cả khi vùng sơ đồ nhỏ. */
    private static final float MIN_MAX_ZOOM_SEAT_DP = 48f;
    /** Khi định vị một ghế, phóng to tới khi cạnh ghế khoảng chừng này (dp). */
    private static final float FOCUS_SEAT_DP = 40f;
    private static final long CAMERA_ANIM_MS = 450L;
    private static final long HIGHLIGHT_ANIM_MS = 1600L;

    private final float density;
    private final Viewport viewport;
    private final SeatRenderer renderer;
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    private final OverScroller scroller;
    private final float[] visibleWorld = new float[4];
    private final float[] panBounds = new float[4];
    private final float[] availableWorld = new float[4];
    private final SeatAccessibilityHelper accessibilityHelper;

    @Nullable
    private CinemaLayout layout;
    private boolean[] selectedFlags = new boolean[0];
    /** Cờ khớp bộ lọc theo chỉ số ghế; null = không lọc. */
    @Nullable
    private boolean[] filterMatches;
    /** Cờ "vẽ mờ" = không khớp bộ lọc (ghế đã chọn bị mờ nhẹ hơn và vẫn giữ dấu ✓). */
    private boolean[] dimmedFlags = new boolean[0];
    @Nullable
    private OnSeatClickListener seatClickListener;

    private float lastFocusX;
    private float lastFocusY;
    private int lastFlingX;
    private int lastFlingY;
    /** true nếu chuỗi chạm hiện tại đã có pinch – khi đó không fling lúc nhấc tay. */
    private boolean pinchedInGesture;
    @Nullable
    private SavedState pendingState;

    // Hiệu ứng camera (Reset View, định vị ghế): nội suy mức zoom theo cấp số nhân, tâm theo tuyến tính.
    private final ValueAnimator cameraAnimator = ValueAnimator.ofFloat(0f, 1f);
    private float animFromZoom;
    private float animToZoom;
    private float animFromCx;
    private float animToCx;
    private float animFromCy;
    private float animToCy;
    /** true nếu hiệu ứng đang đưa về góc nhìn mặc định (kết thúc bằng resetToDefault). */
    private boolean animToDefault;
    private boolean cameraAnimCancelled;

    // Vòng sáng nhấp nháy quanh ghế vừa được định vị.
    private final ValueAnimator highlightAnimator = ValueAnimator.ofFloat(0f, 1f);
    private int highlightIndex = HitTester.NO_HIT;

    public SeatMapView(Context context) {
        this(context, null);
    }

    public SeatMapView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SeatMapView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;
        viewport = new Viewport(FIT_PADDING_DP * density);
        viewport.setMinimumMaxScale(MIN_MAX_ZOOM_SEAT_DP * density / MockCinemaRepository.SEAT_SIZE);
        renderer = new SeatRenderer(context);
        scroller = new OverScroller(context);
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        // Tắt "quick scale" (chạm đúp rồi kéo) để chạm đúp không vô tình chọn/bỏ chọn ghế.
        scaleDetector.setQuickScaleEnabled(false);
        gestureDetector = new GestureDetector(context, new GestureListener());
        gestureDetector.setIsLongpressEnabled(false);
        // SimpleOnGestureListener cũng là OnDoubleTapListener: nếu để mặc định, lần chạm thứ hai
        // của một cặp chạm nhanh sẽ đi vào onDoubleTap thay vì onSingleTapUp và bị bỏ qua.
        gestureDetector.setOnDoubleTapListener(null);
        setClickable(true);
        setFocusable(true);
        // Mỗi ghế là một "virtual view" cho TalkBack mà vẫn vẽ trên một Canvas duy nhất.
        accessibilityHelper = new SeatAccessibilityHelper(this);
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper);
        setupAnimators();
    }

    private void setupAnimators() {
        cameraAnimator.setDuration(CAMERA_ANIM_MS);
        cameraAnimator.setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f));
        cameraAnimator.addUpdateListener(animation -> {
            float f = (float) animation.getAnimatedValue();
            float zoom = (float) (animFromZoom * Math.pow(animToZoom / animFromZoom, f));
            viewport.restore(zoom, animFromCx + (animToCx - animFromCx) * f, animFromCy + (animToCy - animFromCy) * f);
            invalidate();
        });
        cameraAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                cameraAnimCancelled = false;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                cameraAnimCancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cameraAnimCancelled) {
                    applyCameraTarget();
                    invalidate();
                }
            }
        });

        highlightAnimator.setDuration(HIGHLIGHT_ANIM_MS);
        highlightAnimator.setInterpolator(new LinearInterpolator());
        highlightAnimator.addUpdateListener(animation -> invalidate());
        highlightAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                highlightIndex = HitTester.NO_HIT;
                invalidate();
            }
        });
    }

    // region Public API

    public void setCinemaLayout(@NonNull CinemaLayout cinemaLayout) {
        layout = cinemaLayout;
        renderer.prepare(cinemaLayout);
        selectedFlags = new boolean[cinemaLayout.getSeats().size()];
        dimmedFlags = new boolean[selectedFlags.length];
        filterMatches = null;
        viewport.setContentSize(cinemaLayout.getWidth(), cinemaLayout.getHeight());
        applyPendingState();
        invalidate();
    }

    public void setOnSeatClickListener(@Nullable OnSeatClickListener listener) {
        seatClickListener = listener;
    }

    /** Đồng bộ trạng thái "đang chọn" từ nguồn dữ liệu thống nhất (ViewModel). */
    public void setSelectedSeatIds(@NonNull Collection<String> ids) {
        if (layout == null) {
            return;
        }
        Arrays.fill(selectedFlags, false);
        for (String id : ids) {
            Seat seat = layout.findSeat(id);
            if (seat != null) {
                selectedFlags[seat.getIndex()] = true;
            }
        }
        invalidate();
        accessibilityHelper.invalidateRoot();
    }

    /**
     * Kết quả bộ lọc theo chỉ số ghế (từ ViewModel); null = không lọc. Ghế không khớp được vẽ mờ;
     * ghế đang chọn mà không khớp chỉ mờ nhẹ và vẫn bỏ chọn được.
     */
    public void setFilterMatches(@Nullable boolean[] matches) {
        if (layout == null) {
            return;
        }
        if (matches != null && matches.length != selectedFlags.length) {
            throw new IllegalArgumentException("matches.length must equal the seat count");
        }
        if (Arrays.equals(filterMatches, matches)) {
            return; // Kết quả hiển thị không đổi (ví dụ "Z" → "ZZ"): không vẽ lại.
        }
        filterMatches = matches;
        updateDimmedFlags();
        invalidate();
        accessibilityHelper.invalidateRoot();
    }

    private void updateDimmedFlags() {
        for (int i = 0; i < dimmedFlags.length; i++) {
            dimmedFlags[i] = filterMatches != null && !filterMatches[i];
        }
    }

    /**
     * Reset View: đưa sơ đồ về vị trí và tỷ lệ mặc định bằng hiệu ứng mượt.
     * Chỉ đổi góc nhìn – không đụng tới bộ lọc hay danh sách ghế đang chọn.
     */
    public void resetView() {
        if (!viewport.isReady() || (viewport.isAtDefault() && !cameraAnimator.isRunning())) {
            return;
        }
        animateCamera(1f, viewport.getContentWidth() * 0.5f, viewport.getContentHeight() * 0.5f, true);
    }

    /** Định vị một ghế: đưa ghế vào giữa, phóng to đủ để đọc số ghế và nháy vòng sáng quanh ghế. */
    public void focusSeat(@NonNull String seatId) {
        if (layout == null || !viewport.isReady()) {
            return;
        }
        Seat seat = layout.findSeat(seatId);
        if (seat == null) {
            return;
        }
        float zoomForSeat = FOCUS_SEAT_DP * density / MockCinemaRepository.SEAT_SIZE / viewport.getFitScale();
        float zoom = Math.min(Math.max(currentTargetZoom(), zoomForSeat), viewport.getMaxZoomLevel());
        WorldRect b = seat.getBounds();
        animateCamera(zoom, b.centerX(), b.centerY(), false);
        highlightAnimator.cancel(); // onAnimationEnd của lần trước xóa chỉ số → gán sau khi hủy.
        highlightIndex = seat.getIndex();
        highlightAnimator.start();
    }

    /** Đưa camera tới một vùng world (ví dụ các ghế khớp từ khóa tìm kiếm), có chừa lề. */
    public void showWorldArea(@NonNull WorldRect area) {
        if (!viewport.isReady()) {
            return;
        }
        float margin = MockCinemaRepository.SEAT_SIZE;
        float zoom = Math.max(1f, viewport.getZoomLevelToFit(area.width() + 2f * margin, area.height() + 2f * margin));
        animateCamera(zoom, area.centerX(), area.centerY(), false);
    }

    private float currentTargetZoom() {
        return cameraAnimator.isRunning() && !animToDefault ? animToZoom : viewport.getZoomLevel();
    }

    private void animateCamera(float toZoom, float toCx, float toCy, boolean toDefault) {
        if (!cameraAnimator.isRunning() && !toDefault && viewport.isAtCamera(toZoom, toCx, toCy)) {
            return; // Đã ở đúng đích (ví dụ bấm Tìm lần nữa): không vẽ lại 450ms vô ích.
        }
        scroller.forceFinished(true);
        cameraAnimator.cancel();
        animFromZoom = viewport.getZoomLevel();
        animFromCx = viewport.getWorldCenterX();
        animFromCy = viewport.getWorldCenterY();
        animToZoom = toZoom;
        animToCx = toCx;
        animToCy = toCy;
        animToDefault = toDefault;
        cameraAnimator.start();
    }

    private void applyCameraTarget() {
        if (animToDefault) {
            viewport.resetToDefault();
        } else {
            viewport.restore(animToZoom, animToCx, animToCy);
        }
    }

    /** Vùng bị che bởi các thành phần nổi phía trên sơ đồ (px). */
    public void setMapInsets(float left, float top, float right, float bottom) {
        if (viewport.setInsets(left, top, right, bottom)) {
            invalidate();
        }
    }

    @NonNull
    public Viewport getViewport() {
        return viewport;
    }

    // endregion

    // region Layout & drawing

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewport.setViewSize(w, h);
        applyPendingState();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (layout == null || !viewport.isReady()) {
            return;
        }
        float scale = viewport.getScale();
        viewport.getVisibleWorldRect(visibleWorld);
        boolean showNumbers = MockCinemaRepository.SEAT_SIZE * scale >= MIN_SEAT_DP_FOR_NUMBERS * density;

        int save = canvas.save();
        canvas.translate(viewport.getTranslateX(), viewport.getTranslateY());
        canvas.scale(scale, scale);
        renderer.draw(canvas, layout, selectedFlags, dimmedFlags, visibleWorld, scale, density, showNumbers);
        if (highlightIndex != HitTester.NO_HIT) {
            renderer.drawHighlight(canvas, highlightIndex, highlightAnimator.getAnimatedFraction(), scale, density);
        }
        canvas.restoreToCount(save);
    }

    @Override
    protected void onDetachedFromWindow() {
        cameraAnimator.cancel();
        highlightAnimator.cancel();
        super.onDetachedFromWindow();
    }

    // endregion

    // region Touch handling

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (layout == null || !viewport.isReady()) {
            return super.onTouchEvent(event);
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            scroller.forceFinished(true);
            cameraAnimator.cancel(); // Người dùng chạm vào là dừng hiệu ứng camera, giữ nguyên chỗ đang dừng.
            pinchedInGesture = false;
            ViewParent parent = getParent();
            if (parent != null) {
                parent.requestDisallowInterceptTouchEvent(true);
            }
        }
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            int x = scroller.getCurrX();
            int y = scroller.getCurrY();
            viewport.panBy(x - lastFlingX, y - lastFlingY);
            lastFlingX = x;
            lastFlingY = y;
            // Scroller đã được giới hạn theo biên Pan nên sẽ tự dừng ở biên.
            postInvalidateOnAnimation();
        }
    }

    private void handleTap(float screenX, float screenY) {
        int index = findSeatAt(screenX, screenY);
        if (index != HitTester.NO_HIT) {
            dispatchSeatClick(index);
        }
    }

    /** Hit-test một điểm màn hình; trả về chỉ số ghế hoặc {@link HitTester#NO_HIT}. */
    private int findSeatAt(float screenX, float screenY) {
        if (layout == null || !viewport.isReady() || !viewport.isInAvailableArea(screenX, screenY)) {
            return HitTester.NO_HIT; // Bỏ qua chạm vào vùng bị thành phần nổi (bottom sheet) che.
        }
        // Đổi điểm chạm sang tọa độ world – đúng với mọi mức Pan/Zoom.
        float worldX = viewport.screenToWorldX(screenX);
        float worldY = viewport.screenToWorldY(screenY);
        float tolerance = Math.min(TOUCH_TOLERANCE_DP * density / viewport.getScale(), MAX_WORLD_TOLERANCE);
        // Chỉ xét ghế người dùng đang nhìn thấy.
        viewport.getAvailableWorldRect(availableWorld);
        return HitTester.findSeatIndex(layout.getSeats(), worldX, worldY, tolerance,
                availableWorld[0], availableWorld[1], availableWorld[2], availableWorld[3]);
    }

    private void dispatchSeatClick(int index) {
        if (layout != null && seatClickListener != null) {
            seatClickListener.onSeatClick(layout.getSeats().get(index));
        }
    }

    private final class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
            scroller.forceFinished(true);
            pinchedInGesture = true;
            lastFocusX = detector.getFocusX();
            lastFocusY = detector.getFocusY();
            return true;
        }

        @Override
        public boolean onScale(@NonNull ScaleGestureDetector detector) {
            float focusX = detector.getFocusX();
            float focusY = detector.getFocusY();
            // Điểm bản đồ dưới tâm hai ngón tay luôn "dính" theo tâm đó khi vừa zoom vừa di chuyển.
            boolean changed = viewport.transform(lastFocusX, lastFocusY, focusX, focusY, detector.getScaleFactor());
            lastFocusX = focusX;
            lastFocusY = focusY;
            if (changed) {
                postInvalidateOnAnimation();
            }
            return true;
        }
    }

    private final class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(@NonNull MotionEvent e) {
            return true;
        }

        @Override
        public boolean onScroll(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
            if (scaleDetector.isInProgress()) {
                return false; // Pan khi đang pinch đã được xử lý trong ScaleListener.
            }
            if (viewport.panBy(-distanceX, -distanceY)) {
                postInvalidateOnAnimation();
            }
            return true;
        }

        @Override
        public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
            if (scaleDetector.isInProgress() || pinchedInGesture) {
                return false; // Không fling khi vừa kết thúc pinch.
            }
            // Giới hạn scroller theo đúng khoảng Pan còn lại để fling dừng tự nhiên ở biên.
            viewport.getTranslationBounds(panBounds);
            float tx = viewport.getTranslateX();
            float ty = viewport.getTranslateY();
            lastFlingX = 0;
            lastFlingY = 0;
            scroller.fling(0, 0, Math.round(velocityX), Math.round(velocityY),
                    Math.round(panBounds[0] - tx), Math.round(panBounds[1] - tx),
                    Math.round(panBounds[2] - ty), Math.round(panBounds[3] - ty));
            postInvalidateOnAnimation();
            return true;
        }

        @Override
        public boolean onSingleTapUp(@NonNull MotionEvent e) {
            performClick();
            handleTap(e.getX(), e.getY());
            return true;
        }
    }

    // endregion

    // region Accessibility (TalkBack)

    @Override
    protected boolean dispatchHoverEvent(MotionEvent event) {
        return accessibilityHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return accessibilityHelper.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, @Nullable Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        accessibilityHelper.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
    }

    private final class SeatAccessibilityHelper extends ExploreByTouchHelper {
        private final Rect seatBounds = new Rect();

        SeatAccessibilityHelper(@NonNull View host) {
            super(host);
        }

        @Override
        protected int getVirtualViewAt(float x, float y) {
            int index = findSeatAt(x, y);
            return index == HitTester.NO_HIT ? ExploreByTouchHelper.INVALID_ID : index;
        }

        @Override
        protected void getVisibleVirtualViews(List<Integer> virtualViewIds) {
            if (layout == null || !viewport.isReady()) {
                return;
            }
            viewport.getAvailableWorldRect(availableWorld);
            List<Seat> seats = layout.getSeats();
            for (int i = 0, n = seats.size(); i < n; i++) {
                if (seats.get(i).getBounds().intersects(
                        availableWorld[0], availableWorld[1], availableWorld[2], availableWorld[3])) {
                    virtualViewIds.add(i);
                }
            }
        }

        // ExploreByTouchHelper vẫn yêu cầu setBoundsInParent() cho virtual view.
        @SuppressWarnings("deprecation")
        @Override
        protected void onPopulateNodeForVirtualView(int virtualViewId, @NonNull AccessibilityNodeInfoCompat node) {
            if (layout == null || virtualViewId < 0 || virtualViewId >= layout.getSeats().size()) {
                node.setContentDescription("");
                node.setBoundsInParent(seatBounds);
                return;
            }
            Seat seat = layout.getSeats().get(virtualViewId);
            boolean selected = selectedFlags[virtualViewId];
            String state = selected
                    ? getContext().getString(com.example.cinemamap.R.string.a11y_state_selected)
                    : seat.getStatus().getDisplayName();
            if (dimmedFlags[virtualViewId]) {
                state = getContext().getString(com.example.cinemamap.R.string.a11y_state_filtered_out, state);
            }
            node.setContentDescription(getContext().getString(com.example.cinemamap.R.string.a11y_seat,
                    seat.getId(), seat.getType().getDisplayName(), PriceFormatter.format(seat.getPrice()), state));
            node.setSelected(selected);
            node.setClickable(true);
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
            RectF r = renderer.getSeatRect(virtualViewId);
            seatBounds.set(
                    Math.round(viewport.worldToScreenX(r.left)), Math.round(viewport.worldToScreenY(r.top)),
                    Math.round(viewport.worldToScreenX(r.right)), Math.round(viewport.worldToScreenY(r.bottom)));
            node.setBoundsInParent(seatBounds);
        }

        @Override
        protected boolean onPerformActionForVirtualView(int virtualViewId, int action, @Nullable Bundle arguments) {
            if (action == AccessibilityNodeInfoCompat.ACTION_CLICK && layout != null
                    && virtualViewId >= 0 && virtualViewId < layout.getSeats().size()) {
                dispatchSeatClick(virtualViewId);
                return true;
            }
            return false;
        }
    }

    // endregion

    // region State saving (giữ góc nhìn khi xoay màn hình)

    @Nullable
    @Override
    protected Parcelable onSaveInstanceState() {
        SavedState state = new SavedState(super.onSaveInstanceState());
        if (viewport.isReady() && cameraAnimator.isRunning()) {
            // Đang chạy hiệu ứng: lưu đích đến để sau khi xoay màn hình góc nhìn đúng như dự định.
            state.hasViewport = true;
            state.atDefault = animToDefault;
            state.zoomLevel = animToZoom;
            state.worldCenterX = animToCx;
            state.worldCenterY = animToCy;
        } else if (viewport.isReady()) {
            state.hasViewport = true;
            state.atDefault = viewport.isAtDefault();
            state.zoomLevel = viewport.getZoomLevel();
            state.worldCenterX = viewport.getWorldCenterX();
            state.worldCenterY = viewport.getWorldCenterY();
        } else if (pendingState != null) {
            state.copyViewportFrom(pendingState);
        }
        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState saved = (SavedState) state;
        super.onRestoreInstanceState(saved.getSuperState());
        pendingState = saved.hasViewport ? saved : null;
        applyPendingState();
    }

    private void applyPendingState() {
        if (pendingState == null || !viewport.isReady()) {
            return;
        }
        if (pendingState.atDefault) {
            viewport.resetToDefault();
        } else {
            viewport.restore(pendingState.zoomLevel, pendingState.worldCenterX, pendingState.worldCenterY);
        }
        pendingState = null;
        invalidate();
    }

    /** Phải là public để Parcel truy cập được CREATOR bằng reflection khi khôi phục tiến trình. */
    public static class SavedState extends BaseSavedState {
        boolean hasViewport;
        boolean atDefault;
        float zoomLevel;
        float worldCenterX;
        float worldCenterY;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            hasViewport = in.readInt() != 0;
            atDefault = in.readInt() != 0;
            zoomLevel = in.readFloat();
            worldCenterX = in.readFloat();
            worldCenterY = in.readFloat();
        }

        void copyViewportFrom(SavedState other) {
            hasViewport = other.hasViewport;
            atDefault = other.atDefault;
            zoomLevel = other.zoomLevel;
            worldCenterX = other.worldCenterX;
            worldCenterY = other.worldCenterY;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(hasViewport ? 1 : 0);
            out.writeInt(atDefault ? 1 : 0);
            out.writeFloat(zoomLevel);
            out.writeFloat(worldCenterX);
            out.writeFloat(worldCenterY);
        }

        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    // endregion
}
