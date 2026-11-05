#!/usr/bin/env bash
set -e
echo "========================================================"
echo "正在将 16 次完整 Git 提交记录推送到您的 GitHub 仓库..."
echo "目标仓库: https://github.com/zty-666-star/Voice-intelligence.git"
echo "========================================================"
git remote remove origin 2>/dev/null || true
git remote add origin https://github.com/zty-666-star/Voice-intelligence.git
git branch -M main
git push -u origin main
echo "推送完成！请刷新 GitHub 页面查看。"
