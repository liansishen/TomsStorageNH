package com.hepdd.toms_storage.tile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;
import com.hepdd.toms_storage.Config;
import com.hepdd.toms_storage.ModRegistry;
import com.hepdd.toms_storage.StoredItemStack;
import com.hepdd.toms_storage.inventory.CombinedStorageInventory;
import com.hepdd.toms_storage.inventory.IStorageInventory;
import com.hepdd.toms_storage.inventory.IStorageNetworkAccess;
import com.hepdd.toms_storage.inventory.IStorageNetworkEndpoint;
import com.hepdd.toms_storage.inventory.StorageInventoryProvider;
import com.hepdd.toms_storage.inventory.StorageInventoryUtils;

public class TileEntityInventoryConnector extends TileEntity implements IStorageNetworkAccess {

    public static final long TOPOLOGY_SCAN_INTERVAL = 20L;
    private static final long STACK_SNAPSHOT_INTERVAL = 2L;

    private static final byte NODE_START = 0;
    private static final byte NODE_CABLE = 1;
    private static final byte NODE_STORAGE = 2;
    private static final byte NODE_TRIM = 3;

    private static final Map<World, Set<TileEntityInventoryConnector>> ACTIVE_CONNECTORS = new WeakHashMap<>();

    private final CombinedStorageInventory inventory = new CombinedStorageInventory(
        this::currentWorldTickOrInvalid,
        this::markStacksDirty,
        this::getStacks);

    private final StampedLongSet checkedCable = new StampedLongSet(256);
    private final StampedLongSet checkedStorage = new StampedLongSet(256);
    private final StampedLongSet watchedPositions = new StampedLongSet(512);
    private final NodeQueue toCheck = new NodeQueue(128);
    private final Set<IStorageNetworkEndpoint> attachedEndpoints = Collections.newSetFromMap(new WeakHashMap<>());

    private long scanId;
    private boolean scanning;
    private long lastScanTick = -1L;
    private boolean topologyDirty = true;
    private boolean stacksDirty = true;
    private long cachedStacksTick = -1L;
    private List<StoredItemStack> cachedStacks = Collections.emptyList();
    private long mutationVersion;
    private boolean cableScanLimitReached;
    private boolean cableNetworkConflict;

    public static boolean isCableBlock(Block block) {
        return block != null && block == ModRegistry.inventoryCable;
    }

    public static void notifyBlockChanged(World world, int x, int y, int z) {
        if (world == null || world.isRemote) return;
        long packed = CoordinatePacker.pack(x, y, z);
        List<TileEntityInventoryConnector> candidates;
        synchronized (ACTIVE_CONNECTORS) {
            Set<TileEntityInventoryConnector> set = ACTIVE_CONNECTORS.get(world);
            if (set == null || set.isEmpty()) return;
            candidates = new ArrayList<>(set);
        }
        for (TileEntityInventoryConnector connector : candidates) {
            if (connector != null && !connector.isInvalid()
                && connector.worldObj == world
                && connector.watchesPackedPosition(packed)) {
                connector.markTopologyDirty();
            }
        }
    }

    @Override
    public void validate() {
        super.validate();
        registerActiveConnector();
        markTopologyDirty();
    }

    @Override
    public void invalidate() {
        unregisterActiveConnector();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        unregisterActiveConnector();
        super.onChunkUnload();
    }

    private void registerActiveConnector() {
        if (worldObj == null || worldObj.isRemote) return;
        synchronized (ACTIVE_CONNECTORS) {
            Set<TileEntityInventoryConnector> set = ACTIVE_CONNECTORS
                .computeIfAbsent(worldObj, key -> Collections.newSetFromMap(new WeakHashMap<>()));
            set.add(this);
        }
    }

    private void unregisterActiveConnector() {
        scanId++;
        if (!attachedEndpoints.isEmpty()) {
            List<IStorageNetworkEndpoint> detached = new ArrayList<>(attachedEndpoints);
            attachedEndpoints.clear();
            for (IStorageNetworkEndpoint endpoint : detached) {
                if (endpoint != null) {
                    endpoint.detachNetwork(this);
                }
            }
        }
        if (worldObj == null) return;
        synchronized (ACTIVE_CONNECTORS) {
            Set<TileEntityInventoryConnector> set = ACTIVE_CONNECTORS.get(worldObj);
            if (set != null) {
                set.remove(this);
                if (set.isEmpty()) {
                    ACTIVE_CONNECTORS.remove(worldObj);
                }
            }
        }
    }

