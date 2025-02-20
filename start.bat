@echo off
:start
python rectv.py
if %ERRORLEVEL% EQU 0 goto end
goto start
:end 