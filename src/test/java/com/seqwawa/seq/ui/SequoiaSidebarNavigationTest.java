package com.seqwawa.seq.ui;

import static com.seqwawa.seq.ui.SequoiaSidebarNavigation.Destination.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class SequoiaSidebarNavigationTest {
    @Test
    void warPlannerIsOnlyListedForAuthorizedMembers() {
        assertEquals(
                List.of(CONNECTION, GITHUB, INGREDIENTS, MAP, PARTY_FINDER, SETTINGS),
                SequoiaSidebarNavigation.destinations(false));
        assertEquals(
                List.of(CONNECTION, GITHUB, INGREDIENTS, MAP, PARTY_FINDER, SETTINGS, WAR),
                SequoiaSidebarNavigation.destinations(true));
    }
}
