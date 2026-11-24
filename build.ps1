param([switch]$ProbeOnly, [switch]$WithProbe)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$runtime = 'D:\MC_mod_test\runtime'
$java = 'D:\MC_mod_test\tools\mojang-java25\bin'
$baseline = 'D:\Minecraft\slashblade-stage1-test\instance\mods'
$game = Join-Path $PSScriptRoot 'instance'
$build = Join-Path $PSScriptRoot 'build'
New-Item -ItemType Directory -Force "$game/mods", "$build/deps", "$build/libs" | Out-Null

$mods = @(
    'fabric-api-0.155.3+26.1.2.jar'
    'ForgeConfigAPIPort-v26.1.5-mc26.1.x-Fabric.jar'
    'SlashBlade-Refabricated-26.1.2-1.5.1-Resharped-2.0.5.jar'
)
foreach ($name in $mods) {
    $source = Join-Path $baseline $name
    $copy = Join-Path "$game/mods" $name
    if (!(Test-Path -LiteralPath $copy)) { Copy-Item -LiteralPath $source -Destination $copy }
    if ((Get-FileHash -LiteralPath $source).Hash -ne (Get-FileHash -LiteralPath $copy).Hash) {
        throw "Dependency copy differs from baseline: $name"
    }
    $archive = [IO.Compression.ZipFile]::OpenRead($copy)
    try {
        foreach ($entry in $archive.Entries) {
            if ($entry.FullName -match '^META-INF/jars/[^/]+\.jar$') {
                $target = Join-Path "$build/deps" $entry.Name
                if (!(Test-Path -LiteralPath $target)) {
                    [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target)
                }
            }
        }
    } finally { $archive.Dispose() }
}

$classpath = @("$runtime/26.1.2/26.1.2.jar")
$classpath += $mods | ForEach-Object { Join-Path "$game/mods" $_ }
$classpath += (Get-ChildItem -LiteralPath "$build/deps", "$runtime/libraries" -Recurse -Filter '*.jar').FullName

function Build-Jar([string]$tree, [string]$name) {
    $classes = Join-Path $build "$name-$([DateTime]::UtcNow.ToString('yyyyMMddHHmmssfff'))"
    New-Item -ItemType Directory -Path $classes | Out-Null
    $sources = (Get-ChildItem -LiteralPath "$PSScriptRoot/$tree/java" -Recurse -Filter '*.java').FullName
    $argsList = @('--release', '25', '-encoding', 'UTF-8', '-proc:none', '-classpath', ($classpath -join ';'), '-d', $classes) + $sources
    $argsList | ForEach-Object { '"' + $_.Replace('\','/') + '"' } | Set-Content -Encoding utf8 "$classes.args"
    & "$java/javac.exe" '-J-Duser.language=en' '-J-Duser.country=US' "@$classes.args"
    Copy-Item -Path "$PSScriptRoot/$tree/resources/*" -Destination $classes -Recurse
    & "$java/jar.exe" --create --file "$build/libs/$name.jar" -C $classes .
    Get-FileHash -LiteralPath "$build/libs/$name.jar"
}

$candidate = 'slashblade-dual-wield-26.1.2-0.4.1-experimental'
if (!$ProbeOnly) { Build-Jar 'src/main' $candidate }
if ($ProbeOnly -or $WithProbe) {
    $classpath += "$build/libs/$candidate.jar"
    Build-Jar 'src/probe' 'dual-wield-recipe-probe'
}
