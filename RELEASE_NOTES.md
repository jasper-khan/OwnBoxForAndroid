## 变更

- `dns-direct`、`dns-remote` 内服务器的 `domain_resolver` 分别固定为 `dns-direct_bootstrap`、`dns-remote_bootstrap`。
- 新增 `dns-direct_bootstrap`（223.5.5.5、119.29.29.29）与 `dns-remote_bootstrap`（1.1.1.1、8.8.8.8）并发组，取最先返回的响应。
- 移除内置 `dns-local` 服务器。

## 注意事项

- 两个引导组的服务器地址固定，不随 DNS 设置中的自定义服务器变化。
