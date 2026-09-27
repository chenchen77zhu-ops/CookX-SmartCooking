# Builds the release APK and re-signs it with v1 + v2 + v3 signatures.
# AGP drops v1 (JAR) signing when minSdk >= 24, but some vendor installers still reject APKs without it.
# Requires keystore.properties (storeFile / storePassword / keyAlias / keyPassword), which is not committed.
param([string]$Output = "..\..\CookX-release.apk")
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$props = @{}
Get-Content keystore.properties | Where-Object { $_ -match "=" } | ForEach-Object { $k, $v = $_ -split "=", 2; $props[$k.Trim()] = $v.Trim() }
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$tools = Get-ChildItem "$sdk\build-tools" | Sort-Object Name -Descending | Select-Object -First 1

.\gradlew.bat :app:assembleRelease
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }

$aligned = Join-Path $env:TEMP "cookx-aligned.apk"
& "$($tools.FullName)\zipalign.exe" -f -p 4 app\build\outputs\apk\release\app-release.apk $aligned
& "$($tools.FullName)\apksigner.bat" sign --ks $props.storeFile --ks-key-alias $props.keyAlias `
    --ks-pass "pass:$($props.storePassword)" --key-pass "pass:$($props.keyPassword)" `
    --min-sdk-version 21 --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true `
    --out $Output $aligned
& "$($tools.FullName)\apksigner.bat" verify --min-sdk-version 21 --verbose $Output | Select-String "Verified using v[123] "
Write-Host "Signed APK: $(Resolve-Path $Output)"
