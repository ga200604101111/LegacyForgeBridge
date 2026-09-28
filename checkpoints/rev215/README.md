# rev215 — 精簡視窗、初始化早退、Prism GUI 交接

僅附加 `feature/generic-conversion-iyamato-corpus3`，父提交 `e97b26514a64eba5f849f99b23bb033f0e38335b`。不覆蓋既有 checkpoint、main、Bamboo、原始模組或伺服器；不修改 workflow，不觸發 Actions、PR、tag、release 或 force-push。

## 完整主檔

`legacyforgebridge-0.2.0-alpha.27-rev215-local-test.1.jar`，4,226,925 bytes。
SHA-256：`52b3471a623079f9c1d2d01d1f7d8a970fe2a1b96b4054e241a3dba88d4b5234`。

對話交付來源包 `LegacyForgeBridge-rev215-build-kit.zip`，123,445 bytes，SHA-256 `24241331db27fe2fa18fa89d17ebf2468a9cc37b502f302b5e0735429af6f3f6`。
驗證 `LegacyForgeBridge-rev215-build-validation.json` SHA-256：`3a8ab5df6448e7fee67bb45edbf2284287262a1eaceb5e8293ba605937a357b9`。

這是完整本地測試版，不是補丁，也不是 Windows／真實 Prism／Minecraft 驗收。

## 修改

1. 主視窗只顯示狀態、模組 X / Y、轉換已耗時間。細節／未載入計數／警告／診斷預設收合；底部只留開啟診斷與取消自動退出／重啟。系統標題列 X 仍可關閉並取消自動動作；轉換結束後凍結轉換耗時。
2. 管理器正常返回、轉換結果與快取／換檔計畫已保存、助手顯示 ACK、取消與循環防護通過，且確認仍在 MinecraftClient 建構子、目前是初始化執行緒、無世界／玩家、尚未載入完成時，使用 Runtime.exit(0) 提早退出。不等完整資源載入或5秒倒數，最多等助手就緒10秒。會執行 JVM shutdown hooks，但不是完整 Minecraft dispose；不使用 halt/taskkill。無法確認早退位置時保留 Executor/tick 的 scheduleStop 備援。逾時排隊工作撤銷，不能稍後退出或覆蓋 TIMED_OUT；退出失敗撤銷核准。
3. 等原 PID/start 退出且真正 ManagedSwapHelper 換檔結果通過 SHA／移除核對後，先呼叫相同 Prism 執行檔／資料根目錄的 `--show 實例ID`，再一次 `--launch 實例ID`。保留原擁有者辨識、取消、binary／artifact／session 再驗、一次性 claim 與防循環。原 Prism 與新的 activation 都仍活著而無法釐清時，轉手動，不盲目另開遊戲。只讀 executable/PID/start，不讀取或重播遊戲 arguments、登入 token 或帳號檔；子行程環境仍為白名單。
4. 只有客端主執行緒回報 isFinishedLoading=true 且 getOverlay()==null 才寫 game-ready.properties。助手核對 session、遊戲目錄、PID/start 與存活後關閉；新 PID／協調 session 單獨出現不等於載入完成。重啟前後助手都能在新遊戲 ready 後關閉。警告／PARTIAL 診斷保留檔案，不改成全部相容。

核對 Prism 官方9.4 Application.cpp：新主行程的 --launch 分支在一般主視窗建立前返回，既有主行程經 LocalPeer 接收請求。這是可能相關的來源路徑，不是使用者「原 Prism 沒接到 log」根因的證明。activation 等待不是 IPC ACK，CLI exit=0 不證明監控已接上。launcher-control.properties 保留 ownerMonitoringVerified=false；本次沒有真實 Windows／Prism 日誌接收驗證，不修改其帳號或主控台偏好。

## 安裝與設定

關閉遊戲並備份後只替換 LegacyForgeBridge，mods 留一版。保留 Energy、依賴、old-mods、設定和快取；轉換語意相容版本仍為rev210，不為介面改動全部重轉，rev211 usingTick 未啟用。

