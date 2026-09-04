package com.seqwawa.seq.integrations;

import com.seqwawa.seq.client.SeqClient;
import com.wynntils.core.components.Models;
import com.wynntils.models.elements.type.Skill;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The player's skill point totals, by way of Wynntils' own character sheet reader.
 *
 * <p>These cannot be derived. The calculator can solve the cheapest assignment that satisfies a
 * loadout's requirements, but that is a floor, not a distribution: a real player meets the
 * requirements and then pours what is left into whichever skill their build scales on. Since
 * dexterity is the whole of the critical hit rate and intelligence halves spell costs, guessing here
 * would move every number on the panel.
 *
 * <p>Wynncraft only prints the totals inside the character sheet, so Wynntils has to open it to read
 * them. That is the one thing in this feature that talks to the server, which is why it happens on
 * request rather than on its own.
 */
public final class WynntilsSkillPointAccess {
    private static final String WYNNTILS_MOD_ID = "wynntils";

    /** Strength, dexterity, intelligence, defence and agility, the order the calculator uses. */
    private static final Skill[] ORDER = {
        Skill.STRENGTH, Skill.DEXTERITY, Skill.INTELLIGENCE, Skill.DEFENCE, Skill.AGILITY
    };

    private WynntilsSkillPointAccess() {}

    /**
     * Skill point totals, gear and tome bonuses included.
     *
     * @param known whether the character sheet has been read; when false the totals are all zero and
     *     the caller should fall back rather than believe them
     */
    public record Snapshot(int[] totals, boolean known) {
        public Snapshot {
            totals = totals.clone();
        }

        public static Snapshot unknown() {
            return new Snapshot(new int[ORDER.length], false);
        }

        public int[] totals() {
            return totals.clone();
        }

        public int sum() {
            int sum = 0;
            for (int total : totals) {
                sum += total;
            }
            return sum;
        }
    }

    public static boolean isAvailable() {
        return FabricLoader.getInstance().isModLoaded(WYNNTILS_MOD_ID);
    }

    public static Snapshot snapshot() {
        if (!isAvailable()) {
            return Snapshot.unknown();
        }
        try {
            int[] totals = new int[ORDER.length];
            for (int i = 0; i < ORDER.length; i++) {
                totals[i] = Models.SkillPoint.getTotalSkillPoints(ORDER[i]);
            }
            // A character sheet that has never been read reports zeroes across the board, which no
            // real character has past the first few levels.
            boolean known = Models.SkillPoint.getTotalSum() > 0;
            return new Snapshot(totals, known);
        } catch (LinkageError | RuntimeException e) {
            SeqClient.LOGGER.debug("[Wynntils] Skill points could not be read", e);
            return Snapshot.unknown();
        }
    }

    /**
     * Asks Wynntils to read the character sheet.
     *
     * <p>Closes whatever container is open and walks the sheet in the background, so it cannot run
     * while the player is looking at their inventory.
     */
    public static boolean requestScan() {
        if (!isAvailable()) {
            return false;
        }
        try {
            Models.SkillPoint.populateSkillPoints();
            return true;
        } catch (LinkageError | RuntimeException e) {
            SeqClient.LOGGER.debug("[Wynntils] Skill point scan could not be started", e);
            return false;
        }
    }
}
