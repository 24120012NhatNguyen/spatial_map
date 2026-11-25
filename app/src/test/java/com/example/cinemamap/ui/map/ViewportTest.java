package com.example.cinemamap.ui.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class ViewportTest {

    private static final float EPS = 1e-3f;
    private static final float CONTENT_W = 1000f;
    private static final float CONTENT_H = 800f;
    private static final float VIEW_W = 1080f;
    private static final float VIEW_H = 1920f;
    private static final float MARGIN = 100f;

    private Viewport viewport;

    @Before
    public void setUp() {
        viewport = new Viewport(20f, MARGIN);
        viewport.setContentSize(CONTENT_W, CONTENT_H);
        viewport.setViewSize(VIEW_W, VIEW_H);
    }

    @Test
    public void defaultViewFitsAndCentersContent() {
        assertTrue(viewport.isAtDefault());
        assertEquals((VIEW_W - 40f) / CONTENT_W, viewport.getScale(), EPS);
        float left = viewport.worldToScreenX(0f);
        float right = viewport.worldToScreenX(CONTENT_W);
        float top = viewport.worldToScreenY(0f);
        float bottom = viewport.worldToScreenY(CONTENT_H);
        assertEquals(VIEW_W / 2f, (left + right) / 2f, EPS);
        assertEquals(VIEW_H / 2f, (top + bottom) / 2f, EPS);
    }

    @Test
    public void screenWorldRoundTripAfterPanAndZoom() {
        viewport.zoomAround(300f, 900f, 2.5f);
        viewport.panBy(-120f, 75f);
        float[][] points = {{0f, 0f}, {123.4f, 567.8f}, {VIEW_W, VIEW_H}, {540f, 960f}};
        for (float[] p : points) {
            float wx = viewport.screenToWorldX(p[0]);
            float wy = viewport.screenToWorldY(p[1]);
            assertEquals(p[0], viewport.worldToScreenX(wx), EPS);
            assertEquals(p[1], viewport.worldToScreenY(wy), EPS);
        }
    }

    @Test
    public void zoomKeepsWorldPointUnderFocus() {
        float focusX = 540f;
        float focusY = 960f;
        float worldX = viewport.screenToWorldX(focusX);
        float worldY = viewport.screenToWorldY(focusY);
        viewport.zoomAround(focusX, focusY, 2f);
        assertEquals(worldX, viewport.screenToWorldX(focusX), EPS);
        assertEquals(worldY, viewport.screenToWorldY(focusY), EPS);
        assertFalse(viewport.isAtDefault());
    }

    @Test
    public void zoomIsClampedToLimits() {
        viewport.zoomAround(540f, 960f, 100f);
        assertEquals(viewport.getMaxScale(), viewport.getScale(), EPS);
        viewport.zoomAround(540f, 960f, 0.0001f);
        assertEquals(viewport.getMinScale(), viewport.getScale(), EPS);
    }

    @Test
    public void invalidZoomFactorIsIgnored() {
        float before = viewport.getScale();
        viewport.zoomAround(540f, 960f, Float.NaN);
        viewport.zoomAround(540f, 960f, -2f);
        viewport.zoomAround(540f, 960f, 0f);
        assertEquals(before, viewport.getScale(), EPS);
    }

    @Test
    public void panCannotPushContentOffScreen() {
        viewport.zoomAround(540f, 960f, 3f);
        viewport.panBy(-100_000f, -100_000f);
        assertContentVisible();
        viewport.panBy(100_000f, 100_000f);
        assertContentVisible();
        // Khi đã ở biên, pan tiếp theo cùng hướng không còn làm thay đổi góc nhìn.
        assertFalse(viewport.panBy(500f, 500f));
    }

    @Test
    public void panIsLimitedWhenContentSmallerThanView() {
        viewport.zoomAround(540f, 960f, 0.1f); // về tỷ lệ nhỏ nhất
        viewport.panBy(5000f, 5000f);
        assertTrue(viewport.worldToScreenX(CONTENT_W) <= VIEW_W - MARGIN + EPS);
        assertTrue(viewport.worldToScreenY(CONTENT_H) <= VIEW_H - MARGIN + EPS);
        assertContentVisible();
    }

    @Test
    public void restoreKeepsZoomAndCenterAcrossSizeChange() {
        viewport.zoomAround(200f, 400f, 2f);
        float zoom = viewport.getZoomLevel();
        float cx = viewport.getWorldCenterX();
        float cy = viewport.getWorldCenterY();
        viewport.setViewSize(VIEW_H, VIEW_W); // xoay ngang
        assertEquals(zoom, viewport.getZoomLevel(), EPS);
        assertEquals(cx, viewport.getWorldCenterX(), 1f);
        assertEquals(cy, viewport.getWorldCenterY(), 1f);
    }

    @Test
    public void defaultViewStaysDefaultAfterSizeChange() {
        viewport.setViewSize(VIEW_H, VIEW_W);
        assertTrue(viewport.isAtDefault());
        assertEquals(1f, viewport.getZoomLevel(), EPS);
    }

    @Test
    public void insetsShrinkAvailableArea() {
        viewport.setInsets(0f, 0f, 0f, 600f);
        float bottom = viewport.worldToScreenY(CONTENT_H);
        assertTrue(bottom <= VIEW_H - 600f);
        assertTrue(viewport.isAtDefault());
    }

    @Test
    public void movingPinchKeepsFocalPointNearPanLimit() {
        viewport.zoomAround(540f, 960f, 2f);
        viewport.panBy(-100_000f, 0f); // sát biên phải
        float worldX = viewport.screenToWorldX(700f);
        float worldY = viewport.screenToWorldY(960f);
        // Tâm hai ngón di chuyển từ x=700 sang x=760 trong khi zoom 1.2×.
        assertTrue(viewport.transform(700f, 960f, 760f, 960f, 1.2f));
        assertEquals(worldX, viewport.screenToWorldX(760f), 0.5f);
        assertEquals(worldY, viewport.screenToWorldY(960f), 0.5f);
    }

    @Test
    public void zoomAtLimitReportsNoChange() {
        viewport.zoomAround(540f, 960f, 100f);
        assertFalse(viewport.zoomAround(540f, 960f, 1.5f));
        assertFalse(viewport.transform(540f, 960f, 540f, 960f, 1.5f));
    }

    @Test
    public void insetsKeepCameraAfterUserMovedMap() {
        viewport.zoomAround(300f, 700f, 2f);
        float zoom = viewport.getZoomLevel();
        float cx = viewport.getWorldCenterX();
        float cy = viewport.getWorldCenterY();
        assertTrue(viewport.setInsets(0f, 0f, 0f, 300f));
        assertEquals(zoom, viewport.getZoomLevel(), EPS);
        assertEquals(cx, viewport.getWorldCenterX(), 1f);
        assertEquals(cy, viewport.getWorldCenterY(), 1f);
        assertFalse("unchanged insets", viewport.setInsets(0f, 0f, 0f, 300f));
    }

    @Test
    public void availableAreaExcludesInsets() {
        viewport.setInsets(0f, 0f, 400f, 300f);
        assertTrue(viewport.isInAvailableArea(10f, 10f));
        assertFalse(viewport.isInAvailableArea(VIEW_W - 100f, 10f));
        assertFalse(viewport.isInAvailableArea(10f, VIEW_H - 100f));
    }

    @Test
    public void translationBoundsMatchClamping() {
        viewport.zoomAround(540f, 960f, 3f);
        float[] bounds = new float[4];
        viewport.getTranslationBounds(bounds);
        viewport.panBy(-100_000f, -100_000f);
        assertEquals(bounds[0], viewport.getTranslateX(), EPS);
        assertEquals(bounds[2], viewport.getTranslateY(), EPS);
        viewport.panBy(100_000f, 100_000f);
        assertEquals(bounds[1], viewport.getTranslateX(), EPS);
        assertEquals(bounds[3], viewport.getTranslateY(), EPS);
    }

    private void assertContentVisible() {
        float left = viewport.worldToScreenX(0f);
        float right = viewport.worldToScreenX(CONTENT_W);
        float top = viewport.worldToScreenY(0f);
        float bottom = viewport.worldToScreenY(CONTENT_H);
        assertTrue("content left of screen", right > 0f);
        assertTrue("content right of screen", left < VIEW_W);
        assertTrue("content above screen", bottom > 0f);
        assertTrue("content below screen", top < VIEW_H);
    }
}
