package com.example.cinemamap.ui.map;

/**
 * Quản lý phép biến đổi giữa tọa độ màn hình (screen, pixel) và tọa độ bản đồ (world).
 *
 * <pre>
 *   screenX = worldX * scale + translateX
 *   screenY = worldY * scale + translateY
 *
 *   worldX  = (screenX - translateX) / scale
 *   worldY  = (screenY - translateY) / scale
 * </pre>
 *
 * Lớp này không phụ thuộc Android SDK để có thể kiểm thử bằng JUnit thuần.
 * Mọi phép Pan/Zoom đều đi qua đây nên giới hạn tỷ lệ và giới hạn biên luôn được áp dụng.
 */
public final class Viewport {

    /** Tỷ lệ nhỏ nhất so với tỷ lệ "vừa khít màn hình". */
    public static final float MIN_ZOOM = 0.75f;
    /** Tỷ lệ lớn nhất so với tỷ lệ "vừa khít màn hình". */
    public static final float MAX_ZOOM = 4f;

    private final float fitPadding;
    private final float panMargin;

    private float contentWidth;
    private float contentHeight;
    private float viewWidth;
    private float viewHeight;
    private float insetLeft;
    private float insetTop;
    private float insetRight;
    private float insetBottom;

    private float fitScale = 1f;
    private float minScale = MIN_ZOOM;
    private float maxScale = MAX_ZOOM;

    private float scale = 1f;
    private float translateX;
    private float translateY;
    /** true khi đang ở góc nhìn mặc định (chưa Pan/Zoom kể từ lần đặt lại gần nhất). */
    private boolean atDefault = true;

    /**
     * @param fitPadding khoảng đệm (px) quanh sơ đồ ở góc nhìn mặc định
     * @param panMargin  khoảng trống tối đa (px) được phép kéo lộ ra ở mép sơ đồ
     */
    public Viewport(float fitPadding, float panMargin) {
        this.fitPadding = fitPadding;
        this.panMargin = panMargin;
    }

    public boolean isReady() {
        return viewWidth > 0 && viewHeight > 0 && contentWidth > 0 && contentHeight > 0;
    }

    public void setContentSize(float width, float height) {
        contentWidth = width;
        contentHeight = height;
        recomputeScaleLimits();
        if (isReady()) {
            resetToDefault();
        }
    }

    /**
     * Cập nhật kích thước View. Nếu đã có góc nhìn trước đó, giữ nguyên mức zoom tương đối
     * và điểm bản đồ ở tâm (ví dụ khi đổi kích thước cửa sổ).
     */
    public void setViewSize(float width, float height) {
        boolean hadState = isReady();
        float zoom = 1f;
        float centerX = 0f;
        float centerY = 0f;
        if (hadState) {
            zoom = getZoomLevel();
            centerX = getWorldCenterX();
            centerY = getWorldCenterY();
        }
        viewWidth = width;
        viewHeight = height;
        recomputeScaleLimits();
        if (!isReady()) {
            return;
        }
        if (hadState && !atDefault) {
            restore(zoom, centerX, centerY);
        } else {
            resetToDefault();
        }
    }

    /**
     * Vùng bị che (thẻ tóm tắt, bottom sheet...) – không tính vào vùng hiển thị sơ đồ.
     * Nếu người dùng đã Pan/Zoom, giữ nguyên mức zoom tương đối và điểm bản đồ ở tâm.
     *
     * @return true nếu giá trị inset thay đổi
     */
    public boolean setInsets(float left, float top, float right, float bottom) {
        left = Math.max(0f, left);
        top = Math.max(0f, top);
        right = Math.max(0f, right);
        bottom = Math.max(0f, bottom);
        if (left == insetLeft && top == insetTop && right == insetRight && bottom == insetBottom) {
            return false;
        }
        boolean keepCamera = isReady() && !atDefault;
        float zoom = keepCamera ? getZoomLevel() : 1f;
        float centerX = keepCamera ? getWorldCenterX() : 0f;
        float centerY = keepCamera ? getWorldCenterY() : 0f;
        insetLeft = left;
        insetTop = top;
        insetRight = right;
        insetBottom = bottom;
        recomputeScaleLimits();
        if (isReady()) {
            if (keepCamera) {
                restore(zoom, centerX, centerY);
            } else {
                resetToDefault();
            }
        }
        return true;
    }

