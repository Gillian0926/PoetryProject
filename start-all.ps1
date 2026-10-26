# start-all.ps1 — 古诗词项目一键启动（Docker 数据库 + Spring Boot 后端 + Vue 前端 + Cloudflare 隧道）
# 启动： powershell -ExecutionPolicy Bypass -File .\start-all.ps1
# 停止： powershell -ExecutionPolicy Bypass -File .\start-all.ps1 -Stop
param([switch]$Stop)

$ErrorActionPreference = 'Continue'
$Root     = 'D:\Users\gilli\hebpu\PoetryProject-main'
$JavaHome = 'D:\env\jdk17'
$Maven    = 'D:\env\maven\bin\mvn.cmd'
$Jar      = Join-Path $Root 'poetry-backend\target\poetry-analysis-0.0.1-SNAPSHOT.jar'
$LogDir   = 'D:\env\logs'
$Cfd      = 'D:\env\cloudflared\cloudflared.exe'
$CfdCfg   = 'C:\Users\gilli\.cloudflared\config.yml'
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

function Say($m, $c = 'Cyan') { Write-Host "[start-all] $m" -ForegroundColor $c }
function BackendProcs { Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like '*poetry-analysis*' } }
function FrontendProcs { Get-CimInstance Win32_Process -Filter "Name='node.exe'" -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like '*vite*' } }
function TunnelProcs { Get-CimInstance Win32_Process -Filter "Name='cloudflared.exe'" -ErrorAction SilentlyContinue }
function Test-Url($u, $sec = 5) { try { $r = Invoke-WebRequest $u -UseBasicParsing -TimeoutSec $sec; return ($r.StatusCode -eq 200) } catch { return $false } }

# ================= 停止模式 =================
if ($Stop) {
    Say '停止后端...'
    BackendProcs | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    Say '停止前端...'
    FrontendProcs | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    Say '停止隧道...'
    TunnelProcs | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    Say '停止数据库容器...'
    Push-Location $Root; docker compose down 2>&1 | Out-Null; Pop-Location
    Say '已全部停止。' 'Green'
    exit 0
}

# ================= 1. Docker =================
Say '① 检查 Docker...'
$dockerUp = $false
try { $v = docker info --format '{{.ServerVersion}}' 2>$null; if ($LASTEXITCODE -eq 0 -and $v) { $dockerUp = $true } } catch {}
if (-not $dockerUp) {
    Say 'Docker 未运行，正在启动 Docker Desktop（最多等 3 分钟）...'
    Start-Process 'C:\Users\gilli\AppData\Local\Programs\DockerDesktop\Docker Desktop.exe'
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 3
        try { $v = docker info --format '{{.ServerVersion}}' 2>$null; if ($LASTEXITCODE -eq 0 -and $v) { $dockerUp = $true; break } } catch {}
    }
}
if (-not $dockerUp) { Say 'Docker 启动失败，请手动打开 Docker Desktop 后重试。' 'Red'; exit 1 }
Say '   Docker 就绪' 'Green'

# ================= 2. 数据库 =================
Say '② 启动 pgvector 数据库...'
Push-Location $Root
docker compose up -d 2>&1 | Out-Null
Pop-Location
$dbOk = $false
for ($i = 0; $i -lt 40; $i++) {
    Start-Sleep -Seconds 2
    docker exec pgvector-db pg_isready -U pguser -d vectordb *> $null
    if ($LASTEXITCODE -eq 0) { $dbOk = $true; break }
}
if ($dbOk) { Say '   数据库就绪：localhost:5432 / vectordb' 'Green' } else { Say '   数据库未就绪，继续尝试启动后端...' 'Yellow' }

