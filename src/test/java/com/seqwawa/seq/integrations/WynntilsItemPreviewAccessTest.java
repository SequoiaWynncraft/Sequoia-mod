package com.seqwawa.seq.integrations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.seqwawa.seq.model.ChatItemPreview;
import com.wynntils.models.character.type.ClassType;
import com.wynntils.models.gear.type.ConsumableType;
import com.wynntils.models.gear.type.GearAttackSpeed;
import com.wynntils.models.gear.type.GearRequirements;
import com.wynntils.models.gear.type.GearType;
import com.wynntils.models.items.items.game.CraftedConsumableItem;
import com.wynntils.models.items.items.game.CraftedGearItem;
import com.wynntils.models.items.items.game.MountItem;
import com.wynntils.models.elements.type.Element;
import com.wynntils.models.elements.type.Skill;
import com.wynntils.models.mount.type.MountColorInfo;
import com.wynntils.models.mount.type.MountInfo;
import com.wynntils.models.mount.type.MountStat;
import com.wynntils.models.mount.type.MountType;
import com.wynntils.models.stats.type.DamageType;
import com.wynntils.models.wynnitem.type.ConsumableEffect;
import com.wynntils.models.wynnitem.type.NamedItemEffect;
import com.wynntils.utils.type.CappedValue;
import com.wynntils.utils.type.Pair;
import com.wynntils.utils.type.RangedValue;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WynntilsItemPreviewAccessTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void createsCraftedGearBaseStatsAndRequirementsSections() {
        CraftedGearItem gear = new CraftedGearItem(
                "Forged Spear",
                GearType.SPEAR,
                GearAttackSpeed.FAST,
                725,
                1250,
                List.of(Pair.of(DamageType.NEUTRAL, RangedValue.of(450, 600))),
                List.of(Pair.of(Element.FIRE, 80)),
                new GearRequirements(
                        105,
                        Optional.of(ClassType.WARRIOR),
                        List.of(Pair.of(Skill.STRENGTH, 70)),
                        Optional.of("A Journey Beyond")),
                List.of(),
                List.of(),
                List.of(),
                3,
                true,
                new CappedValue(180, 220),
                0);

        List<ChatItemPreview.Section> sections = WynntilsItemPreviewAccess.sections(gear);

        assertEquals(
                List.of(
                        "Attack Speed: Fast",
                        "Average DPS: 725",
                        "Health: +1250",
                        "Neutral Damage: 450–600",
                        "Fire Defence: +80"),
                sections.get(0).lines());
        assertEquals(
                List.of(
                        "Combat Level: 105",
                        "Class: Warrior",
                        "Strength: 70",
                        "Quest: A Journey Beyond"),
                sections.get(1).lines());
    }

    @Test
    void createsEveryMountV3Section() {
        MountInfo info = new MountInfo(
                84,
                new MountColorInfo(1, "midnight", "Midnight", List.of()),
                new MountColorInfo(2, "silver", "Silver", List.of()),
                new CappedValue(73, 100),
                Map.of(MountStat.SPEED, new CappedValue(12, 20), MountStat.JUMP_HEIGHT, new CappedValue(4, 8)),
                true,
                Map.of(MountStat.SPEED, 25, MountStat.JUMP_HEIGHT, 10));
        MountItem mount = new MountItem("Zephyr", MountType.WYVERN, info, true);

        List<ChatItemPreview.Section> sections = WynntilsItemPreviewAccess.sections(mount);

        assertEquals(List.of(
                        "Type: Wyvern",
                        "Form: Summon Item",
                        "Potential: 84",
                        "Primary Color: Midnight",
                        "Secondary Color: Silver",
                        "Energy: 73/100"),
                sections.get(0).lines());
        assertEquals(List.of("Speed: 12/20 • Max: ~25", "Jump Height: 4/8 • Max: ~10"),
                sections.get(1).lines());
    }

    @Test
    void createsCraftedConsumableUsesDurationAndEffectsSections() {
        CraftedConsumableItem consumable = new CraftedConsumableItem(
                "Restorative Tonic",
                ConsumableType.POTION,
                105,
                List.of(),
                List.of(
                        new NamedItemEffect(ConsumableEffect.HEAL, 4500),
                        new NamedItemEffect(ConsumableEffect.DURATION, 180)),
                List.of(),
                new CappedValue(2, 3));

        List<ChatItemPreview.Section> sections = WynntilsItemPreviewAccess.sections(consumable);

        assertEquals(List.of("Uses: 2/3"), sections.get(0).lines());
        assertEquals(List.of("Heal: +4500 Health", "Duration: 180s"), sections.get(1).lines());
    }
}
