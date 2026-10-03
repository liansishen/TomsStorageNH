package com.hepdd.toms_storage.mixin.nei;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hepdd.toms_storage.gui.ContainerCraftingTerminal;
import com.hepdd.toms_storage.nei.TerminalOverlayHandler;

import codechicken.nei.recipe.GuiOverlayButton;
import codechicken.nei.recipe.GuiOverlayButton.ItemOverlayState;
import codechicken.nei.recipe.RecipeHandlerRef;

@Mixin(value = GuiOverlayButton.class, remap = false)
public abstract class GuiOverlayButtonMixin {

    @Shadow
    @Final
    public GuiContainer firstGui;

    @Shadow
    @Final
    protected List<ItemOverlayState> itemPresenceCache;

    @Shadow
    protected boolean canFillCraftingGrid;

    @Shadow
    protected abstract List<ItemOverlayState> ingredientsOverlay();

    @Unique
    private int tomsstorage$presenceStorageRevision = -1;

    @Unique
    private int tomsstorage$presencePreviewRevision = -1;

    @Unique
    private ItemStack[] tomsstorage$presenceInventory;

    @Inject(method = "ingredientsOverlay", at = @At("HEAD"))
    private void tomsstorage$refreshPresence(CallbackInfoReturnable<List<ItemOverlayState>> cir) {
        if (firstGui != null && firstGui.inventorySlots instanceof ContainerCraftingTerminal) {
            ContainerCraftingTerminal container = (ContainerCraftingTerminal) firstGui.inventorySlots;
            int storageRevision = container.getClientDataRevision();
            int previewRevision = container.getAutoCraftPreview()
                .getRevision();
            int size = container.inventorySlots.size();
            boolean changed = tomsstorage$presenceInventory == null || tomsstorage$presenceInventory.length != size
                || tomsstorage$presenceStorageRevision != storageRevision
                || tomsstorage$presencePreviewRevision != previewRevision;
            for (int i = 0; i < size && !changed; i++) {
                if (i == ContainerCraftingTerminal.GRID_SLOT_START - 1) continue;
                changed = !ItemStack.areItemStacksEqual(
                    tomsstorage$presenceInventory[i],
                    container.getSlot(i)
                        .getStack());
            }
            if (changed) {
                tomsstorage$presenceStorageRevision = storageRevision;
                tomsstorage$presencePreviewRevision = previewRevision;
                tomsstorage$presenceInventory = new ItemStack[size];
                for (int i = 0; i < size; i++) {
                    if (i == ContainerCraftingTerminal.GRID_SLOT_START - 1) continue;
                    tomsstorage$presenceInventory[i] = ItemStack.copyItemStack(
                        container.getSlot(i)
                            .getStack());
                }
                itemPresenceCache.clear();
            }
        }
    }

    @Redirect(
        method = "ingredientsOverlay",
        at = @At(
            value = "INVOKE",
            target = "Lcodechicken/nei/recipe/RecipeHandlerRef;getPresenceOverlay(Lnet/minecraft/client/gui/inventory/GuiContainer;)Ljava/util/List;"))
    private List<ItemOverlayState> tomsstorage$terminalPresence(RecipeHandlerRef recipe, GuiContainer gui) {
        if (gui.inventorySlots instanceof ContainerCraftingTerminal) {
            return TerminalOverlayHandler.getTerminalPresence(gui, recipe.handler, recipe.recipeIndex);
        }
        return recipe.getPresenceOverlay(gui);
    }

    @Inject(method = "drawContent", at = @At("HEAD"))
    private void tomsstorage$refreshPresenceIcon(Minecraft mc, int mouseX, int mouseY, boolean hover, CallbackInfo ci) {
        if (firstGui != null && firstGui.inventorySlots instanceof ContainerCraftingTerminal) ingredientsOverlay();
    }

    @Inject(method = "handleTooltip", at = @At("HEAD"))
    private void tomsstorage$refreshPresenceTooltip(List<String> tooltip, CallbackInfoReturnable<List<String>> cir) {
        if (firstGui != null && firstGui.inventorySlots instanceof ContainerCraftingTerminal) ingredientsOverlay();
    }

    @Inject(method = "overlayRecipe", at = @At("HEAD"), cancellable = true)
    private void tomsstorage$fillRecipe(boolean shift, CallbackInfo ci) {
        if (firstGui != null && firstGui.inventorySlots instanceof ContainerCraftingTerminal) {
            if (canFillCraftingGrid) {
                ((GuiOverlayButton) (Object) this).handlerRef.fillCraftingGrid(firstGui, shift ? 0 : 1);
            }
            ci.cancel();
        }
    }
}
