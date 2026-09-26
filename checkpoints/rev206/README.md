# rev206 — 投射物通用性審查與排除原因修正

**僅來源／診斷更新，沒有 rev206 安裝 JAR，也沒有新增可用投射物規則。** 最新交付主檔仍為 rev205；不能把這份審查當成飛刀、弩箭已經修好。

已確認原始樣本有 54 筆實體註冊，但 rev205 上游因設定／計數器等未解析參數而全部拒絕；投射物分析器又漏掉這些排除，最後錯誤地呈現 0 規則、0 排除、0 診斷。這次改為保留 24 個基底／介面候選的排除及 1 個上游總結，既有產出 pass 可留下 24 個警告；**准入仍是 0**。

完整說明見 [繁體中文審查](AUDIT.zh-TW.md)、[數據](audit.json) 與 [可直接閱讀的來源差異](projectile-reporting.patch)。原始子彈／霰彈 sprite 確實全透明，不應一律換成可見箭；自訂飛刀／弩箭還需要正確的身分、狀態、渲染與客端生命週期。

## 取得來源與重現測試

兩個文字片段為 checksum-pinned XZ 來源包，包含 21 個來源／測試／報告檔案，不含 Minecraft、來源模組或相依 JAR。解包同時檢查整包與逐檔 SHA-256：

```sh
python checkpoints/rev206/unpack.py --output ../LegacyForgeBridge-projectile-audit
```

在解包目錄依 README 執行 `verify.py`，提供 JDK 21、ASM core/tree/analysis、Gson、完整 rev205 主檔及原始來源檔。測試不執行來源模組。已通過 17 個合成案例、86 個分析器斷言及 9 個實際 pass 斷言；JSON 另由 Python 讀回確認。`apply.py` 只寫入新檔並拒絕未知基準。

本地 ASM 是 Kotlin 隨附版本的套件重定位副本，另有 InputStream 建構式相容轉接；Gson classes 由既有 installer 原樣取出。installer 沒有被執行。來源、相依校驗值與限制均附於解包內容，不能說是正式 Maven 相依或完整遊戲建置。

沒有完成完整 Gradle/Loom、累積還原、Bamboo 原始樣本、Minecraft API/Mixin、遊戲渲染或連服驗證。跳躍與原始伺服器不改。先前 819 檔 rev202–205 來源包已補存並完成校驗，不代表本次重跑歷史建構。

只更新 `feature/generic-conversion-iyamato-corpus3`，不觸發 Actions、不改 workflows、不開 PR/tag/release、不推 main 或竹分支。提交使用 `[skip ci] [skip actions]`。
