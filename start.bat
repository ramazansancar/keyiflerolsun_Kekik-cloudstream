@echo off
title RecTV
:start
cls
python rectv.py
if %ERRORLEVEL% EQU 1 goto start
goto end
:end