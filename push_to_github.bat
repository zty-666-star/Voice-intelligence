@echo off
chcp 65001 >nul
title 一键推送代码到 GitHub (Voice-intelligence)
cd /d "%~dp0"

echo ========================================================
echo 正在检查 Git 是否已安装...
echo ========================================================
where git >nul 2>nul
if %errorlevel% neq 0 (
    echo [错误] 您的电脑尚未安装 Git！
    echo 请先在 Android Studio 右下角点击 "Download and Install" 安装 Git，
    echo 或者访问 https://git-scm.com/download/win 下载安装后再双击本文件。
    echo.
    pause
    exit /b 1
)

echo Git 已就绪，正在推送 16 次完整 Git 提交记录到您的仓库...
echo 目标仓库: https://github.com/zty-666-star/Voice-intelligence.git
echo ========================================================
git remote remove origin 2>nul
git remote add origin https://github.com/zty-666-star/Voice-intelligence.git
git branch -M main
git push -u origin main

if %errorlevel% neq 0 (
    echo.
    echo [提示] 如果上方提示网络超时，请开启加速器或稍后重试。
) else (
    echo.
    echo [成功] 推送完成！请刷新 https://github.com/zty-666-star/Voice-intelligence 查看全部代码与提交记录。
)
echo.
pause
