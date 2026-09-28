# rev214 — 修正退出倒數只依賴 end-tick，增加主佇列排程及逾時提示

接續 `d12af775e76e6130bb158c39a297c2c5096afb2e`，僅 feature/generic-conversion-iyamato-corpus3。以精確 rev213 完整主檔增量建置，保留所有既有 checkpoint／根目錄來源。沒有啟用 rev211 usingTick，也沒有更改轉換語意、伺服器或原始模組。

## 交付及本目錄範圍

- 完整主檔 `legacyforgebridge-0.2.0-alpha.27-rev214-local-test.1.jar`：4,219,747 bytes；SHA-256 `61bb7ae55df6c6c5821a37760acfef7a55f7256c39585ddad1b77e03d052197c`。
- 精確 rev213 基底 SHA-256：`9ecf5ed9c89ac9d6f2ea6105efb4a60b5e9c830a1b07de0b6ea341c2f6bd4dcd`。
- 完整來源／重建／測試包 `LegacyForgeBridge-rev214-build-kit.zip`：126,609 bytes；SHA-256 `145ef4d41f5cb7f51d1c8185cdb4c096ae61793175ec3597a270b442e3c5b824`。
- 驗證紀錄 `LegacyForgeBridge-rev214-build-validation.json`：8,996 bytes；SHA-256 `bb64f9a4dcef160e86770c26939c54d729504872e83ac76a25c9c80474267f88`。

完整主 JAR、來源包及驗證由對話附件交付。本 Git 目錄保存六份產品來源的可審查差異、前後雜湊、精確主檔整合器及交付紀錄，**不是獨立完整 build kit，沒有 unpack.py 或內嵌完整測試膠囊**。完整 kit 包含 rebuild.py、verify.py、44 份 hash-pinned 建置輸入、原始測試紀錄及依賴抽取工具；不含主檔基底、原始模組、依賴、帳號資料或字型。

套用 source-changes.diff 前，依 changes.json 檢查全部 preimage：ConversionOutcomeReport、DesktopConversionSession、DesktopHelper 取自 rev213 kit；DesktopFiles、NativeDesktopTick 沿用 rev212；DesktopRestartPump 是新增檔。不是直接對舊 root src 套用。實際 git apply 後六份來源 hash 與成品建置來源一致。兩份透過 blob API 上傳的 diff／整合器也核對 Git blob SHA 與本地檔案一致。

## 現場證據與仍未知的部分

使用者提供的 desktop.zip 顯示一輪 COUNTDOWN 停在 3 秒，約 18 分鐘後才有取消紀錄；沒有 STOPPING、退出核准或啟動器呼叫標記。Prism 路徑與實例欄位存在。最新另一輪回報已載入三個 PARTIAL、待重啟零。

這定位了「沒有進入自動退出／呼叫啟動器」，不能證明某個 FPS 模組、死鎖、I/O 或外部操作是最初原因。沒有遊戲 thread dump，外部原因仍未知。未提交使用者原始 ZIP、私人路徑或原模組 JAR。

## 修改

1. DesktopRestartPump 在 client initializer 接入，每 250 ms 經 MinecraftClient 實作的 JDK Executor 提交控制工作，最多一筆未完成任務。保留原 end-tick 後備，不再只靠 end-tick 才有倒數。
2. 僅初始化捕獲的客端執行緒能讀 world/player 並呼叫 scheduleStop。倒數用 monotonic clock；持久循環防護仍用 epoch。背景线程不讀遊戲世界、不直接退出、不強殺、不重播啟動命令。
3. 首筆主佇列回應保留 120 秒初始化寬限；有回應後無回應 15 秒取消。獨立視窗另監測 READY 120 秒、COUNTDOWN 15 秒、STOPPING 45 秒未更新，留下置頂手動提示。晚到佇列任務不能復活已取消／逾時工作。
4. helper-state.properties 現在記錄同一狀態的文字變化；新增 pump.properties 的提交／完成／排程時間與失敗標記，方便定位卡點。
5. 子行程環境白名單增加 Windows SystemDrive／ProgramData，仍排除憑證與 JVM 注入參數。沒有將此環境補充宣稱為已確認的倒數根因。

