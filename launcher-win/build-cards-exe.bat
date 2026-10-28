@echo off
rem Builds Cards.exe at the repository root with the .NET Framework C# compiler.
setlocal
for %%I in ("%~dp0..") do set "ROOT=%%~fI"
set "CSC=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if not exist "%CSC%" set "CSC=%WINDIR%\Microsoft.NET\Framework\v4.0.30319\csc.exe"
if not exist "%CSC%" (
  echo csc.exe was not found. Install .NET Framework 4.x.
  exit /b 1
)

"%CSC%" /nologo /target:winexe /optimize+ /out:"%ROOT%\Cards.exe" ^
  /reference:System.dll /reference:System.Windows.Forms.dll /reference:System.Management.dll ^
  /reference:System.IO.Compression.dll /reference:System.IO.Compression.FileSystem.dll ^
  "%~dp0CardsLauncher.cs"
if errorlevel 1 exit /b 1

echo Built %ROOT%\Cards.exe
