@echo off
REM Topeltklikk siia — lükkab kõik lokaalsed commitid GitHub'i.
REM Kasutab sinu Windows git credential manager'is olevat autentimist.

cd /d "%~dp0"
echo.
echo === git status ===
git status -sb
echo.
echo === unpushed commits ===
git log origin/main..HEAD --oneline
echo.
echo === pushing to origin/main ===
git push origin main
echo.
if %ERRORLEVEL% EQU 0 (
  echo [OK] Push oennestus.
) else (
  echo [VIGA] Push ebaoennestus - kontrolli autentimist.
)
pause