    /** Góc nhìn mặc định: toàn bộ sơ đồ vừa khít và nằm giữa vùng hiển thị. */
    public void resetToDefault() {
        scale = fitScale;
        translateX = availableCenterX() - contentWidth * scale * 0.5f;
        translateY = availableCenterY() - contentHeight * scale * 0.5f;
        clampTranslation();
        atDefault = true;
    }

    /**
     * Đặt góc nhìn theo mức zoom tương đối và điểm bản đồ cần nằm ở tâm vùng hiển thị.
     * Dùng để khôi phục góc nhìn sau khi xoay màn hình.
     */
    public void restore(float zoomLevel, float worldCenterX, float worldCenterY) {
        scale = clamp(zoomLevel * fitScale, minScale, maxScale);
        translateX = availableCenterX() - worldCenterX * scale;
        translateY = availableCenterY() - worldCenterY * scale;
        clampTranslation();
        atDefault = false;
    }

    /**
     * Dịch chuyển sơ đồ theo pixel màn hình.
     *
     * @return true nếu góc nhìn thực sự thay đổi (false khi đã chạm biên)
     */
    public boolean panBy(float dx, float dy) {
        float oldX = translateX;
        float oldY = translateY;
        translateX += dx;
        translateY += dy;
        clampTranslation();
        boolean moved = oldX != translateX || oldY != translateY;
        if (moved) {
            atDefault = false;
        }
        return moved;
    }

    /**
     * Phóng to/thu nhỏ quanh tiêu điểm (focusX, focusY) trên màn hình.
     * Điểm bản đồ nằm dưới tiêu điểm được giữ cố định:
     * <pre>
     *   s'  = clamp(s * factor)
     *   k   = s' / s
     *   t'  = focus - (focus - t) * k
     * </pre>
     */
    public boolean zoomAround(float focusX, float focusY, float factor) {
        if (!isValidFactor(factor)) {
            return false;
        }
        return transform(focusX, focusY, focusX, focusY, factor);
    }

    /**
     * Phép biến đổi của cử chỉ hai ngón: điểm bản đồ nằm dưới tâm hai ngón ở khung trước
     * ({@code previousFocus}) được đưa tới tâm hai ngón hiện tại ({@code focus}) với tỷ lệ mới.
     * Tính translation từ cả hai tiêu điểm rồi mới giới hạn biên MỘT lần, nên tiêu điểm
     * không bị lệch khi đang ở sát biên.
     * <pre>
     *   world = (previousFocus - t) / s
     *   s'    = clamp(s * factor)
     *   t'    = clampPan(focus - world * s')
     * </pre>
     *
     * @return true nếu góc nhìn thay đổi (để chỉ vẽ lại khi cần)
     */
    public boolean transform(float previousFocusX, float previousFocusY, float focusX, float focusY, float factor) {
        if (!isValidFactor(factor)) {
            factor = 1f;
        }
        float worldX = screenToWorldX(previousFocusX);
        float worldY = screenToWorldY(previousFocusY);
        float oldScale = scale;
        float oldX = translateX;
        float oldY = translateY;
        scale = clamp(scale * factor, minScale, maxScale);
        translateX = focusX - worldX * scale;
        translateY = focusY - worldY * scale;
        clampTranslation();
        boolean changed = scale != oldScale || translateX != oldX || translateY != oldY;
        if (changed) {
            atDefault = false;
        }
        return changed;
    }

    /** Điểm màn hình có nằm trong vùng hiển thị sơ đồ (không bị inset che) hay không. */
    public boolean isInAvailableArea(float screenX, float screenY) {
        return screenX >= insetLeft && screenX <= viewWidth - insetRight
                && screenY >= insetTop && screenY <= viewHeight - insetBottom;
    }