    private long currentWorldTickOrInvalid() {
        return worldObj != null && !worldObj.isRemote ? worldObj.getTotalWorldTime() : Long.MIN_VALUE;
    }

    public void markTopologyDirty() {
        topologyDirty = true;
        stacksDirty = true;
        mutationVersion++;
    }

    public void markStacksDirty() {
        stacksDirty = true;
        mutationVersion++;
    }

    public long getMutationVersion() {
        return mutationVersion;
    }

    public long getScanId() {
        return scanId;
    }

    public IStorageInventory getDirectStorageInventory() {
        return inventory;
    }

    public boolean needsTopologyRefresh() {
        return !scanning && worldObj != null && !worldObj.isRemote && shouldScanTopology(worldObj.getTotalWorldTime());
    }

    public void ensureTopologyUpToDate() {
        if (needsTopologyRefresh()) {
            scanInventories();
        }
    }

    public boolean watchesPackedPosition(long packed) {
        return watchedPositions.contains(packed);
    }

    private boolean shouldScanTopology(long now) {
        return topologyDirty || lastScanTick < 0L || now < lastScanTick || now - lastScanTick >= TOPOLOGY_SCAN_INTERVAL;
    }

    @Override
    public IStorageInventory getStorageInventory() {
        if (worldObj != null && !worldObj.isRemote && shouldScanTopology(worldObj.getTotalWorldTime())) {
            scanInventories();
        }
        return inventory;
    }

