@rem Gradle wrapper for ShadeVPN-Android (Windows). See gradlew for the notes.
@rem NOTE: CI runs on Linux only, so this file is not exercised there.
@if "%DEBUG%"=="" @echo off
setlocal

set APP_HOME=%~dp0
set PROPS=%APP_HOME%gradle\wrapper\gradle-wrapper.properties
set WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

if defined JAVA_HOME (set JAVACMD=%JAVA_HOME%\bin\java.exe) else (set JAVACMD=java.exe)

if exist "%WRAPPER_JAR%" (
    "%JAVACMD%" %JAVA_OPTS% %GRADLE_OPTS% -classpath "%WRAPPER_JAR%" -Dorg.gradle.appname=gradlew org.gradle.wrapper.GradleWrapperMain %*
    exit /b %ERRORLEVEL%
)

if not exist "%PROPS%" (
    echo gradlew: cannot read %PROPS% 1>&2
    exit /b 1
)

rem No wrapper jar: hand the bootstrap to PowerShell, then exec gradle.bat.
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop';" ^
  "$p=Get-Content -Raw '%PROPS%';" ^
  "$url=([regex]::Match($p,'(?m)^\s*distributionUrl\s*=\s*(.+)$').Groups[1].Value.Trim() -replace '\\:',':');" ^
  "$sha=([regex]::Match($p,'(?m)^\s*distributionSha256Sum\s*=\s*(.+)$').Groups[1].Value.Trim());" ^
  "$zip=[System.IO.Path]::GetFileName($url); $name=[System.IO.Path]::GetFileNameWithoutExtension($url);" ^
  "$gh=if($env:GRADLE_USER_HOME){$env:GRADLE_USER_HOME}else{Join-Path $env:USERPROFILE '.gradle'};" ^
  "$dir=Join-Path $gh ('wrapper\bootstrap\'+$name);" ^
  "if(-not (Get-ChildItem -Path $dir -Filter gradle.bat -Recurse -ErrorAction SilentlyContinue)){" ^
  "  Write-Host ('gradlew: no gradle-wrapper.jar, bootstrapping '+$name);" ^
  "  New-Item -ItemType Directory -Force -Path $dir | Out-Null;" ^
  "  $tmp=Join-Path $dir ($zip+'.part');" ^
  "  Invoke-WebRequest -Uri $url -OutFile $tmp -UseBasicParsing;" ^
  "  if($sha){ $a=(Get-FileHash $tmp -Algorithm SHA256).Hash.ToLower(); if($a -ne $sha.ToLower()){ Remove-Item $tmp -Force; throw ('checksum mismatch for '+$zip) } }" ^
  "  Expand-Archive -Path $tmp -DestinationPath $dir -Force; Remove-Item $tmp -Force;" ^
  "}" ^
  "$bat=(Get-ChildItem -Path $dir -Filter gradle.bat -Recurse | Select-Object -First 1);" ^
  "if(-not $bat){ throw ('bootstrap failed: no gradle.bat under '+$dir) }" ^
  "Set-Content -Path (Join-Path $env:TEMP 'shadevpn-gradle-path.txt') -Value $bat.FullName"
if errorlevel 1 exit /b 1

set /p GRADLE_BAT=<"%TEMP%\shadevpn-gradle-path.txt"
call "%GRADLE_BAT%" %*
exit /b %ERRORLEVEL%
