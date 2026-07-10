package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.model.RankProfilesResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class RankProfileBadgeAssetCacheTest {
    @Test
    void acceptsOnlyHashedPngAssetsFromTheAssetHost() throws Exception {
        byte[] bytes = "badge".getBytes(StandardCharsets.UTF_8);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        RankProfilesResponse.AssetDefinition asset = new RankProfilesResponse.AssetDefinition(
                "badge.wtp.gold",
                "https://assets.seqwawa.com/v1/" + digest + "/wtp_gold.png",
                "image/png",
                digest);

        assertTrue(RankProfileBadgeAssetCache.isValid(asset));
        assertTrue(RankProfileBadgeAssetCache.matchesDigest(bytes, digest));
        assertFalse(RankProfileBadgeAssetCache.matchesDigest("other".getBytes(StandardCharsets.UTF_8), digest));
    }

    @Test
    void rejectsAssetsFromUnexpectedOrigins() {
        RankProfilesResponse.AssetDefinition asset = new RankProfilesResponse.AssetDefinition(
                "badge.wtp.gold",
                "https://example.com/wtp_gold.png",
                "image/png",
                "a".repeat(64));

        assertFalse(RankProfileBadgeAssetCache.isValid(asset));
    }
}
