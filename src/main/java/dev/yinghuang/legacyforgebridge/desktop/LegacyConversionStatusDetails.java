package dev.yinghuang.legacyforgebridge.desktop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * User-visible, read-only explanation of the conversion-state outcome already determined by
 * ConversionOutcomeReport. This DOES NOT alter runtime admission or suppress errors.
 * Conversion success != loader admission != loading in the running client.
 */
public final class LegacyConversionStatusDetails {
    private static final int MAX_MODULES = 10;
    private static final int MAX_NAME = 115;
    private static final int MAX_DIAGNOSTIC = 220;

    private LegacyConversionStatusDetails() { }

    /** A malformed sidecar must never stop the desktop reporting flow itself. */
    public static String safeExplain(JsonObject report) {
        try { return explain(report); }
        catch (RuntimeException malformed) {
            return "轉換診斷明細暫時無法解析（" + malformed.getClass().getSimpleName()
                    + "）；請提供 legacy-cache/conversion-state.json 和完整診斷 ZIP。";
        }
    }

    public static String explain(JsonObject result) {
        JsonArray modules = array(result, "modules");
        if (modules == null || modules.isEmpty()) return "尚無可用的逐模組轉換明細；請提供 legacy-cache/conversion-state.json。";
        StringBuilder output = new StringBuilder();
        int unsupported = 0, shown = 0;
        for (JsonElement raw : modules) {
            if (raw == null || !raw.isJsonObject()) continue;
            JsonObject item = raw.getAsJsonObject();
            String outcome = value(item, "outcome");
            if ("LOADED_REPORTED".equals(outcome) || "PENDING_RESTART".equals(outcome)) continue;
            unsupported++;
            if (shown >= MAX_MODULES) continue;
            shown++;
            String source = clipped(value(item, "sourceFile"), MAX_NAME);
            if (source.isBlank()) source = "(未記錄來源檔名)";
            String status = value(item, "status");
            String phase = value(item, "phase");
            output.append(shown).append(". ").append(source)
                    .append(" | conversion=").append(status.isBlank() ? "UNKNOWN" : status)
                    .append(" | load=").append(outcome.isBlank() ? "UNKNOWN" : outcome).append('\n');
            output.append("   ").append(reason(outcome, status, phase)).append('\n');
            JsonObject conversion = object(item, "conversion");
            String error = firstDiagnostic(conversion);
            String manifestReason = value(conversion, "unavailableReason");
            String managerMessage = value(item, "message");
            if (!error.isBlank()) output.append("   診斷：").append(clipped(error, MAX_DIAGNOSTIC)).append('\n');
            else if (!manifestReason.isBlank()) output.append("   原因：")
                    .append(clipped(manifestReason, MAX_DIAGNOSTIC)).append('\n');
            else if (!managerMessage.isBlank()) output.append("   管理器：")
                    .append(clipped(managerMessage, MAX_DIAGNOSTIC)).append('\n');
        }
        if (unsupported == 0) return "本次轉換報告沒有標記無法使用的模組；注意已載入不等於玩法完整相容。";
        if (unsupported > shown) output.append("還有 ").append(unsupported - shown).append(" 個模組，請參考完整報告。\n");
        output.append("需要完整判定：legacy-cache/conversion-state.json、")
              .append("legacy-cache/reports/conversion-diagnostics-rev213.zip、logs/latest.log。")
              .append("請勿刪除完整錯誤堆疊或強制安裝 loaderSafe=false 的候選檔。");
        return output.toString();
    }

    private static String reason(String outcome, String status, String phase) {
        return switch (outcome) {
            case "FAILED" -> "轉換程序失敗；必須檢查具體異常與失敗的 Pass。";
            case "BLOCKED" -> "轉換來源不具備足夠的安全證明；禁止把候選當成完整可玩模組。";
            case "NOT_LOADER_SAFE" -> "生成檔未通過 Fabric 載入安全判定；檢查入口類別與遺留位元組碼。";
            case "NOT_STAGED" -> "候選檔未完成安裝排程；檢查轉換產物與 mods 目錄狀態。";
            case "LOAD_NOT_CONFIRMED" -> "轉換器尚未確認本次載入；需檢查 Minecraft 是否已重新啟動及 Fabric 載入日誌。";
            case "INCONSISTENT_RESTART_STATE" -> "快取中的重啟與安裝狀態不一致，需檢查 conversion-state.json。";
            case "CONFLICT" -> "Fabric 模組 ID 與現有模組衝突；不可讓兩個 JAR 同時註冊相同 ID。";
            case "INCOMPLETE_STATE" -> "轉換狀態遺失或未知；檢查狀態檔而不是推論轉換成功。";
            default -> ("FAILED".equals(status) || "BLOCKED".equals(status))
                    ? "轉換未達安全載入門檻，請查看每個診斷碼。"
                    : "尚無可信的載入確認；manager phase=" + clipped(phase, 100) + "。";
        };
    }

    private static String firstDiagnostic(JsonObject conversion) {
        JsonArray diagnostics = array(conversion, "diagnostics");
        if (diagnostics == null) return "";
        String warning = "";
        for (JsonElement entry : diagnostics) {
            if (entry == null || !entry.isJsonObject()) continue;
            JsonObject diagnostic = entry.getAsJsonObject();
            String severity = value(diagnostic, "severity");
            String id = value(diagnostic, "ruleId");
            if (id.isBlank()) id = value(diagnostic, "code");
            String message = value(diagnostic, "message");
            if (message.isBlank()) continue;
            String detail = (id.isBlank() ? "" : id + ": ") + message;
            if ("ERROR".equalsIgnoreCase(severity) || "UNSUPPORTED".equalsIgnoreCase(value(diagnostic, "supportLevel")))
                return detail;
            if (warning.isBlank() && "WARNING".equalsIgnoreCase(severity)) warning = detail;
        }
        return warning;
    }

    private static JsonObject object(JsonObject item, String key) {
        if (item == null) return null;
        JsonElement raw = item.get(key);
        return raw != null && raw.isJsonObject() ? raw.getAsJsonObject() : null;
    }
    private static JsonArray array(JsonObject item, String key) {
        if (item == null) return null;
        JsonElement raw = item.get(key);
        return raw != null && raw.isJsonArray() ? raw.getAsJsonArray() : null;
    }
    private static String value(JsonObject item, String key) {
        if (item == null) return "";
        JsonElement raw = item.get(key);
        if (raw == null || !raw.isJsonPrimitive()) return "";
        try { return raw.getAsString(); } catch (RuntimeException unexpected) { return ""; }
    }
    private static String clipped(String text, int max) {
        if (text == null) return "";
        String safe = text.replace('\n', ' ').replace('\r', ' ').replace('\0', ' ').trim();
        return safe.length() <= max ? safe : safe.substring(0, max - 1) + "…";
    }
}
