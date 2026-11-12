param(
    [switch]$CheckJavaOnly
)

$ErrorActionPreference = "Stop"
$jdkRoots = @(
    "C:\Program Files\Eclipse Adoptium",
    "C:\Program Files\Java"
)

$jdk = $null
foreach ($root in $jdkRoots) {
    if (Test-Path $root) {
        $jdk = Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue |
            Where-Object {
                (Test-Path (Join-Path $_.FullName "bin\java.exe")) -and
                (Test-Path (Join-Path $_.FullName "bin\javac.exe"))
            } |
            Sort-Object Name -Descending |
            Select-Object -First 1
        if ($jdk) { break }
    }
}

if (-not $jdk) {
    throw "Java JDK was not found. Install Java 21 and try again."
}

$javaHome = $jdk.FullName
$env:JAVA_HOME = $javaHome
$env:Path = "$(Join-Path $javaHome 'bin');$env:Path"

if ($CheckJavaOnly) {
    & (Join-Path $javaHome "bin\java.exe") -version
    & (Join-Path $javaHome "bin\javac.exe") -version
    exit $LASTEXITCODE
}

try {
    $health = Invoke-RestMethod -Uri "http://localhost:8080/api/health" -TimeoutSec 2
    if ($health.status -eq "UP" -and $health.service -eq "NetMoni-Server") {
        Write-Host "NetMoni backend is already running at http://localhost:8080. Reusing the current instance."
        exit 0
    }
}
catch {
    # No healthy NetMoni instance is responding; continue with startup.
}

$dbUsername = if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME)) { "postgres" } else { $env:DB_USERNAME }
$securePassword = Read-Host "Enter the PostgreSQL password for user $dbUsername" -AsSecureString
if ($securePassword.Length -eq 0) {
    throw "Database password cannot be empty."
}
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new("", $securePassword).Password

if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) {
    $randomBytes = New-Object byte[] 48
    $randomGenerator = New-Object System.Security.Cryptography.RNGCryptoServiceProvider
    try {
        $randomGenerator.GetBytes($randomBytes)
        $env:JWT_SECRET = [Convert]::ToBase64String($randomBytes)
    }
    finally {
        $randomGenerator.Dispose()
    }
}

Push-Location $PSScriptRoot
$mavenExitCode = 0
try {
    & ".\mvnw.cmd" spring-boot:run
    $mavenExitCode = $LASTEXITCODE
}
finally {
    Pop-Location
}
exit $mavenExitCode
