package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class IngredientGuideScreenTest {
    @Test
    void panelsAndToolbarStayOutsideTheSidebarAtBothLayouts() {
        for (float width : new float[] {420, 640, 960, 1280}) {
            var layout = IngredientGuideScreen.guideLayout(width, 540);
            var list = layout.list();
            var detail = layout.detail();
            for (var bounds : new IngredientGuideScreen.Bounds[] {list, detail, layout.category(), layout.refresh()}) {
                assertTrue(bounds.x() >= SequoiaSidebarNavigation.WIDTH + 14);
                assertTrue(bounds.x() + bounds.width() <= width - 14);
                assertTrue(bounds.y() + bounds.height() <= 540 - 14);
                assertTrue(bounds.width() > 0 && bounds.height() > 0);
            }
            assertFalse(list.contains(detail.x() + 1, detail.y() + 1));
            assertTrue(detail.contains(detail.x() + 1, detail.y() + 1));
            assertFalse(list.contains(70, 150));
            assertFalse(layout.category().contains(layout.refresh().x() + 1, layout.refresh().y() + 1));
        }
    }

    @Test
    void narrowWindowsStackPanelsAndWideWindowsPlaceThemSideBySide() {
        var narrow = IngredientGuideScreen.guideLayout(640, 540);
        assertEquals(narrow.list().x(), narrow.detail().x());
        assertTrue(narrow.detail().y() >= narrow.list().y() + narrow.list().height() + 9);
        var wide = IngredientGuideScreen.guideLayout(960, 540);
        assertEquals(wide.list().y(), wide.detail().y());
        assertTrue(wide.detail().x() > wide.list().x() + wide.list().width());
    }

    @Test
    void limitsFarmSpotPreviewsToThree() {
        assertEquals(0, IngredientGuideScreen.farmSpotVisiblePreviewCount(-1));
        assertEquals(2, IngredientGuideScreen.farmSpotVisiblePreviewCount(2));
        assertEquals(3, IngredientGuideScreen.farmSpotVisiblePreviewCount(7));
    }

    @Test
    void keepsThreeOrFewerFarmSpotPreviewsStatic() {
        assertEquals(0, IngredientGuideScreen.farmSpotPreviewIndex(3, 0, 12_000));
        assertEquals(1, IngredientGuideScreen.farmSpotPreviewIndex(3, 1, 12_000));
        assertEquals(2, IngredientGuideScreen.farmSpotPreviewIndex(3, 2, 12_000));
    }

    @Test
    void rotatesLabelWhenAllIngredientIconsFit() {
        assertEquals(0, IngredientGuideScreen.farmSpotLabelIndex(2, 0));
        assertEquals(1, IngredientGuideScreen.farmSpotLabelIndex(2, 2_500));
        assertEquals(0, IngredientGuideScreen.farmSpotLabelIndex(2, 5_000));
    }

    @Test
    void keepsSingleIngredientLabelStaticAndRejectsEmptyLists() {
        assertEquals(0, IngredientGuideScreen.farmSpotLabelIndex(1, 12_000));
        assertEquals(-1, IngredientGuideScreen.farmSpotLabelIndex(0, 12_000));
    }

    @Test
    void rotatesThreeVisiblePreviewsWhenFarmSpotHasMoreIngredients() {
        assertEquals(0, IngredientGuideScreen.farmSpotPreviewIndex(4, 0, 0));
        assertEquals(2, IngredientGuideScreen.farmSpotPreviewIndex(4, 1, 2_500));
        assertEquals(3, IngredientGuideScreen.farmSpotPreviewIndex(4, 2, 2_500));
        assertEquals(0, IngredientGuideScreen.farmSpotPreviewIndex(4, 2, 5_000));
        assertEquals(0, IngredientGuideScreen.farmSpotPreviewIndex(4, 0, 10_000));
    }

    @Test
    void rejectsPreviewSlotsBeyondThreeIconLimit() {
        assertEquals(-1, IngredientGuideScreen.farmSpotPreviewIndex(7, 3, 0));
        assertEquals(-1, IngredientGuideScreen.farmSpotPreviewIndex(0, 0, 0));
    }
}
