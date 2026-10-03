package com.hepdd.toms_storage.tile;

import net.minecraft.nbt.NBTTagCompound;

public class TileEntityCraftingTerminal extends TileEntityStorageTerminal {

    private boolean autoRefill = true;

    public boolean isAutoRefillEnabled() {
        return autoRefill;
    }

    public void setAutoRefillEnabled(boolean enabled) {
        autoRefill = enabled;
        markDirty();
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setBoolean("autoRefill", autoRefill);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        autoRefill = !tag.hasKey("autoRefill") || tag.getBoolean("autoRefill");
    }
}
