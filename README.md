# SimpMC-BanItem

面向 Folia 26.1.2 的交互式违规物品没收插件。

## 安装

1. 将 target 目录中的 SimpMC-BanItem-1.0.0.jar 放入服务器 plugins 目录。
2. 启动服务器，插件会生成 config.yml、banned-items.yml 和违规记录文件 violations.yml。
3. 修改配置后执行 /banitem reload，需要权限 banitem.admin。

## 配置

- banned-items.yml：违规材质列表。支持 BEDROCK、minecraft:bedrock、minecraft:* 和 *。
- config.yml 中的 whitelist.users：按玩家名绕过没收，名称不区分大小写。
- config.yml 中的 whitelist.uuids：按 UUID 绕过没收。
- config.yml 中的 creative-drop.blacklist：禁止创造模式玩家丢出的材质。
- config.yml 中的 creative-drop.enabled：是否启用创造模式丢弃限制。

白名单只绕过违规物品没收，不绕过创造模式丢弃限制。若一个材质同时出现在两个名单里，非白名单玩家的物品会优先被没收；白名单创造玩家仍不能丢出它。

## 检测与日志

插件会在使用物品、攻击或交互实体、切换物品栏、交换双手、拾取、丢弃、背包点击/拖动/开关等交互时检查。物品和背包操作在 Folia 所属区域线程即时完成；配置读取和 violations.yml 写入在后台执行。其他插件已保护或取消的容器操作不会删除容器中的物品，但仍会检查玩家自己的背包与光标。

每条记录包含玩家名、UUID、UTC 时间、材质、数量、触发动作、世界和坐标。错误的 YAML、UUID 或 Minecraft 材质会使重载失败，旧规则继续有效。
