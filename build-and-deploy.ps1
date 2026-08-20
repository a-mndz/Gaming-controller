#Requires -Version 5.1
<#
.SYNOPSIS
    One-click build, test and deploy for EuroPad (PC server + Android phone deck).

.DESCRIPTION
    Runs the whole loop in the order that actually works on this machine:

        1. stop a running EuroPadServer.exe      (it holds a file lock on its own output,
                                                  so publish/test fails while it runs)
        2. dotnet test  server/EuroPad.Server.sln
        3. dotnet publish -> server-bin\EuroPadServer.exe
        4. start the server again in its own window (the pairing QR + PIN print there)
        5. gradle testDebugUnitTest assembleDebug   (JAVA_HOME pinned to JDK 17)
           plus bundleDebug when -Bundle is passed
        6. copy the fresh APK over EuroPad-Mobile.apk at the repo root, and the AAB over
           EuroPad-Mobile.aab when -Bundle. Both are tracked on purpose - they are the
           "install this now" snapshot, and refreshing them by hand is how the .aab ended up
           a day behind the .apk. -NoCopy leaves them alone. Without -Bundle the summary
           reports the root .aab as STALE rather than letting it drift unnoticed.
        7. adb am force-stop  +  adb install -r  +  relaunch
           ("install -r" does NOT kill the running app: without the force-stop the phone
            keeps showing the old build and you chase ghosts)

    Nothing in step 6 can fail the run: a locked or missing snapshot degrades to a WARN row,
    because the whole point is step 7.

    Every external tool is located rather than assumed to be on PATH. Nothing is
    redirected with PowerShell's ">" operator anywhere in here - it mangles binary and
    rewrites text as UTF-16, which is how server.log ended up full of NUL bytes.

.EXAMPLE
    .\build-and-deploy.ps1
        Full loop: server tests, publish, restart, app tests, APK, refresh the root
        snapshot, install, relaunch.

.EXAMPLE
    .\build-and-deploy.ps1 -SkipServer
        Phone only - fastest way to iterate on Kotlin.

.EXAMPLE
    .\build-and-deploy.ps1 -SkipServer -Bundle
        Phone, plus refresh EuroPad-Mobile.aab. Use this before committing or sharing,
        so the .apk and .aab in the folder describe the same build.

.EXAMPLE
    .\build-and-deploy.ps1 -SkipTests -SkipApp
        Republish and restart the server only.
#>
[CmdletBinding()]
param(
    # adb serial. Omit when exactly one device is attached.
    [string]$Serial = '',
    # JDK 17 root. Omit to use JAVA_HOME, else auto-detect Eclipse Adoptium.
    [string]$JavaHome = '',
    # Skip unit tests on both sides (build + deploy only).
    [switch]$SkipTests,
    # Leave the Android app alone.
    [switch]$SkipApp,
    # Leave the PC server alone.
    [switch]$SkipServer,
    # Build the APK but do not touch the phone.
    [switch]$NoInstall,
    # Also run bundleDebug and refresh EuroPad-Mobile.aab. Off by default: it roughly doubles the
    # Android build time and the phone is installed from the APK, so it is wasted on a normal
    # code-and-check loop.
    [switch]$Bundle,
    # Leave EuroPad-Mobile.apk / .aab at the repo root alone (build to app\build only).
    [switch]$NoCopy,
    # Publish the server but leave it stopped.
    [switch]$NoServerStart,
    # Wipe the phone's logcat after launching, so the next capture is clean.
    [switch]$ClearLog
)

$ErrorActionPreference = 'Stop'

$Root       = $PSScriptRoot
$AppDir     = Join-Path $Root 'app'
$Sln        = Join-Path $Root 'server\EuroPad.Server.sln'
$Csproj     = Join-Path $Root 'server\EuroPad.Server\EuroPad.Server.csproj'
$ServerOut  = Join-Path $Root 'server-bin'
# Tracked on purpose - see the .gitignore header. These are the two files a person is told to grab
# from the folder, so the script keeps them in step with the tree rather than trusting a manual copy.
$ApkShip    = Join-Path $Root 'EuroPad-Mobile.apk'
$AabShip    = Join-Path $Root 'EuroPad-Mobile.aab'
$PackageId  = 'com.europad.app'
$Activity   = "$PackageId/.MainActivity"

