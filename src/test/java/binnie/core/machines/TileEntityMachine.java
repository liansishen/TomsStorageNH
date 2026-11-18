package binnie.core.machines;

import java.util.Collection;
import java.util.Collections;

import com.hepdd.toms_storage.inventory.StorageTestInventory;

import binnie.core.machines.storage.ComponentCompartmentInventory;

public class TileEntityMachine extends StorageTestInventory {

    public Machine machine;

    public TileEntityMachine(int slots, boolean compartment) {
        super(slots);
        machine = new Machine(compartment ? new ComponentCompartmentInventory() : new Object());
    }

    public Machine getMachine() {
        return machine;
    }

    public static class Machine {

        private final Object component;

        public Machine(Object component) {
            this.component = component;
        }

        public Collection<Object> getComponents() {
            return Collections.singletonList(component);
        }
    }
}
