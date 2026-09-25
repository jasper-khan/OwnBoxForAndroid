# OwnBox v2.8.23-preview 预览版

- 内核换成 OwnBox 自己的 sing-box 分支（基于 reF1nd `v1.15.0-alpha.8-reF1nd`，只加不改，reF1nd 原有功能一个不删）。
- 新增内核选项 `route.default_concurrent_dial`：把域名解析出的所有地址（IPv4/IPv6）同时拨号，谁先连通用谁，对标 mihomo 的 `tcp-concurrent`。
- 核心设置新增“并发拨号（多 IP 同时连接）”开关；覆盖直连目标域名、节点服务器域名与 DNS 服务器域名，TCP 与 UDP 都生效。
