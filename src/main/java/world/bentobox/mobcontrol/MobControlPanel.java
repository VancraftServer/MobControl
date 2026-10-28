package world.bentobox.mobcontrol;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import world.bentobox.bentobox.api.panels.Panel;
import world.bentobox.bentobox.api.panels.PanelItem;
import world.bentobox.bentobox.api.panels.builders.PanelBuilder;
import world.bentobox.bentobox.api.panels.builders.PanelItemBuilder;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;

/**
 * 怪物生成开关面板：每个配置的怪物一个刷怪蛋图标，点一下切换，图标当场刷新。
 */
public class MobControlPanel {

    private final MobControlAddon addon;
    private final User user;
    private final Island island;

    public MobControlPanel(MobControlAddon addon, User user, Island island) {
        this.addon = addon;
        this.user = user;
        this.island = island;
    }

    public void open() {
        Settings settings = addon.getSettings();
        int rows = Math.max(1, Math.min(6, settings.getGuiRows()));
        int size = rows * 9;

        PanelBuilder builder = new PanelBuilder()
                .name(settings.getGuiTitle())
                .user(user)
                .size(size);

        int slot = 0;
        for (EntityType type : addon.getMobs()) {
            if (slot >= size) {
                break;
            }
            builder.item(slot++, createItem(addon, island, type));
        }

        Panel panel = builder.build();
        panel.setIsland(island);
        panel.open(user.getPlayer());
    }

    /**
     * 生成单个怪物的面板图标（开着 = 附魔光晕，关掉 = 灰名）。
     * 独立成静态方法，方便离线验证图标/文字是否正常生成。
     */
    public static PanelItem createItem(MobControlAddon addon, Island island, EntityType type) {
        Settings settings = addon.getSettings();
        boolean allowed = addon.isMobAllowed(island, type);

        List<String> lore = new ArrayList<>();
        lore.add(allowed ? settings.getAllowedLore() : settings.getBlockedLore());
        lore.add(settings.getClickHint());

        return new PanelItemBuilder()
                .icon(icon(type))
                .name((allowed ? "&a" : "&c") + displayName(type))
                .description(lore)
                .glow(allowed)
                .clickHandler((panel, clicker, clickType, clickedSlot) -> {
                    boolean nowAllowed = addon.toggleMob(island, type);
                    String message = nowAllowed ? settings.getToggledOn() : settings.getToggledOff();
                    MobControlAddon.send(clicker, message.replace("%mob%", displayName(type)));
                    // 就地刷新这一个格子，面板不关、不闪
                    PanelItem refreshed = createItem(addon, island, type);
                    panel.getItems().put(clickedSlot, refreshed);
                    panel.getInventory().setItem(clickedSlot, refreshed.getItem());
                    // 返回 true = 消费这次点击（面板保持打开）
                    return true;
                })
                .build();
    }

    private static Material icon(EntityType type) {
        if (!isLiving(type)) {
            return Material.BARRIER;
        }
        Material egg = Material.matchMaterial(type.name() + "_SPAWN_EGG");
        return egg != null ? egg : Material.BARRIER;
    }

    private static boolean isLiving(EntityType type) {
        Class<?> clazz = type.getEntityClass();
        return clazz != null && LivingEntity.class.isAssignableFrom(clazz) && type.isSpawnable();
    }

    /** SLIME -> Slime，ZOMBIE_VILLAGER -> Zombie Villager */
    public static String displayName(EntityType type) {
        StringBuilder sb = new StringBuilder();
        for (String part : type.name().toLowerCase(Locale.ENGLISH).split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
