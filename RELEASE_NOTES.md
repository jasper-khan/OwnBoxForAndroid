# OwnBox v2.8.22-preview 预览版

- 移除“核心设置”里的“双网络加速”和“并发拨号 (Happy Eyeballs)”两个开关：前者会让 Wi‑Fi 与流量同时出网，容易被机场判定多 IP 登录；后者名不副实（实际只是网卡回退，不是 IPv4/IPv6 并发拨号）。
- 内核能力没有删：需要网络策略时可在“进阶设置 → 自定义配置”写入 `{"route":{"default_network_strategy":"hybrid"}}`（hybrid = 双网络并发，fallback = 主网络优先、超时回退）。
- 同步清理对应的多语言文案、设置页监听、文档说明、README 功能描述与过时的 openspec 条目。
