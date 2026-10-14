package com.seqwawa.seq.courage;

import com.seqwawa.seq.model.WynnClassType;
import com.seqwawa.seq.utils.WynnClassCache;
import com.seqwawa.seq.utils.WynnWeaponClassIndex;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Tracks the Shamans whose Courage aura should be drawn and how many players
 * currently stand inside each one.
 *
 * <p>A player's class is what decides whether they get a ring. The held weapon is
 * only how that class is first observed for someone other than the local player,
 * so once a player is known the ring no longer depends on what they are holding.
 */
public final class CourageRangeTracker {
    /** Courage buffs allies within four blocks of the Acolyte. */
    public static final double RADIUS = 4.0;

    /** Bounds a long session spent around many players; classes are dropped least-recently-seen first. */
    static final int MAX_KNOWN_CLASSES = 512;

    /** Wynncraft's scoreboard name for every player entity it drives itself. */
    static final String NPC_SCOREBOARD_NAME = "?";

    private static final double MAX_TRACK_DISTANCE = 64.0;
    private static final int MAX_CIRCLES = 8;

    private static final Map<UUID, WynnClassType> knownClasses = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, WynnClassType> eldest) {
            return size() > MAX_KNOWN_CLASSES;
        }
    };
    private static volatile List<CourageCircle> circles = List.of();

    private CourageRangeTracker() {}

    /** Snapshot of the active Courage rings, nearest first with the local player leading. */
    public static List<CourageCircle> circles() {
        return circles;
    }

    /** The local player's own aura, or {@code null} when they are not a Shaman. */
    public static CourageCircle ownCircle() {
        for (CourageCircle circle : circles) {
            if (circle.self()) {
                return circle;
            }
        }
        return null;
    }

    /** The other players' auras the local player is currently standing in, nearest first. */
    public static List<CourageCircle> coveringCircles() {
        return circles.stream().filter(circle -> !circle.self() && circle.coversLocalPlayer()).toList();
    }

    public static void reset() {
        knownClasses.clear();
        circles = List.of();
    }

    public static void tick(Minecraft client) {
        ClientPacketListener connection = client.getConnection();
        if (client.level == null || client.player == null || connection == null) {
            reset();
            return;
        }

        WynnWeaponClassIndex.scan(client.getResourceManager());

        List<AbstractClientPlayer> players = trackablePlayers(client, realPlayerIds(connection));
        List<PositionedPlayer> positions = new ArrayList<>(players.size());
        for (AbstractClientPlayer player : players) {
            positions.add(new PositionedPlayer(player.getUUID(), player.position()));
        }

        Vec3 localPosition = client.player.position();
        List<CourageCircle> updated = new ArrayList<>();
        for (AbstractClientPlayer player : players) {
            WynnClassType classType = classOf(client, player);
            if (classType != WynnClassType.SHAMAN) continue;
            if (player.position().distanceToSqr(localPosition) > MAX_TRACK_DISTANCE * MAX_TRACK_DISTANCE) continue;

            boolean self = player == client.player;
            updated.add(new CourageCircle(
                    player,
                    self,
                    countPlayersInRange(player.position(), player.getUUID(), positions, RADIUS),
                    !self && isInRange(player.position(), localPosition, RADIUS)));
        }

        updated.sort(Comparator.comparing((CourageCircle circle) -> !circle.self())
                .thenComparingDouble(circle -> circle.player().position().distanceToSqr(localPosition)));
        circles = List.copyOf(updated.size() > MAX_CIRCLES ? updated.subList(0, MAX_CIRCLES) : updated);
    }

    /** Counts everyone standing inside the ring, excluding the Shaman at its centre. */
    static int countPlayersInRange(Vec3 center, UUID sourceId, List<PositionedPlayer> players, double radius) {
        int count = 0;
        for (PositionedPlayer player : players) {
            if (player.id().equals(sourceId)) continue;
            if (isInRange(center, player.position(), radius)) {
                count++;
            }
        }
        return count;
    }

    /**
     * The ring is drawn as a cylinder, so range is measured the same way: inside the
     * circle on the ground and no more than a ring radius above or below it.
     */
    static boolean isInRange(Vec3 center, Vec3 position, double radius) {
        double dx = position.x - center.x;
        double dz = position.z - center.z;
        return dx * dx + dz * dz <= radius * radius && Math.abs(position.y - center.y) <= radius;
    }

    private static List<AbstractClientPlayer> trackablePlayers(Minecraft client, Set<UUID> realPlayerIds) {
        List<AbstractClientPlayer> players = new ArrayList<>();
        for (AbstractClientPlayer player : client.level.players()) {
            if (player.isSpectator()) continue;
            if (!isRealPlayer(
                    player.getScoreboardName(),
                    player.getUUID(),
                    player == client.player,
                    realPlayerIds)) {
                continue;
            }
            players.add(player);
        }
        return players;
    }

    /**
     * Pets, NPCs and mobs are all spawned as player entities, so the world's player
     * list alone would count a shopkeeper standing next to the Shaman.
     *
     * <p>Two things separate a real account. Wynncraft gives every entity it drives
     * itself the scoreboard name {@value #NPC_SCOREBOARD_NAME} — the same single
     * marker Wynntils keys its own NPC test on — and a real player is also listed in
     * the server's tab roster.
     */
    static boolean isRealPlayer(
            String scoreboardName, UUID playerId, boolean local, Set<UUID> realPlayerIds) {
        if (NPC_SCOREBOARD_NAME.equals(scoreboardName)) {
            return false;
        }
        return local || realPlayerIds.contains(playerId);
    }

    private static Set<UUID> realPlayerIds(ClientPacketListener connection) {
        Set<UUID> ids = new HashSet<>();
        for (PlayerInfo playerInfo : connection.getListedOnlinePlayers()) {
            ids.add(playerInfo.getProfile().id());
        }
        return ids;
    }

    /**
     * Reads the local player's class from the authoritative provider and any other
     * player's from the weapon they are holding, the only class marker Wynncraft
     * puts on the wire for them.
     */
    private static WynnClassType classOf(Minecraft client, AbstractClientPlayer player) {
        WynnClassType observed =
                player == client.player ? WynnClassCache.resolveLocalClassType() : null;
        if (observed == null) {
            observed = WynnWeaponClassIndex.classOf(player.getMainHandItem());
        }
        return rememberClass(knownClasses, player.getUUID(), observed);
    }

    /**
     * A class only ever needs observing once. Holding a consumable, a scroll, or
     * nothing at all leaves the player the class they were already seen playing.
     */
    static WynnClassType rememberClass(
            Map<UUID, WynnClassType> classes, UUID playerId, WynnClassType observed) {
        if (observed == null) {
            return classes.get(playerId);
        }
        classes.put(playerId, observed);
        return observed;
    }

    /** A Shaman's Courage ring and the number of players standing inside it. */
    public record CourageCircle(
            AbstractClientPlayer player, boolean self, int playersInRange, boolean coversLocalPlayer) {}

    record PositionedPlayer(UUID id, Vec3 position) {}
}
