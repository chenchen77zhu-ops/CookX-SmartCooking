param([string]$JavaHome=$env:JAVA_HOME,[string]$AndroidHome=$env:ANDROID_HOME,[string]$OutputDirectory='artifacts/android')
$ErrorActionPreference='Stop'
$repository=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) { throw 'Specify -JavaHome with JDK 21' }
if (-not (Test-Path -LiteralPath $AndroidHome)) { throw 'Specify -AndroidHome with SDK platform 36 and build-tools 35' }
$env:JAVA_HOME=$JavaHome
$env:ANDROID_HOME=$AndroidHome
$env:ANDROID_SDK_ROOT=$AndroidHome
$env:GRADLE_USER_HOME=Join-Path $env:TEMP 'cookx-gradle-user'
$stage=Join-Path $env:TEMP ('cookx-android-'+[guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Path $stage | Out-Null
Push-Location (Join-Path $repository 'frontend')
try {
  npm.cmd run build
  if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed' }
  npx.cmd cap sync android
  if ($LASTEXITCODE -ne 0) { throw 'Capacitor sync failed' }
} finally { Pop-Location }
robocopy (Join-Path $repository 'frontend/android') (Join-Path $stage 'android') /E /XD build .gradle /NFL /NDL /NJH /NJS | Out-Null
if ($LASTEXITCODE -ge 8) { throw 'Android staging failed' }
foreach($module in @('android','app','local-notifications')) {
  $destination=Join-Path $stage ('node_modules/@capacitor/'+$module)
  robocopy (Join-Path $repository ('frontend/node_modules/@capacitor/'+$module)) $destination /E /XD build .gradle /NFL /NDL /NJH /NJS | Out-Null
  if ($LASTEXITCODE -ge 8) { throw 'Plugin staging failed' }
}
('sdk.dir='+$AndroidHome.Replace('\','/')) | Set-Content (Join-Path $stage 'android/local.properties') -Encoding ascii
& (Join-Path $stage 'android/gradlew.bat') -p (Join-Path $stage 'android') assembleDebug testDebugUnitTest --no-daemon
if ($LASTEXITCODE -ne 0) { throw 'Android validation failed' }
$commit=(& git -C $repository rev-parse HEAD).Trim()
$outputPath=[System.IO.Path]::GetFullPath((Join-Path $repository $OutputDirectory))
New-Item -ItemType Directory -Force -Path $outputPath | Out-Null
$apk=Join-Path $outputPath ('CookX-'+$commit.Substring(0,7)+'-debug.apk')
Copy-Item -LiteralPath (Join-Path $stage 'android/app/build/outputs/apk/debug/app-debug.apk') -Destination $apk
@{sourceCommit=$commit;apk=$apk;sha256=(Get-FileHash $apk -Algorithm SHA256).Hash;stage=$stage;environment='Windows JDK21 Android SDK36; no physical device';builtAt=(Get-Date).ToUniversalTime().ToString('o')} | ConvertTo-Json | Set-Content (Join-Path $outputPath 'build-manifest.json') -Encoding utf8
Write-Output $apk
