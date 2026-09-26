## 变更

- 「强制 dns-direct」DNS 规则不再包含远程 DNS 服务器域名；远程 DNS（含自定义远程 DoH 域名）的解析由 `dns-remote_bootstrap` 并发组负责。
