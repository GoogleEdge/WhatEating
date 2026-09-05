# 时鲜

面向 Android 11 及以上设备的 AI 做饭助手。它把时令、实时天气、个人偏好和冰箱食材交给用户配置的 OpenAI Responses API，生成可实际执行的菜谱。

## 功能

- 冰箱食材增删、数量记录及照片 AI 识别
- 结合城市天气与当季食材联网推荐
- 预算、口味、忌口、过敏、人数、目标和烹饪时间约束
- 明确标出已有、缺少及可选食材
- 逐步烹饪模式、火候与食品安全提示
- 菜谱收藏和购物清单
- 自定义 Base URL、API Key，自动获取模型列表

## 构建

需要 JDK 17 与 Android SDK 35：

```bash
gradle assembleDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。GitHub Actions 也会自动构建并上传 APK artifact。
