# rev200 — 客戶端跳躍診斷／可選速度回傳調和，及 GUI 通用性審查

接續 `feature/generic-conversion-iyamato-corpus3`，父提交
`419d183e8fe31516b2e8152daabbb36a2584b457`。

**本檢查點只有原始碼，不包含可安裝 JAR。客戶端接線原始碼已加入增量還原流程，但尚未完成真正 Minecraft/Fabric/Mixin 編譯與啟動驗證。翅膀偶發上托尚未在遊戲中確認解決。**

## 使用者限制

只處理 Fabric 1.21.11 客戶端連入 Forge 1.7.10。伺服器、原始 RPGTool 與 `old-mods` 不改；先前錯端的 RPGTool 伺服器修補包不是這次方案。直接使用 GitHub 基準，不要求使用者上傳橋接主 JAR。

## 已實作的客戶端來源變更

- 既有 `LegacyLivingBehaviorMixin` 起跳回呼改經 `LegacyClientJumpMotion.sourceJump`；原 `LegacyBehaviorRuntime.jump` 仍恰好呼叫一次，記錄實際前後速度差，不寫死翅膀 ID、0.15 加成或三格高度。
- 在 `LegacyBehaviorClient` 初始化有界 tick 觀測；新增 `LegacyJumpMotionPacketMixin` 的本地主角速度封包前後掛點，以及位置校正／重生、傷害／狀態、爆炸的失效掛點。
- `LegacyJumpEchoWindow` 比對八個 client ticks 內的實際上升歷史，最多每 tick 兩筆樣本。只有來源事件確實增加 Y 且未變更 X/Z 才建立候選；需位置已有上升、當前仍上升、封包三軸吻合舊歷史並將提高當前 Y，才列為疑似延遲回傳。
- 每次跳躍最多處理第一個本地主角速度封包；不吻合也消耗候選。落地、下降、不明再次加速、時鐘倒退、換世界／連線、飛行、騎乘、水／岩漿、攀爬、受傷與校正等情境不調和。
- 選擇調和模式時，只保留「套用該封包之前」的客端 Y；封包 X/Z 保留。沒有取消任何封包、沒有更改 serverbound 封包、沒有修改伺服器或原始模組。
- 同 tick 再次執行 source jump 會被記錄並放棄回傳比對，不會直接刪掉第二次來源事件。

`apply.py` 將三個新 Java 類別、兩處回呼接線與既有 client Mixin JSON 一起套用；不是只放一個未接線 helper。它只接受還原後的 rev198/rev199 fingerprint，先檢查全部來源錨點與新增檔案，再寫入。既有其他 Mixin、fall 回呼、GUI 與先前 checkpoint 保留。

## 為何預設只觀察

現有使用者日誌只含 Forge/FML 自訂封包，沒有跳躍當下的速度／位置校正序列，不能確認症狀的唯一原因。更重要的是速度封包沒有「本次跳躍編號／推力原因」：另一個合法推力也可能恰好有相同向量。數值吻合不是因果證明。

因此預設 `observe` 只紀錄／判定，不改移動。`reconcile` 是明確選用的實驗模式，不能宣稱所有擊退一定不受影響。已知受傷／爆炸情境及不吻合封包會保留；但完全不可區分的同向量情境仍有誤判風險。

真正編譯、啟動驗證後，可使用 Java 啟動參數：

```text
-Dlegacyforgebridge.jumpEcho=observe
-Dlegacyforgebridge.jumpTrace=true
```

需要對照調和候選時，第一行改成：

```text
-Dlegacyforgebridge.jumpEcho=reconcile
```

停用則為 `-Dlegacyforgebridge.jumpEcho=off`。模式在啟動時讀取。觀測紀錄標籤為 `LFB jump-trace`，由既有 `LegacyForgeBridge.LOGGER` 輸出，每個連線情境上限 4096 行；不記錄聊天、驗證資訊或完整封包內容。這些參數對目前已安裝的舊版橋接 JAR 沒有效果，必須先編譯包含這次原始碼的橋接器。

## 實際驗證

- 純 Java 有界狀態機：862 個斷言通過。
- 實際新 adapter 方法搭配明確 recording doubles：observe／reconcile／off 各 48 個斷言，共 144 個通過。
- 原始碼套用器：10 個測試通過，涵蓋 preflight 不寫入、保留舊內容、拒絕錯誤基準／重複錨點／重複套用／symlink 逸出，以及中途寫入失敗回復。
- 測試 JDK 21.0.11，Java 測試執行 `-Xverify:all`。新 Java 原始碼本體編譯，但 Minecraft、Fabric、Mixin、既有 bridge 類別使用的都是測試替身。

**沒有真正產品 API classpath 編譯、完整 Gradle/Loom 建置、Mixin 套用、Minecraft 進入世界、連服驗證、完整累積還原鏈實跑或 Actions 建置。** Recording doubles 不會放進產品原始碼或發行 JAR。測試資料中的延遲不是從使用者遊戲擷取的封包。

```sh
python checkpoints/rev200/tests/run_validation.py --work /path/to/new-validation
python checkpoints/rev200/tests/test_apply.py
python checkpoints/rev200/restore.py --output ../LegacyForgeBridge-rev200-source
```

須在完整 GitHub checkout 執行還原；單獨此增量 ZIP 缺少前版來源，不能獨立建立主橋接器。若已有已還原的 rev199：

```sh
python checkpoints/rev200/apply.py --source-root /path/restored-rev199 --check-only
python checkpoints/rev200/apply.py --source-root /path/restored-rev199
```

這次只有客戶端執行邏輯，沒有更改轉換輸出，因此保留 rev198 的 converter fingerprint，避免為了 runtime 診斷強迫所有 old-mods 重新轉換。rev200 是原始碼 checkpoint 標號，不是假冒已建置的主 JAR 版本。

## Bamboo GUI

見 `GUI-Audit.zh-TW.md`：現有營火 GUI 是固定來源指紋和固定家族布局，不是任意 GUI 的通用轉換。這次完成查核，沒有冒充已完成其通用化，也沒有刪掉檢查把任意 GUI 塞進營火模板。