    /** Ghi khoảng translation hợp lệ ở tỷ lệ hiện tại vào {@code out} = [minX, maxX, minY, maxY]. */
    public void getTranslationBounds(float[] out) {
        float scaledWidth = contentWidth * scale;
        float scaledHeight = contentHeight * scale;
        float startX = insetLeft;
        float endX = viewWidth - insetRight;
        float startY = insetTop;
        float endY = viewHeight - insetBottom;
        out[0] = axisMin(scaledWidth, startX, endX);
        out[1] = axisMax(scaledWidth, startX, endX);
        out[2] = axisMin(scaledHeight, startY, endY);
        out[3] = axisMax(scaledHeight, startY, endY);
    }

    public float screenToWorldX(float screenX) {
        return (screenX - translateX) / scale;
    }

    public float screenToWorldY(float screenY) {
        return (screenY - translateY) / scale;
    }

    public float worldToScreenX(float worldX) {
        return worldX * scale + translateX;
    }

    public float worldToScreenY(float worldY) {
        return worldY * scale + translateY;
    }

    /** Ghi vùng bản đồ đang nhìn thấy vào {@code out} = [left, top, right, bottom] (world). */
    public void getVisibleWorldRect(float[] out) {
        out[0] = screenToWorldX(0f);
        out[1] = screenToWorldY(0f);
        out[2] = screenToWorldX(viewWidth);
        out[3] = screenToWorldY(viewHeight);
    }

    public float getScale() {
        return scale;
    }

    public float getTranslateX() {
        return translateX;
    }

    public float getTranslateY() {
        return translateY;
    }

    public float getMinScale() {
        return minScale;
    }

    public float getMaxScale() {
        return maxScale;
    }

    public float getFitScale() {
        return fitScale;
    }

    public boolean isAtDefault() {
        return atDefault;
    }

    /** Mức zoom tương đối: 1 = vừa khít màn hình. */
    public float getZoomLevel() {
        return scale / fitScale;
    }

    public float getWorldCenterX() {
        return screenToWorldX(availableCenterX());
    }

    public float getWorldCenterY() {
        return screenToWorldY(availableCenterY());
    }

    private void recomputeScaleLimits() {
        float availableWidth = viewWidth - insetLeft - insetRight - 2f * fitPadding;
        float availableHeight = viewHeight - insetTop - insetBottom - 2f * fitPadding;
        if (availableWidth <= 0f || availableHeight <= 0f || contentWidth <= 0f || contentHeight <= 0f) {
            return;
        }
        fitScale = Math.min(availableWidth / contentWidth, availableHeight / contentHeight);
        minScale = fitScale * MIN_ZOOM;
        maxScale = fitScale * MAX_ZOOM;
    }

    /**
     * Giới hạn Pan: sơ đồ không bao giờ bị kéo ra khỏi vùng hiển thị.
     * Mép sơ đồ chỉ được lùi vào trong tối đa {@code panMargin} px; nếu sơ đồ nhỏ hơn vùng
     * hiển thị thì toàn bộ sơ đồ phải nằm trong vùng đó.
     */
    private void clampTranslation() {
        translateX = clampAxis(translateX, contentWidth * scale, insetLeft, viewWidth - insetRight);
        translateY = clampAxis(translateY, contentHeight * scale, insetTop, viewHeight - insetBottom);
    }

    private float clampAxis(float translate, float scaledContent, float start, float end) {
        return clamp(translate, axisMin(scaledContent, start, end), axisMax(scaledContent, start, end));
    }

    // a = vị trí khi mép phải/dưới của sơ đồ chạm (end - margin)
    // b = vị trí khi mép trái/trên của sơ đồ chạm (start + margin)
    private float axisMin(float scaledContent, float start, float end) {
        return Math.min(end - panMargin - scaledContent, start + panMargin);
    }

    private float axisMax(float scaledContent, float start, float end) {
        return Math.max(end - panMargin - scaledContent, start + panMargin);
    }

    private static boolean isValidFactor(float factor) {
        return factor > 0f && !Float.isNaN(factor) && !Float.isInfinite(factor);
    }

    private float availableCenterX() {
        return (insetLeft + viewWidth - insetRight) * 0.5f;
    }

    private float availableCenterY() {
        return (insetTop + viewHeight - insetBottom) * 0.5f;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
