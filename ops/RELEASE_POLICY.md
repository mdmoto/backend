# 📜 Lilishop 发布节奏与变更纪律 (Release & Change Policy)

本规范用于保障 Lilishop (Boot 3) 在生产环境的高可用性和可维护性，防止由于不规范的操作引入回归和稳定性风险。

## 1. 发布节奏与要求 (Release Rhythm)

所有生产环境的发布必须遵循以下步骤：

### 1.1 绝对基线与 CI 门禁
1. **任何合入主干 (master/main) 的代码，必须通过 GitHub Actions CI 检查。**
2. **禁止本地编译直传**：任何编译产物应当由自动化流水线产出，禁止将个人电脑上编译的未校验产物直接推送到生产分支/服务器。
3. **强制自动化验收**：服务器上所有服务的更新部署，必须跑通一次 `bash ops/verify-pipeline.sh`，并在发布完成前强制执行 `ops/external-probe.sh` 的全链路健康检查。

### 1.2 Release Notes 与 Tag 规范
每次大版本或核心特性发布，必须强制打 Tag 并编写简要 Release Note，结构包含：
- **Tag 命名**: `prod-backend-YYYYMMDD` 或 `prod-uniapp-YYYYMMDD`
- **内容要求**: 包含了哪些特性、修复了什么问题、是否需要特殊的数据库 Schema /环境变量改动。

## 2. 线上灰度与变更纪律 (Change Discipline)

### 2.1 严禁手动修改 Nginx 
**线上 Nginx 灰度切流和回滚只能按照 [ops/release-sop.md](./release-sop.md) 中规定的脚本进行。**
绝对禁止人为进入 `/etc/nginx/sites-available/maollar` 凭记忆修改端口或路由规则，以杜绝因拼写错误导致的全站 502 (白屏)。

### 2.2 生产单一事实来源 (Single Source of Truth)
部署脚本的源头永远在仓库的 `ops/` 目录下。
Azure 根目录下的脚本仅作为软代理 (Wrapper) 存在。业务发布如需修改部署逻辑，必须提交 PR 审查后合并进版本库，再在服务器上拉取执行。

## 3. 下一步技术债清理 (Tech Debt Priority)

我们的系统在技术实现上处于“跑通且健康”阶段，但仍有一些历史债务亟待解决，必须纳入接下来的高 ROI 小目标：

1. **消除系统残余循环依赖 (Circular Dependencies)**
   - 目标：将后台的 `SPRING_MAIN_ALLOW_CIRCULAR_REFERENCES=true` 改回框架默认的 `false`。
   - 措施：排查 `setup-persistence.sh` 中该环境变量遮蔽的具体循环依赖链路进行代码级重构拆解。
2. **逐步缩减测试盲区 (Test Coverage)**
   - 现状：由于历史原因，部分模块存在打包构建时“-DskipTests”的情况。
   - 规划：从核心交易链路 (Cart / Order / Payment) 开始，逐步恢复 3-5 个端到端的关键业务用例，减少测试护栏中的盲区。

---
*坚守此纪律，我们的系统才能在高速迭代下稳如磐石。*
