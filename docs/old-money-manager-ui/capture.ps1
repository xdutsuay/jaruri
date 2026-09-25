# Capture old Money Manager UI screenshots (Samsung / USB ADB)
# Package: money.expense.budget.wallet.manager.track.finance.tracker
$ErrorActionPreference = "Stop"
$adb = if (Test-Path "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe") {
    "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
} else { "adb" }
$serial = (& $adb devices | Select-String "device$" | ForEach-Object { ($_ -split "\s+")[0] } | Select-Object -First 1)
if (-not $serial) { Write-Error "No device. Plug in Samsung with USB debugging." }
$out = Join-Path $PSScriptRoot "."
$pkg = "money.expense.budget.wallet.manager.track.finance.tracker"

Write-Host "Device=$serial  Out=$out"
& $adb -s $serial shell monkey -p $pkg -c android.intent.category.LAUNCHER 1 | Out-Null
Start-Sleep -Seconds 2

function Cap([string]$name) {
    $remote = "/sdcard/Download/mm_cap_$name.png"
    $local = Join-Path $out "$name.png"
    & $adb -s $serial shell screencap -p $remote
    & $adb -s $serial pull $remote $local | Out-Null
    & $adb -s $serial shell rm $remote
    Write-Host "Saved $local"
}

# Automated: home + open drawer if possible via keyevents
Cap "01_home"
# KEYCODE_MENU / drawer often needs UI Automator; pause for manual nav
Write-Host @"

Navigate the OLD Money Manager on the phone. After each screen, press Enter here to capture.
Suggested order:
  02_drawer
  03_add_transaction
  04_categories
  05_accounts
  06_budgets
  07_chart_reports
  08_settings
  09_backup_export
  10_search_filter
  11_calendar
  done
"@

while ($true) {
    $label = Read-Host "Screen label (or 'done')"
    if ($label -eq "done" -or [string]::IsNullOrWhiteSpace($label)) { break }
    Cap $label.Trim()
}
Write-Host "Done. Files in $out"
