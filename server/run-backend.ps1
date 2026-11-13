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

$databaseUrl = if ([string]::IsNullOrWhiteSpace($env:DB_URL)) {
    "jdbc:postgresql://localhost:5432/netmoni"
} else {
    $env:DB_URL
}
$databaseUri = $null
if (-not [Uri]::TryCreate(($databaseUrl -replace '^jdbc:', ''), [UriKind]::Absolute, [ref]$databaseUri)) {
    throw "DB_URL is not a valid PostgreSQL JDBC URL: $databaseUrl"
}
$databaseHost = $databaseUri.Host
$databasePort = $databaseUri.Port
if (-not (Test-NetConnection -ComputerName $databaseHost -Port $databasePort -InformationLevel Quiet -WarningAction SilentlyContinue)) {
    $postgresService = Get-Service -Name 'postgresql-*' -ErrorAction SilentlyContinue |
        Where-Object { $_.Status -ne 'Running' } |
        Select-Object -First 1
    if ($postgresService) {
        throw "Cannot connect to PostgreSQL at ${databaseHost}:$databasePort. Windows service '$($postgresService.Name)' is $($postgresService.Status). Start PostgreSQL first. If it fails to start, repair its timezone files/configuration, then rerun this script."
    }
    throw "Cannot connect to PostgreSQL at ${databaseHost}:$databasePort. Confirm that PostgreSQL is installed and running, and that DB_URL points to the correct host and port."
}

$dbUsername = if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME)) { "postgres" } else { $env:DB_USERNAME }
$securePassword = Read-Host "Enter the PostgreSQL password for user $dbUsername" -AsSecureString
if ($securePassword.Length -eq 0) {
    throw "Database password cannot be empty."
}
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new("", $securePassword).Password

if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) {
    $jwtSecretPath = Join-Path $PSScriptRoot ".netmoni-jwt-secret"
    if (Test-Path -LiteralPath $jwtSecretPath) {
        $env:JWT_SECRET = [System.IO.File]::ReadAllText($jwtSecretPath).Trim()
    } else {
        $randomBytes = New-Object byte[] 48
        $randomGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try {
            $randomGenerator.GetBytes($randomBytes)
            $env:JWT_SECRET = [Convert]::ToBase64String($randomBytes)
            Set-Content -LiteralPath $jwtSecretPath -Value $env:JWT_SECRET -NoNewline -Encoding ascii
        }
        finally {
            $randomGenerator.Dispose()
        }
    }
}
if ($env:JWT_SECRET.Length -lt 32) {
    throw "JWT_SECRET in .netmoni-jwt-secret must contain at least 32 characters. Remove that local file to generate a new secret."
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
