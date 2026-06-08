param(
    [string]$BaseUrl = "http://localhost:8080",
    [Parameter(Mandatory = $true)]
    [string]$AccessToken,
    [string]$Profile = "smoke",
    [int]$ThinkTimeSeconds = 1
)

$ErrorActionPreference = "Stop"

$ScriptPath = Join-Path $PSScriptRoot "post-list-capacity.js"

k6 run `
  -e BASE_URL=$BaseUrl `
  -e ACCESS_TOKEN=$AccessToken `
  -e PROFILE=$Profile `
  -e THINK_TIME_SECONDS=$ThinkTimeSeconds `
  $ScriptPath
