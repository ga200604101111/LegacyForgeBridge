# LegacyForgeBridge — Bamboo Corpus #2 接續計畫（2026-09-15）

工作分支：`feature/generic-conversion-bamboo-corpus2`

目前基準 HEAD：`4ee9da14bacb382874b78261a6f6d5ac4b38a992`

最後已驗證 CI：`corpus2-p0-verification` run #212 / `34877552352`，Full build/tests、remap、artifact upload 全部成功。

## 目前正確狀態

使用者要的是：LegacyForgeBridge 本體可以把 BambooMod 1.7.10 ver2.6.8.5 轉成可安全載入、可實際遊玩的現代候選模組，而不是只產生 sidecar、補丁或 `PARTIAL` candidate。

目前 Bamboo 整包仍未完成。使用者實測日誌：

```text
Conversion result for Bamboo-2.6.8.5.jar: status=PARTIAL, profile=generic-forge-1.7.10, loaderSafe=false, candidate=Bamboo-2.6.8.5-lfb.jar, managed=none, phase=PARTIAL
```

不要把以下四件事混為一談：

1. LFB 本體可以 build。
2. 某一段 source semantics 已能分析／materialize。
3. Bamboo candidate loader-safe。
4. Bamboo 在實際遊戲與舊伺服器上功能正常。

只有 3+4 同時成立，才接近可交付的 Bamboo 轉換成品。

## 已完成、不要重做

- Generic registry：先前 exact corpus 基線為 42 Item + 63 Block。
- Lifecycle / recipes / OreDictionary / fuel 的既有 generic analyzer/materializer。
- Block behavior inventory、placement compiler、drop evidence compiler。
- Block activation read-only compiler：clicked side、raw metadata、`World.isRemote`、`Player.isSneaking`。
- Activation program hardening：384 組完整 typed-input enumeration、來源 stack/local verifier、switch/branch validation。
- Held-item-gated metadata effect compiler/runtime：commit `4ee9da1`。
- Bamboo 三個灰泥註冊可產生完整 effect rules：`decoplaster`、`decocurveplaster`、`decodcurveplaster`，手持來源 identity 為 `bamboomod:itembamboo`，legacy notify flag = 2，每條 1152 組輸入結果。

不要重新套用舊 recovery patch，也不要回退 `4ee9da1`。

## Exact corpus 識別

原版：`Bamboo-2.6.8.5.jar`

SHA-256：

```text
bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402
```

使用者實際產生 candidate：

```text
Bamboo-2.6.8.5-lfb.jar
SHA-256: 82327a139d570496992b22c587d024cd8b02324dec147c09997083c2b15aa338
```

CI #212 LFB 本體：

```text
LegacyForgeBridge 0.2.0-alpha.26
SHA-256: a255734a581467327cf8dbaf7badab43ca16a57e9f4f10553adc9d0af26e84de
```

## 下一輪第一優先：可重現 exact-corpus 基線

1. 重新讀 branch HEAD，若有新 commit 不可 force push 覆蓋。
2. 使用完整 Bamboo original JAR，先驗 exact SHA。
3. 在乾淨 staging 跑完整 conversion pipeline。
4. 保存 manifest、sidecars、剩餘 legacy references、output hash。
5. 相同 build + 相同 input 至少重跑兩次，驗 deterministic。
6. exact corpus 不存在或 hash 不符時，正式 corpus regression 必須 fail，而不是 skip 後當作 pass。
7. synthetic fixtures 與 exact Bamboo corpus 結果分開記錄。

## 第二優先：修正 converter cache / build identity

目前 `LegacyConversionManager` 使用：

```text
BuildInfo.VERSION + ":" + sourceSha256
```

作為 cache fingerprint。多個不同 converter 程式若仍沿用同一個 `alpha.26`，可能重用舊 candidate。

下一版需：

- 使用新的內部版本／converter revision。
- 確認會影響 conversion output 的 schema/revision 納入 fingerprint。
- 回歸測試：converter 升級必須重轉；同 build 可安全復用；candidate 缺失或 hash 不符不可算已驗證。

這是驗證可靠性問題，不是目前 `loaderSafe=false` 的唯一根因。

## 第三優先：341 個 original class 依賴分類

