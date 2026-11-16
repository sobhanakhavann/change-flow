param([Parameter(ValueFromRemainingArguments=$true)][string[]]$ToolArguments)
$ErrorActionPreference = 'Stop'
$repository = Split-Path $PSScriptRoot -Parent
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to the Java 21 installation first.' }
Push-Location $repository
try {
    & .\gradlew.bat --offline --no-daemon prepareAcceptanceTool
    if ($LASTEXITCODE -ne 0) { throw 'Acceptance tool compilation failed.' }
    # Direct Java invocation preserves the real terminal for hidden Console.readPassword.
    & "$env:JAVA_HOME/bin/java.exe" '@build/acceptance/launcher.args' @ToolArguments
    if ($LASTEXITCODE -ne 0) { throw 'Acceptance tool failed; no human result has been recorded.' }
} finally { Pop-Location }
