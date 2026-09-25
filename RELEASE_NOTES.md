# OwnBox v2.8.19-preview 预览版

- 内嵌面板（左侧菜单「Sing-box 仪表盘」）恢复“点开就是面板”：首次打开自动写入本地面板连接，不用手动点一次 Connect。
- 面板本体换成 sing-box 官方 dashboard，由内核 api 服务在 127.0.0.1:9091/dashboard/ 提供；Clash API 仍在 127.0.0.1:9090。
- 重启连接服务后生效，首次重启会清理设备上旧的面板文件。
