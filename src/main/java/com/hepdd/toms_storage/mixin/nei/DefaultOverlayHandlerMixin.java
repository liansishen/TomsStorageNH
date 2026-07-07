package com.hepdd.toms_storage.mixin.nei;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hepdd.toms_storage.gui.ContainerCraftingTerminal;
import com.hepdd.toms_storage.nei.NEIDebug;
import com.hepdd.toms_storage.nei.TerminalOverlayTransfer;

import codechicken.nei.recipe.DefaultOverlayHandler;
import codechicken.nei.recipe.IRecipeHandler;

@Mixin(value = DefaultOverlayHandler.class, remap = false)
public abstract class DefaultOverlayHandlerMixin {

    @Shadow
    public int offsetx;

    @Shadow
    public int offsety;

    @Inject(method = "transferRecipe", at = @At("HEAD"), cancellable = true)
    private void tomsstorage$transferRecipe(GuiContainer gui, IRecipeHandler handler, int recipeIndex, int multiplier,
        CallbackInfoReturnable<Integer> cir) {
        if (gui.inventorySlots instanceof ContainerCraftingTerminal) {
            NEIDebug.log(
                "DefaultOverlayHandler.transferRecipe intercepted gui=" + gui.getClass()
                    .getName()
                    + " handler="
                    + handler.getClass()
                        .getName()
                    + " recipeIndex="
                    + recipeIndex
                    + " multiplier="
                    + multiplier);
            cir.setReturnValue(
                TerminalOverlayTransfer
                    .transferRecipe((DefaultOverlayHandler) (Object) this, gui, handler, recipeIndex, multiplier));
        }
    }

    @Inject(method = "craft", at = @At("HEAD"), cancellable = true)
    private void tomsstorage$craft(GuiContainer gui, IRecipeHandler handler, int recipeIndex, int multiplier,
        CallbackInfoReturnable<Boolean> cir) {
        if (gui.inventorySlots instanceof ContainerCraftingTerminal) {
            NEIDebug.log(
                "DefaultOverlayHandler.craft intercepted gui=" + gui.getClass()
                    .getName()
                    + " handler="
                    + handler.getClass()
                        .getName()
                    + " recipeIndex="
                    + recipeIndex
                    + " multiplier="
                    + multiplier);
            cir.setReturnValue(
                TerminalOverlayTransfer
                    .craft((DefaultOverlayHandler) (Object) this, gui, handler, recipeIndex, multiplier));
        }
    }
}
