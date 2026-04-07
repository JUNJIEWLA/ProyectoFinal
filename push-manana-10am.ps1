$ErrorActionPreference = 'Stop'

$repoPath = "C:\Users\Willian\Documents\PUCMM\ProyectoWeb\ProyectoFinalWeb"
$logPath = Join-Path $repoPath "push-manana-10am.log"

Set-Location $repoPath

"[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] Iniciando git push origin main" | Out-File -FilePath $logPath -Append -Encoding utf8

git push origin main 2>&1 | Out-File -FilePath $logPath -Append -Encoding utf8

if ($LASTEXITCODE -eq 0) {
    "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] Push completado correctamente" | Out-File -FilePath $logPath -Append -Encoding utf8
} else {
    "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] Error en git push. ExitCode=$LASTEXITCODE" | Out-File -FilePath $logPath -Append -Encoding utf8
    exit $LASTEXITCODE
}

