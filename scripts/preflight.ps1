[CmdletBinding()]
param(
    [switch]$Json
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$results = [System.Collections.Generic.List[object]]::new()

function Add-Result {
    param(
        [Parameter(Mandatory)]
        [string]$Name,
        [Parameter(Mandatory)]
        [ValidateSet('PASS', 'WARN', 'FAIL')]
        [string]$Status,
        [Parameter(Mandatory)]
        [string]$Details
    )

    $results.Add([pscustomobject]@{
        name = $Name
        status = $Status
        details = $Details
    })
}

function Resolve-JavaHome {
    $candidates = [System.Collections.Generic.List[string]]::new()
    if ($env:JAVA_HOME) {
        $candidates.Add($env:JAVA_HOME)
    }
    if ($env:ProgramFiles) {
        $candidates.Add((Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'))
    }
    if ($env:LOCALAPPDATA) {
        $candidates.Add((Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jbr'))
    }

    foreach ($candidate in $candidates | Select-Object -Unique) {
        if (Test-Path -LiteralPath (Join-Path $candidate 'bin\java.exe')) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    return $null
}

function Read-LocalSdkPath {
    param([Parameter(Mandatory)][string]$ProjectRoot)

    foreach ($environmentName in @('ANDROID_SDK_ROOT', 'ANDROID_HOME')) {
        $environmentValue = [Environment]::GetEnvironmentVariable($environmentName)
        if ($environmentValue -and (Test-Path -LiteralPath $environmentValue)) {
            return (Resolve-Path -LiteralPath $environmentValue).Path
        }
    }

    $localProperties = Join-Path $ProjectRoot 'local.properties'
    if (-not (Test-Path -LiteralPath $localProperties)) {
        return $null
    }
    $sdkLine = Get-Content -LiteralPath $localProperties |
        Where-Object { $_ -match '^sdk\.dir=' } |
        Select-Object -First 1
    if (-not $sdkLine) {
        return $null
    }

    $escapedPath = $sdkLine.Substring('sdk.dir='.Length)
    $sdkPath = $escapedPath.Replace('\:', ':').Replace('\\', '\')
    if (Test-Path -LiteralPath $sdkPath) {
        return (Resolve-Path -LiteralPath $sdkPath).Path
    }
    return $null
}

function Read-CompileSdk {
    param([Parameter(Mandatory)][string]$ProjectRoot)

    $buildFile = Join-Path $ProjectRoot 'app\build.gradle.kts'
    if (-not (Test-Path -LiteralPath $buildFile)) {
        return $null
    }
    $contents = Get-Content -Raw -LiteralPath $buildFile
    $match = [regex]::Match($contents, 'compileSdk\s*\{\s*version\s*=\s*release\((\d+)\)')
    if ($match.Success) {
        return [int]$match.Groups[1].Value
    }
    return $null
}

function Read-ApplicationId {
    param([Parameter(Mandatory)][string]$ProjectRoot)

    $buildFile = Join-Path $ProjectRoot 'app\build.gradle.kts'
    $contents = Get-Content -Raw -LiteralPath $buildFile
    $match = [regex]::Match($contents, 'applicationId\s*=\s*"([^"]+)"')
    if ($match.Success) {
        return $match.Groups[1].Value
    }
    return $null
}

$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$mobileRoot = Join-Path $repositoryRoot 'BatteryNotifierAndroidMobileApp'
$wearRoot = Join-Path $repositoryRoot 'BatteryNotifierAndroidWearApp'

if (Test-Path -LiteralPath (Join-Path $repositoryRoot 'AGENTS.md')) {
    Add-Result 'repository.contract' 'PASS' "AGENTS.md found at $repositoryRoot"
} else {
    Add-Result 'repository.contract' 'FAIL' 'AGENTS.md is missing from the repository root.'
}

$javaHome = Resolve-JavaHome
if (-not $javaHome) {
    Add-Result 'java.home' 'FAIL' 'JAVA_HOME is invalid and Android Studio JBR was not found.'
} else {
    $javaExecutable = Join-Path $javaHome 'bin\java.exe'
    try {
        $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
        $startInfo.FileName = $javaExecutable
        $startInfo.Arguments = '-version'
        $startInfo.UseShellExecute = $false
        $startInfo.CreateNoWindow = $true
        $startInfo.RedirectStandardOutput = $true
        $startInfo.RedirectStandardError = $true
        $javaProcess = [System.Diagnostics.Process]::new()
        $javaProcess.StartInfo = $startInfo
        [void]$javaProcess.Start()
        $javaOutput = $javaProcess.StandardOutput.ReadToEnd()
        $javaError = $javaProcess.StandardError.ReadToEnd()
        $javaProcess.WaitForExit()
        $javaExitCode = $javaProcess.ExitCode
        $javaVersionLine = (($javaError + $javaOutput) -split "`r?`n" | Select-Object -First 1).ToString()
        $versionMatch = [regex]::Match($javaVersionLine, 'version\s+"(\d+)')
        if ($javaExitCode -ne 0 -or -not $versionMatch.Success) {
            Add-Result 'java.version' 'FAIL' "Unable to determine Java version from $javaExecutable"
        } else {
            $javaMajor = [int]$versionMatch.Groups[1].Value
            if ($javaMajor -ge 17) {
                Add-Result 'java.version' 'PASS' "Java $javaMajor at $javaHome"
            } else {
                Add-Result 'java.version' 'FAIL' "Java $javaMajor is too old; AGP requires Java 17 or newer."
            }
        }
    } catch {
        Add-Result 'java.version' 'FAIL' "Java could not start: $($_.Exception.Message)"
    }
    if (-not $env:JAVA_HOME) {
        Add-Result 'java.environment' 'WARN' "JAVA_HOME is unset. Use process-local JAVA_HOME=$javaHome before Gradle."
    } elseif (-not (Test-Path -LiteralPath $env:JAVA_HOME)) {
        Add-Result 'java.environment' 'WARN' "JAVA_HOME does not exist; use $javaHome for this process."
    } elseif ((Resolve-Path -LiteralPath $env:JAVA_HOME).Path -ne $javaHome) {
        Add-Result 'java.environment' 'WARN' "JAVA_HOME differs from the resolved JBR: $javaHome"
    } else {
        Add-Result 'java.environment' 'PASS' 'JAVA_HOME points to a usable JDK.'
    }
}

$androidSdk = Read-LocalSdkPath -ProjectRoot $mobileRoot
if (-not $androidSdk) {
    Add-Result 'android.sdk' 'FAIL' 'Android SDK was not found through environment variables or Mobile local.properties.'
} else {
    Add-Result 'android.sdk' 'PASS' "Android SDK at $androidSdk"
    foreach ($project in @(
        [pscustomobject]@{ Name = 'mobile'; Root = $mobileRoot },
        [pscustomobject]@{ Name = 'wear'; Root = $wearRoot }
    )) {
        $compileSdk = Read-CompileSdk -ProjectRoot $project.Root
        if (-not $compileSdk) {
            Add-Result "android.$($project.Name).compileSdk" 'FAIL' 'compileSdk could not be parsed.'
            continue
        }
        $platformJar = Join-Path $androidSdk "platforms\android-$compileSdk\android.jar"
        if (Test-Path -LiteralPath $platformJar) {
            Add-Result "android.$($project.Name).compileSdk" 'PASS' "android-$compileSdk is installed."
        } else {
            Add-Result "android.$($project.Name).compileSdk" 'FAIL' "Missing SDK platform android-$compileSdk."
        }
    }

    $adbExecutable = Join-Path $androidSdk 'platform-tools\adb.exe'
    if (Test-Path -LiteralPath $adbExecutable) {
        Add-Result 'android.adb' 'PASS' "adb found at $adbExecutable"
    } else {
        Add-Result 'android.adb' 'WARN' 'Android platform-tools/adb.exe is missing; emulator and device checks cannot run.'
    }
}

foreach ($project in @(
    [pscustomobject]@{ Name = 'mobile'; Root = $mobileRoot },
    [pscustomobject]@{ Name = 'wear'; Root = $wearRoot }
)) {
    $wrapperScript = Join-Path $project.Root 'gradlew.bat'
    $wrapperProperties = Join-Path $project.Root 'gradle\wrapper\gradle-wrapper.properties'
    $wrapperJar = Join-Path $project.Root 'gradle\wrapper\gradle-wrapper.jar'
    if (
        (Test-Path -LiteralPath $wrapperScript) -and
        (Test-Path -LiteralPath $wrapperProperties) -and
        (Test-Path -LiteralPath $wrapperJar)
    ) {
        $distributionLine = Get-Content -LiteralPath $wrapperProperties |
            Where-Object { $_ -match '^distributionUrl=' } |
            Select-Object -First 1
        Add-Result "gradle.$($project.Name).wrapper" 'PASS' $distributionLine
    } else {
        Add-Result "gradle.$($project.Name).wrapper" 'FAIL' 'Gradle wrapper files are incomplete.'
    }
}

$gitCommand = Get-Command git.exe -ErrorAction SilentlyContinue
if (-not $gitCommand) {
    Add-Result 'git.command' 'FAIL' 'git.exe is not available on PATH.'
} else {
    $gitSafeDirectory = $repositoryRoot.Replace('\', '/')
    $gitStatus = & $gitCommand.Source -c "safe.directory=$gitSafeDirectory" status --short 2>&1
    if ($LASTEXITCODE -ne 0) {
        Add-Result 'git.repository' 'FAIL' ($gitStatus -join [Environment]::NewLine)
    } else {
        Add-Result 'git.repository' 'PASS' 'Repository is readable with a command-local safe.directory override.'
        $changeCount = @($gitStatus).Count
        if ($changeCount -gt 0) {
            Add-Result 'git.worktree' 'WARN' "Working tree has $changeCount changed paths; preserve unrelated changes."
        } else {
            Add-Result 'git.worktree' 'PASS' 'Working tree is clean.'
        }
    }
}

$mobileApplicationId = Read-ApplicationId -ProjectRoot $mobileRoot
$wearApplicationId = Read-ApplicationId -ProjectRoot $wearRoot
if ($mobileApplicationId -and $wearApplicationId -and $mobileApplicationId -eq $wearApplicationId) {
    Add-Result 'datalayer.applicationId' 'PASS' "Common application ID: $mobileApplicationId"
} else {
    Add-Result 'datalayer.applicationId' 'WARN' "Mobile=$mobileApplicationId; Wear=$wearApplicationId. DEC-001 blocks Data Layer E2E."
}

if ($env:CODEX_SANDBOX_NETWORK_DISABLED -eq '1') {
    Add-Result 'codex.sandbox' 'WARN' 'Network is disabled. Prime dependencies before --offline checks.'
    Add-Result 'kotlin.compiler' 'WARN' 'Use -Pkotlin.compiler.execution.strategy=in-process if Kotlin daemon IPC is unavailable.'
}

$passCount = @($results | Where-Object { $_.status -eq 'PASS' }).Count
$warnCount = @($results | Where-Object { $_.status -eq 'WARN' }).Count
$failCount = @($results | Where-Object { $_.status -eq 'FAIL' }).Count

if ($Json) {
    [pscustomobject]@{
        repositoryRoot = $repositoryRoot
        resolvedJavaHome = $javaHome
        resolvedAndroidSdk = $androidSdk
        summary = [pscustomobject]@{
            pass = $passCount
            warn = $warnCount
            fail = $failCount
        }
        checks = $results
    } | ConvertTo-Json -Depth 5
} else {
    foreach ($result in $results) {
        Write-Output "[$($result.status)] $($result.name): $($result.details)"
    }
    Write-Output "PREFLIGHT_SUMMARY pass=$passCount warn=$warnCount fail=$failCount"
    if ($javaHome) {
        Write-Output "RESOLVED_JAVA_HOME=$javaHome"
    }
    if ($androidSdk) {
        Write-Output "RESOLVED_ANDROID_SDK=$androidSdk"
    }
}

if ($failCount -gt 0) {
    exit 1
}
exit 0
