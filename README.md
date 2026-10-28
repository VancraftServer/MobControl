# MobControl — BentoBox 岛屿怪物生成开关附属

给玩家一个面板，让他们自己决定「哪些怪物不许在我的岛上生成」。开/关状态存在**每个岛自己**身上
（BentoBox 岛屿 metadata），所以重启服务器、甚至换数据库都不会丢。

默认配置里只写了史莱姆：玩家点一下史莱姆图标，自己岛上就再也不刷史莱姆了。

```
/is mob              # 主用法：挂在岛屿命令下面（/island mob 也行，别名 /is mobs）
/mobcontrol          # 备用：独立命令（别名 /mobs），方便控制台或没装游戏模式时用
```

---

## 目录结构

```
MobControl/
├─ pom.xml                          Maven 构建
├─ build.ps1                        不装 Maven 时的编译脚本（javac + jar）
├─ dist/MobControl-1.0.0.jar        产物：丢进 plugins/BentoBox/addons/ 即可
└─ src/main/
   ├─ java/world/bentobox/mobcontrol/
   │  ├─ MobControlAddon.java       主类：onLoad 读配置、onEnable 注册命令+监听器
   │  ├─ MobControlCommand.java     /is mob 与 /mobcontrol：找岛、查权限、开面板
   │  ├─ MobControlPanel.java       面板：一个怪物一个刷怪蛋图标，点击切换
   │  ├─ SpawnListener.java         CreatureSpawnEvent：拦住被关掉的怪物
   │  └─ Settings.java              配置对象（@StoreAt / @ConfigEntry）
   └─ resources/
      ├─ addon.yml                  附属描述（name / main / api-version …）
      └─ config.yml                 默认配置（会被复制到 plugins/BentoBox/addons/MobControl/）
```

---

## 编译

### 方式一：build.ps1（不需要联网、不需要 Maven）

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
```

参数都有默认值，一般不用传：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `-JavaHome` | `F:\Java\JDK25` | **必须 JDK 25**，见下面「踩过的坑」 |
| `-BentoBoxJar` | `..\bb3.jar` | BentoBox 核心 jar |
| `-PaperLibraries` | `..\paper-test\libraries` | Paper 自带的依赖（adventure 等） |
| `-Version` | `1.0.0` | 产物版本号 |

产物：`dist\MobControl-<Version>.jar`（约 13 KB）。

### 方式二：Maven

```powershell
$env:JAVA_HOME = 'F:\Java\JDK25'
mvn -B -s ..\.tmp\maven-settings.xml package
```

如果本地仓库里还没有 BentoBox（内网拉不到 codemc），先把核心 jar 装进去：

```powershell
mvn -B -s ..\.tmp\maven-settings.xml install:install-file `
    '-Dfile=..\bb3.jar' '-DgroupId=world.bentobox' '-DartifactId=bentobox' `
    '-Dversion=3.23.4' '-Dpackaging=jar'
```

---

## 安装 & 使用

1. 把 `dist\MobControl-1.0.0.jar` 放进 `plugins/BentoBox/addons/`；
2. 重启服务器（BentoBox 只在自己启用时扫描 addons 目录）；
3. 首次启动会生成 `plugins/BentoBox/addons/MobControl/config.yml`；
4. 在游戏里站在自己岛上输入 `/is mob`（`/is` 是本服 BSkyBlock 的岛屿命令，别的游戏模式是 `/ai`、`/cb`…；`/mobcontrol` 也仍然可用）。

面板里每个怪物一个刷怪蛋图标：

* 绿色名字 + 附魔光晕 = 允许生成（默认）
* 红色名字 + 没有光晕 = 禁止生成
* 点一下立刻切换，图标原地刷新，面板不会关掉

### `/is mob` 是怎么挂上去的

`/is` 不是 Bukkit 注册的插件命令，而是游戏模式（GameModeAddon）自己的 `CompositeCommand`。
所以本附属在 `onEnable()`（以及 `allLoaded()` 兜底）里遍历所有已启用的游戏模式，
往它的玩家命令下 `new MobControlCommand(this, playerCommand)` ——
`CompositeCommand` 的「带 parent」构造器会自动把自己塞进父命令的 `getSubCommands()`：

```java
for (GameModeAddon gameMode : getPlugin().getAddonsManager().getGameModeAddons()) {
    gameMode.getPlayerCommand().ifPresent(parent -> {
        if (parent.getSubCommand("mob").isEmpty()) {
            new MobControlCommand(this, parent);   // /is mob、/ai mob …
        }
    });
}
```

顺序上也没问题：BentoBox 先启用所有 GameModeAddon，再启用其它附属，所以这里拿得到玩家命令。
`onDisable()` 里会把 `mob`/`mobs` 从父命令的子命令表里摘掉。

---

## 权限

| 权限 | 默认 | 作用 |
| --- | --- | --- |
| `mobcontrol.use` | 所有玩家 | 用 `/mobcontrol` 打开面板 |
| `mobcontrol.admin` | OP | 用 `/mobcontrol` 开关**任意**岛的怪物 |
| `<游戏模式>.mobcontrol.use`（如 `bskyblock.mobcontrol.use`） | 所有玩家 | 用 `/is mob` 打开面板 |
| `<游戏模式>.mobcontrol.admin`（如 `bskyblock.mobcontrol.admin`） | OP | 用 `/is mob` 开关**任意**岛的怪物 |

`addon.yml` 里后面两条是用 `[gamemode]` 占位声明的，BentoBox 会为每个已启用的游戏模式各注册一份
（BSkyBlock → `bskyblock.…`、AcidIsland → `acidisland.…`）。

只有岛主、岛屿成员（或带上面两种 admin 权限的人）才能改这个岛。

---

## 配置文件 `config.yml`

```yaml
# 允许玩家自己开关的怪物列表；只有写在这里的怪物才会出现在面板里
mobs:
  - SLIME
  # - ZOMBIE
  # - SKELETON

