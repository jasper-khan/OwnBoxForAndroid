package libcore

const (
	geoipDat       = "geoip.db"
	geositeDat     = "geosite.db"
	geoipVersion   = "geoip.version.txt"
	geositeVersion = "geosite.version.txt"

	// 官方 sing-box dashboard，由 sing-box api 服务在 /dashboard/ 提供
	dashboardDstFolder = "dashboard"
	dashboardVersion   = "dashboard.version.txt"
)

var apkAssetPrefixSingBox = "sing-box/"
var internalAssetsPath string
var externalAssetsPath string
