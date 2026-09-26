# OwnBox v2.8.25-preview 预览版

- 回退 v2.8.24-preview 的 `cache_file.flush_interval = "1s"`：fakeip 映射恢复内核默认的攒批写盘行为，`SingBoxOptions.CacheFile` 的字段和 ConfigBuilder 里的配置项都已移除。
- 其余不变：并发拨号（`route.default_concurrent_dial`）与内核 `v1.15.0-alpha.8-ownbox.1` 均未改动。