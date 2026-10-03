# rev247 — UI、快取識別與落地後速度診斷

分支：`feature/generic-conversion-iyamato-corpus3`。
基準提交：`e34f9ed26478212beda239751c77b69338d9ef94`。
日期：2026-10-03。

## 已交付完整主 JAR

`legacyforgebridge-0.2.0-alpha.27-rev247-corpus4-local.30-ui-cache-diagnostic.jar`

4,553,459 bytes；SHA-256 `db7627f77ddac1d84f2eff300f6397c9304971b56f0578d507d4a573944ae88a`。

精確基底為 rev246 主 JAR，SHA-256 `6fc64f88351db277ef438b8eb226d313baa5410bf5a8e1a007c65b85bed414c4`。二進位交付於對話，不包含在此原始碼檢查點。

**不是跳躍根因修復，也沒有聲稱重現使用者那次 rev246 轉換例外。**

## 修改

1. 在真正的內嵌 DesktopHelper 上重現舊子視窗橫向捲動：內容寬 2095、可視寬 683。改用追隨 viewport 寬度的 Scrollable 欄位、依實際寬度換行與計算段落高度，取消固定卡片高度，只能上下捲動。新增關閉／Escape，重複點擊重用既有子視窗。閱讀子視窗時延後既有自動隱藏計時器。主視窗標題保留指定格式，CONVERTING 顯示「轉換中」。
2. 新增明確點擊才執行的「複製診斷摘要」，只複製 allowlist 中的狀態、pass、message、摘要等欄位，不上傳資料、不複製登入 token。摘要不是完整例外堆疊。ERROR/WARN 等真實狀態不改成成功。
3. rev245 與 rev246 的 conversion classes 經二進位比對完全一致，但 UI 版改了 CONVERTER_REVISION，造成不必要快取失效。rev247 的 artifact version 保持 rev247，轉換語義識別保留 rev245；只將精確 rev246 的同 cache version／schema 2／有效 source SHA 指紋視為同一語義版本。快取 sidecar 也只接受這個精確別名。未知版本、不同 schema/source hash、FAILED、缺失或雜湊錯誤的產物仍由原檢查拒絕，不做廣泛繞過或強制清除快取。
4. 保留原 rev245 關聯核心，在 trace decorator 增補同 localJump 下、落地後收到第二筆正 Y 的觀測分類，標為 `postGroundPositiveCandidate=true`，並記錄落地 Y 與時間差。兩秒是診斷標籤範圍，不是限高或移動規則；沒有改遊戲速度、封包、原模組或伺服器。
5. RPGTool 的描述改為歷史實測基線與目前跳躍問題仍在回歸測試，避免把未解問題說成全功能驗證。Bamboo、iYAMATO 保留部分相容／持續驗證；清单不是 production 模組名稱白名單。

## 證據與未解問題

使用者澄清三格半的半磚是刻意測試障礙。因此異常集中該高度，不能獨立證明半磚碰撞錯誤；五格上不去也不能直接推出 1.7.10 有五格反作弊限高。沒有伺服器端呼叫紀錄或原生 1.7.10 配對結果。

目前提供的 logs.zip 仍是 rev245 啟動，不是 rev246 報錯。未取得該次例外或原模組 corpus，沒有執行原模組集合的完整轉換；使用者報告的轉換失敗根因未確認。這版修正的是可獨立證明的無謂快取失效與 UI 缺陷，沒有偽造轉換成功。

既有使用者日誌前兩段 CRC 正常資料的回放：89 筆套用、9 段同 localJump 第二筆正速度，其中補標 5 次落地後案例。不使用損壞的第三段，也不是新的遊戲實測。

## 實際驗證

- JDK 21 編譯、受限制 ASM 方法修改、12 個 Java 21 類別解析。
- 外層成品 JAR：真實 DesktopHelper/Swing，145 檢查。
- 內嵌 helper：145 檢查；2 倍 UI 縮放再 145 檢查。
- 子視窗寬度 480／650／720／1000，驗證無水平捲動、文字末端未裁切、重複開啟、錯誤標題、摘要排除 secret key。
- 真實成品 cache／Snapshot 方法：25 檢查；不是整個 conversion manager 的端到端執行。
- 成品診斷回歸：20 情境／643 斷言；Minecraft/Fabric/source event/writer 採明確 test doubles，未包進主 JAR。
- 新觀測分類與既有日誌回放：11 檢查。
- ZIP 完整、1708 個原有外層項目內容不變；第二次獨立建構逐位元一致。原移動核心、來源行為、落地／溫泉 adapter、Mixin 保留。

沒有 Minecraft／live Via／原生 Forge 客戶端／伺服器驗證；Xvfb 是真實 AWT/Swing 顯示測試，不是 Windows 實機。未重做磁碟 writer 壓力／輪替測試，原 writer 保持基底內容。無 Gradle/Loom、依賴下載、Actions dispatch、PR、tag/release、workflow 或 main/Bamboo 變更。

## 重現

```sh
python3 checkpoints/rev247/build_local.py /path/to/exact-rev246.jar /path/to/rev247.jar
python3 checkpoints/rev247/test_local.py /path/to/rev247.jar /path/to/new-test-report-dir
# 可選：回放同一份使用者 logs.zip（不隨 repository 提供）
python3 checkpoints/rev247/test_local.py /path/to/rev247.jar /path/to/another-report-dir /path/to/logs.zip
```

需要本機 JDK 21、Python 3.10+、Xvfb。建構器拒絕錯誤基底與現有輸出，不改歷史 checkpoint。對話另提供含完整結果 JSON 的原始碼測試 ZIP：SHA-256 `ad4981cf67e2a86ead996b79c1757e122f063b7008caa05aaed4df1090b54872`。

下一步先驗啟動與排版，不必重跳數十次。若仍轉換錯誤，保留該次 logs/legacyforgebridge.log、轉換診斷報告與新摘要。跳躍捕捉仍自動、唯讀；後續因果區分仍需原生 Forge 對照或另行授權的伺服器端證據。
