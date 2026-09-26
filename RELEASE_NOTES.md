# OwnBox v2.8.27-preview 预览版

- 修复崩溃：设置页「清除缓存」确认后，500ms 延迟回调里的 `needReload()` 会在用户已经按返回离开设置页（Fragment 已 detach）时继续执行，`getString` 走 `requireContext()` 抛 `IllegalStateException` 导致 App 崩溃并自动重启。
- 现在 `needReload()` 在 Fragment 未 attach 时直接返回；设置页内的正常提示行为不变。
- 其余不变：内核仍为 `v1.15.0-alpha.8-ownbox.1`，v2.8.26 的应用分流（user_id）修复一并包含。
