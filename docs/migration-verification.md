# 项目目录迁移验证

验证日期：2026-10-24（Asia/Shanghai）。本次仅迁移目录、修正路径和文档，不调整 UI、路由、业务逻辑或依赖版本。

## 最终目录

```text
RentPilot/
├── frontend/
│   ├── android/
│   ├── src/
│   ├── public/
│   ├── scripts/
│   ├── tests/
│   ├── package.json
│   ├── package-lock.json
│   └── Vite、TypeScript、Vitest、Playwright、Capacitor 配置
├── backend/.gitkeep
├── admin-web/.gitkeep
├── deliverables/
├── design-reference/
│   ├── stitch_rentpilot_ai_home_ui/
│   └── stitch_rentpilot_ai_home_ui.zip
├── docs/
├── README.md
└── .gitignore
```

现有根 `.idea/` 为用户本地 IDE 元数据，已保留并忽略提交；其引用原 ZIP 的链接已修正为新的设计资源位置。根目录没有新建 Git 仓库。

## 内容完整性

迁移前记录并在移动、离线资源导入、构建和同步后重复核对 **130 个文件的 SHA-256**，结果 **130 / 130 一致，0 缺失、0 内容变化**。覆盖范围：

- `frontend/src/` 的页面、样式、组件、状态与 Mock 数据，保留 9 个核心页面与 7 个交互状态。
- `frontend/public/` 本地设计素材，原有测试文件与前端依赖/构建配置。
- Android 原生 `app/src/main` 源码和资源（Capacitor 自动同步的 assets 不计入静态基线）。
- 全部原始 `deliverables/` 文件，包括 CSV 和数据说明。
- 完整 Stitch 解压参考和已移动的原始 ZIP。

基线见 [before.json](migration/before.json)，核对结果见 [integrity-result.json](migration/integrity-result.json)。从 `frontend/` 可执行：

```powershell
node scripts/migration-integrity.mjs verify ..
```

只改动了开发脚本的目录解析、文档和忽略规则。没有改动业务源码，没有升级包或重写 UI。

## 路径检查

- `import-stitch.mjs` 根据脚本位置定位仓库根设计资源/报告及前端 assets/src；在仓库根和 `frontend/` 两个工作目录均实际以 `--offline` 运行，通过并复用 15 张缓存原图，0 截图回退。
- `capture-ui.mjs` 从新位置执行，仍将截图写到根 `docs/screenshots/`，结果 `issues: []`；未生成 `frontend/docs/`。
- Vite、TypeScript、测试配置及 `Capacitor webDir: 'dist'` 的相对关系保持正确。
- Android 自动同步重建的三条 `../node_modules/@capacitor/...` 均解析至 `frontend/node_modules/` 的现存目录。SDK 本机路径继续由 `frontend/android/local.properties` 提供。
- 根 `.gitignore` 覆盖递归依赖、Web/Android 构建与缓存、本地环境配置、签名文件、测试产物和 Capacitor 自动生成文件；Wrapper、原生源码资源、锁文件、CSV、ZIP 和示例环境文件仍可提交。日志是本地调试产物，结论保存在本 Markdown 中。

## 已实际执行的前端验证

工作目录：`D:\CODE\RentPilot\frontend`。

| 命令 | 本次实际结果 |
| --- | --- |
| `npm test` | 退出 0，5 项状态测试通过 |
| `npm run test:e2e` | 退出 0，6 项真实 Chrome 流程通过 |
| `npm run android:sync` | 退出 0，TypeScript + Vite 生产构建及 Capacitor copy/update 均通过 |
| `node scripts/capture-ui.mjs` | 退出 0，`issues: []` |
| `node scripts/migration-integrity.mjs verify ..` | 退出 0，130 项哈希全部一致 |

现有浏览器测试继续覆盖聊天、偏好/版本、收藏与空态筛选、方案横滑、LifeSim/对比、键盘视口缩小，以及 320/360/402/480px 的 60 个页面/尺寸组合。未修改测试以适应新的业务行为。

同步日志确认 2 个原生插件：`@capacitor/app@8.1.2`、`@capacitor/keyboard@8.0.6`。生产资源已位于 `frontend/android/app/src/main/assets/public/`。

## Android 原生编译

本次从 `frontend/android/` 独立执行 `assembleDebug --no-daemon --console=plain`，临时使用已验证的 JDK 21 和本机 SDK。16:32:47–16:35:48，Wrapper 分发包 `gradle-8.14.3-all.zip` 下载仅到约 10%；达到 180 秒验证时限后，仅终止了本次构建的进程树。

这是**下载未完成而有界结束验证**，不是 Gradle 自行报告编译错误。受终止构建进程退出码为 1，尚未进入 AGP、SDK 检查或 Android 编译，**未生成 APK**。命令、环境和原始输出见 [Gradle 本次记录](migration/gradle-result.md)。没有改变目标 SDK 或全局 Java/Android 设置；后续仍需安装 Platform 36 并完成 Gradle/Maven 依赖下载。

## Android Studio 入口

**直接打开 `D:\CODE\RentPilot\frontend\android` 整个目录。**

需要 JDK 21、Android SDK Platform 36 和可下载 Gradle/Maven 依赖的网络。本机 SDK 路径为 `D:\chenxiaohao\Android\Sdk`，已知仅安装 Platform 37，不能代替工程要求的 Platform 36。当前没有真机或 AVD 的运行证据，浏览器通过不代表已完成 Android 安装验证。
