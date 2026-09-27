# rev208 — 一般 Dagger 的來源網格投射物執行支援

基底：`46963c573f01f2eed0b0d9341b03b1fe3c3f147b` 的 rev207 完整主 JAR。只更新 feature/generic-conversion-iyamato-corpus3，不更改 main、Bamboo 分支、workflow、原始模組或伺服器；不建立 PR/tag/release、不觸發 Actions。

**本版實際新增四種一般 Dagger 的生成、空中飛行與来源網格渲染接線，不再只是 rev207 的診斷報告。仍未完成全部長按武器、特殊 Dagger、任意 GUI 或跳躍同步。**

## 完整主檔

- `legacyforgebridge-0.2.0-alpha.27-rev208-local-test.1.jar`
- SHA-256：`81be9ccaabb178ea7a8be1050cdfe0a814c0f4f8e6489a533bc48e17eb058cf1`，4,082,525 bytes。
- 基底 SHA-256：`f6990b24564b13c599f05ef550dd864bdb6a4d121a2cbbd0e63a402b4e03cfa1`。
- 轉換指紋：`2026-09-27.208-source-rendered-projectiles`。
- 本地 javac 與有基底 hash 檢查的 ASM 增量整合。這不是 Gradle/Loom clean build，也沒有 Minecraft/Fabric/Mixin/Via 連服或真實畫面驗收。
- 1,574 個原有項目全部保留，1,566 個逐位元組不變、8 個修改、0 刪除；新增 24 個產品 class 與建構紀錄。motion/menu/behavior 及 FmlRuntimeClient class 原樣保留。

成品從對話附件下載。此 checkpoint 保存來源、重建工具與驗證，不包含主 JAR、原模組、遊戲 classfiles 或第三方依賴 JAR。

## 實際來源和執行路徑

原始 iYAMATO 樣本 SHA-256：`35a79ca5a034fd6cda4fe4ee1f658c4ce058f9b7ec026516e76363ebdf89635e`。鐵、金、鑽石、大馬士革鋼四種一般 Dagger 在右鍵按下時直接投擲；其他武器可能需要 setItemInUse/onUsingTick/鬆鍵。本次不強迫所有武器套弓的使用流程。

配置身分分析讀取來源 Configuration.getInt、初始化計數器及遞增註冊的已證明直線前綴，遇條件/未知 helper 停止。ID 來自來源契約與客端原 Forge 設定，不以模組名稱、Dagger 名稱或日誌 4803 分流。來源預設 4800 不是產品硬編碼常數；設定需與伺服器一致。

數值 renderer 編譯器保留原貼圖、6 四邊形/24 頂點、UV、法線、旋轉、縮放與 shake，使用有界純數值 VM 和 immutable render-state，沒有套用新版 ArrowRenderer，也不執行原模組傷害程式。

實際 LegacyProjectilePresentationPass 呼叫新 pass，生成 `legacyforgebridge/source-projectile-runtime.json` 的 4 個規則並复制原圖 bytes。舊 v1 報告對該樣本仍是 0；新的 4 條規則是獨立來源網格契約。Registry factory、FML codec/remote spawn、既有 Via tracker、carrier tick、距離可見性、renderer dispatcher 已接線。無程式的既有 carrier 保持舊行為。

空中步進沿用收到的伺服器當前位置/速度，沒有忽略移動或生成第二個本地假實體。碰撞、傷害、掉落及最終銷毀仍由伺服器掌管；不將原本透明的子彈換成可見箭。

## 驗證和限制

147 個新增斷言通過：54 個原始來源/真正 pass/更名和數值變異、74 個原生接線、19 個設定/數值/啟動接線邊界。rev207 的 143 個、rev205 的 3,596 個既有斷言在最終產品重新通過。31 個新增/變更 class、286 方法，以及全橋接器 1,577 class/14,169 方法通過 ASM BasicVerifier；內部引用錯誤 0、public/protected 移除 0。

两個新目錄獨立編譯組裝得到一致完整 JAR。第一組舊版回歸被執行上限中斷，剩餘測試另以有界 heap 逐項重跑成功。來源/錯誤基底 preflight 拒絕測試通過。272 個 checkpoint 檔案逐檔 hash 與往返還原驗證通過，七個 Git blob hash 與本地相同。

原生接線測試使用產品 registry、FML codec、FmlRuntimeClient 的 applyRemoteProjectileSpawn、carrier 和渲染器方法，但 MC/Fabric/trace/GL 是 recording hosts；**不是實機畫面、public receiver 排程或真實 Via 網路測試**。產品編譯使用 compile-only MC/Fabric declarations 及先前下載 ViaProxy artifact 的真實 ASM/Gson，非全套 Maven clean resolve；declarations/測試不包入成品。

目前只准入受限的 direct EntityThrowable、常數 World 建構式及數值 Tessellator renderer 家族。未轉換來源 impact 粒子/聲音、方塊 raytrace/portal、水中氣泡、custom tick 或 additional spawn data。特殊 Dagger 不在這四種之中。

長按武器跨模組 ArrowNockEvent 審查仍可能阻止開始，未強制放行；連射/裝填尚未全面補完。GUI、跳躍不變，不以壓制速度封包修正。本次取得 Bamboo 原始樣本，但沒有新的全 Bamboo/RPGTool 實機回歸。

## 安裝

關閉遊戲，用新主檔取代舊主檔，mods 只留一版。保留 ViaFabricPlus 等依賴及 old-mods，重轉後按提示重啟。伺服器若修改 EntityID，客端 `config/iymts_mod.cfg` 須一致；來源預設未改則不必另建配置。

日誌驗收點：`Source-rendered configured projectile rules=4`、`Loaded source-rendered projectile runtime: mod=iymts_mod rules=4`、`LFB source projectile READY`。投擲時應匹配 remote projectile 並記錄 `Converted legacy FML remote projectile spawned`。這些不代替實機可見飛行確認。

## 解包與本地重建

```sh
python checkpoints/rev208/unpack.py --output ../lfb-rev208-source
```

解包後閱讀 README.zh-TW.md。需要 JDK 21、精確 rev207 主檔、ASM core/tree/analysis/commons + Gson；旧回歸另需 Guava 和先前 rev205 source kit。執行 `rebuild.py --base-main ... --classpath ... --work ... --iyamato ...`；可选 `--baseline-kit ...`。Windows classpath 用分號。未提供的 corpus/舊回歸不會被宣稱執行。此來源為累積二進位增量套件；根 src 仍非最新累積樹，沒有用舊根原始碼覆蓋新工作。
