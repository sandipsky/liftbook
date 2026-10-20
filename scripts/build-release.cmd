@echo off
rem Double-click, or run from a Command Prompt: builds the signed release APK into dist\.
rem Pass -Install to put it on the connected phone as well.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-release.ps1" %*
