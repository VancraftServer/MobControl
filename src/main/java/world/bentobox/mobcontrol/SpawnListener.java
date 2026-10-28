package world.bentobox.mobcontrol;

import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import world.bentobox.bentobox.database.objects.Island;

/**
 * 在怪物生成时检查它落在哪个岛上，以及该岛是否允许这种怪物生成。
 */
public class SpawnListener implements Listener {

    private final MobControlAddon addon;

    public SpawnListener(MobControlAddon addon) {
        this.addon = addon;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        EntityType type = event.getEntityType();
        // 配置文件里没写的怪物，完全不处理（性能与行为都保持原样）
        if (!addon.isConfigured(type)) {
            return;
        }
        // 只拦自然生成时，刷怪笼/刷怪蛋/分裂/插件生成的都不管
        if (addon.getSettings().isOnlyNatural()
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
            return;
        }
        Island island = addon.getIslands().getIslandAt(event.getLocation()).orElse(null);
        if (island == null) {
            // 不在任何岛的保护范围内，交给原版逻辑
            return;
        }
        boolean allowed = addon.isMobAllowed(island, type);
        if (!allowed) {
            event.setCancelled(true);
        }
    }
}
