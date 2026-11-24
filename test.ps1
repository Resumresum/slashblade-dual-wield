[CmdletBinding(DefaultParameterSetName = 'Probe')]
param(
    [Parameter(Mandatory, ParameterSetName = 'Probe')][ValidatePattern('^recipe-[a-z0-9-]+$')][string]$World,
    [Parameter(ParameterSetName = 'Probe')][switch]$Baseline,
    [Parameter(ParameterSetName = 'Probe')][switch]$Views,
    [Parameter(ParameterSetName = 'Probe')][switch]$UpdateChecks,
    [Parameter(Mandatory, ParameterSetName = 'Manual')][switch]$Playtest,
    [Parameter(ParameterSetName = 'Manual')][switch]$PrepareOnly,
    [switch]$Combat
)

$ErrorActionPreference = 'Stop'
$Combat = $Combat -or $UpdateChecks

function Get-JarSha256([string]$Path) {
    $sha256 = [Security.Cryptography.SHA256]::Create()
    try {
        $stream = [IO.File]::OpenRead($Path)
        try { [BitConverter]::ToString($sha256.ComputeHash($stream)).Replace('-', '') }
        finally { $stream.Dispose() }
    } finally { $sha256.Dispose() }
}

$runtime = 'D:\MC_mod_test\runtime'
$java = 'D:\MC_mod_test\tools\mojang-java25\bin\java.exe'
$game = if ($Playtest) { Join-Path $PSScriptRoot 'playtest' } else { Join-Path $PSScriptRoot 'instance' }
$runName = if ($Playtest) { 'playtest-' + [DateTime]::UtcNow.ToString('yyyyMMddHHmmssfff') } else { $World }
$evidence = Join-Path $PSScriptRoot "evidence/$runName"
$libs = Join-Path $PSScriptRoot 'build/libs'
if (!$PrepareOnly -and (Get-Process java, javaw -ErrorAction SilentlyContinue)) { throw 'A Java process is already running' }
if (!$Playtest) {
    foreach ($path in @($evidence, "$game/saves/$World", "$game/$World.txt")) {
        if (Test-Path -LiteralPath $path) { throw 'Choose a new test-world name' }
    }
}
New-Item -ItemType Directory -Force $evidence, "$game/tmp", "$game/natives" | Out-Null

