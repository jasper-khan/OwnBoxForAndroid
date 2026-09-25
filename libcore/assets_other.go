//go:build !android

package libcore

func extractAssets() {}

// 面板解压只在 Android 生效，与 extractAssets 同步保持非 Android 可编译。
func extractAssetName(name string, useOfficialAssets bool) error {
	return nil
}
