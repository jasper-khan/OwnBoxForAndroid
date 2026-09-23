# OwnBox v2.8.8-preview 预览版

- **HTTP 代理出站**：改用 sing-box 官方实现，恢复 HTTP/3、CONNECT-UDP 和官方连接管理。
- **VLESS 与 XHTTP**：恢复官方切网及空闲连接接口，并在切网时重置 XHTTP 连接池。
- **URLTest**：取消测速时保留已有的节点测速记录。
- **WebDAV**：测试、备份和恢复仅接受 HTTPS 地址。
- **内核版本**：继续使用官方 sing-box v1.15.0-alpha.7，保留 OwnBox 的 XHTTP、Juicity 和负载均衡功能。