`config/legacyforgebridge-desktop.properties` 新增 earlyRestart=true；舊設定缺此項也預設true，false回正常主執行緒退出。showWindow、autoClose、autoRestartPrism 和已填 Prism 路徑保留。重啟紀錄在 legacy-cache/desktop/sessions/<id>/，新增 launcher-control.properties 與 game-ready.properties。全域轉換診斷／耗時檔名仍沿用rev213。

## 實際驗證

新桌面／啟動相關194項：Startup215Test27、保留Executor回歸121、LauncherHandoff215Test35、實際Swing11，全部通過。另8個分離行程流程通過：真正Runtime.exit、真正換檔助手、一次GUI activation再一次launch、新session不關窗而native-ready關閉前後助手。Minecraft是記錄型host、Prism是C參數記錄器；流程以fixture準備已保存狀態，沒有跑完整管理器initialize。

成品重跑數值21,902、安全10、Nock20、原始iYAMATO來源／改名／變異501、Outcome56、FailureBoundary16、DesktopSafety41。同FailureBoundary在local214基底16項也通過，不稱新的缺陷。主檔1,610class／14,492methods，ASM／已知內部引用錯誤0；13,324 API保留檢查無移除／不相容。206外部繼承成員未以真遊戲驗證；新增兩個公開MinecraftClient方法按Yarn1.21.11+build.1精確核對。

基底1,637項全保留：1,626不變、11修改、0刪除；成品1,639項。Energy與未改動的玩法class bytes相同。兩次独立最終建置、原capsule還原建置，以及文件勘誤後第四次建置都得到同一主檔SHA。38份可執行輸入保持原hash；45檔還原匹配，7種無效解包在產出前拒絕。

JDK21.0.11、javac --release21＋精確基底ASM完整主檔增量建置，不是Gradle/Loom clean build。2,439個真實依賴class原樣來自既有ViaProxy artifact10832672769，重新逐class核對，非本次Maven clean resolve；編譯宣告、測試host和依賴未包入產品。實際Swing在Linux/Xvfb，截圖為合成狀態。

未跑Windows／真實Prism／Minecraft／Fabric／Mixin／Via／原服、原始模組完整引擎或DataFixer、Bamboo/RPGTool/Twilight完整corpus、舊3,886套件；未量測使用者端節省時間。已記錄並修正晚到Executor覆蓋逾時診斷；前景驗證逾時未算通過，最終整套已完成。

## 基底與文件來源勘誤

實際二進位基底是先前對話交付local214：`ce6e6c82402d8d2359422d2f3e973023a82f720f44353dd57d701b118e107ccc`。最終重讀GitHub，該分支另存rev214主檔SHA為`61bb7ae55df6c6c5821a37760acfef7a55f7256c39585ddad1b77e03d052197c`。保留此checkpoint，不聲稱兩者二進位相同或完整合併其另一套DesktopRestartPump。

已上傳來源capsule中的两份文件曾記入未確認的另一版雜湊f55841…；metadata-correction.json依最後查核作文件勘誤，含精確前後hash。unpack在驗證完整capsule後、任何輸出前，僅修正README及validation/summary.json，不改可執行輸入。交付ZIP和獨立validation已含更正。

## 還原

```sh
python checkpoints/rev215/unpack.py --output ../lfb-rev215-build-kit
```

還原45份來源／建置／測試／文件／摘要，含38份hash-pinned可執行輸入。完整對話來源ZIP另有全部raw logs及全主檔audit；此capsule只含摘要。按README.zh-TW.md，用精確local214基底、JDK21與真實依賴執行rebuild.py／verify.py。本checkpoint不含主JAR、基底、依賴或原始模組。

XZ SHA-256：`c3aee0117c66c35a7a3053ff8624e94f76261b5022205e9d61d42985ab04c2a2`。

官方來源：
https://github.com/PrismLauncher/PrismLauncher/blob/9.4/launcher/Application.cpp
https://maven.fabricmc.net/docs/yarn-1.21.11+build.1/net/minecraft/client/MinecraftClient.html

提交包含 [skip ci] [skip actions]。
