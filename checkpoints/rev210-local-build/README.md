# rev210 完整主檔 — 2026-09-28 本地建置與交付紀錄

本紀錄附加在 rev211 checkpoint 之後，**不回退分支、不修改 rev211 來源**。依使用者要求，交付的主檔只包含 rev209 累積修正及精確 rev210 兩份來源；不包含 rev211 的 pendingUsingTick，也沒有啟用裝填／連射 tick。

## 完整主 JAR

- 檔名：`legacyforgebridge-0.2.0-alpha.27-rev210-local-test.1.jar`
- 大小：4,141,097 bytes。
- SHA-256：`445ef6b3a514492ccd74bb96b287c3aba3d9a9a1469030b9970edc49b94a0b32`
- 版本：`0.2.0-alpha.27-rev210-local-test.1`
- 轉換指紋：`2026-09-28.210-source-feedback-numeric-helpers`
- 來源修正提交：`25fdf21b5cdfbed30289c0aee2b7c292d4070e85`

主 JAR 由對話附件交付；這個目錄是建置紀錄，不包含主檔或原始模組。
它是完整的本地測試版主 JAR，不是附加補丁；不代表所有 iYAMATO 武器、特殊彈體、裝填、GUI 都已完成。

## 精確建置鏈

使用者提供的 rev208 SHA-256：
`81be9ccaabb178ea7a8be1050cdfe0a814c0f4f8e6489a533bc48e17eb058cf1`。

還原並核對 rev209 六個 payload 分片，XZ SHA-256：
`4ce478ee4fc86a74fa94ce9d3a2c35a2bed16694fc456b0cc63f268b667c6aed`，全部 70 個 delta 檔案 hash 通過。
未還原 199 份 rev208 reuse／完整 269 檔 kit；需要的七份產品來源均在已驗證 delta 中。
另建立 28 份明確拋錯的 compile-only 原生 API 宣告，與產品目錄隔離。

用 OpenJDK 21.0.11、javac --release 21 及精確 Integrate209 建構，得到完整 rev209：
`f9cce93d55b745a5de286351a70304ed051157caebd505b0dffdcb2607fb19d7`，
4,140,190 bytes，**逐位元組匹配前次交付**。

兩份 rev210 來源取自 rev211 kit 的 baseline，並獨立核對精確 rev210 hash：

- ClientFeedbackProgram.java：`03b0a81ed656d58e701942cc726b25beee9b42b18aea04fe7d0b47ade0708dd9`
- ClientFeedbackCompiler.java：`3d12bb395e03dd70a938a39e7e34145992f19e317dc9d9c3f788a5cbafbd9b85`

兩份来源直接以真實 rev209 產品與真實依賴為 classpath 編譯，沒有使用假的 ConversionContext。
新增 Integrate210 將精確 11 個產品 class 覆蓋至已驗證 rev209，並更新 live BuildInfo、日誌、FML trace 與 Fabric metadata 的版本／指紋。

三次獨立本地編譯組裝的 rev210 主檔全部相同；後兩次實際使用交付重建包的 rebuild.py。
全部 1,622 個 rev209 entries 保留：1,616 相同、6 修改、0 刪除；另新增一份建構紀錄。
六項修改為兩個功能 class、BuildInfo、LegacyFileLogger、FmlConnectionTrace、fabric.mod.json。
既有原生回呼、Nock closure、Dagger、renderer、GUI、跳躍等其餘 class bytes 與 rev209 相同。

這是完整主 JAR 的 javac/ASM 增量建置，不是 Gradle/Loom clean build。
依賴 class 原樣擷取自既有 ViaVersion/ViaProxy artifact 10832672769，逐 class 比對相同，不是本次 Maven clean resolve。
編譯宣告、測試 hosts 與依賴均未包入主 JAR。

## 本輪真正執行的驗證

對完整 rev210 成品執行：

- 數值 helper 21,902 項通過；同測試在精確 rev209 重現 14,536 項預期失敗。172 次合成來源方法 verifier 檢查。
- 原始使用者 iYAMATO 的來源／全類別改名／除數變異測試 501 項通過，觀察到 19 個動作投影；不是 19 個完整技能。
- FeedbackSafety 10 項、NockClosureSafety 20 項、live 版本／指紋／rev211 排除 7 項通過。
- 全橋接器 1,599 class、14,354 methods（含 abstract/native 定義）掃描，ASM BasicVerifier／已知內部引用錯誤零。
- 13,229 項 API 保留檢查通過，無 public/protected 移除或不相容；相對 rev209 無新增 Minecraft/Fabric 成員引用。
- 205 個依賴外部繼承的成員未以真實遊戲 API 驗證；結構驗證不等於遊戲 ABI、Mixin 或連服驗收。
- 錯誤基底與缺漏 overlay 在產出前拒絕；ZIP CRC／重複 entry 檢查通過。
- 最終來源包重新建構並重跑上述測試通過；三次主檔 hash 相同。

原始 iYAMATO SHA-256：
`35a79ca5a034fd6cda4fe4ee1f658c4ce058f9b7ec026516e76363ebdf89635e`。
原始模組與主檔輸入 hash 均未被修改。

## 未通過／未執行的驗證界線

正式 LegacyConversionEngine 對原始 iYAMATO 的完整轉換嘗試，rev209 和 rev210 都在配方階段因環境缺少 `com/mojang/serialization/Codec` 中止；沒有跑完所有 pass。
測試用 DataFixer 是明確拋錯的邊界，沒有假造成功資料；沒有交付測試候選作為完成的轉換模組。
以上不宣稱已完成原始模組完整轉換，也不是產品在真實遊戲必然失敗的證明。

未跑原始 Bamboo/RPGTool、舊 3,886 項全產品回歸、完整原生 held-use recording-host 套件、Minecraft/Fabric/Mixin 啟動渲染、真實 Via 或連原服驗收。
完整裝填／連射 tick、特殊彈體、其他能力、任意 GUI 仍未完成。

## 附件與後續重建

- `LegacyForgeBridge-rev210-build-kit.zip`：348,580 bytes，SHA-256 `4a1846cb74d5cde5fa26e2b8e47721d1538e5b2ea1b3b6c7bfb00cfdb5712bec`。
- `LegacyForgeBridge-rev210-build-validation.json`：8,035 bytes，SHA-256 `79c66c6014a9f28bda5021e7e22e3ee5bfe9ab79d875da88e60bac73dc9735ab`。

重建包包含 50 份帶 hash 的 Java 建構／產品／測試來源、rebuild.py、verify.py、Integrate209/210、MainJarAudit、原始測試記錄與依賴來源紀錄。
不含主檔基底、原始模組、依賴或遊戲 JAR。從精確 rev208 重建，先要求 rev209 SHA 完全匹配才允許整合 rev210；詳細指令見包內 README.zh-TW.md。

主 JAR 安裝：關閉遊戲，備份原設定與轉換結果，取代舊 LegacyForgeBridge 主檔且只留一版，保留依賴和 old-mods；按新版指紋重新轉換，提示時重啟。ZIP 不放入 mods。

不修改原模組／伺服器／main／Bamboo／workflow，不觸發 Actions、建立 PR、tag 或 release；提交包含 `[skip ci] [skip actions]`。
