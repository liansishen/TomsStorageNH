package com.hepdd.toms_storage.mixin.nei;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hepdd.toms_storage.StoredItemStack;
import com.hepdd.toms_storage.gui.ContainerStorageTerminal;
import com.hepdd.toms_storage.nei.AutoCraftPreviewInventory;
import com.hepdd.toms_storage.nei.NEIDebug;

import codechicken.nei.ItemStackAmount;
import codechicken.nei.recipe.AutoCraftingManager;

@Mixin(value = AutoCraftingManager.class, remap = false)
public abstract class AutoCraftingManagerMixin {

    @Inject(method = "getInventoryItems", at = @At("RETURN"))
    private static void tomsstorage$getInventoryItems(GuiContainer guiContainer,
        CallbackInfoReturnable<ItemStackAmount> cir) {
        if (!(guiContainer.inventorySlots instanceof ContainerStorageTerminal)) return;

        ItemStackAmount inventory = cir.getReturnValue();
        ContainerStorageTerminal container = (ContainerStorageTerminal) guiContainer.inventorySlots;
        if (container.getAutoCraftPreview()
            .isActive()) {
            inventory.clear();
            for (ItemStack stack : container.getAutoCraftPreview()
                .getPlayerStacks(guiContainer.mc.thePlayer.inventory)) {
                inventory.add(stack);
            }
        }
        int added = 0;
        for (StoredItemStack stack : AutoCraftPreviewInventory.getStacks(container)) {
            ItemStack actual = stack.getActualStack();
            if (actual != null && actual.stackSize > 0) {
                inventory.add(actual);
                added++;
            }
        }
        NEIDebug.log("AutoCraftingManager.getInventoryItems added terminal stacks=" + added);
    }
}
