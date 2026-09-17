# Oh My Minecraft Client Forge 1.20.1

本 mod 迁移至 [ommc](https://github.com/plusls/oh-my-minecraft-client)

> 这是 [Oh My Minecraft Client](https://github.com/plusls/oh-my-minecraft-client) 的 **Forge 1.20.1 移植版**。
> 原版是 Fabric 模组，作者为 plusls。本移植版与原版各自独立维护，功能范围也不完全一致
> （差异见下文「尚未实现的功能」与「与 Fabric 版的差异」）。

[>>> English <<<](./README_EN.md)

让Minecraft再次伟大！

默认使用 **O + C** 打开设置界面

![icon](./icon.jpg)

# 依赖

| 依赖 | 下载 |
|----|----|
| MaFgLib | [GitHub](https://github.com/ThinkingStudio/MaFgLib) |

MaFgLib 是 malilib 的 Forge 移植版，本模组的配置系统、配置界面与热键系统全部来自它，**属于硬依赖，必须一并安装**。

# 功能

## 保留聊天记录 (dontClearChatHistory)

离开世界时把聊天框里的消息记录保存到文件，下次进入同一个世界时自动恢复显示。

- 分类: `通用`
- 类型: `开关`
- 默认值: `false`

**保存位置**

```
<游戏目录>/config/ommc/chat-history/<SHA-1 指纹>.json
```

**按世界隔离**

聊天记录会按「世界」分开保存，不同存档、不同服务器之间**互不串档**：

- 单人存档：以存档根目录（`saves/<存档名>`）的绝对路径作为唯一标识
- 多人服务器：以服务器地址（`主机:端口`）作为唯一标识

标识经 SHA-1 哈希后作为文件名，因此既不会重名，也不会因为世界名含有非法字符而写不出文件。
文件内部同时记录了可读的世界名，方便人工辨认；读取时还会再校验一次文件内的标识，
即使文件被手工改名或复制，也不会串档。

**保存时机**

在「退出世界」时保存。此时聊天记录尚未被清空，所以内容完整。

**恢复时的行为**

- 恢复的消息会一次性显示出来，约 10 秒后随原版机制淡出
- 恢复的消息会剥掉点击 / 悬停事件 —— 那些事件原本绑定在服务器消息上，
  离线恢复后若保留，点击会向当前服务器发送指令，既莫名其妙又有风险
- 消息前会加一条分隔线 `── 上次会话的聊天记录 ──`，便于区分
- 最多保留最近 100 条

**与 Fabric 版的差异**

原版的 `dontClearChatHistory` 是「在会话内阻止聊天框被清空」。
本移植版改为「退出时存盘、进入时恢复」，属于**跨会话持久化**，
因此也不受 F3+D 清空聊天的影响。另外原版语义包含保留输入历史（按 ↑ 翻的那些），
本移植版**只保存消息记录**，不保存输入历史。

# 尚未实现的功能

以下功能在 Fabric 版中存在，但本移植版**尚未实现**，
在游戏内的配置界面中会以红色「（未实现）」标注：

## 通用

- `clearWaypoint` 取消高亮坐标点
- `parseWaypointFromChat` 从聊天中解析路径点
- `forceParseWaypointFromChat` 强制从聊天中解析路径点

## 特性开关

- `disableBlocklistCheck` 关闭玩家黑名单检查
- `disablePistonPushEntity` 禁止活塞推动实体
- `highlightPersistentMob` 高亮不会消失的怪物
- `highlightPersistentMobClientMode` 高亮不会消失的怪物客户端模式
- `worldEaterMineHelper` 世吞挖矿助手

## 列表

- `highlightEntityBlackList` 高亮实体列表黑名单
- `highlightEntityListType` 高亮实体列表类型
- `highlightEntityWhiteList` 高亮实体列表白名单
- `blockModelNoOffsetBlackList` 方块模型没有偏移列表黑名单
- `blockModelNoOffsetListType` 方块模型没有偏移列表类型
- `blockModelNoOffsetWhiteList` 方块模型没有偏移列表白名单
- `worldEaterMineHelperWhitelist` 世吞挖矿助手白名单

# 许可

本项目在 LGPL-3.0 许可证下可用。原版 [Oh My Minecraft Client](https://github.com/plusls/oh-my-minecraft-client) 同样使用 LGPL-3.0。
