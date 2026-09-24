# OwnBox v2.8.13-preview 预览版

- FakeIP DNS 路由改为末尾兜底规则，关闭 IPv6 时仅处理 A，开启后处理 A 和 AAAA。
- 去除提前生成的重复 FakeIP DNS 规则；返回记录的 TTL 设为 1 秒。
- 显式关闭 FakeIP 映射持久化。
