package world.bentobox.mobcontrol;

import java.util.ArrayList;
import java.util.List;

import world.bentobox.bentobox.api.configuration.ConfigComment;
import world.bentobox.bentobox.api.configuration.ConfigEntry;
import world.bentobox.bentobox.api.configuration.ConfigObject;
import world.bentobox.bentobox.api.configuration.StoreAt;

/**
 * 附属配置。
 * <p>
 * 规矩：implements ConfigObject、public 无参构造、每个字段都有 getter/setter、
 * 类上标 {@code @StoreAt}（决定文件落在 plugins/BentoBox/addons/MobControl/config.yml）。
 * </p>
 */
@StoreAt(filename = "config.yml", path = "addons/MobControl")
@ConfigComment("MobControl configuration")
@ConfigComment("mobs 里列出允许玩家自己开关的怪物（Bukkit EntityType 名称），默认只有 SLIME。")
public class Settings implements ConfigObject {

    @ConfigEntry(path = "mobs")
    private List<String> mobs = new ArrayList<>(List.of("SLIME"));

    @ConfigEntry(path = "gui.title")
    private String guiTitle = "&8岛屿怪物生成控制";

    @ConfigEntry(path = "gui.rows")
    private int guiRows = 3;

    @ConfigEntry(path = "gui.allowed-lore")
    private String allowedLore = "&a当前状态: &2允许生成";

    @ConfigEntry(path = "gui.blocked-lore")
    private String blockedLore = "&c当前状态: &4禁止生成";

    @ConfigEntry(path = "gui.click-hint")
    private String clickHint = "&7点击切换";

    @ConfigEntry(path = "spawn.only-natural")
    private boolean onlyNatural = true;

    @ConfigEntry(path = "messages.no-island")
    private String noIsland = "&c你还没有岛屿。";

    @ConfigEntry(path = "messages.not-your-island")
    private String notYourIsland = "&c这不是你的岛屿，你没有权限修改。";

    @ConfigEntry(path = "messages.no-mobs")
    private String noMobs = "&c服务端没有配置任何可切换的怪物。";

    @ConfigEntry(path = "messages.toggled-on")
    private String toggledOn = "&a[怪物控制] 已允许 &e%mob% &a在你的岛屿生成。";

    @ConfigEntry(path = "messages.toggled-off")
    private String toggledOff = "&c[怪物控制] 已禁止 &e%mob% &c在你的岛屿生成。";

    public List<String> getMobs() {
        return mobs;
    }

    public void setMobs(List<String> mobs) {
        this.mobs = mobs;
    }

    public String getGuiTitle() {
        return guiTitle;
    }

    public void setGuiTitle(String guiTitle) {
        this.guiTitle = guiTitle;
    }

    public int getGuiRows() {
        return guiRows;
    }

    public void setGuiRows(int guiRows) {
        this.guiRows = guiRows;
    }

    public String getAllowedLore() {
        return allowedLore;
    }

    public void setAllowedLore(String allowedLore) {
        this.allowedLore = allowedLore;
    }

    public String getBlockedLore() {
        return blockedLore;
    }

    public void setBlockedLore(String blockedLore) {
        this.blockedLore = blockedLore;
    }

    public String getClickHint() {
        return clickHint;
    }

    public void setClickHint(String clickHint) {
        this.clickHint = clickHint;
    }

    public boolean isOnlyNatural() {
        return onlyNatural;
    }

    public void setOnlyNatural(boolean onlyNatural) {
        this.onlyNatural = onlyNatural;
    }

    public String getNoIsland() {
        return noIsland;
    }

    public void setNoIsland(String noIsland) {
        this.noIsland = noIsland;
    }

    public String getNotYourIsland() {
        return notYourIsland;
    }

    public void setNotYourIsland(String notYourIsland) {
        this.notYourIsland = notYourIsland;
    }

    public String getNoMobs() {
        return noMobs;
    }

    public void setNoMobs(String noMobs) {
        this.noMobs = noMobs;
    }

    public String getToggledOn() {
        return toggledOn;
    }

    public void setToggledOn(String toggledOn) {
        this.toggledOn = toggledOn;
    }

    public String getToggledOff() {
        return toggledOff;
    }

    public void setToggledOff(String toggledOff) {
        this.toggledOff = toggledOff;
    }
}