    public List<StoredItemStack> getStacks() {
        if (worldObj == null || worldObj.isRemote) return new ArrayList<>();
        IStorageInventory active = getStorageInventory();
        long now = worldObj.getTotalWorldTime();
        if (!stacksDirty && cachedStacksTick >= 0L
            && now >= cachedStacksTick
            && now - cachedStacksTick < STACK_SNAPSHOT_INTERVAL) {
            return cachedStacks;
        }
        cachedStacks = Collections.unmodifiableList(StorageInventoryUtils.getStacks(active));
        cachedStacksTick = now;
        stacksDirty = false;
        return cachedStacks;
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) return;
        long now = worldObj.getTotalWorldTime();
        if (topologyDirty || (now % TOPOLOGY_SCAN_INTERVAL == 0L && lastScanTick != now)) {
            scanInventories();
        }
    }

    private void scanInventories() {
        if (scanning) return;
        scanning = true;
        try {
            registerActiveConnector();
            scanId++;
            lastScanTick = worldObj.getTotalWorldTime();
            topologyDirty = false;
            stacksDirty = true;
            mutationVersion++;

            inventory.clear();
            cableScanLimitReached = false;
            cableNetworkConflict = false;

            checkedCable.clear();
            checkedStorage.clear();
            watchedPositions.clear();
            toCheck.clear();

            long startPacked = CoordinatePacker.pack(xCoord, yCoord, zCoord);
            checkedCable.add(startPacked);
            checkedStorage.add(startPacked);
            watchedPositions.add(startPacked);
            toCheck.enqueue(startPacked, NODE_START);

            int cableNodes = 0;
            double rangeSq = Config.getInventoryConnectorRangeSquared();
            List<EndpointRef> cableEndpoints = new ArrayList<>();

            while (!toCheck.isEmpty()) {
                long currentPacked = toCheck.peekCoord();
                byte currentType = toCheck.pollType();
                int cx = CoordinatePacker.unpackX(currentPacked);
                int cy = CoordinatePacker.unpackY(currentPacked);
                int cz = CoordinatePacker.unpackZ(currentPacked);

                boolean isCableNode = currentType == NODE_START || currentType == NODE_CABLE;
                boolean isStorageNode = currentType == NODE_START || currentType == NODE_STORAGE
                    || currentType == NODE_TRIM;

                for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
                    int nx = cx + direction.offsetX;
                    int ny = cy + direction.offsetY;
                    int nz = cz + direction.offsetZ;

                    if (isCableNode) {
                        long nextPacked = CoordinatePacker.pack(nx, ny, nz);
                        watchedPositions.add(nextPacked);
                        if (!checkedCable.contains(nextPacked)
                            && StorageInventoryUtils.isChunkLoaded(worldObj, nx, nz)) {
                            Block nextBlock = worldObj.getBlock(nx, ny, nz);
                            if (isCableBlock(nextBlock)) {
                                if (cableNodes >= Config.maxInventoryCableNodes) {
                                    cableScanLimitReached = true;
                                } else {
                                    checkedCable.add(nextPacked);
                                    cableNodes++;
                                    toCheck.enqueue(nextPacked, NODE_CABLE);
                                }
                                continue;
                            }
                            if (nextBlock == null || nextBlock != Blocks.air) {
                                TileEntity tile = worldObj.getTileEntity(nx, ny, nz);
                                if (currentType == NODE_CABLE && tile instanceof TileEntityInventoryConnector
                                    && tile != this) {
                                    cableNetworkConflict = true;
                                    continue;
                                }
                                if (tile instanceof IStorageNetworkEndpoint) {
                                    IStorageNetworkEndpoint endpoint = (IStorageNetworkEndpoint) tile;
                                    if (endpoint.canConnectNetworkFrom(direction.getOpposite())) {
                                        cableEndpoints.add(new EndpointRef(endpoint, direction.getOpposite()));
                                    }
                                    continue;
                                }
                            }
                        }
                    }

                    if (!isStorageNode || getDistanceSq(nx, ny, nz) > rangeSq) continue;

                    long nextPacked = CoordinatePacker.pack(nx, ny, nz);
                    watchedPositions.add(nextPacked);
                    if (checkedStorage.contains(nextPacked)) continue;
                    if (!StorageInventoryUtils.isChunkLoaded(worldObj, nx, nz)) continue;

                    Block nextBlock = worldObj.getBlock(nx, ny, nz);
                    if (isCableBlock(nextBlock)) continue;

                    checkedStorage.add(nextPacked);
                    if (nextBlock != null && nextBlock == Blocks.air) continue;

                    if (nextBlock != null && nextBlock == ModRegistry.trim) {
                        toCheck.enqueue(nextPacked, NODE_TRIM);
                        continue;
                    }

                    TileEntity tile = worldObj.getTileEntity(nx, ny, nz);
                    if (tile == null || tile instanceof TileEntityInventoryConnector
                        || tile instanceof IStorageNetworkEndpoint) {
                        continue;
                    }

                    if (!Config.onlyTrimsConnect || currentType == NODE_TRIM) {
                        IStorageInventory storage = StorageInventoryProvider
                            .getInventory(tile, direction.getOpposite());
                        if (storage != null) {
                            inventory.add(storage);
                            toCheck.enqueue(nextPacked, NODE_STORAGE);
                        }
                    }
                }
            }

            if (cableEndpoints.isEmpty()) {
                if (!attachedEndpoints.isEmpty()) {
                    List<IStorageNetworkEndpoint> detached = new ArrayList<>(attachedEndpoints);
                    attachedEndpoints.clear();
                    for (IStorageNetworkEndpoint oldEndpoint : detached) {
                        if (oldEndpoint != null) {
                            oldEndpoint.detachNetwork(this);
                        }
                    }
                }
            } else {
                Set<IStorageNetworkEndpoint> currentEndpoints = Collections.newSetFromMap(new WeakHashMap<>());
                for (EndpointRef ref : cableEndpoints) {
                    currentEndpoints.add(ref.endpoint);
                }
                if (!attachedEndpoints.isEmpty()) {
                    for (IStorageNetworkEndpoint oldEndpoint : new ArrayList<>(attachedEndpoints)) {
                        if (oldEndpoint != null && !currentEndpoints.contains(oldEndpoint)) {
                            oldEndpoint.detachNetwork(this);
                        }
                    }
                    attachedEndpoints.clear();
                }
                attachedEndpoints.addAll(currentEndpoints);

                for (EndpointRef endpoint : cableEndpoints) {
                    if (cableNetworkConflict) {
                        endpoint.endpoint.markNetworkConflict(this, endpoint.side);
                    } else {
                        endpoint.endpoint.attachNetwork(this, endpoint.side);
                    }
                }
            }
        } finally {
            scanning = false;
        }
    }

    public boolean isCableScanLimitReached() {
        return cableScanLimitReached;
    }

    public boolean hasCableNetworkConflict() {
        return cableNetworkConflict;
    }

    private double getDistanceSq(int px, int py, int pz) {
        double dx = px - xCoord;
        double dy = py - yCoord;
        double dz = pz - zCoord;
        return dx * dx + dy * dy + dz * dz;
    }

    private static final class EndpointRef {

        private final IStorageNetworkEndpoint endpoint;
        private final ForgeDirection side;

        private EndpointRef(IStorageNetworkEndpoint endpoint, ForgeDirection side) {
            this.endpoint = endpoint;
            this.side = side;
        }
    }

    private static final class StampedLongSet {

        private long[] keys;
        private int[] stamps;
        private int currentStamp = 1;
        private int size;
        private int resizeThreshold;

        StampedLongSet(int initialCapacity) {
            int cap = Integer.highestOneBit(Math.max(16, initialCapacity - 1)) << 1;
            this.keys = new long[cap];
            this.stamps = new int[cap];
            this.resizeThreshold = (int) (cap * 0.65F);
        }

        void clear() {
            size = 0;
            if (++currentStamp == 0) {
                Arrays.fill(stamps, 0);
                currentStamp = 1;
            }
        }

        boolean contains(long key) {
            int mask = keys.length - 1;
            int idx = mix(key) & mask;
            while (stamps[idx] == currentStamp) {
                if (keys[idx] == key) return true;
                idx = (idx + 1) & mask;
            }
            return false;
        }

        boolean add(long key) {
            int mask = keys.length - 1;
            int idx = mix(key) & mask;
            while (stamps[idx] == currentStamp) {
                if (keys[idx] == key) return false;
                idx = (idx + 1) & mask;
            }
            keys[idx] = key;
            stamps[idx] = currentStamp;
            if (++size >= resizeThreshold) {
                rehash();
            }
            return true;
        }

        private void rehash() {
            long[] oldKeys = keys;
            int[] oldStamps = stamps;
            int oldStamp = currentStamp;

            int newCap = oldKeys.length << 1;
            long[] newKeys = new long[newCap];
            int[] newStamps = new int[newCap];
            int mask = newCap - 1;

            for (int i = 0; i < oldKeys.length; i++) {
                if (oldStamps[i] == oldStamp) {
                    long key = oldKeys[i];
                    int idx = mix(key) & mask;
                    while (newStamps[idx] == 1) {
                        idx = (idx + 1) & mask;
                    }
                    newKeys[idx] = key;
                    newStamps[idx] = 1;
                }
            }
            this.keys = newKeys;
            this.stamps = newStamps;
            this.currentStamp = 1;
            this.resizeThreshold = (int) (newCap * 0.65F);
        }

        private static int mix(long key) {
            long h = key * 0x9E3779B97F4A7C15L;
            return (int) (h ^ (h >>> 32));
        }
    }

    private static final class NodeQueue {

        private long[] coords;
        private byte[] types;
        private int head;
        private int tail;

        NodeQueue(int initialCapacity) {
            int cap = Integer.highestOneBit(Math.max(16, initialCapacity - 1)) << 1;
            this.coords = new long[cap];
            this.types = new byte[cap];
        }

        void clear() {
            head = 0;
            tail = 0;
        }

        boolean isEmpty() {
            return head == tail;
        }

        void enqueue(long coord, byte type) {
            coords[tail] = coord;
            types[tail] = type;
            tail = (tail + 1) & (coords.length - 1);
            if (tail == head) {
                grow();
            }
        }

        long peekCoord() {
            return coords[head];
        }

        byte pollType() {
            byte type = types[head];
            head = (head + 1) & (coords.length - 1);
            return type;
        }

        private void grow() {
            int oldCap = coords.length;
            int newCap = oldCap << 1;
            long[] newCoords = new long[newCap];
            byte[] newTypes = new byte[newCap];
            int right = oldCap - head;
            System.arraycopy(coords, head, newCoords, 0, right);
            System.arraycopy(coords, 0, newCoords, right, head);
            System.arraycopy(types, head, newTypes, 0, right);
            System.arraycopy(types, 0, newTypes, right, head);
            coords = newCoords;
            types = newTypes;
            head = 0;
            tail = oldCap;
        }
    }
}
