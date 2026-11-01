param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$OutputPath
)
$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')
$checks = [System.Collections.Generic.List[string]]::new()
function Confirm-Catalog([bool]$Condition, [string]$Name) {
    if (-not $Condition) { throw "验证失败：$Name" }
    $checks.Add($Name)
}
function Get-Catalog([string]$Path) {
    Invoke-RestMethod -Method Get -Uri ($BaseUrl + $Path) -TimeoutSec 15
}
function Get-CatalogStatus([string]$Path) {
    try {
        return [int](Invoke-WebRequest -UseBasicParsing -Uri ($BaseUrl + $Path) -TimeoutSec 15).StatusCode
    } catch {
        if ($null -ne $_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        throw
    }
}

$health = Get-Catalog '/api/health'
Confirm-Catalog ($health.status -eq 'UP' -and $health.dataset.rowCount -eq 1500) '服务启动且已导入1500条'
Confirm-Catalog ($health.dataset.sha256 -eq '07ff43a3600e34cffbdc0de8751406cac33f6cfc330c169472ae739ed8abdfa8') '来源哈希一致'
Confirm-Catalog ($health.dataset.missingCoordinates -eq 14 -and $health.dataset.missingMetroWalk -eq 423) '缺失值数量保持原样'
$ids = [System.Collections.Generic.HashSet[string]]::new()
for ($page = 0; $page -lt 15; $page++) {
    $result = Get-Catalog "/api/listings?page=$page&size=100"
    Confirm-Catalog ($result.items.Count -eq 100 -and $result.total -eq 1500) "第$page 页100条"
    foreach ($listing in $result.items) {
        if (-not $ids.Add($listing.id)) { throw "分页重复编号：$($listing.id)" }
        if ($listing.dataKind -ne 'SIMULATED') { throw '房源未标记模拟数据' }
    }
}
Confirm-Catalog ($ids.Count -eq 1500) '遍历全部分页共1500个唯一编号'
$district = [Uri]::EscapeDataString('浦东新区')
$filtered = Get-Catalog "/api/listings?district=$district&rentalType=ENTIRE&bedrooms=1&maxRent=3000&sort=rentAsc&size=100"
Confirm-Catalog ($filtered.total -eq 68) '浦东整租一居不超过3000元共68条'
foreach ($listing in $filtered.items) {
    if ($listing.district -ne '浦东新区' -or $listing.rentalType -ne 'ENTIRE' -or $listing.bedrooms -ne 1 -or $listing.rent -gt 3000) {
        throw '返回房源违反已传入的筛选条件'
    }
}
$missing = Get-Catalog '/api/listings/SH-MOCK-000201'
Confirm-Catalog ($null -eq $missing.longitude -and $null -eq $missing.metroWalkMinutes -and $missing.amenities.petsAllowed -eq 'UNKNOWN') '详情空值与设施未知保留'
$empty = Get-Catalog '/api/listings?maxRent=1'
Confirm-Catalog ($empty.total -eq 0 -and $empty.items.Count -eq 0) '无匹配结果不放宽预算'
Confirm-Catalog ((Get-CatalogStatus '/api/listings/absent') -eq 404) '不存在的编号返回404'
Confirm-Catalog ((Get-CatalogStatus '/api/listings?commute=30') -eq 400) '未实现的通勤条件返回400'
Confirm-Catalog ((Get-CatalogStatus '/api/listings?size=101') -eq 400) '超过分页上限返回400'
$filters = Get-Catalog '/api/listing-filters'
Confirm-Catalog ($filters.districts.Count -eq 16 -and $filters.towns.Count -eq 218) '筛选项覆盖16区218街镇'
$evidence = [pscustomobject]@{
    verifiedAt = [DateTimeOffset]::UtcNow.ToString('o')
    baseUrl = $BaseUrl
    checkCount = $checks.Count
    checks = $checks.ToArray()
    uniqueListings = $ids.Count
    dataset = $health.dataset
    combinationQueryTotal = $filtered.total
    sampleIds = @($filtered.items | Select-Object -First 3 -ExpandProperty id)
}
if ($OutputPath) { $evidence | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $OutputPath -Encoding utf8 }
$evidence | Select-Object baseUrl, checkCount, uniqueListings, combinationQueryTotal
