# rev207 — 累積主 JAR 本地建構與投射物來源報告接線

基底為使用者提供的精確 rev205 完整主檔，不是舊 rev185 改名。這次在本地編譯新增產品程式並整合成完整主 JAR，沒有執行 GitHub Actions。這不是整個專案的 Gradle/Loom clean build，也沒有 Minecraft/Fabric/Mixin 或連服驗收。

**沒有完成全部長按武器、投射物執行階段或任意 GUI。** 此版新增靜態 MAY-flow 和上游排除報告的正式 pass 接線，不放寬既有 runtime 准入。iYAMATO 仍為 0 個投射物呈現規則、24 個明確排除。

## 成品

- 主檔：`legacyforgebridge-0.2.0-alpha.27-rev207-local-test.1.jar`，4,022,055 bytes。
- SHA-256：`f6990b24564b13c599f05ef550dd864bdb6a4d121a2cbbd0e63a402b4e03cfa1`。
- 輸入 rev205 SHA-256：`3ce19e3a6d9b4788251aae95edd0c179c93bc44f4d5f65fba054be67ce4461de`。
- 指紋：`2026-09-27.207-projectile-source-coverage`。

成品由對話附件交付；此 Git checkpoint 保存來源、工具、測試與驗證紀錄，不包含主 JAR、原始模組、遊戲 class 或第三方依賴。

## 累積保留與驗證

rev205 的 1,558 個項目全數保留，1,553 個逐位元組相同、5 個修改、沒有刪除。修改僅為版本資訊及投射物呈現 pass；motion/menu/render/behavior 保留原 class bytes，network 只更新 trace 版本字串。新增 15 個產品 class 和 1 個建構紀錄。

兩個全新目錄獨立建構，完整 JAR SHA-256 一致。143 個新斷言通過；3,596 個既有斷言先在輸入 rev205，再在最終 rev207 重新通過。19 個新增／修改 class、167 方法和整個 bridge namespace 1,553 class、13,967 方法通過 ASM BasicVerifier；無內部參照錯誤或 public/protected 移除。錯誤基底與改動過的來源在輸出前遭拒。

新增來源依賴真正 ASM/Gson 類別，取自先前下載的 ViaProxy artifact，未重新觸發工作流程，非 Maven 清潔解析。舊版 native runtime 測試使用隔離 recording hosts，絕不包入成品。MANIFEST 中的 Gradle/Loom 資訊繼承自舊基底，不代表本次跑過該建構。

原始 iYAMATO 的 99 個註冊物品根產生 48 條可能生成關聯，涵蓋 24 個實體類別：PRESS 7、HELD_TICK 2、RELEASE 39、FINISH 0。這不是 48 種武器已支援。測試的前置 staging manifest 由當前 registry analyzer 建立，並非真實完整轉換引擎／網路端到端測試。

rev206 的上游排除語義在本次正式 pass 邊界接入；原 `LegacyProjectilePresentationAnalyzer` class 沒有改動。單獨直接呼叫舊分析器不會得到此新報告。

## 未完成與不變項目

ArrowNockEvent 跨模組安全審查不繞過；射擊、裝填、source client lifecycle、投射物身分／額外資料／渲染，以及任意 GUI 仍未補完。跳躍維持 OBSERVE_ONLY，上托未修復。沒有修改伺服器或原始模組；沒有本次原始 Bamboo/RPGTool 新回歸或實機驗收。

## 取出與重建

```sh
python checkpoints/rev207/unpack.py --output ../lfb-rev207-source
```

50 個來源／驗證檔案的壓縮 SHA-256 與逐檔 SHA-256 已通過本地往返驗證；四個 Git payload blob hash 也與本地一致。解包後閱讀 `README.zh-TW.md`，再以 JDK 21、精確 rev205 主檔及 ASM/Gson classpath 執行 `rebuild.py`。可选 iYAMATO 原始 JAR，舊版回歸另需 Guava 及先前來源工作包的 `retained-checkpoints/rev205`；未提供的測試不會被宣稱執行。

安裝時關閉遊戲，以新主檔取代舊主檔，不要同時放兩版。保留既有依賴與 old-mods；轉換後依提示重啟。舊的缺 NBT 物品不會自動補好。

只更新 `feature/generic-conversion-iyamato-corpus3`。根 src 仍不是最新完整累積樹；此 checkpoint 不覆寫它。提交包含 `[skip ci] [skip actions]`，不建立 PR/tag/release，不更動 workflow。
