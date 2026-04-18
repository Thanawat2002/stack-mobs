package com.stackmobs.stacking;

import net.minecraft.world.entity.Mob;

import java.util.UUID;

/**
 * Immutable snapshot of a mob stack's runtime state.
 */
public final class MobStack {

    private final UUID    primaryUUID;
    private final Mob     primary;
    private final int     stackSize;
    private final String  worldKey;

    public MobStack(Mob primary, int stackSize, String worldKey) {
        this.primary     = primary;
        this.primaryUUID = primary.getUUID();
        this.stackSize   = stackSize;
        this.worldKey    = worldKey;
    }

    public UUID getPrimaryUUID() { return primaryUUID; }
    public Mob  getPrimary()     { return primary; }
    public int  getStackSize()   { return stackSize; }
    public String getWorldKey()  { return worldKey; }

    public boolean isValid() {
        return primary != null && !primary.isRemoved() && primary.isAlive();
    }

    @Override
    public String toString() {
        return "MobStack{uuid=" + primaryUUID +
               ", size=" + stackSize +
               ", world=" + worldKey +
               ", valid=" + isValid() + "}";
    }
}
