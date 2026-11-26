$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$taskJava='D:/MC_mod_test/tools/mojang-java25/bin'
$taskBuildArgs=Get-ChildItem -LiteralPath "$taskRoot/build" -Filter 'slashblade-dual-wield-26.1.2-0.4.12-experimental*.args'|Sort-Object LastWriteTime -Descending|Select-Object -First 1
if(!$taskBuildArgs){throw 'Build the preview first'}
$taskLines=Get-Content -LiteralPath $taskBuildArgs.FullName
$taskClasspath=$taskLines[6].Trim('"')+';'+$taskLines[8].Trim('"')
$taskOutput=Join-Path $taskRoot 'build/frame-check'
New-Item -ItemType Directory -Force $taskOutput|Out-Null
& "$taskJava/javac.exe" --release 25 -encoding UTF-8 -proc:none -cp $taskClasspath -d $taskOutput "$PSScriptRoot/BladeFrameTest.java"
if($LASTEXITCODE -ne 0){throw 'Frame check compilation failed'}
& "$taskJava/java.exe" -cp "$taskClasspath;$taskOutput" BladeFrameTest
if($LASTEXITCODE -ne 0){throw 'Frame check failed'}
