# rev209 — 來源長按使用准入與客端動作

接續 `badbab10ca1349a18e84b07d5d990c7f8acd197f`，只更新 `feature/generic-conversion-iyamato-corpus3`。本次是實際長按起手／來源動作執行修正，不是只有診斷；**不是全部 iYAMATO 武器玩法、特殊彈體或裝填表現已完成**。

## 完整主 JAR

- `legacyforgebridge-0.2.0-alpha.27-rev209-local-test.1.jar`，4,140,190 bytes。
- SHA-256：`f9cce93d55b745a5de286351a70304ed051157caebd505b0dffdcb2607fb19d7`。
- 精確 rev208 基底：`81be9ccaabb178ea7a8be1050cdfe0a814c0f4f8e6489a533bc48e17eb058cf1`。
- 指紋：`2026-09-27.209-source-held-client-closure`。
- 本地 javac／帶基底 hash 檢查的 ASM 增量建構；兩次全新目錄獨立編譯組裝，完整主檔一致。不是 Gradle/Loom clean build，也沒有 Minecraft/Fabric/Mixin、真實 Via 或連服驗收。

主 JAR 從對話附件交付；此 checkpoint 不包含主 JAR、原始模組、遊戲 class 或依賴 JAR。

## 共同修正

`SourceNockRiskProof` 逐項分析來源反射／初始化／可選類別風險；`ClientNockClosurePass` 在正式轉換引擎最後驗證並雜湊實際生成的可執行範圍、metadata 與來源移除證據。保留原始 nockEventAudit。只有來源語義與輸出共同證明無適用客端監聽器才准入，不以移除 class 代替證明，也不以武器名稱或觀測 ID 分流。真正監聽器、未知初始化、活躍 core plugin、可選初始化類別存在、來源或輸出竄改仍拒絕。可選類別檢查原始來源、已載入 Fabric 模組及 JVM 資源，不執行類別初始化。

使用 runtime 保留來源起手／動作／時長／彈藥與 NBT 條件；同次 preflight 有嚴格 tick/thread/player/world/hand/stack/input 一次性匹配。開始使用返回 CONSUME，不額外要求自動揮手。

`ClientFeedbackCompiler`／有界數值 VM 將來源 release 與 entity-interaction 回呼投影為客端揮手，保留充能數值條件、子類虛擬 helper 覆寫與原呼叫順序。遇未知事件／世界操作明確 DEFER，不假設事件未取消、不執行伺服器傷害／爆炸／耗彈／耐久。實際接線至 ConvertedBehaviorItem 的 release 與 useOnEntity。

## 武器檢查

鐵／金／鑽石／大馬士革鋼四組 Warhammer、Halberd、Spear、Short Sword，共 16 件，逐項測起手、72000 時長、來源動作及鬆鍵。Warhammer 測 20/21、32/33 tick 的來源充能邊界；Halberd/Spear 保留來源在 ArrowLooseEvent 前的揮手，不猜測後續事件。Short Sword 空中使用為 NONE；右鍵點生物的原始方法本來就揮手並返回 false，保留動作與 PASS，不改送左鍵攻擊。

29 條來源 use 規則均測前置與 native use 回傳／來源初始 NBT；20 個繼承式格擋另測起手、BLOCK 及無自動揮手。19 條來源動作投影不等於 19 個完整技能。整個來源類別改名後結果一致；Warhammer 充能除數 20 改 40 的變異會移動觸發門檻。

## 保留與驗證

- rev208 原有 1,599 個項目全數保留：1,592 位元組相同、7 修改、0 刪除；新增 22 個產品 class 與建構紀錄。projectile/render/motion/menu/FmlRuntimeClient 原 class bytes 不變。
- 原轉換檔與新版實際打包結果的 use/action/duration/NBT、物品屬性、四條 Dagger 契約一致；674 个既有 assets bytes 相同。
- 1,749 個新增斷言通過：20 閉包邊界、10 VM 邊界、501 來源／改名／數值、537 native 使用掛點、681 打包契約／資源。
- 最終產品重跑 3,886 個前版斷言：rev208 147、rev207 143、rev205 3,596。
- 28 個新／變更 class、234 方法；全橋接器 1,599 class、14,321 方法通過 ASM BasicVerifier。內部引用問題 0，public/protected 移除 0。錯誤基底／來源 preflight 拒絕，原始输入 hash 不變。

上述 native 邊界是隔離 recording hosts，不是遊戲。iYAMATO 與 Bamboo 跑了真實 LegacyConversionEngine 的所有 pass 派發與打包，但原版 DataFixer 測試邊界明確拋錯而非偽造資料；基底與新版候選皆 PARTIAL、installable=false，候選不交付為完成的轉換模組。沒有連服畫面或原服技能結果驗收。真實 ASM/Gson/Guava/SLF4J classes 從先前既有 ViaProxy artifact 擷取且逐位元組核對；不是本次 Maven clean resolve。編譯宣告、測試 hosts 和依賴不包入主 JAR。

特殊彈體、完整裝填／連射客端 tick、其他特殊能力與任意 GUI 仍未全部完成。跳躍不變，不忽略速度封包。沒有修改原始模組或伺服器。

## 安裝与還原

關閉遊戲，用新主檔取代舊主檔，mods 只留一版；保留依賴及 old-mods。所有舊模組都應按新版指紋重轉，尤其共用 Nock 審查的模組；有提示就重啟。新取得物品測試，仍遵守來源彈藥與裝填條件。

```sh
python checkpoints/rev209/unpack.py --output ../lfb-rev209-source
# 已有解包後的 rev208 原始碼時：
python checkpoints/rev209/unpack.py --base-kit /path/rev208-source --output ../lfb-rev209-source
```

本 checkpoint 用 70 份 delta 與 199 份逐檔 hash 驗證的 rev208 不變來源，重建 269 份來源／精簡驗證檔。70 份 delta 包含 reuse manifest。--base-kit 路徑已實跑還原，全部 218 個建構來源 hash 與交付 kit 一致；預設父 checkpoint 自動解包鏈未於本次另行全程重跑。完整詳細紀錄另隨對話 ZIP 交付。解包後閱讀 README.zh-TW.md，以 JDK21、精確 rev208 主檔和真實依賴執行 rebuild.py；未提供的 corpus／舊回歸不宣稱通過。GitHub 老根 src 不當作最新累積來源。

提交含 `[skip ci] [skip actions]`。不改 main/Bamboo/workflow，不 dispatch Actions，不建立 PR/tag/release。
