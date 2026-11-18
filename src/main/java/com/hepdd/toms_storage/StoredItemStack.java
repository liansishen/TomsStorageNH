package com.hepdd.toms_storage;

import java.util.Comparator;
import java.util.function.Function;
import java.util.function.LongFunction;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class StoredItemStack {

    private static final String ITEM_COUNT_NAME = "c";
    private static final String ITEMSTACK_NAME = "s";
    private static volatile int languageGeneration;

    private final ItemStack stack;
    private long count;

    private transient int cachedHash;
    private transient boolean hashComputed;
    private transient String cachedDisplayName;
    private transient int cachedLanguageGeneration = -1;
    private transient long cachedCountValue = Long.MIN_VALUE;
    private transient String cachedCountText;

    public StoredItemStack(ItemStack stack) {
        this(stack, stack == null ? 0 : stack.stackSize);
    }

    public StoredItemStack(ItemStack stack, long count) {
        this(stack, count, true);
    }

    private StoredItemStack(ItemStack stack, long count, boolean copy) {
        if (stack == null) {
            this.stack = null;
        } else {
            this.stack = copy ? stack.copy() : stack;
            this.stack.stackSize = 1;
        }
        this.count = count;
    }

    public static StoredItemStack wrapOwned(ItemStack stack, long count) {
        return new StoredItemStack(stack, count, false);
    }

    public static void invalidateLanguageCache() {
        languageGeneration++;
    }

    public ItemStack getStack() {
        return stack;
    }

    public long getQuantity() {
        return count;
    }

    public void setQuantity(long count) {
        this.count = count;
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
        return wrapOwned(stack, tag.getLong(ITEM_COUNT_NAME));
    }

    public String getDisplayName() {
        if (stack == null) return "";
        int gen = languageGeneration;
        String name = cachedDisplayName;
        if (name != null && cachedLanguageGeneration == gen) {
            return name;
        }
        try {
            name = stack.getDisplayName();
            if (name == null) name = "";
        } catch (RuntimeException ignored) {
            name = "";
        }
        cachedDisplayName = name;
        cachedLanguageGeneration = gen;
        return name;
    }

    public String getFormattedCount(LongFunction<String> formatter) {
        long current = this.count;
        String text = this.cachedCountText;
        if (text != null && this.cachedCountValue == current) {
            return text;
        }
        text = formatter.apply(current);
        this.cachedCountValue = current;
        this.cachedCountText = text;
        return text;
    }

    @Override
    public int hashCode() {
        if (!hashComputed) {
            if (stack == null) {
                cachedHash = 1;
            } else {
                int result = 1;
                result = 31 * result + System.identityHashCode(stack.getItem());
                result = 31 * result + stack.getItemDamage();
                result = 31 * result + (stack.hasTagCompound() ? stack.getTagCompound()
                    .hashCode() : 0);
                cachedHash = result;
            }
            hashComputed = true;
        }
        return cachedHash;
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