目前 candidate 仍有 341 個 source class，不能直接全部刪除，也不能只把 `loaderSafe` 改成 true。

每個 source class 分成：

1. 已被 generated semantics 完整替代。
2. 可證明在目前 runtime scope 不可達／不需要。
3. 還有必要 runtime/presentation 語義未遷移。
4. 無法判定（反射、動態載入、未知 classloader path 等）。

只有 1/2 在引用閉合與可達性證據完整後，才允許從 loader-facing candidate 排除。

## 第四優先：按共享能力解缺口，不按 Bamboo 類名硬編碼

### A. Block / BlockState / interaction end-to-end

先用三個已完成灰泥當端到端 anchor：

```text
取得 -> 放置 -> raw metadata -> 正確/錯誤手持物 -> 右鍵旋轉 -> client presentation -> server correction -> 重連
```

要驗：

- registry identity
- metadata / modern BlockState
- block/chunk update
- item identity
- direction mapping
- model/texture
- placement + interaction consistency
- server-authoritative result

不能只驗 sidecar JSON。

### B. BlockEntity / NBT / Inventory / Menu

從實際 corpus dependency graph 選一個最小但完整的容器工作包。

完整能力至少包括：

- BlockEntity identity
- NBT/state restore
- inventory slots
- stack identity/meta/components
- open menu/gui
- click/shift-click
- sync
- close/reopen/reconnect consistency

只讓 GUI 打開或 callback `return true` 不算完成。

### C. 其他共享能力

依 dependency graph 排序：

- ItemBlock 自訂 placement
- neighbor/tick/block-added/break callback
- runtime drops（現有 drop compiler 目前只是 evidence/plan，不等於 runtime 完成）
- ItemFood / ItemSeeds / ItemReed / projectile item families
- entity spawn/tracking/DataWatcher/runtime entity semantics
- renderer registration / old OpenGL semantic migration
- skipped recipes 的 exact 原因
- Forge/FML event runtime adapters

Server-authoritative gameplay 不在 client 重複執行。

## loaderSafe / installable 的完成條件

不允許：

- 硬改 `loaderSafe=true`
- 硬改 `status=CONVERTED`
- 為了 loader check 全刪 source class
- 把 generated wrapper 存在當成功
- 把 skipped tests 當 pass

應該驗證：

- loader-facing candidate 不再包含未證明安全的 legacy executable classes。
- generated classes 的 superclass/descriptor/method/field references 不再指向不可載入的 1.7 Forge/Minecraft APIs。
- necessary runtime semantics 已 materialize 或有明確 modern adapter。
- `ManagedCandidateInstaller` 能正常接管 candidate。
- restart/pending swap/loaded candidate 流程正常。

## 實機驗收層次

四層分開記：

1. **LFB build**：Gradle、tests、remap、namespace/ASM verification。
2. **Exact corpus conversion**：完整 original SHA、deterministic conversion、written/skipped provenance。
3. **Loader/installation**：loader-safe、managed install、restart 後真正載入。
4. **Gameplay/network**：進世界、連 Forge 1.7.10 server、必要 content/GUI/entity/render/sync 正常。

RPGTool 已驗收能力為不可回歸基線。

## GitHub 工作規則

- 沿 `feature/generic-conversion-bamboo-corpus2` 繼續。
- 寫入前重新讀 HEAD。
- 一個完整工作包用一次 logical commit（code + tests + docs/resources）。
- CI fail 先讀完整原因，再一次修完。
- 不 force push。
- 不自動 merge main。
- 每個可測 build 要記：version、commit、CI run、artifact SHA-256、exact corpus coverage、仍未驗證項目。

## 下次直接從這裡開始

1. 讀本文件與 `docs/CORPUS2-BAMBOOMOD-CURRENT-INSPECTION.md`。
2. 讀 branch 最新 HEAD。
3. 建 exact Bamboo whole-JAR regression path。
4. 修 converter revision / cache fingerprint。
5. 產生 class/runtime dependency classification。
6. 從能解除最多真實阻塞的 shared capability 開始實作。

直到 necessary semantics、loader safety、managed installation、exact corpus regression 與實機 smoke test 同時達標前，Bamboo candidate 都維持 `PARTIAL`。
