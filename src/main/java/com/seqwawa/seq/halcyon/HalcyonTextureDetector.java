package com.seqwawa.seq.halcyon;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class HalcyonTextureDetector {
	private static final double MAX_DISTANCE_SQR = 64.0 * 64.0;
	private static final int MIN_CLUSTER_SIZE = 2;
	private static final int MIN_DISTINCT_POSITIONS = 2;
	private static final int MIN_RING_POSITIONS = 4;
	private static final int ANGLE_SECTORS = 8;
	private static final int MIN_RING_SECTORS = 5;
	private static final int MIN_STABLE_OBSERVATIONS = 2;
	private static final int PENDING_EXPIRE_TICKS = 8;
	private static final int FIRST_RANGE_CAPTURE_TICKS = 20;
	private static final int CAPTURE_MISSING_TICKS = 3;
	private static final double MIN_RING_RANGE = 1.0;
	private static final double MAX_RING_RANGE_SPREAD = 1.5;

	private static LearnedTextureGroup learnedGroup;
	private static final Map<TextureGroupKey, PendingTextureGroup> pendingGroups = new LinkedHashMap<>();
	private static double currentRange;
	private static double capturedRange;
	private static boolean rangeKnown;
	private static int observationTick;
	private static int rangeCaptureStartedTick;
	private static int lastRangeCaptureTick;

	private HalcyonTextureDetector() {
	}

	public static void reset() {
		learnedGroup = null;
		pendingGroups.clear();
		currentRange = 0.0;
		capturedRange = 0.0;
		rangeKnown = false;
		observationTick = 0;
		rangeCaptureStartedTick = 0;
		lastRangeCaptureTick = 0;
	}

	public static double getCurrentRange() {
		return currentRange;
	}

	public static boolean hasKnownRange() {
		return rangeKnown;
	}

	public static void tick(Minecraft client) {
		if (client.level == null || client.player == null) {
			reset();
			return;
		}

		if (!ResourcePackModelScanner.isScanned()) {
			ResourcePackModelScanner.scan(client);
		}

		if (!HalcyonHeldItem.isHoldingHalcyon(client)) {
			pendingGroups.clear();
			return;
		}

		if (rangeKnown) {
			return;
		}

		observationTick++;
		List<Candidate> candidates = collectCandidates(client);
		updateFromBestTextureGroup(candidates);

	}

	private static void updateFromBestTextureGroup(List<Candidate> candidates) {
		Map<TextureGroupKey, TextureGroupStats> groups = new LinkedHashMap<>();

		for (Candidate candidate : candidates) {
			TextureGroupKey key = new TextureGroupKey(candidate.itemName(), candidate.modelInfo().texture());
			groups.computeIfAbsent(key, ignored -> new TextureGroupStats(candidate)).add(candidate);
		}

		if (learnedGroup == null) {
			learnFirstStableRangeGroup(groups);
			return;
		}

		captureFirstRangePulse(groups);
	}

	private static void learnFirstStableRangeGroup(Map<TextureGroupKey, TextureGroupStats> groups) {
		Iterator<Map.Entry<TextureGroupKey, PendingTextureGroup>> iterator = pendingGroups.entrySet().iterator();
		while (iterator.hasNext()) {
			PendingTextureGroup pendingGroup = iterator.next().getValue();
			if (observationTick - pendingGroup.lastSeenTick() > PENDING_EXPIRE_TICKS) {
				iterator.remove();
			}
		}

		for (Map.Entry<TextureGroupKey, TextureGroupStats> entry : groups.entrySet()) {
			TextureGroupStats group = entry.getValue();
			if (!group.isRangeLike()) continue;

			Candidate sample = group.sample();
			Set<Integer> textureModels = ResourcePackModelScanner.getModelsWithSameTexture(sample.itemName(), sample.model());
			if (textureModels.isEmpty()) continue;

			PendingTextureGroup pendingGroup = pendingGroups.computeIfAbsent(entry.getKey(), ignored -> new PendingTextureGroup());
			pendingGroup.observe();
			if (!pendingGroup.isStable()) continue;

			learnedGroup = new LearnedTextureGroup(sample.itemName(), sample.modelInfo().texture(), textureModels);
			capturedRange = group.farthestRange();
			rangeCaptureStartedTick = observationTick;
			lastRangeCaptureTick = observationTick;

			return;
		}
	}

	private static void captureFirstRangePulse(Map<TextureGroupKey, TextureGroupStats> groups) {
		TextureGroupStats group = groups.get(new TextureGroupKey(learnedGroup.itemName(), learnedGroup.texture()));
		if (group != null && group.isRangeLike()) {
			for (Candidate candidate : group.candidates()) {
				if (learnedGroup.matches(candidate)) {
					capturedRange = Math.max(capturedRange, candidate.horizontalDistance());
					lastRangeCaptureTick = observationTick;
				}
			}
		}

		boolean captureTimedOut = observationTick - rangeCaptureStartedTick >= FIRST_RANGE_CAPTURE_TICKS;
		boolean firstPulseEnded = observationTick - lastRangeCaptureTick >= CAPTURE_MISSING_TICKS;
		if (capturedRange <= 0.0 || (!captureTimedOut && !firstPulseEnded)) return;

		currentRange = capturedRange;
		rangeKnown = true;
	}

	private static List<Candidate> collectCandidates(Minecraft client) {
		List<Candidate> candidates = new ArrayList<>();

		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity == client.player) continue;
			if (!(entity instanceof Display.ItemDisplay itemDisplay)) continue;

			Display.ItemDisplay.ItemRenderState renderState = itemDisplay.itemRenderState();
			if (renderState == null) continue;

			ItemStack stack = renderState.itemStack();
			CustomModelData customModelData = stack.get(DataComponents.CUSTOM_MODEL_DATA);
			if (customModelData == null || customModelData.floats().isEmpty()) continue;

			Vec3 pos = entity.position();
			double distanceSqr = pos.distanceToSqr(client.player.position());
			if (distanceSqr > MAX_DISTANCE_SQR) continue;

			float model = customModelData.floats().getFirst();
			String itemName = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
			ResourcePackModelScanner.ModelInfo modelInfo = ResourcePackModelScanner.getModelInfo(itemName, model);
			if (modelInfo == null || modelInfo.texture() == null) continue;

			candidates.add(new Candidate(
				itemName,
				model,
				ResourcePackModelScanner.normalizeAnimatedModel(model),
				modelInfo,
				pos
			));
		}

		return candidates;
	}

	private record Candidate(
		String itemName,
		float model,
		int baseModel,
		ResourcePackModelScanner.ModelInfo modelInfo,
		Vec3 pos
	) {
		private double horizontalDistance() {
			Minecraft client = Minecraft.getInstance();
			if (client.player == null) return 0.0;

			double dx = pos.x - client.player.getX();
			double dz = pos.z - client.player.getZ();
			return Math.sqrt(dx * dx + dz * dz);
		}

		private int angleSector() {
			Minecraft client = Minecraft.getInstance();
			if (client.player == null) return 0;

			double dx = pos.x - client.player.getX();
			double dz = pos.z - client.player.getZ();
			double angle = Math.atan2(dz, dx) + Math.PI;
			return Math.min(ANGLE_SECTORS - 1, (int) (angle / (Math.PI * 2.0) * ANGLE_SECTORS));
		}
	}

	private record TextureGroupKey(String itemName, String texture) {
	}

	private record PositionKey(long x, long y, long z) {
		private static PositionKey from(Vec3 pos) {
			return new PositionKey(
				Math.round(pos.x * 8.0),
				Math.round(pos.y * 8.0),
				Math.round(pos.z * 8.0)
			);
		}
	}

	private record LearnedTextureGroup(String itemName, String texture, Set<Integer> models) {
		private boolean matches(Candidate candidate) {
			return itemName.equals(candidate.itemName())
				&& texture.equals(candidate.modelInfo().texture())
				&& models.contains(candidate.baseModel());
		}
	}

	private static final class PendingTextureGroup {
		private int lastSeenTick = -1;
		private int stableObservations;

		private void observe() {
			if (lastSeenTick == observationTick - 1) {
				stableObservations++;
			} else {
				stableObservations = 1;
			}

			lastSeenTick = observationTick;
		}

		private boolean isStable() {
			return stableObservations >= MIN_STABLE_OBSERVATIONS;
		}

		private int lastSeenTick() {
			return lastSeenTick;
		}
	}

	private static final class TextureGroupStats {
		private final Candidate sample;
		private final List<Candidate> candidates = new ArrayList<>();
		private final Set<PositionKey> positions = new HashSet<>();
		private final Set<Integer> sectors = new HashSet<>();
		private final Set<Integer> models = new TreeSet<>();
		private int count;
		private double nearestRange = Double.MAX_VALUE;
		private double farthestRange;

		private TextureGroupStats(Candidate sample) {
			this.sample = sample;
		}

		private void add(Candidate candidate) {
			candidates.add(candidate);
			count++;
			positions.add(PositionKey.from(candidate.pos()));
			models.add(candidate.baseModel());

			double range = candidate.horizontalDistance();
			nearestRange = Math.min(nearestRange, range);
			farthestRange = Math.max(farthestRange, range);
			sectors.add(candidate.angleSector());
		}

		private boolean isValid() {
			return count >= MIN_CLUSTER_SIZE && positions.size() >= MIN_DISTINCT_POSITIONS && models.size() >= MIN_CLUSTER_SIZE;
		}

		private boolean isRangeLike() {
			if (!isValid()) return false;
			if (positions.size() < MIN_RING_POSITIONS) return false;
			if (sectors.size() < MIN_RING_SECTORS) return false;
			if (nearestRange < MIN_RING_RANGE) return false;

			return rangeSpread() <= MAX_RING_RANGE_SPREAD;
		}

		private Candidate sample() {
			return sample;
		}

		private List<Candidate> candidates() {
			return candidates;
		}

		private double farthestRange() {
			return farthestRange;
		}

		private double rangeSpread() {
			if (nearestRange == Double.MAX_VALUE) return 0.0;
			return farthestRange - nearestRange;
		}
	}
}