$script:Results = New-Object System.Collections.ArrayList
$script:ServerWasRunning = $false
$script:ServerRestarted = $false

function Write-Head([string]$Text) {
    Write-Host ''
    Write-Host ("==== {0} " -f $Text).PadRight(74, '=') -ForegroundColor Cyan
}

function Add-Result([string]$Step, [string]$State, [string]$Note = '') {
    [void]$script:Results.Add([pscustomobject]@{ Step = $Step; Result = $State; Note = $Note })
}

function Invoke-Native {
    param(
        [Parameter(Mandatory)][string]$File,
        [string[]]$Arguments = @(),
        [string]$What = 'command'
    )
    Write-Host ("> {0} {1}" -f $File, ($Arguments -join ' ')) -ForegroundColor DarkGray
    & $File @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$What failed (exit code $LASTEXITCODE)" }
}

function Resolve-Tool {
    param([string[]]$Candidates = @(), [string]$OnPath = '')
    foreach ($c in $Candidates) {
        if ($c -and (Test-Path -LiteralPath $c)) { return (Resolve-Path -LiteralPath $c).Path }
    }
    if ($OnPath) {
        $cmd = Get-Command $OnPath -ErrorAction SilentlyContinue
        if ($cmd) { return $cmd.Source }
    }
    return $null
}

function Find-JavaHome {
    if ($JavaHome) { return $JavaHome }
    if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        return $env:JAVA_HOME
    }
    foreach ($base in @('C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Java', 'C:\Program Files\Microsoft')) {
        if (-not (Test-Path -LiteralPath $base)) { continue }
        # Newest JDK 17 wins: the Android plugin (AGP 8.4) needs 17, and 21 breaks Kotlin 1.9.24.
        $hit = Get-ChildItem -LiteralPath $base -Directory -ErrorAction SilentlyContinue |
               Where-Object { $_.Name -match 'jdk-?17' } |
               Sort-Object Name -Descending |
               Select-Object -First 1
        if ($hit -and (Test-Path -LiteralPath (Join-Path $hit.FullName 'bin\java.exe'))) { return $hit.FullName }
    }
    return $null
}

function Stop-EuroPadServer {
    $procs = @(Get-Process -Name 'EuroPadServer' -ErrorAction SilentlyContinue)
    if ($procs.Count -eq 0) { return $false }
    Write-Host "Stopping $($procs.Count) running EuroPadServer process(es) - it locks its own exe." -ForegroundColor Yellow
    $procs | Stop-Process -Force
    Start-Sleep -Milliseconds 800
    return $true
}

function Get-AdbDevices([string]$Adb) {
    $lines = & $Adb devices
    $ids = @()
    foreach ($line in $lines) {
        if ($line -match '^(\S+)\s+device$') { $ids += $Matches[1] }
    }
    return $ids
}

# ----------------------------------------------------------------------------- tools

Write-Head 'Locating tools'

