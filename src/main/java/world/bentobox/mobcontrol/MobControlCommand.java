package world.bentobox.mobcontrol;

import java.util.List;

import org.bukkit.entity.Player;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;

/**
 * 打开怪物生成开关面板。
 * <p>
 * 两种注册方式：
 * <ul>
 *   <li>{@code new MobControlCommand(addon)} → 独立命令 {@code /mobcontrol}（别名 {@code /mobs}）</li>
 *   <li>{@code new MobControlCommand(addon, gameModePlayerCommand)} → 岛屿命令的子命令
 *       {@code /is mob}（别名 {@code /is mobs}），由 {@link MobControlAddon} 挂到每个游戏模式的玩家命令上</li>
 * </ul>
 * </p>
 */
public class MobControlCommand extends CompositeCommand {

    /** 独立命令：/mobcontrol（别名 /mobs） */
    public MobControlCommand(MobControlAddon addon) {
        super(addon, "mobcontrol", "mobs");
    }

    /**
     * 岛屿命令的子命令：/is mob（别名 /is mobs）。
     * <p>
     * 标签故意只用 "mob"：BentoBox 的玩家命令标签由各游戏模式决定（BSkyBlock 是 is、AcidIsland 是 ai…），
     * 所以这里不写死前缀，帮助里显示成 {@code /is mob} 是拼接 topLabel 出来的。
     * </p>
     *
     * @param addon  本附属
     * @param parent 游戏模式的玩家命令（{
     *               {@code gameMode.getPlayerCommand().get()}）
     */
    public MobControlCommand(MobControlAddon addon, CompositeCommand parent) {
        super(addon, parent, "mob", "mobs");
    }

    @Override
    public void setup() {
        // 注意：setup() 是父类构造器里调用的，此时子类字段还没赋值，
        // 所以这里只能用 getAddon() / getParent()，不能用 this.addon 之类的字段。
        if (getParent() == null) {
            // 独立命令：permissionPrefix 是 "mobcontrol."，setPermission 会自动拼前缀，
            // 所以这里只写 "use"，最终权限是 mobcontrol.use。
            setPermission("use");
        } else {
            // 游戏模式下的子命令：permissionPrefix 是游戏模式前缀（bskyblock.），
            // 所以这里写全 "mobcontrol.use"，最终权限是 bskyblock.mobcontrol.use；
            // addon.yml 里用 [gamemode] 占位声明，BentoBox 会为每个游戏模式各注册一份。
            setPermission("mobcontrol.use");
        }
        setDescription("打开岛屿怪物生成控制面板");
        setUsage(buildUsage());
        setOnlyPlayer(true);
    }

    /**
     * 帮助里显示的用法：独立命令是 {@code mobcontrol}，子命令优先用最短别名，
     * 这样 BentoBox 的玩家命令（label 是 island、别名里有 is）就会显示成 {@code /is mob} 而不是 {@code /island mob}。
     */
    private String buildUsage() {
        String top = getTopLabel();
        CompositeCommand parent = getParent();
        if (parent == null) {
            return top;
        }
        for (String alias : parent.getAliases()) {
            if (alias != null && !alias.isBlank() && alias.length() < top.length()) {
                top = alias;
            }
        }
        return top + " " + getLabel();
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        MobControlAddon addon = (MobControlAddon) getAddon();
        Settings settings = addon.getSettings();

        if (addon.getMobs().isEmpty()) {
            MobControlAddon.send(user, settings.getNoMobs());
            return false;
        }

        Island island = findIsland(user);
        if (island == null) {
            MobControlAddon.send(user, settings.getNoIsland());
            return false;
        }
        if (!canManage(user, island)) {
            MobControlAddon.send(user, settings.getNotYourIsland());
            return false;
        }

        new MobControlPanel(addon, user, island).open();
        return true;
    }

    /**
     * 优先用玩家脚下这个岛；脚下不是自己的岛（比如站在别人岛上）就退回到自己第一个岛。
     */
    private Island findIsland(User user) {
        Player player = user.getPlayer();
        if (player != null) {
            Island here = getIslands().getIslandAt(player.getLocation()).orElse(null);
            if (here != null && canManage(user, here)) {
                return here;
            }
        }
        List<Island> own = getIslands().getIslands(user.getUniqueId());
        if (own != null) {
            for (Island island : own) {
                if (canManage(user, island)) {
                    return island;
                }
            }
        }
        return null;
    }

    /**
     * 岛主、岛屿成员，或者有管理员权限的人可以改。
     * <p>
     * 管理员权限有两种：独立命令下的 {@code mobcontrol.admin}，以及挂在游戏模式下的
     * {@code <gamemode>.mobcontrol.admin}（例如 bskyblock.mobcontrol.admin）。
     * </p>
     */
    private boolean canManage(User user, Island island) {
        if (island == null) {
            return false;
        }
        if (user.getUniqueId().equals(island.getOwner())) {
            return true;
        }
        if (island.getMemberSet() != null && island.getMemberSet().contains(user.getUniqueId())) {
            return true;
        }
        if (user.hasPermission("mobcontrol.admin")) {
            return true;
        }
        return getPlugin().getIWM().getAddon(island.getWorld())
                .map(gameMode -> user.hasPermission(gameMode.getPermissionPrefix() + "mobcontrol.admin"))
                .orElse(false);
    }
}
