# rev212 — 完整本地主檔：獨立進度視窗與不重播憑證的 Prism 重啟

接續 `5b0016d3090173e82fc56e9e3319abffd0f8942f`，僅更新 `feature/generic-conversion-iyamato-corpus3`。以精確 rev210 完整主 JAR 增量建置；保留 rev211 checkpoint，但本次主檔不包含 rev211 的 usingTick 擴充。

## 交付

- 主檔：`legacyforgebridge-0.2.0-alpha.27-rev212-local-test.1.jar`，4,198,373 bytes。
- 主檔 SHA-256：`2e18fdb7c1ec4d97e83c892ac2e13ebba5e5f7c9428b13fd94df8e4f1b9fd4e9`。
- 精確 rev210 基底：`445ef6b3a514492ccd74bb96b287c3aba3d9a9a1469030b9970edc49b94a0b32`。
- 來源建置包：`LegacyForgeBridge-rev212-build-kit.zip`，85,034 bytes，SHA-256 `19d175443bc1122dfac146b6a4ea11ba1b360d195721162db6cd492b2efa941c`。
- 驗證：`LegacyForgeBridge-rev212-build-validation.json`，SHA-256 `d5a9e6dd6d8ed61290cf06084c61ce1ff35e00e5261f8ce51fbcbf64026fe62c`。

完整主 JAR、來源 ZIP、驗證及視窗實際測試截圖均從對話附件交付。本 checkpoint 只存可核對的來源／建置／測試材料，不含主檔、基底、依賴或原始模組。這是本地測試版，不是 Windows／真實 Prism／Minecraft 連服驗收版本。

## 實際接線與流程

新增獨立 JDK/Swing 助手，從主 JAR 內嵌資源準備；不需另裝補丁。正式 LegacyConversionManager 啟動點、progress、引擎 pass／額外 Nock 和 feedback 階段、打包及既有客端 end-tick 已用帶精確錨點檢查的 ASM 接線。顯示模組數、來源、實際階段、工作進度與耗時；百分比不是剩餘時間預測。

管理器結束且檔案狀態合格後，確認助手視窗已就緒、尚未進入世界、沒有取消或循環，再倒數 5 秒呼叫 MinecraftClient.scheduleStop()；不 System.exit、不強殺 JVM。保留既有 ManagedSwapHelper，等父 PID／啟動時間對應的行程退出並核對所有預期 SHA／移除結果，才進入重啟。換檔失敗、未知結果或未核准退出不能觸發啟動器。

Prism 呼叫只包含 `[絕對執行檔, --dir, 資料根目錄, --launch, 目前實例資料夾名稱]`。讀取父行程的 executable path，不讀 arguments/commandLine；不讀帳號資料或 token，不重播原 Minecraft 指令。子行程環境使用白名單，不沿用 JAVA_TOOL_OPTIONS 等 JVM 注入變數。路徑必須對應目前 instance.cfg、mmc-pack.json 及可辨識的 Prism 資料根目錄；啟動前重新驗證執行檔 SHA 與實例。此 SHA 只檢查連續性，不是發行者簽章。

換檔完成後另有 3 秒可取消倒數，只允許一次 Prism 呼叫。同一更新計畫有持久循環防護，短期不同計畫也限制重啟；正常無待重啟的啟動可清除防護。新遊戲回報同目錄的新協調工作階段才標示已重新啟動，不把 ProcessBuilder.start 成功當成遊戲成功。找不到唯一 Prism 路徑、啟動失敗、未取得新工作階段等情況，留下置頂手動提示。置頂效果仍受平台限制。

目前只支援直接的 Prism 執行檔；Flatpak/AppImage 包裝、其他啟動器及無法解析的資料目錄改為手動。視窗關閉只取消自動動作，不中止轉換。PARTIAL 保留提示，不宣稱所有模組功能已完成。

## 設定

首次執行建立 `config/legacyforgebridge-desktop.properties`：

```properties
showWindow=true
autoClose=true
autoRestartPrism=true
prism.executable=
prism.dataDirectory=
```

留白先嘗試唯一且與當前實例匹配的目標。無法辨識時，可填入實際 Prism 路徑及包含 prismlauncher.cfg 的資料根目錄；Windows 使用 `/` 分隔，例如 `D:/PrismLauncher/prismlauncher.exe`。不是填 .minecraft，也不接受任意重啟腳本或遊戲參數。原始模組與伺服器不變。

## 加速與快取

候選 JAR 壓縮改為 level 1，讀寫與雜湊緩衝 128 KiB。沒有跳過來源驗證、轉換 pass 或事件安全審查，也沒有無界平行轉換。加入逐階段耗時紀錄：`legacy-cache/reports/conversion-timings-rev212.json`。

