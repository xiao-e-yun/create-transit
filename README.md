# Create: Nest Network

Create 6 附加模組：新增 **Stock Proxyer（庫存代理器）**，讓子物流網路的庫存以「虛擬 Stock Link」形式掛載到父網路，構成單向的階層式供應鏈（類似 AE2 的 ME 介面之於子網路）。

- **Minecraft**: 1.20.1（Forge）
- **依賴**: Create 6.x（CreateRegistrate 註冊框架）
- **mod id**: `create_nest_network`
- **License**: All Rights Reserved

## 設計決策（定案）

父網路 Stock Link →（附著於）→ **Stock Proxyer** →（委派查詢）→ 子網路

1. **接法比照打包機**：父網路的原版 Stock Link 直接貼在 Stock Proxyer 上（如同貼在 Packager 上）。父側 100% 原版 Link，免費繼承註冊表、keepAlive、紅石優先級。實作：`StockProxyerBlockEntity` 繼承 `PackagerBlockEntity`（Repackager 有先例），覆寫 `getAvailableItems()` 回傳子網路摘要、覆寫出貨路徑轉發訂單。
2. **純上行**：Proxyer 只把子網路庫存公開給父網路。下行補給用原版 Redstone Requester / Factory Gauge 直接調到父網路，原版已覆蓋。
3. **環處理 = 查詢期重入防護**（取代拓撲 DFS）：摘要聚合與訂單分派都是同步呼叫棧，用 ThreadLocal visited set（freqId 集合）防重入；環「合法但無效」——環上節點貢獻空摘要。觸發時以 goggles 提示。不做放置期偵測、不做 hop 中繼資料。
4. **同步轉發約束**：Proxyer 收到請求當場同步呼叫子網路 `broadcastPackageRequest`，不做「欠著補發」的延遲重試（維持 visited set 有效性；未來要做延遲重試時用 promise 表內建的來源資訊當種子）。
5. **兩段式下單**：下單瞬間不信任快取——先對子網路做即時可行性檢查（庫存 + `isTooBusyFor`），模擬通過才登記 promise 並轉發，否則回絕。顯示層走原版 20-tick 快取（可以舊），成交走即時模擬（保證對）。
6. **無 tick 設計**：不實作睡眠/喚醒也不開放更新頻率設定——摘要查詢與訂單都是被動觸發，`LogisticsManager` 的 20-tick 快取天然節流；轉發中 promise 表用惰性清理（查詢/收貨時順手掃過期）。
7. **穩定外殼 + 可換內裡**（AE2 Storage Bus 模式）：對外身分終生不變，失效狀態只把內部委派換成空摘要，不反覆註冊/註銷。
8. **乒乓補貨不處理**：異步包裹物流 + Gauge 發送上限使其僅是浪費吞吐，歸類玩家設計問題，文件警告即可。
9. **區塊載入不管**：職責邊界外，文件註明「子網路需保持載入」。
10. **實體物流地址轉譯**：(a) 純轉發地址或 (b) 中繼倉緩衝（Repackager 式）。**尚未定案**。

## 第一版（v0.1.0）實作範圍

- [ ] Stock Proxyer 可被原版 Stock Link 附著（繼承 `PackagerBlockEntity` 路線）
- [ ] 子網路綁定機制（比照 Stock Link 的調頻互動）
- [ ] 摘要委派 + ThreadLocal visited set 重入防護
- [ ] 同步訂單轉發 + 地址轉譯
- [ ] 兩段式下單（模擬通過才登記 promise）
- [ ] 轉發中 promise 表（惰性過期清理）
- [ ] goggles 顯示（綁定狀態／循環警告）
- [ ] 同容器重複計數防護（直連 + 經 Proxy 同時公開時的 `InventoryIdentifier` 去重）
- [ ] 材質、合成配方、創造模式分頁、本地化（en_us / zh_tw）

## 下一步功課

- 讀 Create 原始碼三個類：`LogisticsManager`、`PackagerLinkBlockEntity`、`StockTickerBlockEntity`，確認 `InventorySummary` 的取得與請求分派流程、虛擬 Link 的註冊點。
- 實測 Create: Meta Logistics 與 Create: Additional Logistics，確認定位不重疊（Meta Logistics 主打未載入區塊的遠端存取，與本模組互補）。

## 開發

```bash
./gradlew build        # 打包
./gradlew runClient    # 啟動開發客戶端
./gradlew runData      # 資料生成（模型/語言檔等，輸出至 src/generated/resources）
```

依賴設定依照官方文件：<https://wiki.createmod.net/developers/depend-on-create/forge-1.20.1>
