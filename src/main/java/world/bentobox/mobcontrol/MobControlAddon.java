package world.bentobox.mobcontrol;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.bukkit.entity.EntityType;
import org.bukkit.event.HandlerList;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.configuration.Config;
import world.bentobox.bentobox.api.metadata.MetaDataValue;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.IslandsManager;

/**
 * MobControl：让玩家在自己岛屿上开关指定怪物的生成。
 * <p>
 * 状态存在每个岛的 metadata 里（key 形如 {@code mobcontrol:SLIME}），
 * 所以重启服务器、甚至换数据库都能保留；没有记录 = 默认允许生成。
 * </p>
 */
public class MobControlAddon extends Addon {

    /** Island metadata 的 key 前缀 */
    private static final String META_PREFIX = "mobcontrol:";

    private Settings settings;
    private SpawnListener spawnListener;
    private final List<EntityType> mobs = new ArrayList<>();

    @Override
    public void onLoad() {
        // config.yml 不在 jar 里就复制一份出来，然后再读
        saveDefaultConfig();
        loadSettings();
    }

    @Override
    public void onEnable() {
        if (settings == null) {
            saveDefaultConfig();
            loadSettings();
        }
        // 注意：CompositeCommand 的构造器内部已经调用 commandsManager.registerCommand()，
        // 这里再手动注册一次会让 Bukkit 的 commandMap.register() 返回 false 并打出
        // "Failed to register command" 报错，所以只 new 不注册。
        new MobControlCommand(this);
        // 同时在每个游戏模式的玩家命令下挂一个子命令：/is mob、/ai mob、/cb mob…
        registerIslandSubCommand();
        this.spawnListener = new SpawnListener(this);
        registerListener(spawnListener);
        log("MobControl enabled. mobs=" + mobs);
    }

    /**
     * 至少在一个游戏模式完全启用之后才做：BentoBox 先启用所有 GameModeAddon，
     * 再启用其它附属，所以这里能拿到玩家命令；allLoaded() 里会再兜底调用一次。
     */
    @Override
    public void allLoaded() {
        registerIslandSubCommand();
    }

    /**
     * 给每个已启用的游戏模式挂上 {@code /is mob} 这样的子命令。
     * 重复调用是安全的（已经有了就跳过）。
     */
    private void registerIslandSubCommand() {
        for (GameModeAddon gameMode : getPlugin().getAddonsManager().getGameModeAddons()) {
            Optional<CompositeCommand> parent = gameMode.getPlayerCommand();
            if (parent.isEmpty()) {
                continue;
            }
            CompositeCommand playerCommand = parent.get();
            if (playerCommand.getSubCommand("mob").isPresent()) {
                continue;
            }
            new MobControlCommand(this, playerCommand);
            log("Registered /" + playerCommand.getLabel() + " mob");
        }
    }

    @Override
    public void onDisable() {
        if (spawnListener != null) {
            HandlerList.unregisterAll(spawnListener);
        }
        // 把挂在游戏模式玩家命令下面的子命令摘掉，避免附属被禁用后还留着空壳
        try {
            for (GameModeAddon gameMode : getPlugin().getAddonsManager().getGameModeAddons()) {
                gameMode.getPlayerCommand().ifPresent(playerCommand -> {
                    playerCommand.getSubCommands().remove("mob");
                    playerCommand.getSubCommandAliases().remove("mobs");
                });
            }
        } catch (Exception e) {
            logWarning("Could not unregister /is mob: " + e.getMessage());
        }
    }

    @Override
    public void onReload() {
        loadSettings();
        log("MobControl reloaded. mobs=" + mobs);
    }

    /**
     * 读取配置并把 mobs 里的名字转成 EntityType（无效的名字会跳过并写日志）。
     */
    public void loadSettings() {
        this.settings = new Config<>(this, Settings.class).loadConfigObject();
        if (settings == null) {
            logWarning("Failed to load settings, falling back to defaults.");
            this.settings = new Settings();
        }
        mobs.clear();
        for (String raw : settings.getMobs()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                EntityType type = EntityType.valueOf(raw.trim().toUpperCase(Locale.ENGLISH));
                if (!mobs.contains(type)) {
                    mobs.add(type);
                }
            } catch (IllegalArgumentException e) {
                logWarning("Unknown entity type in config mobs: " + raw);
            }
        }
    }

    public Settings getSettings() {
        return settings;
    }

    /** 面板里要显示的怪物列表（已按配置顺序解析好） */
    public List<EntityType> getMobs() {
        return mobs;
    }

    /** 这个怪物是否在配置里（不在配置里的不拦、也不显示） */
    public boolean isConfigured(EntityType type) {
        return mobs.contains(type);
    }

    /**
     * 该岛是否允许这个怪物生成。没有设置过就是允许。
     */
    public boolean isMobAllowed(Island island, EntityType type) {
        if (island == null) {
            return true;
        }
        return island.getMetaData(metaKey(type)).map(MetaDataValue::asBoolean).orElse(true);
    }

    /**
     * 翻转该岛对这个怪物的开关，并立刻写库。
     *
     * @return 切换后的状态：true = 允许生成
     */
    public boolean toggleMob(Island island, EntityType type) {
        boolean nowAllowed = !isMobAllowed(island, type);
        island.putMetaData(metaKey(type), new MetaDataValue(nowAllowed));
        island.setChanged();
        // 注意：IslandsManager.updateIsland() 在岛屿文件还没落盘时会静默跳过（objectExists=false），
        // 所以这里直接 saveIsland，保证点完立刻写进数据库。
        IslandsManager.saveIsland(island);
        return nowAllowed;
    }

    private static String metaKey(EntityType type) {
        return META_PREFIX + type.name();
    }

    /** 把带 & 颜色码的字符串转成 Adventure 组件 */
    public static Component color(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text == null ? "" : text);
    }

    /** 给玩家发一条带 & 颜色码的消息（走 Adventure，避免 BentoBox 翻译器把 & 当原文） */
    public static void send(User user, String message) {
        if (user == null || !user.isPlayer() || user.getPlayer() == null) {
            return;
        }
        user.getPlayer().sendMessage(color(message));
    }
}
