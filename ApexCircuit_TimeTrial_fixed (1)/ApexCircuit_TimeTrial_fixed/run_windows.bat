@echo off
echo Compiling ApexCircuit...
cd /d "%~dp0src"
javac *.java
if %errorlevel% neq 0 (
    echo Compilation failed! Make sure Java JDK is installed.
    pause
    exit /b 1
)
echo Running ApexCircuit...
java Main
pause