$meta = Get-Content -Raw -LiteralPath "$runtime/26.1.2/26.1.2.json" | ConvertFrom-Json
$loader = Get-Content -Raw -LiteralPath "$runtime/fabric-loader-0.20.1-26.1.2/fabric-loader-0.20.1-26.1.2.json" | ConvertFrom-Json
$classpath = @(foreach ($lib in $loader.libraries) {
    $group, $name, $version = $lib.name.Split(':')
    "$runtime/libraries/$($group.Replace('.','/'))/$name/$version/$name-$version.jar"
})
$classpath += foreach ($lib in $meta.libraries) {
    $allowed = !$lib.rules
    foreach ($rule in $lib.rules) {
        if (!$rule.os -or $rule.os.name -eq 'windows') { $allowed = $rule.action -eq 'allow' }
    }
    if ($allowed -and $lib.downloads.artifact.path) { "$runtime/libraries/$($lib.downloads.artifact.path)" }
}
$classpath += "$runtime/26.1.2/26.1.2.jar"
$extraMods = @()
if ($Playtest) {
    $extraMods += (Get-ChildItem -LiteralPath "$PSScriptRoot/instance/mods" -File -Filter '*.jar').FullName
} else {
    $extraMods += "$libs/dual-wield-recipe-probe.jar"
}
if (!$Baseline) { $extraMods += "$libs/slashblade-dual-wield-26.1.2-0.4.1-experimental.jar" }
if ($Combat) {
    $stage1Mods = 'D:\Minecraft\slashblade-stage1-test\instance\mods'
    $copies = Join-Path $PSScriptRoot 'build/stage1-deps'
    New-Item -ItemType Directory -Force $copies | Out-Null
    foreach ($dependency in Get-ChildItem -LiteralPath $stage1Mods -File) {
        if ($dependency.Name -notmatch '^(slashblade-stage1|slashblade-(combat-compat|twilight-compat|projectile-fix|anvil-enchant-fix)|twilight(forest|-lich-candle-fix|-slimy-soles-fix))-.+\.jar$') { continue }
        $copy = Join-Path $copies $dependency.Name
        if (!(Test-Path -LiteralPath $copy)) { Copy-Item -LiteralPath $dependency.FullName -Destination $copy }
        if ((Get-JarSha256 $copy) -ne (Get-JarSha256 $dependency.FullName)) {
            throw "Stage 1 dependency copy differs from its source: $($dependency.Name)"
        }
        $extraMods += $copy
    }
}
foreach ($file in ($classpath + $extraMods)) {
    if (!(Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing file: $file" }
}
if (!$Playtest) {
    @('renderDistance:3', 'simulationDistance:5', 'pauseOnLostFocus:false', 'maxFps:30', 'enableVsync:false') |
        Set-Content -LiteralPath "$game/options.txt"
}
$argsList = @('-Xmx2G', "-Djava.io.tmpdir=$game/tmp", "-Dorg.lwjgl.system.SharedLibraryExtractPath=$game/natives",
    "-Ddualwield.probe.world=$World", "-Dfabric.addMods=$($extraMods -join ';')",
    "-Ddualwield.probe.combat=$($Combat.ToString().ToLowerInvariant())",
    "-Ddualwield.probe.views=$($Views.ToString().ToLowerInvariant())", "-Ddualwield.probe.evidence=$evidence",
    "-Ddualwield.probe.updatesOnly=$($UpdateChecks.ToString().ToLowerInvariant())",
    '-cp', (($classpath | Select-Object -Unique) -join ';'), 'net.fabricmc.loader.impl.launch.knot.KnotClient',
    '--username', 'DualWieldTester', '--uuid', '00000000-0000-4000-8000-000000000002',
    '--accessToken', '0', '--version', '26.1.2', '--gameDir', $game, '--assetsDir', "$runtime/assets",
    '--assetIndex', $meta.assetIndex.id, '--userType', 'legacy', '--width', '900', '--height', '600')
$javaArgs = @($argsList | ForEach-Object { '"' + $_.Replace('\','/') + '"' })
[IO.File]::WriteAllLines("$evidence/java.args", [string[]]$javaArgs, [Text.UTF8Encoding]::new($false))
$extraMods | ForEach-Object {
    [pscustomobject]@{ Algorithm = 'SHA256'; Hash = Get-JarSha256 $_; Path = $_ }
} | Export-Csv -NoTypeInformation "$evidence/jars.csv"
if ($PrepareOnly) {
    "Prepared isolated playtest at $game"
    return
}
$launch = @{
    FilePath = $java; ArgumentList = ('"@{0}"' -f "$evidence/java.args"); WorkingDirectory = $game
    WindowStyle = 'Hidden'; PassThru = $true
    RedirectStandardOutput = "$evidence/console.log"; RedirectStandardError = "$evidence/stderr.log"
}
if ($Playtest) {
    $launch.WindowStyle = 'Normal'
    $process = Start-Process @launch
    $process.Id | Set-Content "$evidence/pid.txt"
    if ($process.WaitForExit(3000)) { throw "Minecraft closed during startup. Logs: $evidence" }
    return
}
$process = Start-Process @launch
$process.Id | Set-Content "$evidence/pid.txt"
if (!$process.WaitForExit(210000)) {
    $process.Kill()
    $process.WaitForExit()
    throw "Disposable client timed out. Logs: $evidence"
}
if (!(Test-Path -LiteralPath "$game/$World.txt")) { throw "Client did not write recipe results. Logs: $evidence" }
Copy-Item -LiteralPath "$game/$World.txt" -Destination "$evidence/result.txt"
$result = Get-Content -Raw -LiteralPath "$evidence/result.txt"
$result
if ($process.ExitCode -ne 0 -or !$result.StartsWith('PASS:')) { exit 1 }
