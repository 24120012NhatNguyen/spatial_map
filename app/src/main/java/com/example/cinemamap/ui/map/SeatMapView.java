package com.example.cinemamap.ui.map;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewParent;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.cinemamap.data.CinemaLayout;
import com.example.cinemamap.data.MockCinemaRepository;
import com.example.cinemamap.data.Seat;

import java.util.Arrays;
import java.util.Collection;

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
    private static final float PAN_MARGIN_DP = 48f;
    private static final float TOUCH_TOLERANCE_DP = 10f;
    /** Không cho vùng dung sai chạm vượt quá ~40% cạnh ghế để tránh chọn nhầm ghế bên cạnh. */
    private static final float MAX_WORLD_TOLERANCE = MockCinemaRepository.SEAT_SIZE * 0.4f;
    /** Chỉ vẽ số ghế khi cạnh ghế trên màn hình đủ lớn (dp). */
    private static final float MIN_SEAT_DP_FOR_NUMBERS = 22f;

    private final float density;
    private final Viewport viewport;
    private final SeatRenderer renderer;
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    private final OverScroller scroller;
    private final float[] visibleWorld = new float[4];
    private final float[] panBounds = new float[4];

    @Nullable
    private CinemaLayout layout;
    private boolean[] selectedFlags = new boolean[0];
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

    public SeatMapView(Context context) {
        this(context, null);
    }

    public SeatMapView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SeatMapView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;
        viewport = new Viewport(FIT_PADDING_DP * density, PAN_MARGIN_DP * density);
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
    }

    // region Public API

    public void setCinemaLayout(@NonNull CinemaLayout cinemaLayout) {
        layout = cinemaLayout;
        renderer.prepare(cinemaLayout);
        selectedFlags = new boolean[cinemaLayout.getSeats().size()];
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
        renderer.draw(canvas, layout, selectedFlags, visibleWorld, scale, density, showNumbers);
        canvas.restoreToCount(save);
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
        if (layout == null || !viewport.isInAvailableArea(screenX, screenY)) {
            return; // Bỏ qua chạm vào vùng bị thẻ tóm tắt/bottom sheet che.
        }
        // Đổi điểm chạm sang tọa độ world – đúng với mọi mức Pan/Zoom.
        float worldX = viewport.screenToWorldX(screenX);
        float worldY = viewport.screenToWorldY(screenY);
        float tolerance = Math.min(TOUCH_TOLERANCE_DP * density / viewport.getScale(), MAX_WORLD_TOLERANCE);
        int index = HitTester.findSeatIndex(layout.getSeats(), worldX, worldY, tolerance);
        if (index != HitTester.NO_HIT && seatClickListener != null) {
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

    // region State saving (giữ góc nhìn khi xoay màn hình)

    @Nullable
    @Override
    protected Parcelable onSaveInstanceState() {
        SavedState state = new SavedState(super.onSaveInstanceState());
        if (viewport.isReady()) {
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