gui:
  title: '&8岛屿怪物生成控制'
  rows: 3                       # 1-6 行
  allowed-lore: '&a当前状态: &2允许生成'
  blocked-lore: '&c当前状态: &4禁止生成'
  click-hint: '&7点击切换'

spawn:
  # true  = 只拦自然生成（刷怪笼、刷怪蛋、史莱姆分裂、插件生成都不受影响）
  # false = 该怪物在这个岛上的所有生成来源都拦
  only-natural: true

messages:                       # 全部支持 & 颜色码，%mob% = 怪物名
  no-island: '&c你还没有岛屿。'
  not-your-island: '&c这不是你的岛屿，你没有权限修改。'
  no-mobs: '&c服务端没有配置任何可切换的怪物。'
  toggled-on: '&a[怪物控制] 已允许 &e%mob% &a在你的岛屿生成。'
  toggled-off: '&c[怪物控制] 已禁止 &e%mob% &c在你的岛屿生成。'
```

怪物名就是 Bukkit `EntityType` 的名字（`SLIME`、`ZOMBIE`、`SKELETON`、`CREEPER`、
`ENDERMAN`、`PHANTOM`、`WITCH`…），大小写都行；写错的名字会被跳过并在控制台提示。
改完配置执行 `/bentobox reload` 或重启即可（`onReload()` 会重新读，不需要重进世界）。

---

## 实现要点

**状态存哪**：每个岛的 `Island.metaData`，key 为 `mobcontrol:<ENTITY_TYPE>`，值为布尔。
没有这个 key = 默认允许。岛屿 JSON 里长这样：

```json
"metaData": {
  "mobcontrol:SLIME": { "booleanValue": false }
}
```

**怎么拦截**：监听 `CreatureSpawnEvent`（`EventPriority.HIGH`, `ignoreCancelled = true`）：

1. 这个类型不在 `mobs` 里 → 直接放行（不参与，零开销）；
2. `only-natural: true` 且 spawnReason 不是 `NATURAL` → 放行；
3. `IslandsManager.getIslandAt(location)` 拿不到岛（在虚空里、在没人要的区块）→ 放行；
4. 否则看该岛对这个怪物的开关，关掉了就 `event.setCancelled(true)`。

**为什么不用 `IslandsManager.updateIsland()`**：它的源码是

```java
if (handler != null && handler.objectExists(island.getUniqueId())) { ... 存库 ... }
```

岛屿刚创建、json 还没落盘时 `objectExists()` 是 `false`，于是它**静默什么都不做**——实测踩过，
点完开关重启就丢。所以这里直接 `island.setChanged()` + `IslandsManager.saveIsland(island)`
（`saveIsland` 是 `saveObjectAsync`，异步写入，不卡主线程）。

---

## 实测记录（Paper 1.21.11 + BentoBox 3.23.4 + BSkyBlock 1.20.0）

| 场景 | 结果 |
| --- | --- |
| 默认状态（未设置）刷史莱姆 | `valid`，附近史莱姆 6 → 7 |
| 面板关掉史莱姆后再刷 | `invalid`（被取消），数量 6 → 6 |
| 关掉史莱姆、刷**没配置**的僵尸 | `valid`（不受影响） |
| 关掉史莱姆、用 `CUSTOM` 原因刷史莱姆（`only-natural: true`） | `valid`（不受影响） |
| 关掉后重启服务器 | 岛屿 json 里仍是 `"mobcontrol:SLIME": false`，继续拦 |
| 重新点开（toggle 回允许） | `allowed=true`，史莱姆 6 → 7，恢复生成 |
| 面板图标 | `SLIME_SPAWN_EGG` / `ZOMBIE_SPAWN_EGG` / `CREEPER_SPAWN_EGG`，关掉的红色无光晕 |
| `/mobcontrol` 从控制台派发 | 返回 `true`（命令注册正常，非玩家会被 `setOnlyPlayer` 拦掉） |
| `/is mob` 是否挂上 | `subKeys=[…, help, mob]`、`subMob=true`、别名 `mobs` 也解析成功 |
| `/is mob` 帮助里的用法 | `usage=/island mob` / `getUsage()=/is mob`（自动挑了最短别名） |
| `/is mob` 的权限字符串 | `bskyblock.mobcontrol.use`，且 `permission registered … = yes(default=true)` |
| `is mob` / `is mobs` 控制台派发 | 都返回 `true` |

---

## 踩过的坑（改代码前先看）

1. **命令不要注册两次**。`CompositeCommand` 的构造器里已经 `commandsManager.registerCommand(this)` 了；
   如果在 `onEnable()` 里再 `registerCommand(new MobControlCommand(this))`，Bukkit 的
   `commandMap.register()` 第二次会返回 `false`，控制台就多一行
   `[BentoBox] Failed to register command mobcontrol mobcontrol`（功能其实正常，纯粹是噪音）。
2. **附属比岛屿先启用**。BentoBox `completeSetup()` 里是 `enableAddons()` → `islandsManager.load()`，
   所以 `onEnable()` 里拿不到任何已有岛屿——需要岛数据的逻辑放到事件里，或者在命令执行时再取。
3. **面板点击要 `return true`**。`PanelListenerManager` 里是
   `event.setCancelled(handler.onClick(...) || hasCursorItem(event))`，返回 `false` 点击就"漏"过去
   （变成玩家真的在拖物品），返回 `true` 才算消费掉、面板保持打开。
4. **`&` 颜色码别直接塞给 `User.sendMessage(String)`**：BentoBox 的翻译器找不到 locale key 时
   只做 `%var%` 替换，不处理 `&` 码。这里统一用 `LegacyComponentSerializer.legacyAmpersand()`
   转成 Adventure `Component` 再发。
5. **`addon.yml` 里的 `api-version` 是"最低 BentoBox 版本"**，不是附属自己的版本；这里写 `3.0.0`。
   权限要生效，`permissions` 里每条都必须同时有 `description` 和 `default`，否则 BentoBox 不注册。
6. **编译必须用 JDK 25**。BentoBox 3.23.4 的 class 是 Java 25 字节码（class version 69），
   JDK 21 的 javac 会报"类文件具有错误的版本 69.0"。脚本里已经 `--release 21` 产出 Java 21 字节码，
   插件在 JDK 21+ 的服务器上照样能跑。
7. **PowerShell 脚本存成 UTF-8 带 BOM**。Windows PowerShell 5.1 会把无 BOM 的 UTF-8 当 ANSI/GBK 读，
   中文注释能让后面的多行管道赋值直接失效（表现为 `$libs` 为 `$null`，javac 说找不到 org.bukkit）。
   `build.ps1` 里故意只写英文注释，改它的时候也建议保存成 UTF-8 with BOM。
8. **`setPermission()` 会自动拼 `permissionPrefix`**，别自己再拼一遍：
   独立命令的前缀是 `mobcontrol.`（所以写 `setPermission("use")` → `mobcontrol.use`），
   子命令的前缀继承父命令（BSkyBlock 是 `bskyblock.`，所以写 `setPermission("mobcontrol.use")`
   → `bskyblock.mobcontrol.use`，并在 `addon.yml` 里用 `'[gamemode].mobcontrol.use'` 声明）。
   我之前两种都写成 `setPermission(getAddon().getPermissionPrefix() + "use")`：独立命令变成
   `mobcontrol.mobcontrol.use`、子命令变成 `bskyblock.mobcontrol.use`，而 `addon.yml` 里只声明了
   `mobcontrol.use`，于是普通玩家全被拒——OP/控制台又永远测不出来（`runPermissionCheck` 里
   `!user.isOp()` 直接短路），必须在普通权限玩家身上才能发现。
9. **`CompositeCommand` 的「带 parent」构造器没有 `plugin.getCommand(label)` 那套检查**，
   它直接 `parent.getSubCommands().put(label, this)`；所以挂子命令不会碰 Bukkit 命令表，
   也不会有 "Failed to register command" 报错。重复挂靠 `parent.getSubCommand("mob").isPresent()` 判重。

