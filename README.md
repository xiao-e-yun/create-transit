# Create: Transit

Create 6 附加模組：新增 **Transit Ticker（跨境發報機）**，讓子物流網路的庫存以「虛擬 Stock Link」形式掛載到父網路，構成單向的階層式供應鏈（類似 AE2 的 ME 介面之於子網路）。

- **Minecraft**: 1.20.1（Forge）
- **依賴**: Create 6.x（CreateRegistrate 註冊框架）
- **mod id**: `create_transit`
- **License**: All Rights Reserved
- **狀態**: 開發中，尚未釋出

## 相關模組（上游原始碼）

需要確認上游行為時，一律以這些 repo 的原始碼為準；查詢方式見 [CLAUDE.md](CLAUDE.md)。

| 模組 | Repository | 對應分支 | 本專案使用版本 |
| --- | --- | --- | --- |
| Create | <https://github.com/Creators-of-Create/Create> | `mc1.20.1/dev` | `create_version=6.0.8-289` |
| Flywheel | <https://github.com/Engine-Room/Flywheel> | `1.20.1/dev` | `flywheel_version=1.0.5` |
| Minecraft Forge | <https://github.com/MinecraftForge/MinecraftForge> | `1.20.1` | `forge_version=47.4.22` |

- **Create**：本模組的宿主。物流網路（`LogisticsManager`、`PackagerBlockEntity`、`PackagerLinkBlockEntity`、`StockTickerBlockEntity`）、CreateRegistrate 註冊框架、方塊模型與 ponder 的參考來源。注意 `master`／預設分支已是 NeoForge 1.21，1.20.1 Forge 的程式碼在 `mc1.20.1/dev`。
- **Flywheel**：Create 的渲染後端（instancing / visual）。牽涉 `Visual`、`InstancedBlockEntity` 之類的渲染行為時查這裡；一般邏輯開發用不到。
- **Minecraft Forge**：mod loader 本身。`@Mod`／`DeferredRegister`／event bus、`Capability`（`IItemHandler` 等）、network channel 的 API 與生命週期查這裡。原始碼在 `src/main/java/net/minecraftforge/`。

## 開發

```bash
./gradlew build        # 打包
./gradlew runClient    # 啟動開發客戶端
./gradlew runData      # 資料生成（模型/語言檔等，輸出至 src/generated/resources）
```

依賴設定依照官方文件：<https://wiki.createmod.net/developers/depend-on-create/forge-1.20.1>
