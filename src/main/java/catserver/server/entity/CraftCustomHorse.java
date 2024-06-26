package catserver.server.entity;

import net.minecraft.entity.passive.AbstractHorse;
import org.bukkit.craftbukkit.v1_12_R1.CraftServer;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftAbstractHorse;
import org.bukkit.entity.Horse;

public class CraftCustomHorse extends CraftAbstractHorse {

    public CraftCustomHorse(CraftServer server, AbstractHorse entity) {
        super(server, entity);
    }

    @Override
    public String toString() {
        return "CraftCustomHorse";
    }

    @Override
    public Horse.Variant getVariant() {
        return Horse.Variant.MOD_CUSTOM;
    }
}
