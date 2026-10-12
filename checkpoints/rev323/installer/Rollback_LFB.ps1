
﻿#requires -version 5.1
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
# Works from either minecraft/lfb_install or the installed minecraft/lfb directory.
$toolDir = [System.IO.Path]::GetFullPath($PSScriptRoot)
$game = [System.IO.Directory]::GetParent($toolDir).FullName
$lfb = Join-Path $game 'lfb'
$lock = $null
function Hash([string]$p) { return (Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash.ToLowerInvariant() }
function Scoped([string]$p) {
    $full = [System.IO.Path]::GetFullPath($p)
    $separator = [System.IO.Path]::DirectorySeparatorChar
    if (-not $full.StartsWith(($game.TrimEnd($separator) + $separator), [StringComparison]::OrdinalIgnoreCase)) { throw '回復清單有超出實例的路徑，已停止。' }
    return $full
}
try {
    if ([System.IO.Path]::GetFileName($toolDir) -notin @('lfb_install', 'lfb')) { throw '請從 minecraft\lfb\Rollback_LFB.cmd 或 minecraft\lfb_install\Rollback_LFB.cmd 執行。' }
    if (-not (Test-Path -LiteralPath (Join-Path $game 'mods') -PathType Container)) { throw '找不到上一層的 mods 資料夾，已停止回復。' }
    $lock = [System.IO.File]::Open((Join-Path $lfb 'session.lock'), [System.IO.FileMode]::OpenOrCreate, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
    $backup = (Get-Content -LiteralPath (Join-Path $lfb 'last-install.txt') -Raw -Encoding UTF8).Trim()
    $backup = Scoped $backup
    if (-not $backup.StartsWith((Join-Path (Join-Path $lfb 'backups') 'bootstrap-'), [StringComparison]::OrdinalIgnoreCase)) { throw '備份位置不符。' }
    $record = Get-Content -LiteralPath (Join-Path $backup 'install-record.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    if ([string]$record.game -ne $game) { throw '這份備份屬於另一個實例。' }
    foreach ($item in $record.installed) {
        $target = Scoped ([string]$item.path)
        if ((Test-Path -LiteralPath $target -PathType Leaf) -and ((Hash $target) -ne [string]$item.sha256)) { throw "安裝後已被更改，為免覆蓋你的檔案，停止自動回復：$target" }
    }
    foreach ($item in $record.previous) {
        $saved = Scoped ([string]$item.backup)
        $target = Scoped ([string]$item.original)
        if (-not $saved.StartsWith(($backup.TrimEnd([System.IO.Path]::DirectorySeparatorChar)+[System.IO.Path]::DirectorySeparatorChar),[StringComparison]::OrdinalIgnoreCase)) { throw '備份來源不在本次備份資料夾。' }
        if ((Hash $saved) -ne [string]$item.sha256) { throw "備份雜湊錯誤：$saved" }
        $knownTarget = @($record.installed | Where-Object { [string]$_.path -eq $target }).Count -gt 0
        if ((Test-Path -LiteralPath $target) -and -not $knownTarget -and ([System.IO.Path]::GetFileName($target) -notin @('bootstrap-state.properties','launch-ticket.properties'))) { throw "回復路徑已有新檔案：$target" }
    }
    Write-Host '將恢復安裝前的 LFB 主 JAR 和啟動工具。old-mods、快取與玩家設定不刪除。' -ForegroundColor Yellow
    $answer = Read-Host '輸入 RESTORE 確認（其他輸入取消）'
    if ($answer -cne 'RESTORE') { Write-Host '已取消。'; exit 0 }
    foreach ($item in $record.installed) { $p=Scoped ([string]$item.path); if (Test-Path -LiteralPath $p -PathType Leaf) { Remove-Item -LiteralPath $p } }
    foreach ($item in $record.previous) { Copy-Item -LiteralPath ([string]$item.backup) -Destination ([string]$item.original) -Force }
    $record | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $lfb 'last-rollback.json') -Encoding UTF8
    Write-Host '已回復安裝前的檔案。Prism 請保留或恢復安裝前的預啟動設定；若之前也是外部 Bootstrap，不要刪除其指令。' -ForegroundColor Green
    Write-Host '此工具不反向搬動快取。若舊版仍使用 legacy-cache，它會重新建立舊目錄；lfb/cache 與原始模組仍保留。'
    exit 0
} catch { Write-Host ('回復停止：' + $_.Exception.Message) -ForegroundColor Red; Write-Host '請先關閉 Minecraft。備份保留在 lfb\backups；沒有提供自動忽略錯誤。'; exit 1 }
finally { if ($null -ne $lock) { $lock.Dispose() } }
