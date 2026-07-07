package com.hepdd.toms_storage.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.hepdd.toms_storage.crafting.CraftingInventorySnapshot.Extraction;

public final class CraftingPlan {

    private final List<CraftOperation> operations = new ArrayList<>();

    public void addOperation(Extraction[] extractions, ItemStack output, boolean finalOutput) {
        operations.add(new CraftOperation(extractions, output, finalOutput));
    }

    public boolean isEmpty() {
        return operations.isEmpty();
    }

    public List<CraftOperation> getOperations() {
        return Collections.unmodifiableList(operations);
    }

    public static final class CraftOperation {

        private final Extraction[] extractions;
        private final ItemStack output;
        private final boolean finalOutput;

        private CraftOperation(Extraction[] extractions, ItemStack output, boolean finalOutput) {
            this.extractions = extractions;
            this.output = output == null ? null : output.copy();
            this.finalOutput = finalOutput;
        }

        public Extraction[] getExtractions() {
            return extractions;
        }

        public ItemStack getOutput() {
            return output == null ? null : output.copy();
        }

        public boolean isFinalOutput() {
            return finalOutput;
        }

    }
}
