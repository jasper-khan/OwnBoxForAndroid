## 变更

- 新增默认 `route.default_domain_match_strategy = "prefer_fqdn"`：域名规则优先匹配连接目标域名，而非嗅探出的域名。

## 注意事项

- 作用于路由规则（含规则集内未显式指定策略的规则）；DNS 规则不受影响。
