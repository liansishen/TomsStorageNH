package com.hepdd.toms_storage;

import java.util.Comparator;
import java.util.function.Function;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class StoredItemStack {

    private static final String ITEM_COUNT_NAME = "c";
    private static final String ITEMSTACK_NAME = "s";

    private final ItemStack stack;
    private long count;

    public StoredItemStack(ItemStack stack) {
        this(stack, stack == null ? 0 : stack.stackSize);
    }

    public StoredItemStack(ItemStack stack, long count) {
        this.stack = stack == null ? null : stack.copy();
        if (this.stack != null) {
            this.stack.stackSize = 1;
        }
        this.count = count;
    }

    public ItemStack getStack() {
        return stack;
    }

    public long getQuantity() {
        return count;
    }

    public ItemStack getActualStack() {
        if (stack == null) return null;
        ItemStack actual = stack.copy();
        actual.stackSize = (int) Math.min(count, Integer.MAX_VALUE);
        return actual;
    }

    public void grow(long amount) {
        count += amount;
    }

    public void writeToNBT(NBTTagCompound tag) {
        tag.setLong(ITEM_COUNT_NAME, count);
        NBTTagCompound stackTag = new NBTTagCompound();
        stack.writeToNBT(stackTag);
        stackTag.removeTag("Count");
        tag.setTag(ITEMSTACK_NAME, stackTag);
    }

    public static StoredItemStack readFromNBT(NBTTagCompound tag) {
        if (!tag.hasKey(ITEMSTACK_NAME)) return null;
        NBTTagCompound stackTag = tag.getCompoundTag(ITEMSTACK_NAME);
        stackTag.setByte("Count", (byte) 1);
        ItemStack stack = ItemStack.loadItemStackFromNBT(stackTag);
        if (stack == null) return null;
        return new StoredItemStack(stack, tag.getLong(ITEM_COUNT_NAME));
    }

    public String getDisplayName() {
        return stack == null ? "" : stack.getDisplayName();
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + (stack == null ? 0 : Item.getIdFromItem(stack.getItem()));
        result = 31 * result + (stack == null ? 0 : stack.getItemDamage());
        result = 31 * result + (stack == null || !stack.hasTagCompound() ? 0
            : stack.getTagCompound()
                .hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof StoredItemStack)) return false;
        StoredItemStack other = (StoredItemStack) obj;
        return StorageItemUtils.areItemStacksEqual(stack, other.stack, true);
    }

    public static class ComparatorAmount implements IStoredItemStackComparator {

        private boolean reversed;

        public ComparatorAmount(boolean reversed) {
            this.reversed = reversed;
        }

        @Override
        public int compare(StoredItemStack first, StoredItemStack second) {
            int result = second.count > first.count ? 1
                : first.count == second.count ? first.getDisplayName()
                    .compareTo(second.getDisplayName()) : -1;
            return reversed ? -result : result;
        }

        @Override
        public boolean isReversed() {
            return reversed;
        }

        @Override
        public void setReversed(boolean reversed) {
            this.reversed = reversed;
        }

        @Override
        public int type() {
            return 0;
        }
    }

    public static class ComparatorName implements IStoredItemStackComparator {

        private boolean reversed;

        public ComparatorName(boolean reversed) {
            this.reversed = reversed;
        }

        @Override
        public int compare(StoredItemStack first, StoredItemStack second) {
            int result = first.getDisplayName()
                .compareTo(second.getDisplayName());
            return reversed ? -result : result;
        }

        @Override
        public boolean isReversed() {
            return reversed;
        }

        @Override
        public void setReversed(boolean reversed) {
            this.reversed = reversed;
        }

        @Override
        public int type() {
            return 1;
        }
    }

    public interface IStoredItemStackComparator extends Comparator<StoredItemStack> {

        boolean isReversed();

        void setReversed(boolean reversed);

        int type();
    }

    public enum SortingTypes {

        AMOUNT(ComparatorAmount::new),
        NAME(ComparatorName::new);

        public static final SortingTypes[] VALUES = values();

        private final Function<Boolean, IStoredItemStackComparator> factory;

        SortingTypes(Function<Boolean, IStoredItemStackComparator> factory) {
            this.factory = factory;
        }

        public IStoredItemStackComparator create(boolean reversed) {
            return factory.apply(reversed);
        }
    }
}
