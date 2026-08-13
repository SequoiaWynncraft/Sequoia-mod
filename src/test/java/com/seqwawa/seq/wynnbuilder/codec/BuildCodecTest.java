package com.seqwawa.seq.wynnbuilder.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.wynnbuilder.data.BuildEquipment;
import com.seqwawa.seq.wynnbuilder.data.CraftedItem;
import com.seqwawa.seq.wynnbuilder.data.EncodingConsts;
import com.seqwawa.seq.wynnbuilder.data.EquipmentSlot;
import com.seqwawa.seq.wynnbuilder.data.Powder;
import com.seqwawa.seq.wynnbuilder.data.Powder.PowderElement;
import com.seqwawa.seq.wynnbuilder.data.WynnBuild;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuildCodecTest {

    private static final EncodingConsts CONSTS = EncodingConsts.DEFAULT;

    private static WynnBuild newBuild() {
        return new WynnBuild(33, CONSTS.maxLevel(), CONSTS.tomeCount(), CONSTS.aspectCount());
    }

    private static WynnBuild roundTrip(WynnBuild build) {
        String hash = BuildCodec.encode(build, CONSTS, id -> null);
        return BuildCodec.decode(hash, CONSTS, id -> null);
    }

    @Test
    void encodesBinaryMarkerSoLegacyDecodersStayOut() {
        String hash = BuildCodec.encode(newBuild(), CONSTS, id -> null);
        assertTrue(BuildCodec.isBinary(hash));
        assertEquals(BuildCodec.BINARY_FLAG, WynnBase64.value(hash.charAt(0)));
        assertEquals(33, BuildCodec.peekDataVersionIndex(hash));
    }

    @Test
    void emptyBuildSurvivesRoundTrip() {
        WynnBuild decoded = roundTrip(newBuild());

        for (EquipmentSlot slot : EquipmentSlot.encodingOrder()) {
            assertTrue(decoded.equipment(slot).isEmpty(), slot + " should stay empty");
            assertTrue(decoded.powders(slot).isEmpty());
        }
        assertEquals(CONSTS.maxLevel(), decoded.level());
        assertFalse(decoded.hasManualSkillPoints());
    }

    @Test
    void equipmentIdsSurviveRoundTrip() {
        WynnBuild build = newBuild();
        build.setEquipment(EquipmentSlot.HELMET, new BuildEquipment.Normal(0));
        build.setEquipment(EquipmentSlot.WEAPON, new BuildEquipment.Normal(5428));
        build.setEquipment(EquipmentSlot.RING1, new BuildEquipment.Normal(167));

        WynnBuild decoded = roundTrip(build);

        // Item ID 0 is a real item and must not be confused with an empty slot.
        assertEquals(new BuildEquipment.Normal(0), decoded.equipment(EquipmentSlot.HELMET));
        assertEquals(new BuildEquipment.Normal(5428), decoded.equipment(EquipmentSlot.WEAPON));
        assertEquals(new BuildEquipment.Normal(167), decoded.equipment(EquipmentSlot.RING1));
        assertTrue(decoded.equipment(EquipmentSlot.BOOTS).isEmpty());
    }

    @Test
    void powdersSurviveRoundTripIncludingRepeats() {
        WynnBuild build = newBuild();
        build.setPowders(EquipmentSlot.WEAPON, List.of(
                new Powder(PowderElement.EARTH, 6),
                new Powder(PowderElement.EARTH, 6),
                new Powder(PowderElement.EARTH, 6),
                new Powder(PowderElement.THUNDER, 6)));

        WynnBuild decoded = roundTrip(build);

        assertEquals(
                List.of(
                        new Powder(PowderElement.EARTH, 6),
                        new Powder(PowderElement.EARTH, 6),
                        new Powder(PowderElement.EARTH, 6),
                        new Powder(PowderElement.THUNDER, 6)),
                decoded.powders(EquipmentSlot.WEAPON));
    }

    @Test
    void powdersAreGroupedByElementOfFirstAppearance() {
        // The game reorders applied powders this way, and the encoder relies on it.
        List<Powder> input = List.of(
                new Powder(PowderElement.EARTH, 6),
                new Powder(PowderElement.AIR, 6),
                new Powder(PowderElement.EARTH, 4),
                new Powder(PowderElement.EARTH, 6),
                new Powder(PowderElement.AIR, 6));

        List<List<Powder>> grouped = BuildCodec.groupPowders(input);

        assertEquals(2, grouped.size());
        assertEquals(
                List.of(
                        new Powder(PowderElement.EARTH, 6),
                        new Powder(PowderElement.EARTH, 4),
                        new Powder(PowderElement.EARTH, 6)),
                grouped.get(0));
        assertEquals(
                List.of(new Powder(PowderElement.AIR, 6), new Powder(PowderElement.AIR, 6)),
                grouped.get(1));
    }

    @Test
    void mixedTierPowdersUseTheChangePowderPath() {
        WynnBuild build = newBuild();
        build.setPowders(EquipmentSlot.CHESTPLATE, List.of(
                new Powder(PowderElement.FIRE, 1),
                new Powder(PowderElement.FIRE, 2),
                new Powder(PowderElement.WATER, 2)));

        WynnBuild decoded = roundTrip(build);

        assertEquals(
                List.of(
                        new Powder(PowderElement.FIRE, 1),
                        new Powder(PowderElement.FIRE, 2),
                        new Powder(PowderElement.WATER, 2)),
                decoded.powders(EquipmentSlot.CHESTPLATE));
    }

    @Test
    void accessorySlotsCarryNoPowderFlag() {
        // If an accessory wrote a powder flag the whole stream would shift by one bit.
        WynnBuild build = newBuild();
        build.setEquipment(EquipmentSlot.NECKLACE, new BuildEquipment.Normal(42));
        build.setEquipment(EquipmentSlot.WEAPON, new BuildEquipment.Normal(99));
        build.setPowders(EquipmentSlot.WEAPON, List.of(new Powder(PowderElement.WATER, 3)));

        WynnBuild decoded = roundTrip(build);

        assertEquals(new BuildEquipment.Normal(42), decoded.equipment(EquipmentSlot.NECKLACE));
        assertEquals(new BuildEquipment.Normal(99), decoded.equipment(EquipmentSlot.WEAPON));
        assertEquals(List.of(new Powder(PowderElement.WATER, 3)), decoded.powders(EquipmentSlot.WEAPON));
    }

    @Test
    void negativeSkillPointsSurviveRoundTrip() {
        WynnBuild build = newBuild();
        build.setAssignedSkillPoint(0, 150);
        build.setAssignedSkillPoint(2, -80);
        build.setAssignedSkillPoint(4, -2048);

        WynnBuild decoded = roundTrip(build);

        assertTrue(decoded.hasManualSkillPoints());
        assertEquals(150, decoded.assignedSkillPoint(0));
        assertNull(decoded.assignedSkillPoint(1));
        assertEquals(-80, decoded.assignedSkillPoint(2));
        assertEquals(-2048, decoded.assignedSkillPoint(4));
    }

    @Test
    void nonMaxLevelIsEncodedExplicitly() {
        WynnBuild build = newBuild();
        build.setLevel(63);

        assertEquals(63, roundTrip(build).level());
    }

    @Test
    void tomesAndAspectsSurviveRoundTrip() {
        WynnBuild build = newBuild();
        build.tomeIds().set(0, 12);
        build.tomeIds().set(7, 200);
        build.aspects().set(0, new WynnBuild.AspectSelection(3, 4));
        build.aspects().set(4, new WynnBuild.AspectSelection(17, 1));

        WynnBuild decoded = roundTrip(build);

        assertEquals(12, decoded.tomeIds().get(0));
        assertNull(decoded.tomeIds().get(1));
        assertEquals(200, decoded.tomeIds().get(7));
        assertEquals(new WynnBuild.AspectSelection(3, 4), decoded.aspects().get(0));
        assertEquals(new WynnBuild.AspectSelection(17, 1), decoded.aspects().get(4));
    }

    @Test
    void craftedEquipmentSurvivesRoundTrip() {
        WynnBuild build = newBuild();
        CraftedItem craft = new CraftedItem(43, List.of(100, 4000, 4000, 200, 4000, 4001), 2, 3, CraftedItem.AttackSpeed.SLOW);
        build.setEquipment(EquipmentSlot.BOOTS, new BuildEquipment.Crafted(craft));
        build.setEquipment(EquipmentSlot.WEAPON, new BuildEquipment.Normal(7));

        String hash = BuildCodec.encode(build, CONSTS, id -> "boots");
        WynnBuild decoded = BuildCodec.decode(hash, CONSTS, id -> "boots");

        BuildEquipment.Crafted result = assertInstanceOf(BuildEquipment.Crafted.class, decoded.equipment(EquipmentSlot.BOOTS));
        assertEquals(craft, result.craft());
        // The slot after a craft must still line up.
        assertEquals(new BuildEquipment.Normal(7), decoded.equipment(EquipmentSlot.WEAPON));
    }

    @Test
    void craftedWeaponCarriesAttackSpeedWithoutShiftingLaterFields() {
        WynnBuild build = newBuild();
        CraftedItem craft = new CraftedItem(500, List.of(4000, 4000, 4000, 4000, 4000, 4000), 3, 3, CraftedItem.AttackSpeed.FAST);
        build.setEquipment(EquipmentSlot.WEAPON, new BuildEquipment.Crafted(craft));
        build.setLevel(100);

        String hash = BuildCodec.encode(build, CONSTS, id -> "bow");
        WynnBuild decoded = BuildCodec.decode(hash, CONSTS, id -> "bow");

        BuildEquipment.Crafted result = assertInstanceOf(BuildEquipment.Crafted.class, decoded.equipment(EquipmentSlot.WEAPON));
        assertEquals(CraftedItem.AttackSpeed.FAST, result.craft().attackSpeed());
        assertEquals(100, decoded.level());
    }

    @Test
    void abilityTreeBitsArePreservedAcrossTheRoundTrip() {
        WynnBuild build = newBuild();
        BitVector atree = new BitVector();
        // Ability tree bits are emitted one at a time, so build the pattern the same way.
        for (char bit : "1011001".toCharArray()) {
            atree.appendBit(bit == '1');
        }
        build.setAbilityTreeBits(atree);

        WynnBuild decoded = roundTrip(build);

        // Trailing Base64 padding may extend the tail, so compare only the meaningful prefix.
        assertTrue(decoded.abilityTreeBits().length() >= 7);
        assertEquals("1011001", decoded.abilityTreeBits().toString().substring(0, 7));
    }

    @Test
    void fullyPopulatedBuildIsStable() {
        WynnBuild build = newBuild();
        for (EquipmentSlot slot : EquipmentSlot.encodingOrder()) {
            build.setEquipment(slot, new BuildEquipment.Normal(slot.ordinal() * 137));
            if (slot.powderable()) {
                build.setPowders(slot, List.of(new Powder(PowderElement.THUNDER, 6), new Powder(PowderElement.WATER, 6)));
            }
        }
        build.setLevel(106);
        build.setAssignedSkillPoint(1, 42);
        build.tomeIds().set(3, 55);
        build.aspects().set(2, new WynnBuild.AspectSelection(9, 2));

        String first = BuildCodec.encode(build, CONSTS, id -> null);
        String second = BuildCodec.encode(BuildCodec.decode(first, CONSTS, id -> null), CONSTS, id -> null);

        assertEquals(first, second, "decode then re-encode must reproduce the original hash");
    }
}
