# OwnBox v2.8.26-preview 预览版

- 修复「路由 → 路由设置 → 应用」规则不生效：libcore 的 `MyInterfaceAddress()` 之前恒返回 nil，内核判定 TUN 连接「不是来自本机」时直接跳过进程/UID 查询，`user_id`（按应用）规则因此永远匹配不上，选中的应用（如抖音）会落到后面的直连规则。
- 现在对齐官方 libbox：打开 TUN 时记下 TUN 自身的地址（172.19.0.1 / fdfe:dcba:9876::1），内核据此正常查到 UID 并按应用分流。
- 其余不变：内核仍为 `v1.15.0-alpha.8-ownbox.1`，并发拨号开关保持不变。
