$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskJava = 'D:/MC_mod_test/tools/mojang-java25/bin'
$taskClasses = Join-Path $taskRoot 'build/motion-test'
& "$taskJava/javac.exe" '-J-Duser.language=en' -d $taskClasses "$taskRoot/src/main/java/local/dualwield/BladeMotion.java" "$taskRoot/src/main/java/local/dualwield/BurstSequence.java" "$PSScriptRoot/BladeMotionTest.java" "$PSScriptRoot/LongBladeViewTest.java" "$PSScriptRoot/DoubleCutTest.java" "$PSScriptRoot/BurstStyleTest.java" "$PSScriptRoot/FlurrySequenceTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Motion check compilation failed' }
& "$taskJava/java.exe" -cp $taskClasses FlurrySequenceTest
if ($LASTEXITCODE -ne 0) { throw 'Twelve-cut boundary check failed' }
& "$taskJava/java.exe" -cp $taskClasses BladeMotionTest
if ($LASTEXITCODE -ne 0) { throw 'Motion path check failed' }
& "$taskJava/java.exe" -cp $taskClasses BurstStyleTest
if ($LASTEXITCODE -ne 0) { throw 'Steep burst/right-side stab check failed' }
& "$taskJava/java.exe" -cp $taskClasses LongBladeViewTest
if ($LASTEXITCODE -ne 0) { throw 'Long-blade view check failed' }
& "$taskJava/java.exe" -cp $taskClasses DoubleCutTest
if ($LASTEXITCODE -ne 0) { throw 'Double-cut and hilt separation check failed' }
& "$PSScriptRoot/server/check.ps1"
if ($LASTEXITCODE -ne 0) { throw 'Contact scheduler check failed' }