$dotnet = Resolve-Tool -Candidates @('C:\Program Files\dotnet\dotnet.exe') -OnPath 'dotnet'
# Gradle's own distributions sit under a hash-named folder, so glob for them rather than pinning one
# machine's hash. Order: a committed/generated wrapper first, then the newest downloaded 8.9, then PATH.
$gradleDists = @(
    Get-ChildItem -Path (Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-8.9-bin\*\gradle-8.9\bin\gradle.bat') -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        ForEach-Object { $_.FullName }
)
$gradle = Resolve-Tool -Candidates (@((Join-Path $AppDir 'gradlew.bat')) + $gradleDists) -OnPath 'gradle'
$adb = Resolve-Tool -Candidates @(
    (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'),
    (Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk\platform-tools\adb.exe')
) -OnPath 'adb'

Write-Host "dotnet : $(if ($dotnet) { $dotnet } else { '<not found>' })"
Write-Host "gradle : $(if ($gradle) { $gradle } else { '<not found>' })"
Write-Host "adb    : $(if ($adb)    { $adb }    else { '<not found>' })"

if (-not $SkipServer -and -not $dotnet) { throw 'dotnet SDK not found. Install .NET 8 SDK or pass -SkipServer.' }
if (-not $SkipApp -and -not $gradle)    { throw 'Gradle not found. Pass -SkipApp or fix the path in this script.' }

if (-not $SkipApp) {
    $javaHomeResolved = Find-JavaHome
    if (-not $javaHomeResolved) { throw 'JDK 17 not found. Pass -JavaHome "C:\path\to\jdk-17".' }
    # Trailing separators leak in from the machine's own JAVA_HOME and some launchers build
    # "$JAVA_HOME\bin\java.exe" naively, giving a doubled separator.
    $env:JAVA_HOME = $javaHomeResolved.TrimEnd('\', '/')
    Write-Host "JAVA_HOME: $env:JAVA_HOME"
}

try {
    # ------------------------------------------------------------------------- server

    if (-not $SkipServer) {
        Write-Head 'PC server'
        $script:ServerWasRunning = Stop-EuroPadServer

        if (-not $SkipTests) {
            Invoke-Native -File $dotnet -Arguments @('test', $Sln, '-c', 'Release', '--nologo') -What 'Server unit tests'
            Add-Result 'Server unit tests' 'OK'
        } else {
            Add-Result 'Server unit tests' 'SKIPPED'
        }

        # Single-file, framework-dependent: one exe in server-bin, which is what the README
        # tells the user to run. Falls back to a plain publish if the RID build is unavailable.
        $publishArgs = @(
            'publish', $Csproj, '-c', 'Release', '-r', 'win-x64', '--self-contained', 'false',
            '-p:PublishSingleFile=true', '-o', $ServerOut, '--nologo'
        )
        Write-Host ("> {0} {1}" -f $dotnet, ($publishArgs -join ' ')) -ForegroundColor DarkGray
        & $dotnet @publishArgs
        if ($LASTEXITCODE -eq 0) {
            $serverExe = Join-Path $ServerOut 'EuroPadServer.exe'
        } else {
            Write-Host 'Single-file publish failed; falling back to a plain publish.' -ForegroundColor Yellow
            $fallbackDir = Join-Path $Root 'server\EuroPad.Server\bin\publish'
            Invoke-Native -File $dotnet -Arguments @('publish', $Csproj, '-c', 'Release', '-o', $fallbackDir, '--nologo') -What 'Server publish'
            $serverExe = Join-Path $fallbackDir 'EuroPadServer.exe'
        }
        if (-not (Test-Path -LiteralPath $serverExe)) { throw "Publish produced no exe at $serverExe" }
        Add-Result 'Server publish' 'OK' $serverExe

        if ($NoServerStart) {
            Add-Result 'Server start' 'SKIPPED' 'left stopped (-NoServerStart)'
        } else {
            # Own console window on purpose: the pairing QR code and PIN are printed there, and
            # capturing stdout would hide them.
            Start-Process -FilePath $serverExe -WorkingDirectory (Split-Path -Parent $serverExe) | Out-Null
            Start-Sleep -Milliseconds 1200
            $up = @(Get-Process -Name 'EuroPadServer' -ErrorAction SilentlyContinue)
            if ($up.Count -eq 0) { throw 'Server exited immediately - check its window for the error (ViGEmBus driver installed?).' }
            $script:ServerRestarted = $true
            Add-Result 'Server start' 'OK' "pid $($up[0].Id) - QR and PIN are in its window"
        }
    } else {
        Add-Result 'PC server' 'SKIPPED'
    }

    # ---------------------------------------------------------------------------- app

    if (-not $SkipApp) {
        Write-Head 'Android app'
        $gradleTasks = @('-p', $AppDir, '--console=plain')
        if (-not $SkipTests) { $gradleTasks += 'testDebugUnitTest' }
        $gradleTasks += 'assembleDebug'
        # Same debug keystore as the APK, so the pair always describes one build. Unqualified, like
        # assembleDebug above: with "-p app" the name is matched across that build's projects, and
        # app\settings.gradle.kts includes only :app, so there is nothing to be ambiguous with.
        if ($Bundle) { $gradleTasks += 'bundleDebug' }
        Invoke-Native -File $gradle -Arguments $gradleTasks -What 'Gradle build'
        Add-Result 'App unit tests' $(if ($SkipTests) { 'SKIPPED' } else { 'OK' })

        $apk = Join-Path $AppDir 'app\build\outputs\apk\debug\app-debug.apk'
        if (-not (Test-Path -LiteralPath $apk)) { throw "APK not found at $apk" }
        $apkInfo = Get-Item -LiteralPath $apk
        Add-Result 'APK build' 'OK' ("{0:N1} MB, {1:HH:mm:ss}" -f ($apkInfo.Length / 1MB), $apkInfo.LastWriteTime)

        # ------------------------------------------------------------- repo-root snapshots
        # Nothing below is allowed to abort the run. The point of this script is getting the build
        # onto the phone, and a snapshot problem - Windows holding a handle on a 25 MB file, or a
        # future AGP renaming its output - must not cost you the install. Every failure here degrades
        # to a WARN row in the summary instead.
        # -ErrorAction Stop is explicit rather than relying on the ambient preference, which is
        # deliberately flipped to 'Continue' further down in this same scope for the adb call.
        $copiedApk = $false
        if ($NoCopy) {
            Add-Result 'Copy to repo root' 'SKIPPED' '-NoCopy'
        } else {
            try {
                Copy-Item -LiteralPath $apk -Destination $ApkShip -Force -ErrorAction Stop
                $copiedApk = $true
                Add-Result 'Copy to repo root' 'OK' 'EuroPad-Mobile.apk'
            } catch {
                Write-Host "Could not refresh EuroPad-Mobile.apk: $($_.Exception.Message)" -ForegroundColor Yellow
                Add-Result 'Copy to repo root' 'WARN' 'apk in use - snapshot left stale'
            }
        }

        $copiedAab = $false
        $aab = Join-Path $AppDir 'app\build\outputs\bundle\debug\app-debug.aab'
        if ($Bundle -and -not (Test-Path -LiteralPath $aab)) {
            # bundleDebug reported success, so this means the output moved (an archivesName or
            # outputFileName override in app\app\build.gradle.kts would do it), not a build failure.
            Write-Host "bundleDebug ran but produced no AAB at $aab" -ForegroundColor Yellow
            Add-Result 'AAB build' 'WARN' 'built, but not at the expected path'
        } elseif ($Bundle) {
            $aabInfo = Get-Item -LiteralPath $aab
            $aabNote = "{0:N1} MB, {1:HH:mm:ss}" -f ($aabInfo.Length / 1MB), $aabInfo.LastWriteTime
            $aabState = 'OK'
            if ($NoCopy) {
                $aabNote = "$aabNote - not copied (-NoCopy)"
            } else {
                try {
                    Copy-Item -LiteralPath $aab -Destination $AabShip -Force -ErrorAction Stop
                    $copiedAab = $true
                } catch {
                    Write-Host "Could not refresh EuroPad-Mobile.aab: $($_.Exception.Message)" -ForegroundColor Yellow
                    $aabState = 'WARN'
                    $aabNote = "$aabNote - built, but the root copy is in use"
                }
            }
            Add-Result 'AAB build' $aabState $aabNote
        } elseif ($NoCopy) {
            # Saying "re-run with -Bundle" here would be a lie: -Bundle -NoCopy skips the copy too.
            Add-Result 'AAB build' 'SKIPPED' '-NoCopy'
        } elseif (Test-Path -LiteralPath $AabShip) {
            # Not a failure - just refuse to let it rot silently, which is what happened when the
            # only way to refresh it was to remember to run bundleDebug by hand.
            # The 5 minute grace stops one build's own pair tripping the check: Copy-Item preserves
            # timestamps and the two tasks can finish in either order under --parallel.
            $shippedAab = (Get-Item -LiteralPath $AabShip).LastWriteTime
            if ($shippedAab -lt $apkInfo.LastWriteTime.AddMinutes(-5)) {
                Add-Result 'AAB build' 'STALE' ("{0:yyyy-MM-dd HH:mm} - re-run with -Bundle" -f $shippedAab)
            } else {
                Add-Result 'AAB build' 'SKIPPED' 'pass -Bundle to rebuild'
            }
        } else {
            Add-Result 'AAB build' 'SKIPPED' 'pass -Bundle to build one'
        }

        if ($NoInstall) {
            Add-Result 'Phone install' 'SKIPPED' '-NoInstall'
        } elseif (-not $adb) {
            Add-Result 'Phone install' 'SKIPPED' 'adb not found'
        } else {
            # @() so a single attached device does not unroll into a bare string.
            $devices = @(Get-AdbDevices $adb)
            if ($devices.Count -eq 0) {
                Add-Result 'Phone install' 'SKIPPED' 'no device (USB debugging on? cable authorised?)'
            } else {
                $target = $Serial
                if ($target) {
                    if ($devices -notcontains $target) { throw "Serial '$target' is not attached. Attached: $($devices -join ', ')" }
                } elseif ($devices.Count -eq 1) {
                    $target = $devices[0]
                } else {
                    throw "More than one device attached ($($devices -join ', ')). Re-run with -Serial <id>."
                }

                # force-stop first: install -r leaves the old process alive, showing the old UI.
                Invoke-Native -File $adb -Arguments @('-s', $target, 'shell', 'am', 'force-stop', $PackageId) -What 'force-stop'

                # Some adb builds report a failed install with exit code 0, so read the text too.
                Write-Host ("> {0} -s {1} install -r <apk>" -f $adb, $target) -ForegroundColor DarkGray
                # PowerShell 5.1 turns a native command's stderr into error records as soon as you
                # capture it with 2>&1. Under $ErrorActionPreference='Stop' that makes adb's harmless
                # "* daemon not running; starting now" line abort a perfectly good install, so relax
                # the preference for this one call and judge the result by text + exit code instead.
                $prevEap = $ErrorActionPreference
                $ErrorActionPreference = 'Continue'
                try {
                    $installOut = & $adb -s $target install -r $apk 2>&1 | ForEach-Object { "$_" }
                } finally {
                    $ErrorActionPreference = $prevEap
                }
                $installOut | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
                if ($LASTEXITCODE -ne 0 -or (($installOut -join ' ') -notmatch 'Success')) {
                    throw "APK install failed: $($installOut -join ' ')"
                }

                if ($ClearLog) { & $adb -s $target logcat -c }
                Invoke-Native -File $adb -Arguments @('-s', $target, 'shell', 'am', 'start', '-n', $Activity) -What 'app launch'
                Add-Result 'Phone install' 'OK' $target
            }
        }
    } else {
        Add-Result 'Android app' 'SKIPPED'
    }

    Write-Head 'Summary'
    $script:Results | Format-Table -AutoSize | Out-String | Write-Host
    Write-Host 'Done. Both sides are current.' -ForegroundColor Green
    # Gated on what was actually written, not on the switches: a copy that hit the WARN branch above
    # must not be advertised here as a fresh file.
    if ($copiedApk) { Write-Host "Folder copy: $ApkShip" -ForegroundColor DarkGray }
    if ($copiedAab) { Write-Host "Folder copy: $AabShip" -ForegroundColor DarkGray }
    if (-not $SkipApp -and -not $NoInstall -and $adb) {
        Write-Host 'Live phone log:  adb logcat -s EuroPadUDP:* *:S' -ForegroundColor DarkGray
    }
    exit 0
}
catch {
    Write-Head 'Summary (FAILED)'
    $script:Results | Format-Table -AutoSize | Out-String | Write-Host
    Write-Host "FAILED: $($_.Exception.Message)" -ForegroundColor Red

    # Do not leave the user without a server just because a later step blew up.
    if ($script:ServerWasRunning -and -not $script:ServerRestarted -and -not $NoServerStart) {
        $exe = Join-Path $ServerOut 'EuroPadServer.exe'
        if (Test-Path -LiteralPath $exe) {
            Write-Host 'Restarting the previously running server so you are not left disconnected.' -ForegroundColor Yellow
            Start-Process -FilePath $exe -WorkingDirectory $ServerOut | Out-Null
        }
    }
    exit 1
}
