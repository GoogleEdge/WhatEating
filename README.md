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
- WorkManager 持久化后台推荐任务，切换标签页或短暂离开应用后仍可继续
- AI 只在信息确实不足时追问，并展示阶段、进度、预计用时和可展开的工作摘要
- 菜谱详情为独立 Activity，支持烹饪模式、收藏和缺少食材加入购物清单
- 设置内置运行日志页，可查看 AI 调用、响应和后台任务错误
- 清新的自适应圆角图标及配套功能图标

## 构建

需要 JDK 17 与 Android SDK 35：

```bash
gradle assembleDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。GitHub Actions 也会自动构建并上传 APK artifact。

提交信息以 `Release 时鲜 1.0` 开头时，`.github/workflows/release.yml` 会构建并发布 `v1.0` GitHub Release，同时附带 `shixian-v1.0.apk`。
