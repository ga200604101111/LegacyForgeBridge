
﻿#requires -version 5.1
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.IO.Compression.FileSystem
# Installation sources live in minecraft/lfb_install, not in the game root.
$installDir = [System.IO.Path]::GetFullPath($PSScriptRoot)
$game = [System.IO.Directory]::GetParent($installDir).FullName
$assets = Join-Path $installDir 'LFB_Package'
$mods = Join-Path $game 'mods'
$lfb = Join-Path $game 'lfb'
$lock = $null
$backup = $null
$written = New-Object 'System.Collections.Generic.List[string]'
$moved = New-Object 'System.Collections.Generic.List[object]'
# Maintenance tools deliberately remain in lfb/ after rollback or installer cleanup.
# Keep their own failure-recovery lists; they are not runtime artifacts to uninstall.
$toolWritten = New-Object 'System.Collections.Generic.List[string]'
$toolMoved = New-Object 'System.Collections.Generic.List[object]'
$command = '"$INST_JAVA" -Xmx2G -jar "$INST_MC_DIR/lfb/LFB-Bootstrap.jar" "$INST_MC_DIR"'
function Hash([string]$path) { return (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Id([string]$path) {
    $zip = $null
    try {
        $zip = [System.IO.Compression.ZipFile]::OpenRead($path)
        $entry = $zip.GetEntry('fabric.mod.json')
        if ($null -eq $entry) { return '' }
        $reader = [System.IO.StreamReader]::new($entry.Open(), [System.Text.Encoding]::UTF8)
        try { $data = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
        if ($null -eq $data.PSObject.Properties['id']) { return '' }
        return [string]$data.id
    } catch { throw "無法安全讀取模組資訊：$path`n$($_.Exception.Message)" }
    finally { if ($null -ne $zip) { $zip.Dispose() } }
}
function InstallMaintenance {
    $names = @('Rollback_LFB.cmd', 'Rollback_LFB.ps1', 'Prism_Prelaunch_Command.txt', 'Collect_Firefly_Diagnostics.cmd', 'Collect_Firefly_Diagnostics.ps1', 'Collect_Surface_Diagnostics.cmd', 'Collect_Surface_Diagnostics.ps1')
    # Validate every destination before moving anything.
    foreach ($name in $names) {
        $source = Join-Path $installDir $name
        $target = Join-Path $lfb $name
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw "缺少回復／指令工具：$source" }
        if ((Test-Path -LiteralPath $target) -and -not (Test-Path -LiteralPath $target -PathType Leaf)) { throw "回復工具路徑被資料夾占用，不會覆蓋：$target" }
    }
    $toolBackup = $null
    foreach ($name in $names) {
        $source = Join-Path $installDir $name
        $target = Join-Path $lfb $name
        if (Test-Path -LiteralPath $target -PathType Leaf) {
            if ((Hash $source) -eq (Hash $target)) { continue }
            if ($null -eq $toolBackup) {
                $backupRoot = Join-Path $lfb 'backups'
                $toolBackup = Join-Path $backupRoot ('installer-tools-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8))
                [System.IO.Directory]::CreateDirectory($toolBackup) | Out-Null
            }
            $saved = Join-Path $toolBackup $name
            Move-Item -LiteralPath $target -Destination $saved
            $toolMoved.Add([pscustomobject]@{original=$target; backup=$saved})
        }
        $toolWritten.Add($target)
        Copy-Item -LiteralPath $source -Destination $target
        if ((Hash $source) -ne (Hash $target)) { throw "回復工具寫入驗證失敗：$target" }
    }
}
function Instruction {
    Write-Host ''
    Write-Host '請在 Prism 編輯這個實例 → 設定 → 自訂指令 → Pre-launch command，完全換成：' -ForegroundColor Cyan
    Write-Host $command -ForegroundColor Yellow
    try { Set-Clipboard -Value $command; Write-Host '這條指令已複製到剪貼簿。' } catch { Write-Host '剪貼簿不可用；請從上方或 使用說明.txt 複製指令。' }
    Write-Host '不要使用 cmd.exe /c，不要保留舊的 Preflight 指令。'
    Write-Host 'old-mods、.fabric 與既有設定都保留。首次啟動會自動把 legacy-cache 移至 lfb/cache，並整合 LFB 日誌。'
    Write-Host ''
    Write-Host '安裝完成後，可刪除整個 minecraft\lfb_install 資料夾。' -ForegroundColor Green
    Write-Host '不要刪除 minecraft\lfb：它是正式啟動工具，不是安裝暫存。'
    Write-Host '回復工具已保留在 minecraft\lfb\Rollback_LFB.cmd；備份仍在 lfb\backups。'
    Write-Host 'Prism 若已設定相同指令，無須再改。'
    Write-Host '樹葉若仍不見或有重複項目，關閉遊戲後執行 minecraft\lfb\Collect_Surface_Diagnostics.cmd。'
    Write-Host '螢火蟲若仍不可見，關閉遊戲後執行 minecraft\lfb\Collect_Firefly_Diagnostics.cmd 產生本機診斷包。'
}
try {
    if (-not [String]::Equals([System.IO.Path]::GetFileName($installDir), 'lfb_install', [StringComparison]::OrdinalIgnoreCase)) { throw '請從 minecraft\lfb_install\Install_LFB.cmd 執行；不要把安裝腳本放在 minecraft 或 mods 根目錄。' }
    if (-not (Test-Path -LiteralPath $mods -PathType Container)) { throw '找不到上一層的 mods 資料夾。正確位置是 minecraft\lfb_install\Install_LFB.cmd；請勿多套一層 lfb_install。' }
    Write-Host "安裝來源：$installDir"
    Write-Host "遊戲目錄：$game"
    if (-not (Test-Path -LiteralPath $assets -PathType Container)) { throw '缺少 LFB_Package 資料夾，請完整解壓安裝包。' }
    $manifest = Get-Content -LiteralPath (Join-Path $assets 'checksums.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($file in $manifest.files) {
        if ([System.IO.Path]::GetFileName([string]$file.name) -ne [string]$file.name) { throw '安裝清單路徑無效。' }
        $path = Join-Path $assets ([string]$file.name)
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "缺少檔案：$path" }
        if ((Hash $path) -ne [string]$file.sha256) { throw "安裝檔雜湊不符，請重新完整解壓：$path" }
    }
    New-Item -ItemType Directory -Force -Path $lfb | Out-Null
    try { $lock = [System.IO.File]::Open((Join-Path $lfb 'session.lock'), [System.IO.FileMode]::OpenOrCreate, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None) }
    catch { throw '此實例仍在遊戲／轉換中。請關閉 Minecraft 後再安裝，不要同時啟動同一實例。' }
    $runtimeName = 'LegacyForgeBridge-rev321-optimizer-core.jar'
    $runtimeSrc = Join-Path $assets $runtimeName
    $runtimes = @(Get-ChildItem -LiteralPath $mods -File -Filter '*.jar' | Where-Object { (Id $_.FullName) -eq 'legacyforgebridge' })
    foreach ($existing in $runtimes) {
        $archive = [System.IO.Compression.ZipFile]::OpenRead($existing.FullName)
        try {
            $entry = $archive.GetEntry('fabric.mod.json')
            $reader = [System.IO.StreamReader]::new($entry.Open(), [System.Text.Encoding]::UTF8)
            try { $info = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
            if ([string]$info.version -match 'rev([0-9]+)' -and [int]$Matches[1] -gt 323) {
                throw 'A newer LFB revision is installed. This rev323 package will not downgrade it.'
            }
        } finally { $archive.Dispose() }
    }
    $already = $runtimes.Count -eq 1
    if ($already) { $already = (Hash $runtimes[0].FullName) -eq (Hash $runtimeSrc) }
    foreach ($name in @('LFB-Bootstrap.jar','LFB-Converter.jar','bundle.properties')) {
        $dst = Join-Path $lfb $name
        if (-not (Test-Path -LiteralPath $dst -PathType Leaf)) { $already = $false }
        elseif ((Hash $dst) -ne (Hash (Join-Path $assets $name))) { $already = $false }
    }
    if ($already) { InstallMaintenance; Write-Host '核心版本已安裝，只補齊可獨立保留的回復工具；不重裝核心，也不重建轉換快取。' -ForegroundColor Green; Instruction; exit 0 }
    # Validate path conflicts before moving a single user file.
    $targetRuntime = Join-Path $mods $runtimeName
    if (Test-Path -LiteralPath $targetRuntime) {
        if (@($runtimes | Where-Object { $_.FullName -eq $targetRuntime }).Count -eq 0) { throw '目標檔名已被另一個非 LFB 檔案占用；不會覆蓋它。' }
    }
    $backup = Join-Path (Join-Path $lfb 'backups') ('bootstrap-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8))
    New-Item -ItemType Directory -Force -Path (Join-Path $backup 'mods'), (Join-Path $backup 'lfb') | Out-Null
    foreach ($file in $runtimes) {
        $to = Join-Path (Join-Path $backup 'mods') $file.Name
        $h = Hash $file.FullName
        Move-Item -LiteralPath $file.FullName -Destination $to
        $moved.Add([pscustomobject]@{original=$file.FullName; backup=$to; sha256=$h})
    }
    foreach ($name in @('LFB-Bootstrap.jar','LFB-Converter.jar','bundle.properties','launch-ticket.properties','bootstrap-state.properties')) {
        $from = Join-Path $lfb $name
        if (Test-Path -LiteralPath $from -PathType Leaf) {
            $to = Join-Path (Join-Path $backup 'lfb') $name
            $h = Hash $from
            Move-Item -LiteralPath $from -Destination $to
            $moved.Add([pscustomobject]@{original=$from; backup=$to; sha256=$h})
        }
    }
    $targets = @([pscustomobject]@{source=$runtimeSrc; destination=$targetRuntime})
    foreach ($name in @('LFB-Bootstrap.jar','LFB-Converter.jar','bundle.properties')) {
        $targets += [pscustomobject]@{source=(Join-Path $assets $name); destination=(Join-Path $lfb $name)}
    }
    foreach ($item in $targets) {
        # Record first, so a partially-copied destination is removed during rollback.
        $written.Add($item.destination)
        Copy-Item -LiteralPath $item.source -Destination $item.destination
        if ((Hash $item.source) -ne (Hash $item.destination)) { throw "寫入驗證失敗：$($item.destination)" }
    }
    InstallMaintenance
    $installed = @($targets | ForEach-Object { [pscustomobject]@{path=$_.destination;sha256=(Hash $_.destination)} })
    $record = [pscustomobject]@{schema=1;game=$game;backup=$backup;installed=$installed;previous=@($moved.ToArray());created=(Get-Date -Format o)}
    $record | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $backup 'install-record.json') -Encoding UTF8
    [System.IO.File]::WriteAllText((Join-Path $lfb 'last-install.txt'), $backup, ([System.Text.UTF8Encoding]::new($false)))
    Write-Host 'LFB rev323 generic-contracts.1 installed. This is a repack, not a new feature fix.' -ForegroundColor Green
    Write-Host "舊版備份：$backup"
    Write-Host '沒有修改原始 Forge JAR、伺服器、Java 設定或 Prism 程式。'
    Instruction
    exit 0
} catch {
    Write-Host ('安裝停止：' + $_.Exception.Message) -ForegroundColor Red
    if ($toolWritten.Count -gt 0 -or $toolMoved.Count -gt 0) {
        foreach ($path in $toolWritten) { try { if (Test-Path -LiteralPath $path -PathType Leaf) { Remove-Item -LiteralPath $path -Force } } catch { Write-Host "請手動檢查回復工具：$path" } }
        foreach ($entry in $toolMoved) { try { if (Test-Path -LiteralPath $entry.backup -PathType Leaf) { Move-Item -LiteralPath $entry.backup -Destination $entry.original -Force } } catch { Write-Host "工具回復未完成，備份仍在：$($entry.backup)" -ForegroundColor Red } }
    }
    if ($written.Count -gt 0 -or $moved.Count -gt 0) {
        Write-Host '正在回復本次安裝前的檔案…' -ForegroundColor Yellow
        foreach ($path in $written) { try { if (Test-Path -LiteralPath $path -PathType Leaf) { Remove-Item -LiteralPath $path -Force } } catch { Write-Host "請手動檢查：$path" } }
        foreach ($entry in $moved) { try { if (Test-Path -LiteralPath $entry.backup -PathType Leaf) { Move-Item -LiteralPath $entry.backup -Destination $entry.original -Force } } catch { Write-Host "回復未完成，備份仍在：$($entry.backup)" -ForegroundColor Red } }
    }
    Write-Host '若是權限錯誤，請使用對這個實例資料夾有寫入權限的帳號；本程式不會自動要求管理員權限。'
    exit 1
} finally { if ($null -ne $lock) { $lock.Dispose() } }
