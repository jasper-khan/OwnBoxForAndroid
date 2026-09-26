## 变更

- 新增内置 DNS hosts 预设（doh.pub、dns.google、cloudflare-dns.com 等常用 DoH 域名），「域名重写」优先；移除 `dns-direct_bootstrap`、`dns-remote_bootstrap` 引导组。
- 代理节点域名改用腾讯 UDP（119.29.29.29）专用解析，对应 mihomo 的 `proxy-server-nameserver`。
- DNS 规则在节点域名解析后拒绝 SVCB、HTTPS、PTR 查询；sniffer 新增 `dns` 协议。
- 新增默认 `route.default_domain_match_strategy = "prefer_fqdn"`：域名规则优先匹配连接目标域名，而非嗅探出的域名。

## 注意事项

- `prefer_fqdn` 作用于路由规则（含规则集内未显式指定策略的规则），DNS 规则不受影响。
