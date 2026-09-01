package com.seqwawa.seq.render;

import java.util.UUID;

/**
 * The player whose nametag is being submitted, for as long as their entity is being
 * submitted for rendering.
 * <p>
 * A nametag reaches the renderer as a bare component, through
 * {@code SubmitNodeCollection#submitNameTag}, which every renderer funnels into:
 * vanilla, Wynntils' custom nametag feature, and mods that rebuild the tag from
 * their own data. That is the only point where the last word on a tag can be had,
 * and it is also the point where nothing says whose tag it is. Publishing the owner
 * around the whole of {@code EntityRenderer#submit} answers that for every one of
 * them, since they all draw from inside it.
 * <p>
 * Whoever is not an avatar claims nobody, so a mob or a hologram carrying a member's
 * name cannot be given their rank. Entities are submitted one at a time on the render
 * thread, and every submission opens by claiming, so an interrupted one cannot leave
 * the wrong owner standing for longer than that.
 */
public final class NametagOwner {

    private static UUID current;

    private NametagOwner() {}

    public static void claim(UUID uuid) {
        current = uuid;
    }

    public static void release() {
        current = null;
    }

    /** The avatar being submitted, or {@code null} when it is not a player's. */
    public static UUID current() {
        return current;
    }
}
