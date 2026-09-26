# rev205 — 通用來源持握與蓄力開始流程；補存 rev202–204

分支：`feature/generic-conversion-iyamato-corpus3`。接續 GitHub rev201 `f3f4cf05a76b50438830edf5f90fc64d7062099d`，同時保存先前只在對話交付的 rev202、rev203、rev204 來源及重建工具。伺服器、原始模組、main、Bamboo 分支與 workflow 檔案不改。

## 真正修正

- 修正 `LegacyIconTableAnalyzer` 的原版 ItemBow 建構契約：不能把所有 ItemBow 子類直接當成 `full3D=true`。來源明確呼叫 setFull3D 或覆寫方法時仍照來源；否則保留原始 sprite/generated 模型。本次六個弓／槍物品不再誤套 handheld，不自行製作槍械網格。
- 通用互動編譯器支援 ArrowNockEvent 的建立、發布、取消、result 分支。Spear 先前在這個節點被拒絕，因而只有右鍵意圖、沒有開始使用。
- 事件不是直接略過。只有所有載入的轉換模組都通過來源 audit、證明不存在相關監聽器時，才使用事件的原始預設結果。缺少 audit、相關訂閱、未知繼承、反射或 transformer 會否決；未支援的 result 替换也拒絕。這不是完整任意 Forge 事件重播。
- 時長、開始條件、使用姿勢來自原始方法，按住進入原生使用狀態，放開保留原生釋放封包；伺服器傷害、耗彈、生成投射物等效果不在客端重播。
- 每個註冊物品輸出 coverage。新增生產程式沒有 iYAMATO/Blunder/Spear/Reload 名称分流；名稱只出現在樣本驗收資料。

## 實際樣本與測試

對 SHA-256 `35a79ca5a034fd6cda4fe4ee1f658c4ce058f9b7ec026516e76363ebdf89635e` 的原始 iYAMATO JAR：99 個註冊物品都有 coverage；95 筆來源屬性、99 個圖示、12 件護甲資產保留。來源 full3D 持握為 57 個，六個弓／槍保留平面貼圖葉模型。使用程式由 15 增加到 29，其中新增 14 個有 ArrowNockEvent 流程；35 個姿勢程式、六個創造 NBT 範本保留。

3,597 個斷言通過，包含改名樣本、事件取消／跨模組否決、缺少 audit、明確 full3D 覆寫、資料與介接回歸。RPGTool 的 71 個圖示表與 rev204 相同。17 個實際變更 class、204 個方法通過 BasicVerifier；內部引用缺失、既有公開／受保護成員遺失皆為 0。64 個受保護既有項目內容相同。

兩個全新工作目錄重建的主 JAR 完全相同。另在本回合重建 rev202→rev203→rev204，每版產物都與原交付位元組相同。這不代表從 rev188 開始的完整 Gradle/Loom 清潔建置。

## 主模組測試產物

`legacyforgebridge-0.2.0-alpha.27-rev205-local-test.1.jar`，3,990,189 bytes。
SHA-256：`3ce19e3a6d9b4788251aae95edd0c179c93bc44f4d5f65fba054be67ce4461de`。
指紋：`2026-09-27.205-source-held-use-events`。

這是完整主模組的離線增量建構，不是附加補丁。替换舊主 JAR，保留依賴與 old-mods；重新轉換後依提示重啟。查看 `LFB source ArrowNockEvent policy: allowed=...`；false 時必須看拒絕原因，不可視作已啟用蓄力修正。

## 來源取得與重建

```sh
python checkpoints/rev205/unpack.py --output ../LegacyForgeBridge-rev202-205-kits
```

14 個 base64 片段是單一固定 SHA-256 的 XZ 來源 JSON；解包會驗證 819 個檔案及各版原始碼 manifest，只寫不存在的新目錄，不建構、不呼叫 Actions。內容為 `rev202/`、`rev203/`、`rev204/`、`rev205/`，各自保留 rebuild.py、來源、API 宣告、測試與工具；不含原始模組、遊戲 class 或相依套件 JAR。

從既有 rev204 主檔重建本版：

```sh
python ../LegacyForgeBridge-rev202-205-kits/rev205/rebuild.py --base-main /path/legacyforgebridge-0.2.0-alpha.27-rev204-local-test.1.jar --classpath '/path/asm.jar:/path/gson.jar:/path/guava.jar' --work /path/new-rev205-build --iyamato /path/iYAMATOs-Mod-1.7.10.jar --rpgtool /path/RPGTool1-1.1-1.7.10.jar
```

Windows classpath 使用分號。歷史重建依序使用各版 pinned base main。GitHub 根 src 仍是舊來源與 checkpoint 組合，不能單獨編譯後改名成 rev205。

## 未完成／不能保證的部分

未做真正 Minecraft/Fabric/Mixin 啟動、第一／第三人稱畫面比對、真實按住／放開封包時序或連服驗收。執行階段測試使用明確 recording hosts；API 宣告與測試替身未放入主 JAR。本地依賴使用重定位 Kotlin ASM、既有完整 Gson 與 Guava，不是官方 Maven/Loom 全套建置。

原始 Bamboo JAR 本回合不可取得，未重跑其事件 audit；不能保證混合模組組合通過 nock policy。自訂 renderer、特殊視角變換、持續使用客端副作用、全部投射物實體、Bamboo GUI 通用化仍未完成。

跳躍維持 OBSERVE_ONLY，本次不改物理。連弩的 connection abort/endOfStream 只證明連線中止，不足以確認武器或伺服器根因；本次不宣稱修好全部掉線。
