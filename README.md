# Buff 数值透视 (Buff Insight)

Minecraft 1.20.1 Forge **客户端**模组，将原版药水效果图标替换为信息完整的 BUFF 卡片面板。

Minecraft 1.20.1 · Forge 47.4.0 · Java 17 · 仅客户端

## 功能

- **卡片面板**：效果名、等级（罗马数字）、0.1 秒精度倒计时、剩余百分比、进度条
- **三种视觉风格**：极简无边风 / 原版提示风 / 彩色卡片风
- **属性详情**：直接显示属性数值（如"移动速度 +20%"），无属性效果显示内置作用描述
- **内置 33 种原版效果说明**（中英双语），其他模组效果可通过资源包扩展 `effect.buff_insight.desc.<命名空间>.<路径>` 词条
- **悬停详细说明**：背包与 HUD 上悬停查看属性 + 描述 + 剩余时间
- **AI 智能分析**（可选）：接入任意 OpenAI 兼容接口（智谱 GLM / DeepSeek / OpenAI / 本地 Ollama 等），自动生成效果作用分析与实用建议，结果按「效果+等级」持久缓存
- **动画**：卡片滑动淡入淡出、即将消失时整卡闪烁、超长文字滚动显示
- **三种 HUD 模式**：替换原版图标 / 与原版并存（获得效果时弹出顶部通知卡）/ 关闭（保留原版并支持悬停详情）
- **反馈**：获得/失去效果提示音（增益高音、减益低音）与粒子
- **界面尺寸自适应**：面板宽度、垂直压缩、创造模式物品栏位置回退均自动处理

## 安装

1. 安装 [Minecraft Forge 1.20.1-47.4.0](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html)。
2. 从 [Releases](https://github.com/Z80Z99/z80z-buff-insight/releases) 下载最新的 `z80z-buff-insight-<版本>.jar`。
3. 放入 `.minecraft/mods/` 文件夹。

**仅需安装在客户端，服务器不需要安装本模组**，也没有第三方前置。

## 使用

- 按 **M** 打开设置界面（可在按键设置中修改），或从 Forge 模组菜单点「配置」
- 配置保存在 `config/buff_insight.json`

### AI 分析配置

设置界面 →「AI 分析」分区，填入接口地址、模型名和 API Key 并开启开关。
默认指向智谱 OpenAI 兼容地址，模型 `glm-4-flash`（免费档）。
数据仅在开关开启时发送到所配置的服务商；分析结果缓存于 `config/buff_insight_ai_cache.json`。

> **隐私提示**：API Key 以明文保存在 `config/buff_insight.json`，请勿分享该文件或随整合包分发。

## 从源码构建

需要 JDK 17，项目自带 Gradle Wrapper：

```bash
./gradlew build
```

产物位于 `build/libs/z80z-buff-insight-<版本>.jar`。

## 环境

- Minecraft 1.20.1
- Forge 47.4.0
- Java 17

## 许可证

MIT，详见 [LICENSE](LICENSE)。