因這次只改桌面協調與壓縮表示，CACHE_COMPATIBILITY_VERSION 明確保留 rev210，CONVERTER_REVISION 仍為 `2026-09-28.210-source-feedback-numeric-helpers`；實際版本與 Fabric metadata 是 rev212。既有 SHA 與輸出完整性檢查仍保留，不因 UI 更新強制重轉全部 rev210 快取。未來轉換語意改變必須另改快取相容版本／指紋。

## 真正執行的驗證

- 對完整成品重跑原數值 21,902、FeedbackSafety 10、NockClosureSafety 20、原始 iYAMATO 來源／改名／變異 501 項斷言，全部通過；不是完整模組轉換或技能支援數量。
- 新 DesktopSafety 41、真實 Swing 視窗 12 項通過。Swing 在 Linux/Xvfb 執行，含可見性、繁中內容、進度、實際置頂屬性、取消按鈕與關窗；截圖使用合成狀態，不是遊戲畫面。
- 10 個主流程加 3 個退出後邊界通過：正常換檔／啟動、取消、進入世界、失敗、錯誤雜湊、循環、無啟動器、視窗未就緒、無須重啟、未核准的父行程提前結束，以及最後倒數取消、Prism 檔案改變、新工作階段回報。真正 ManagedCandidateInstaller 和 ManagedSwapHelper 處理測試檔案；Minecraft 是記錄型測試替身，Prism 是 C 參數記錄器。未核准父行程案例正常 return，不是假稱測過 JVM 致命崩潰。
- 新舊真實 LegacyJarWriter 分別打包原始 iYAMATO 的 420 個檔案，5 輪暖機、9 輪交錯測量：中位數 47.229370 → 37.820391 ms；只代表打包耗時減少約 19.9%，不是整體轉換加速。輸出 1,024,512 → 1,054,642 bytes；解壓內容一致，11,786 項斷言通過。
- 全主檔 ASM：1,607 class／14,437 methods，BasicVerifier／已知內部引用錯誤 0；13,229 項 API 保留檢查通過。206 個外部繼承成員尚未用真實遊戲驗證。唯一新增原生方法引用 `net/minecraft/class_310.method_1592()V` 是 scheduleStop，依 Yarn 1.21.11+build.1 核對。
- 所有 1,623 個 rev210 項目保留：1,611 相同、12 修改、0 移除；候選共 1,633 項。215 個選定既有 runtime class 逐位元組相同；編譯宣告／測試 hosts／依賴不包入產品。
- 兩次獨立最終編譯組裝相同；59 檔 checkpoint 還原逐位元組一致，還原後第三次完整建置也得到同一主檔 SHA。錯基底／缺 overlay、五種無效解包案例均在產出前拒絕。

JDK 21.0.11，javac --release 21 + hash-pinned ASM 完整主檔增量組裝，不是 Gradle/Loom clean build。真實 ASM/Gson/Guava/SLF4J 的 2,355 個 class 原樣取自先前 ViaProxy artifact 10832672769；不是這次 Maven clean resolve，沒有新 Actions。完整來源 hash、依賴來源、原始紀錄及修正過的測試環境問題見解包後 validation/。

本輪沒有在真實環境執行完整 LegacyConversionManager.initialize、所有轉換引擎 pass、DataFixer、Windows、Prism、Minecraft/Fabric/Mixin/Via 或連原服；沒有重跑 Bamboo/RPGTool 或舊 3,886 全產品回歸。原有特殊投射物、裝填／連射 tick、任意 GUI 等缺口仍未完成。

## 還原

```sh
python checkpoints/rev212/unpack.py --output ../lfb-rev212-build-kit
```

解包後依 README.zh-TW.md 使用精確 rev210 主檔、JDK21 與真實依賴執行 rebuild.py／verify.py。來源膠囊 XZ SHA-256：`a0e930e441921a9aab7608df7ebc35a1a04a93bd1cc05221dc5e5cbabdb0595f`。主檔安裝只替換舊 LegacyForgeBridge，mods 留一版；保留依賴、設定、old-mods 與既有快取，不把 ZIP 放進 mods。

官方依據：Prism CLI https://prismlauncher.org/wiki/getting-started/command-line-interface/ ；Yarn https://maven.fabricmc.net/docs/yarn-1.21.11+build.1/net/minecraft/client/MinecraftClient.html 。

不修改原模組／伺服器／main／Bamboo／workflow，不 dispatch Actions，不開 PR、tag、release 或 force-push；提交包含 `[skip ci] [skip actions]`。
