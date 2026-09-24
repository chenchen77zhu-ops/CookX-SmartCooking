param([string]$OutputDirectory = '', [switch]$SkipWebBuild)
$ErrorActionPreference = 'Stop'
$frontendRoot = Split-Path $PSScriptRoot -Parent
$repoRoot = Split-Path $frontendRoot -Parent
# Record authoring state before Capacitor regenerates platform files/line endings.
$commit = (& git -C $repoRoot rev-parse HEAD).Trim()
$dirty = !!(& git -C $repoRoot status --porcelain)
if (!$OutputDirectory) { $OutputDirectory = Join-Path $repoRoot 'tmp/localtest-delivery' }
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
Push-Location $frontendRoot
try {
 if (!$SkipWebBuild) { & npm.cmd run build:localtest; if ($LASTEXITCODE -ne 0) { throw 'Local web build failed' } }
 # Refuse to package the online edition accidentally when reusing a build.
 $markerPath = Join-Path $frontendRoot 'dist/local-test-build.json'
 if (!(Test-Path -LiteralPath $markerPath)) { throw 'dist is not a local-test build' }
 $marker = Get-Content -LiteralPath $markerPath -Raw | ConvertFrom-Json
 if ($marker.edition -ne 'local-test') { throw 'Invalid test build marker' }
 & npx.cmd cap sync android
 if ($LASTEXITCODE -ne 0) { throw 'Capacitor sync failed' }
 $stage = Join-Path $env:TEMP ('cookx-localtest-' + [guid]::NewGuid().ToString('N').Substring(0,8))
 New-Item -ItemType Directory -Path $stage | Out-Null
 & robocopy (Join-Path $frontendRoot 'android') (Join-Path $stage 'android') /E /XD .gradle build /NFL /NDL /NJH /NJS /NP | Out-Null
 if ($LASTEXITCODE -ge 8) { throw 'Android staging failed' }
 foreach ($plugin in @('android','app','local-notifications')) {
  & robocopy (Join-Path $frontendRoot "node_modules/@capacitor/$plugin") (Join-Path $stage "node_modules/@capacitor/$plugin") /E /XD build .gradle /NFL /NDL /NJH /NJS /NP | Out-Null
  if ($LASTEXITCODE -ge 8) { throw "Plugin staging failed: $plugin" }
 }
 $utf8 = [Text.UTF8Encoding]::new($false)
 $gradleFile = Join-Path $stage 'android/app/build.gradle'
 $gradleText = [IO.File]::ReadAllText($gradleFile).Replace('applicationId "com.smartcooking.app"','applicationId "com.smartcooking.app.localtest"')
 [IO.File]::WriteAllText($gradleFile,$gradleText,$utf8)
 $stringsFile = Join-Path $stage 'android/app/src/main/res/values/strings.xml'
 $stringsText = [IO.File]::ReadAllText($stringsFile).Replace('>CookX<','>CookX 本地测试<').Replace('>com.smartcooking.app<','>com.smartcooking.app.localtest<')
 [IO.File]::WriteAllText($stringsFile,$stringsText,$utf8)
 $configFile = Join-Path $stage 'android/app/src/main/assets/capacitor.config.json'
 $config = Get-Content -LiteralPath $configFile -Raw | ConvertFrom-Json
 $config.appId='com.smartcooking.app.localtest';$config.appName='CookX 本地测试';$config.server=[pscustomobject]@{androidScheme='https'}
 [IO.File]::WriteAllText($configFile,($config | ConvertTo-Json -Depth 10),$utf8)
 # Disable Android backup for this isolated test sandbox, retaining ordinary online configuration in the source tree.
 $manifestFile = Join-Path $stage 'android/app/src/main/AndroidManifest.xml'
 [IO.File]::WriteAllText($manifestFile,([IO.File]::ReadAllText($manifestFile).Replace('android:allowBackup="true"','android:allowBackup="false"')),$utf8)
 if (!$env:JAVA_HOME) { $env:JAVA_HOME=Join-Path $repoRoot 'tmp/toolchains/jdk21/jdk-21.0.12.1+1' }
 if (!$env:ANDROID_HOME) { $env:ANDROID_HOME=Join-Path $repoRoot 'tmp/toolchains/android-sdk' }
 if (!$env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME=Join-Path $env:TEMP 'cookx-gradle-user' }
 & (Join-Path $stage 'android/gradlew.bat') -p (Join-Path $stage 'android') assembleDebug testDebugUnitTest --no-daemon 2>&1 | Tee-Object -FilePath (Join-Path $OutputDirectory 'android-build.log')
 if ($LASTEXITCODE -ne 0) { throw 'Android build failed' }
 $apkName = "CookX-localtest-$($commit.Substring(0,7))-debug.apk"
 $destination = Join-Path $OutputDirectory $apkName
 Copy-Item -LiteralPath (Join-Path $stage 'android/app/build/outputs/apk/debug/app-debug.apk') -Destination $destination
 $record = [ordered]@{edition='local-test';applicationId='com.smartcooking.app.localtest';sourceCommit=$commit;sourceDirty=$dirty;apk=$apkName;sha256=(Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash;stage=$stage;builtAt=(Get-Date).ToString('o');validation='Build and JVM tests only; no Android device claim'}
 [IO.File]::WriteAllText((Join-Path $OutputDirectory 'build-manifest.json'),($record | ConvertTo-Json),$utf8)
 Write-Output "APK: $destination"
} finally { Pop-Location }
