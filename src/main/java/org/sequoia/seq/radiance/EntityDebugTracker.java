package org.sequoia.seq.radiance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.Vec3;

public final class EntityDebugTracker {
    private static final double RADIANCE_MERGE_DISTANCE_SQR = 9.0;
    private static final double DUPLICATE_DETECTION_DISTANCE_SQR = RADIANCE_MERGE_DISTANCE_SQR;
    private static final long DUPLICATE_DETECTION_COOLDOWN_TICKS = 40L;
    private static final float MAX_DETECTION_DISTANCE_SQR = 64.0f * 64.0f;
    private static final int MIN_RADIANCE_CLUSTER_SIZE = 8;

    private static final Set<UUID> SEEN_ENTITIES = new HashSet<>();
    private static final Set<Integer> BAD_MODELS = new HashSet<>();
    private static final List<RecentDetection> RECENT_DETECTIONS = new ArrayList<>();
    private static float knownRadianceModel;

    private EntityDebugTracker() {}

    public static void reset() {
        SEEN_ENTITIES.clear();
        BAD_MODELS.clear();
        RECENT_DETECTIONS.clear();
        knownRadianceModel = 0.0f;
    }

    public static void confirmModel(float model) {
        if (knownRadianceModel != model) {
            knownRadianceModel = model;
        }
    }

    public static void markBadModel(float model) {
        int m = ResourcePackModelScanner.normalizeAnimatedModel(model);
        if (m > 0 && knownRadianceModel <= 0.0f) {
            BAD_MODELS.add(m);
        }
    }

    public static float getKnownModel() {
        return knownRadianceModel;
    }

    public static boolean isBadModel(float model) {
        return BAD_MODELS.contains(ResourcePackModelScanner.normalizeAnimatedModel(model));
    }

    public static boolean isRadianceModel(float model) {
        return ResourcePackModelScanner.hasRadianceModels() && ResourcePackModelScanner.isRadianceModel(model);
    }

    public static void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            SEEN_ENTITIES.clear();
            RECENT_DETECTIONS.clear();
            return;
        }

        if (!ResourcePackModelScanner.isScanned()) {
            ResourcePackModelScanner.scan(client);
        }

        List<RadianceCandidate> candidates = new ArrayList<>();
        Set<UUID> entitiesThisTick = new HashSet<>();
        long gameTime = client.level.getGameTime();
        RECENT_DETECTIONS.removeIf(detection -> gameTime - detection.tick > DUPLICATE_DETECTION_COOLDOWN_TICKS);

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player) {
                continue;
            }

            entitiesThisTick.add(entity.getUUID());
            if (SEEN_ENTITIES.contains(entity.getUUID())) {
                continue;
            }

            if (!(entity instanceof Display.ItemDisplay itemDisplay)) {
                continue;
            }

            Display.ItemDisplay.ItemRenderState itemRenderState = itemDisplay.itemRenderState();
            if (itemRenderState == null) {
                continue;
            }

            ItemStack stack = itemRenderState.itemStack();
            if (!stack.is(Items.OAK_BOAT)) {
                continue;
            }

            CustomModelData customModelData = stack.get(DataComponents.CUSTOM_MODEL_DATA);
            if (customModelData == null || customModelData.floats().isEmpty()) {
                continue;
            }

            float model = customModelData.floats().getFirst();

            if (model < ResourcePackModelScanner.MIN_DISCOVERY_MODEL) {
                continue;
            }
            if (!ResourcePackModelScanner.hasRadianceModels() && isBadModel(model)) {
                continue;
            }
            if (ResourcePackModelScanner.hasRadianceModels() && !isRadianceModel(model)) {
                continue;
            }

            Vec3 pos = entity.position();
            if (pos.distanceToSqr(client.player.position()) > MAX_DETECTION_DISTANCE_SQR) {
                continue;
            }

            candidates.add(new RadianceCandidate(pos, model));
        }

        processCandidates(candidates, gameTime);

        SEEN_ENTITIES.clear();
        SEEN_ENTITIES.addAll(entitiesThisTick);
    }

    private static void processCandidates(List<RadianceCandidate> candidates, long gameTime) {
        List<RadianceCluster> clusters = buildClusters(candidates);
        for (RadianceCluster cluster : clusters) {
            boolean knownRadiance = ResourcePackModelScanner.hasRadianceModels()
                    && ResourcePackModelScanner.isRadianceModel(cluster.model);
            if (!knownRadiance && cluster.count < MIN_RADIANCE_CLUSTER_SIZE) {
                continue;
            }
            if (isDuplicateRadiance(cluster.pos, gameTime)) {
                continue;
            }

            RECENT_DETECTIONS.add(new RecentDetection(cluster.pos, gameTime));
            RadianceSequenceHandler.onRadianceDetected(cluster.pos, cluster.model, cluster.models, knownRadiance);
        }
    }

    private static List<RadianceCluster> buildClusters(List<RadianceCandidate> candidates) {
        List<RadianceCluster> clusters = new ArrayList<>();
        for (RadianceCandidate candidate : candidates) {
            RadianceCluster cluster = findNearbyCluster(clusters, candidate.pos);
            if (cluster == null) {
                clusters.add(new RadianceCluster(candidate.pos, candidate.model));
            } else {
                cluster.add(candidate.model);
            }
        }

        return clusters;
    }

    private static RadianceCluster findNearbyCluster(List<RadianceCluster> clusters, Vec3 pos) {
        for (RadianceCluster cluster : clusters) {
            if (cluster.pos.distanceToSqr(pos) <= RADIANCE_MERGE_DISTANCE_SQR) {
                return cluster;
            }
        }

        return null;
    }

    private static boolean isDuplicateRadiance(Vec3 pos, long gameTime) {
        for (RecentDetection detection : RECENT_DETECTIONS) {
            if (gameTime - detection.tick <= DUPLICATE_DETECTION_COOLDOWN_TICKS
                    && pos.distanceToSqr(detection.pos) <= DUPLICATE_DETECTION_DISTANCE_SQR) {
                return true;
            }
        }

        return false;
    }

    private record RadianceCandidate(Vec3 pos, float model) {}

    private static final class RadianceCluster {
        private final Vec3 pos;
        private final float model;
        private final Set<Integer> models = new HashSet<>();
        private int count = 1;

        private RadianceCluster(Vec3 pos, float model) {
            this.pos = pos;
            this.model = model;
            models.add((int) model);
        }

        private void add(float model) {
            count++;
            models.add((int) model);
        }
    }

    private record RecentDetection(Vec3 pos, long tick) {}
}