既有退出核准、父 PID／啟動時間、換檔結果 SHA 核對、Prism 路徑／實例驗證、取消、一次性啟動與新工作階段確認維持。只有主佇列仍運作時，才可在 end-tick 停止後安全繼續退出；整個遊戲主執行緒卡死時不保證自動重啟，會取消自動動作並留下提示。

Energy 內嵌及依賴不變，rev210 語意快取仍相容，不為此 UI／協調更新強制重轉。PARTIAL 不改成 CONVERTED，已載入且無待重啟的正常啟動不會為測試而自動重啟。

## 已執行驗證

新流程使用相同記錄型客端與 C 啟動器記錄器：

| 案例 | 結果 |
| --- | --- |
| rev213 在 3 秒後停止 end-tick、主佇列仍工作 | 重現 COUNTDOWN；退出 0，啟動器 0 |
| rev214 同條件 | 退出 1，真正換檔助手完成後啟動器請求 1 |
| rev214 完全不發 end-tick | 主佇列仍能退出 1、啟動器請求 1 |
| 取消、進入世界、主佇列也停止 | 各自退出 0、啟動器 0；逾時後恢復佇列仍無過期動作 |

六個新流程（含一個舊版對照）、九個原流程及三個退出後邊界均分開完成。真正產品 coordinator／helper／ManagedSwapHelper 在隔離目錄運作；Minecraft 是 queued Executor host，Prism 是參數記錄器，不是真實遊戲。

核心通過 ConversionOutcome 56、FailureBoundary 16、RestartPump 31、DesktopSafety 41、數值 helper 21,902、FeedbackSafety 10、NockClosureSafety 20、原始 iYAMATO 來源／改名／變異 501 項。Linux/Xvfb 真正 Swing：新 RestartWindow 18、既有 SwingWindow 12、OutcomeWindow 10 項通過。

全主檔 1,610 classes／14,483 methods 通過 ASM BasicVerifier／已知內部引用檢查；13,319 API 保留檢查通過。全主檔相對基底沒有新增 Minecraft/Fabric 成員引用，外部繼承成員仍未以真實遊戲驗證。

兩次獨立 JDK 21.0.11 javac／ASM 主檔組裝 byte-identical；全部 1,636 基底 entries 保留，1,623 不變、13 改動、0 刪除，新增 pump class 與建置紀錄。錯基底、缺少來源在產出前拒絕；來源差異套回雜湊完全匹配。

測試 lambda 型別宣告錯誤已修正；首次 watchdog 斷言只允許 pump 先逾時，改為容許獨立助手先取消，仍要求零退出／零啟動、佇列最多一筆及晚到任務無效。一次合併 verify 命令被工具逾時中止，不記成全程成功；上述結果由分開完成的核心、流程與 Swing 紀錄支持。詳見交付 validation JSON。

## 建置方式與未驗證範圍

完整主檔是精確 rev213 基底＋javac 編譯六份產品來源＋Integrate214 的帶錨點 ASM 接線，不是 Gradle/Loom clean build。NativeDesktopTick 使用明確拋錯的 compile-only Minecraft 宣告編譯，未包入主檔；其餘產品用真實基底類別與依賴。2,386 個依賴 class bytes 原樣取自先前已存在的 ViaProxy artifact 10832672769，不是這次 Maven clean resolve，也不包進產品。完整 kit 內保存來源 ZIP/JAR/class 雜湊與抽取程式。

没有 Windows、真實 Prism、Minecraft/Fabric/Mixin/Via／原服驗收；沒有真實客端 Executor 調度時機驗證、原始模組全部 pass 轉換、Bamboo/RPGTool/Twilight corpus 或舊 3,886 全產品回歸。不是補齊實體、特殊投射物、裝填／連射或任意 GUI 的更新。

官方 API 依據：https://maven.fabricmc.net/docs/yarn-1.21.11+build.1/net/minecraft/client/MinecraftClient.html （Executor／getInstance／scheduleStop）；https://prismlauncher.org/wiki/getting-started/command-line-interface/ （--dir／--launch）。

安裝：關閉遊戲，mods 只留 rev214 主檔，保留 Energy、依賴、old-mods、設定和快取。ZIP 不放 mods。完整 kit 的 README.zh-TW.md 提供 rebuild.py／verify.py 指令。

不修改 main/Bamboo／原模組／伺服器／workflow，不 dispatch Actions、不開 PR/tag/release、不 force-push；提交含 [skip ci] [skip actions]。
