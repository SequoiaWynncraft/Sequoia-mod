package com.seqwawa.seq.managers;

import com.mojang.blaze3d.platform.NativeImage;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.RankProfilesResponse;
import com.seqwawa.seq.model.SeqBadge;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

final class RankProfileBadgeAssetCache {
    private static final int MAX_ASSET_BYTES = 2 * 1024 * 1024;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private RankProfileBadgeAssetCache() {}

    static void refresh(RankProfilesResponse.Catalog catalog) {
        supportedAssets(catalog).forEach((badge, asset) -> load(badge, asset).exceptionally(throwable -> {
            SeqClient.LOGGER.debug(
                    "[LeaderboardBadges] Could not load catalog asset {}: {}",
                    asset.key(),
                    rootMessage(throwable));
            return null;
        }));
    }

    static Map<SeqBadge, RankProfilesResponse.AssetDefinition> supportedAssets(
            RankProfilesResponse.Catalog catalog) {
        if (catalog == null) {
            return Map.of();
        }
        Map<String, SeqBadge> badges = LeaderboardBadgeService.badgeDefinitions(catalog);
        Map<String, RankProfilesResponse.AssetDefinition> assets = new HashMap<>();
        if (catalog.assets() != null) {
            catalog.assets().stream()
                    .filter(asset -> asset != null && asset.key() != null)
                    .forEach(asset -> assets.put(asset.key(), asset));
        }

        Map<SeqBadge, RankProfilesResponse.AssetDefinition> supported = new HashMap<>();
        if (catalog.roles() != null) {
            catalog.roles().stream()
                    .filter(role -> role != null)
                    .forEach(role -> add(supported, badges.get(role.key()), assets.get(role.assetKey())));
        }
        if (catalog.awards() != null) {
            catalog.awards().stream()
                    .filter(award -> award != null)
                    .forEach(award -> add(supported, badges.get(award.key()), assets.get(award.assetKey())));
        }
        return Map.copyOf(supported);
    }

    private static void add(
            Map<SeqBadge, RankProfilesResponse.AssetDefinition> target,
            SeqBadge badge,
            RankProfilesResponse.AssetDefinition asset) {
        if (badge != null && isValid(asset)) {
            target.put(badge, asset);
        }
    }

    private static CompletableFuture<Void> load(
            SeqBadge badge, RankProfilesResponse.AssetDefinition asset) {
        return bytes(asset).thenCompose(bytes -> register(badge, asset, bytes));
    }

    private static CompletableFuture<byte[]> bytes(RankProfilesResponse.AssetDefinition asset) {
        Path cached = cachePath(asset.sha256());
        try {
            if (Files.isRegularFile(cached)) {
                if (Files.size(cached) > MAX_ASSET_BYTES) {
                    Files.deleteIfExists(cached);
                    return download(asset, cached);
                }
                byte[] bytes = Files.readAllBytes(cached);
                if (matchesDigest(bytes, asset.sha256())) {
                    return CompletableFuture.completedFuture(bytes);
                }
                Files.deleteIfExists(cached);
            }
        } catch (IOException exception) {
            SeqClient.LOGGER.debug("[LeaderboardBadges] Could not read cached asset {}", asset.key(), exception);
        }

        return download(asset, cached);
    }

    private static CompletableFuture<byte[]> download(
            RankProfilesResponse.AssetDefinition asset, Path cached) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(asset.url()))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "image/png")
                .GET()
                .build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply(response -> {
            if (response.statusCode() != 200) {
                throw new IllegalStateException("asset request returned status " + response.statusCode());
            }
            byte[] bytes = response.body();
            if (bytes.length == 0
                    || bytes.length > MAX_ASSET_BYTES
                    || !matchesDigest(bytes, asset.sha256())) {
                throw new IllegalStateException("asset content did not match its catalog digest");
            }
            write(cached, bytes);
            return bytes;
        });
    }

    private static CompletableFuture<Void> register(
            SeqBadge badge, RankProfilesResponse.AssetDefinition asset, byte[] bytes) {
        final NativeImage image;
        try {
            image = NativeImage.read(bytes);
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(exception);
        }

        CompletableFuture<Void> registered = new CompletableFuture<>();
        Minecraft.getInstance().execute(() -> {
            try {
                DynamicTexture texture = new DynamicTexture(() -> "Sequoia badge " + asset.key(), image);
                Minecraft.getInstance().getTextureManager().register(badge.textureId(), texture);
                registered.complete(null);
            } catch (RuntimeException exception) {
                image.close();
                registered.completeExceptionally(exception);
            }
        });
        return registered;
    }

    static boolean isValid(RankProfilesResponse.AssetDefinition asset) {
        if (asset == null
                || asset.key() == null
                || asset.url() == null
                || !"image/png".equalsIgnoreCase(asset.contentType())
                || asset.sha256() == null
                || !asset.sha256().matches("[0-9a-fA-F]{64}")) {
            return false;
        }
        try {
            URI uri = URI.create(asset.url());
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "assets.seqwawa.com".equalsIgnoreCase(uri.getHost());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static boolean matchesDigest(byte[] bytes, String expected) {
        try {
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            return actual.equalsIgnoreCase(expected);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void write(Path target, byte[] bytes) {
        try {
            Files.createDirectories(target.getParent());
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(temp, bytes);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            SeqClient.LOGGER.debug("[LeaderboardBadges] Could not cache badge asset {}", target, exception);
        }
    }

    private static Path cachePath(String digest) {
        return FabricLoader.getInstance()
                .getGameDir()
                .resolve("config")
                .resolve("sequoia")
                .resolve("cache")
                .resolve("rank-profile-badge-assets")
                .resolve(digest.toLowerCase() + ".png");
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null || current.getMessage() == null
                ? "unknown"
                : current.getMessage().replace('\n', ' ').replace('\r', ' ');
    }
}
