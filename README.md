# 🦞 ClawBridge

Android 无障碍桥接 APK —— 让 OpenClaw 直接操作你的手机屏幕。

## 原理

ClawBridge 注册为 Android **无障碍服务**，获得系统级权限：
- 读取任意 App 的屏幕节点树（不依赖任何 API）
- 模拟点击、滑动、文字输入
- 触发系统按键（返回、主页、最近任务）

同时在 `localhost:9876` 开一个 HTTP 服务，OpenClaw 通过 JSON 协议与它通信。

## 安装

1. 从 [Actions](https://github.com/suyike/clawbridge/actions) 下载最新 APK
2. 安装后打开 App
3. 点击「打开无障碍设置」→ 找到 ClawBridge → 开启
4. 回到 App → 点击「启动服务」

## API

所有接口返回 JSON。

### `GET /status`
```json
{"ok": true, "service_running": true, "accessibility_enabled": true}
```

### `GET /screen`
返回当前屏幕的交互元素列表：
```json
{
  "ok": true,
  "package": "com.example.app",
  "screen_width": 1080,
  "screen_height": 1920,
  "total_elements": 42,
  "elements": [
    {
      "id": 0,
      "class": "android.widget.Button",
      "text": "搜索",
      "bounds": {"l": 100, "t": 200, "r": 300, "b": 280},
      "flags": {"click": true}
    }
  ]
}
```

### `POST /tap`
```json
{"x": 200, "y": 400}
```

### `POST /swipe`
```json
{"x1": 100, "y1": 800, "x2": 100, "y2": 200, "duration": 300}
```

### `POST /text`
```json
{"text": "你好世界"}
```

### `POST /key`
```json
{"key": "back"}
// 支持: back, home, recents, notifications, quick_settings, power_dialog, lock_screen
```

### `POST /find`
查找并点击匹配的文本：
```json
{"text": "搜索", "click": true}
```

### `POST /open`
直接启动 App（无需桌面点击）：
```json
{"package": "com.tencent.mm"}
// 或按应用显示名称搜索
{"name": "微信"}
```

通过包名（`package`）精确启动；通过名称（`name`）则在全量已安装应用中模糊搜索第一个匹配项。

## 安全

- 只监听 `localhost`，外网无法访问
- 无外部依赖，不联网
- 开源透明
