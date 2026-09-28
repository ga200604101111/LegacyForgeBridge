# rev213 — 轉換未完成提示、錯誤收束與診斷打包

以精確 rev212 完整主 JAR 增量建置。本版修正能重現的結果誤報與診斷缺口；**尚未確認使用者實際「沒轉換好」的根因，也不宣稱補齊原模組的未支援功能。**

## 安裝與排查

只用完整 `legacyforgebridge-0.2.0-alpha.27-rev213-local-test.1.jar` 取代旧 LegacyForgeBridge，mods 只留一版。保留依賴、Energy、old-mods、設定與快取，不需要為 UI 更新清除全部轉換結果。啟動一次後，請提供：

`legacy-cache/reports/conversion-diagnostics-rev213.zip`

該 ZIP 只包含程式產生的最終診斷 JSON、文字摘要及耗時 JSON，不收集啟動器帳號檔、完整遊戲命令列或原模組 JAR。診斷可能包含本機檔案路徑或原模組錯誤訊息，分享前可自行檢視。另有同名 .txt/.json 與 conversion-timings-rev213.json。沒有匹配的最終 manifest 時會明確標示 UNAVAILABLE，不以掃描或耗時猜失敗原因。

## 已修正

1. 已在精確 rev212 重現：狀態 PARTIAL、loaderSafe=false、沒有安裝產物時，finish() 仍產生 DONE，原助手在 1.8 秒後自動隱藏。rev213 對不可載入／未安裝／載入未確認等結果保留 ERROR 視窗；PARTIAL 且管理器回報已載入時改為保留 WARN。正常完成才自動關窗。這不會更改或強制放行安裝條件。
2. pass-level NoClassDefFoundError／NoSuchMethodError 等 LinkageError 改經 IOException 進入既有引擎 FAILED 分支，保留 pass、錯誤型別／符號與失敗 manifest，不把不存在的相容性當成成功。測試中透過真正 LegacyConversionEngine 的合成缺類步驟驗證。OOM／StackOverflow 等 VM 錯誤仍向外傳遞；未宣稱所有 metadata／pass 外錯誤都可收束。
3. 從真正引擎的 ConversionResult 擷取結果，不用步驟數推算成功。快取路徑以來源 SHA、轉換語意 revision、狀態與 Fabric ID 核對 sidecar，顯示 status、installable、loaderSafe、loadedThisLaunch 及具體 diagnostic。installable、loaderSafe 與實際功能完整度是不同欄位，不混為一談。詳細診斷超出上限會標示截斷，錯誤／UNSUPPORTED 優先保留。
4. 獨立助手新增可捲動結果及「開啟轉換診斷」按鈕；錯誤／警告保留置頂提示，實際置頂仍受平台控制。自動退出／Prism 無憑證重啟安全限制維持；不能用重啟解決未支援功能，也不重播登入命令。

## 未動的部分

Energy 內嵌 JAR 與必要依賴宣告完全不變，沒有證據顯示它造成這次故障。保留 rev212 的打包／緩衝加速與 rev210 語意快取 revision；本次不宣稱新增轉換加速。沒有啟用 rev211 usingTick、修改傷害／耗彈／原模組／伺服器，沒有去掉轉換安全檢查。根目錄 src 不當作最新建構來源，全部以指定完整基底整合。

## 建置與測試

JDK 21.0.11；產品直接以精確 rev212 和真實 ASM/Gson classpath 編譯，不用假的 ConversionContext 或 Minecraft 宣告。來源包含明確的 synthetic/recording 測試 host，但不包進產品。真實依賴 class 取自對話已存在的 ViaProxy artifact 10832672769，詳見 validation/dependency-provenance.json，非新 Maven clean resolve。

兩次獨立 javac/ASM 組裝得到相同完整主 JAR。精確基底的 1,633 個項目全保留：1,624 不變、9 修改、0 移除；新增 2 個報告 class 與建構紀錄，最終 1,636 項。1,609 class／14,467 方法掃描無 ASM／已知內部引用錯誤；13,295 項 API 保留檢查通過，無新增 Minecraft/Fabric 成員引用。206 個外部繼承成員未以真正遊戲驗證。

候選通過：ConversionOutcomeTest 56、ConversionFailureBoundaryTest 16、DesktopSafetyTest 41、OutcomeWindowTest 10、SwingWindowTest 12；既有數值 21,902、FeedbackSafety 10、NockClosureSafety 20、原始 iYAMATO 來源對照／改名／變異 501。基底失敗邊界測試 5 項確認原缺陷。9 個主流程與 3 個退出後邊界通過；後者含最後取消、啟動器修改與新工作階段握手。改名叫 crash 的舊測試只是父行程提前正常返回，不是致命 JVM 崩潰測試。

測試環境是 Linux/Xvfb、記錄型 Minecraft host／C Prism 參數記錄器與真正既有換檔助手，**不是 Windows／真實 Prism／Minecraft／Fabric／Mixin／Via／原服驗收**。引擎缺類測試是真的引擎＋合成步驟，不代表已跑完原始模組所有原生 pass。沒有跑原始 Bamboo/RPGTool/Twilight 或旧3,886全產品回歸。完整核對紀錄見 validation/summary.json。

工具環境把整合驗證命令限制為約 30 秒，兩次合併執行在後段測試中被工具中止，之後已分開完成 core、9流程、3後段流程及兩套 Swing。沒有將被中止的命令記成全程通過。舊版 done 測試把 UPDATE_STAGED 與 restartRequired=false 混用，這個不一致狀態在新版應報錯；正常 LOADED/DONE 由新結果測試與實際 Swing 測試覆蓋。

### 重建

需 Python 3.11+、JDK21 與 ASM core/tree/analysis/commons、Gson 真實 JAR；測試還需 Guava/SLF4J。來源 ZIP 不含完整基底、原模組、依賴或遊戲檔。

```sh
python rebuild.py --base /path/rev212-main.jar --classpath /path/real-dependencies.jar --work ../rev213-build
python verify.py --build ../rev213-build --base /path/rev212-main.jar --classpath /path/real-dependencies.jar --iyamato /path/iYAMATOs-Mod-1.7.10.jar --work ../rev213-verify
```

Linux 有 X11/Xvfb、cc 及 Python psutil 時可加 `--process-flows` 跑記錄型流程與 Swing；沒有加時會明確記錄略過。多 JAR classpath 用 Windows `;` 或 Linux `:`。work 必須是 kit 外不存在的新目錄。

SHA-256 精確基底：`2e18fdb7c1ec4d97e83c892ac2e13ebba5e5f7c9428b13fd94df8e4f1b9fd4e9`。
最終主檔：`9ecf5ed9c89ac9d6f2ea6105efb4a60b5e9c830a1b07de0b6ea341c2f6bd4dcd`，4,213,283 bytes。

GitHub 只接續 feature/generic-conversion-iyamato-corpus3；保留所有舊 checkpoint，不 force-push，不修改 main/Bamboo、workflow，不 dispatch Actions，不建 PR/tag/release。提交使用 [skip ci] [skip actions]。

## GitHub 還原

`python checkpoints/rev213/unpack.py --output ../lfb-rev213-build-kit`

來源 ZIP SHA-256：`244f485313d1fa2b7500f0a57078a52055eae8ee6cb0c9ebda0ad33aad5ea045`。checkpoint 還原只是來源／建置／測試材料，不是主 JAR。
