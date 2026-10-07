#Requires -Version 5.1
<#
.SYNOPSIS
泱穹 AI 开源版 Docker 镜像构建脚本（Windows PowerShell）

.DESCRIPTION
在 yangqiong-ai-docker 目录内封装 docker compose build / push / up。
镜像名前缀与标签通过环境变量 YQ_IMAGE_PREFIX / YQ_IMAGE_TAG 传入 docker-compose.yml。

.PARAMETER Registry
镜像仓库前缀（如 registry.cn-hangzhou.aliyuncs.com/yangqiong），留空使用默认 yangqiongai

.PARAMETER Tag
镜像标签，默认 latest

.PARAMETER Push
构建后推送镜像

.PARAMETER Up
构建后启动整套服务（docker compose up -d）

.EXAMPLE
./build.ps1
.EXAMPLE
./build.ps1 -Registry "registry.cn-hangzhou.aliyuncs.com/yangqiong" -Tag v1.0.0 -Push
#>
param(
  [string]$Registry = "",
  [string]$Tag = "latest",
  [switch]$Push,
  [switch]$Up
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if ($Registry) { $env:YQ_IMAGE_PREFIX = $Registry }
$env:YQ_IMAGE_TAG = $Tag

docker compose build backend frontend
if ($LASTEXITCODE -ne 0) { throw '镜像构建失败' }

if ($Push) {
  docker compose push backend frontend
  if ($LASTEXITCODE -ne 0) { throw '镜像推送失败' }
}

if ($Up) {
  docker compose up -d
  if ($LASTEXITCODE -ne 0) { throw '服务启动失败' }
}

Write-Host 'Docker 镜像构建完成' -ForegroundColor Green
