package com.seqwawa.seq.wynnbuilder.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WynnDataVersionsTest {

    /** The shape of upstream's declaration, with the list cut short. */
    private static final String SCRIPT =
            """
            // Aspects and tomes
            const wynn_version_names = [
                '2.0.1.1',
                '2.0.1.2',
                "2.2.0.12"
            ];

            const WYNN_VERSION_LATEST = wynn_version_names.length - 1;
            """;

    @Test
    void readsTheVersionListFromUpstreamsScript() {
        assertEquals(List.of("2.0.1.1", "2.0.1.2", "2.2.0.12"), WynnDataVersions.parseUpstreamScript(SCRIPT));
    }

    @Test
    void aScriptWithoutTheDeclarationYieldsNothing() {
        assertTrue(WynnDataVersions.parseUpstreamScript("const other = ['2.0.1.1'];").isEmpty());
        assertTrue(WynnDataVersions.parseUpstreamScript(null).isEmpty());
    }

    @Test
    void upstreamsListIsAdoptedInItsOwnOrder() {
        WynnDataVersions builtIn = WynnDataVersions.builtIn();
        List<String> upstream = new ArrayList<>(builtIn.all());
        upstream.add("2.3.0.0");

        WynnDataVersions adopted = builtIn.adopt(upstream);

        assertEquals("2.3.0.0", adopted.latest());
        assertEquals(builtIn.size(), adopted.indexOf("2.3.0.0"), "the new version takes the next index");
    }

    @Test
    void aDamagedListIsIgnored() {
        WynnDataVersions builtIn = WynnDataVersions.builtIn();

        assertSame(builtIn, builtIn.adopt(List.of("2.0.1.1")), "a truncated list would renumber every link");

        List<String> withJunk = new ArrayList<>(builtIn.all());
        withJunk.add("baseline");
        assertSame(builtIn, builtIn.adopt(withJunk));

        List<String> withDuplicate = new ArrayList<>(builtIn.all());
        withDuplicate.add(builtIn.latest());
        assertSame(builtIn, builtIn.adopt(withDuplicate));
    }
}
