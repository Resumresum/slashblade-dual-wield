$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$project = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$javaBin = 'D:/MC_mod_test/tools/mojang-java25/bin'
$output = Join-Path $project 'build/server-check'
& "$javaBin/javac.exe" '-J-Duser.language=en' --release 25 -encoding UTF-8 -d $output (Join-Path $project 'src/main/java/local/dualwield/StrokeScheduler.java') (Join-Path $PSScriptRoot 'StrokeSchedulerCheck.java')
& "$javaBin/java.exe" -ea -cp $output local.dualwield.StrokeSchedulerCheck
