# OwnBox v2.8.18-preview 预览版

- 内置面板从 yacd 换成 sing-box 官方 dashboard，由内核 api 服务在 127.0.0.1:9091/dashboard/ 提供。
- Clash API 保留在 127.0.0.1:9090，继续供流量图表、连接诊断与局域网共享使用。
- 重启连接服务后生效，首次重启会清理设备上旧的面板文件；面板地址设置项更名为 panelURL，旧的自定义地址不再继承。