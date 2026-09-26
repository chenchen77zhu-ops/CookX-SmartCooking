param(
 [Parameter(Mandatory=$true)][string]$Apk,
 [string]$Adb = 'adb',
 [string]$OutputDirectory = './tmp/android-install'
)
$ErrorActionPreference = 'Stop'
$Apk = (Resolve-Path -LiteralPath $Apk).Path
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$devices = @(& $Adb devices | Where-Object { $_ -match '^\S+\s+device$' })
if ($LASTEXITCODE -ne 0 -or $devices.Count -ne 1) { throw 'Connect exactly one authorized Android device before retrying.' }
# Keep existing app data. Never uninstall, clear data, or bypass version/signature checks.
& $Adb install -r $Apk 2>&1 | Tee-Object -FilePath (Join-Path $OutputDirectory 'install.txt')
if ($LASTEXITCODE -ne 0) { throw 'Installation failed. Read install.txt for the Android error code; existing data was not cleared.' }
& $Adb shell pm path com.smartcooking.app.localtest 2>&1 | Tee-Object -FilePath (Join-Path $OutputDirectory 'package.txt')
if ($LASTEXITCODE -ne 0) { throw 'Installed package cannot be found' }
& $Adb shell am start -W -n com.smartcooking.app.localtest/com.smartcooking.app.MainActivity 2>&1 | Tee-Object -FilePath (Join-Path $OutputDirectory 'launch.txt')
if ($LASTEXITCODE -ne 0) { throw 'Launcher failed; inspect launch.txt' }
Write-Output 'Installation output and launcher diagnostics saved. Check the phone for the actual UI.'
