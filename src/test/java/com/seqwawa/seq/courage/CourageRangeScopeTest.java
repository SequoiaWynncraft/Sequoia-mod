package com.seqwawa.seq.courage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CourageRangeScopeTest {
    @Test
    void detectsTheRaidSidebarHeader() {
        assertTrue(CourageRangeScope.isRaidSidebarActive(
                List.of("Sequoia", "Raid: The Nameless Anomaly", "Challenges: 2/4")));
    }

    @Test
    void ignoresAPlainPartySidebar() {
        assertFalse(CourageRangeScope.isRaidSidebarActive(List.of("Sequoia", "Party:", "Soup Person")));
    }

    @Test
    void toleratesMissingSidebarLines() {
        assertFalse(CourageRangeScope.isRaidSidebarActive(Arrays.asList(null, "")));
    }
}