# ================= 3. 后端 =================
Say '③ 启动后端（Spring Boot, 8080）...'
if (-not (Test-Path $Jar)) {
    Say '   未找到 jar，正在用 Maven 打包（首次约 1-2 分钟）...'
    $env:JAVA_HOME = $JavaHome
    Push-Location (Join-Path $Root 'poetry-backend')
    & $Maven -q -DskipTests package
    Pop-Location
}
$env:JAVA_HOME = $JavaHome
$env:DASHSCOPE_API_KEY = [Environment]::GetEnvironmentVariable('DASHSCOPE_API_KEY', 'User')
if (BackendProcs) {
    Say '   后端已在运行，跳过启动' 'Yellow'
} else {
    Start-Process -FilePath (Join-Path $JavaHome 'bin\java.exe') `
        -ArgumentList '-jar', $Jar `
        -WorkingDirectory (Join-Path $Root 'poetry-backend') `
        -RedirectStandardOutput (Join-Path $LogDir 'backend.log') `
        -RedirectStandardError  (Join-Path $LogDir 'backend.err.log') `
        -WindowStyle Hidden
}
$apiOk = $false
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 2
    if (Test-Url 'http://localhost:8080/api/chart/emotion?groupType=dynasty' 5) { $apiOk = $true; break }
}
if ($apiOk) { Say '   后端就绪：http://localhost:8080' 'Green' } else { Say "   后端未就绪，查看 $LogDir\backend.log" 'Red' }

# ================= 4. 前端 =================
Say '④ 启动前端（Vite preview, 4173 —— 与隧道 ingress 保持一致）...'
$FrontDir = Join-Path $Root 'poetry-frontend'
if (FrontendProcs) {
    Say '   前端已在运行，跳过启动' 'Yellow'
} else {
    if (-not (Test-Path (Join-Path $FrontDir 'dist\index.html'))) {
        Say '   未找到 dist，正在构建前端...'
        $buildCmd = 'npm run build > "' + (Join-Path $LogDir 'frontend-build.log') + '" 2>&1'
        Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', $buildCmd -WorkingDirectory $FrontDir -WindowStyle Hidden -Wait
    }
    $npmCmd = 'npm run preview -- --port 4173 --host > "' + (Join-Path $LogDir 'frontend.log') + '" 2>&1'
    Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', $npmCmd -WorkingDirectory $FrontDir -WindowStyle Hidden
}
$webOk = $false
for ($i = 0; $i -lt 45; $i++) {
    Start-Sleep -Seconds 2
    if (Test-Url 'http://localhost:4173' 5) { $webOk = $true; break }
}
if ($webOk) { Say '   前端就绪：http://localhost:4173' 'Green' } else { Say "   前端未就绪，查看 $LogDir\frontend.log" 'Red' }

# ================= 5. Cloudflare 隧道 =================
Say '⑤ 启动公网隧道（Cloudflare）...'
$domainOk = $false
if ((Test-Path $Cfd) -and (Test-Path $CfdCfg)) {
    if (TunnelProcs) {
        Say '   隧道已在运行，跳过启动' 'Yellow'
    } else {
        # --protocol http2：本机 QUIC/IPv6 常报 unreachable → 隧道掉线 530，强制走 TCP
        Start-Process -FilePath $Cfd -ArgumentList 'tunnel', '--protocol', 'http2', '--config', $CfdCfg, 'run', 'poetry-app' `
            -RedirectStandardOutput (Join-Path $LogDir 'tunnel.out') `
            -RedirectStandardError  (Join-Path $LogDir 'tunnel.err') `
            -WindowStyle Hidden
        Start-Sleep -Seconds 12
    }
    $domainOk = Test-Url 'https://app.gillianjin0926.online' 20
    if ($domainOk) { Say '   公网域名就绪：https://gillianjin0926.online' 'Green' }
    else { Say "   隧道已启动但域名未通，查看 $LogDir\tunnel.err" 'Red' }
} else {
    Say '   未找到 cloudflared 或配置，跳过' 'Yellow'
}

# ================= 汇总 =================
Write-Host ''
Say '================ 启动结果 ================'
Say ("数据库   " + $(if ($dbOk)  { 'OK   pgvector-db @ 5432' } else { 'FAIL' })) $(if ($dbOk) { 'Green' } else { 'Red' })
Say ("后端     " + $(if ($apiOk) { 'OK   http://localhost:8080' } else { 'FAIL' })) $(if ($apiOk) { 'Green' } else { 'Red' })
Say ("前端     " + $(if ($webOk) { 'OK   http://localhost:4173' } else { 'FAIL' })) $(if ($webOk) { 'Green' } else { 'Red' })
Say ("域名     " + $(if ($domainOk) { 'OK   https://gillianjin0926.online' } else { 'FAIL' })) $(if ($domainOk) { 'Green' } else { 'Red' })
Say ("AI Key   " + $(if ($env:DASHSCOPE_API_KEY) { '已配置' } else { '未配置' })) $(if ($env:DASHSCOPE_API_KEY) { 'Green' } else { 'Yellow' })
Say '=========================================='
Write-Host ''
if ($domainOk) { Say '公网地址：https://gillianjin0926.online   （本机：http://localhost:4173）' 'Cyan' }
