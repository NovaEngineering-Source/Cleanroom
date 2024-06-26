package catserver.api.bukkit.entity;

import net.minecraft.entity.Entity;
import net.minecraftforge.fml.common.registry.EntityRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

public class EntityManager {
    public static List<CustomEntityClass> getCustomEntityList() {
        List<CustomEntityClass> list = new ArrayList<CustomEntityClass>();
        for (Entry<String, Class<? extends Entity>> entitySet : EntityRegistry.entityClassMap.entrySet()) {
            list.add(new CustomEntityClass(entitySet.getKey(), entitySet.getValue()));
        }
        return list;
    }
}
