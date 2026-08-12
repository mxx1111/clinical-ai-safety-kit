# Privacy-safe public project metrics / 隐私安全的公开项目指标

**English** · [简体中文](#简体中文)

## English

Clinical AI Safety Kit measures repository health from public, repository-level aggregate counts. It does not add analytics code to the application or documentation site.

Run a local snapshot:

```bash
GITHUB_TOKEN=... python3 scripts/collect_public_metrics.py \
  --repository mxx1111/clinical-ai-safety-kit \
  --output target/public-metrics.json \
  --as-of 2026-08-12T08:00:00Z
```

`GITHUB_TOKEN` is optional for public repositories but recommended to avoid the unauthenticated API rate limit. The token is read only from the environment and is never written to the report. The scheduled workflow uses the repository's short-lived token with read-only permissions and uploads `public-metrics.json` as a 30-day Actions artifact.

### Metric definitions

| Group | Metric | Exact formula |
| --- | --- | --- |
| Reach | `stars`, `forks`, `subscribers` | Current public counts returned by the GitHub repository API. Subscribers are users explicitly watching all repository activity, not page visitors. |
| Activation | `publishedReleases` | Count of non-draft releases published no later than `asOf`. |
| Activation | `releaseAssetDownloads` | Sum of public download counters for assets attached to included releases. Git clones and source archive downloads are not included. |
| Activation | `firstUserFeedbackSubmissions` | Open plus closed Issues carrying the `feedback` label. |
| Feedback | `open`, `closed`, `total` | Aggregate state counts for Issues carrying the `feedback` label; pull requests are excluded. |
| Contribution | `pullRequestsOpened` | Pull requests created no later than `asOf`. |
| Contribution | `pullRequestsMerged` | Those pull requests with `merged_at` no later than `asOf`. |
| Contribution | `pullRequestMergeRatePercent` | `merged / opened × 100`, rounded to two decimals; `null` when no pull request exists. |
| Contribution | `agentTasksOpen`, `agentTasksClosed` | Aggregate state counts for Issues carrying the `agent-task` label. |
| Quality | `completedWorkflowRuns` | Completed Actions runs created in the inclusive 30-day window ending at `asOf`. |
| Quality | `successfulWorkflowRuns` | Included completed runs whose conclusion is `success`. |
| Quality | `workflowSuccessRatePercent` | `successful / completed × 100`, rounded to two decimals; `null` when no run completed. |

`asOf` records collection time; it is not a historical reconstruction guarantee. Issue state and public counters reflect the API response at collection time. A release download is a distribution signal, not proof of installation, use, benefit, or clinical validation. Small samples must be reported as counts alongside percentages.

GitHub limits a filtered workflow-run query to 1,000 results. The collector validates the API's declared total and fails without writing a snapshot when the 30-day window exceeds that limit or pagination is inconsistent; it never silently reports a truncated quality metric.

### Privacy and non-goals

The collector emits aggregate counts plus fixed schema, collection-time, window, and privacy metadata only. It never writes the repository owner, release tags, account names, actor IDs, Issue or PR titles and bodies, comments, IP addresses, page views, clones, cookies, tracking pixels, patient data, PHI, prompts, logs, or secrets. It adds no external telemetry service or analytics SDK.

Metrics must not be used to claim clinical effectiveness, production readiness, safety certification, endorsement, or a number of real-world users. Maintainers should use them to notice whether people can find the repository, complete a first feedback action, contribute through the governed workflow, and receive reliable CI evidence.

## 简体中文

Clinical AI Safety Kit 仅使用 GitHub 上公开的仓库级汇总数量观察项目健康度，不会在应用或文档站点中加入任何统计代码。

本地生成快照：

```bash
GITHUB_TOKEN=... python3 scripts/collect_public_metrics.py \
  --repository mxx1111/clinical-ai-safety-kit \
  --output target/public-metrics.json \
  --as-of 2026-08-12T08:00:00Z
```

公开仓库可以不设置 `GITHUB_TOKEN`，但容易受到未认证 API 速率限制。令牌只从环境变量读取，绝不会写入报告。定时工作流使用仓库提供的短期只读令牌，并把 `public-metrics.json` 保存为保留 30 天的 Actions 构建附件。

### 指标定义

| 分组 | 指标 | 精确口径 |
| --- | --- | --- |
| 触达 | `stars`、`forks`、`subscribers` | GitHub 仓库 API 返回的当前公开总数。`subscribers` 是主动关注全部仓库动态的人数，不是页面访客数。 |
| 激活 | `publishedReleases` | 在 `asOf` 之前发布且不是草稿的 Release 数量。 |
| 激活 | `releaseAssetDownloads` | 纳入统计的 Release 附件公开下载数之和，不包含 Git clone 或源码压缩包下载。 |
| 激活 | `firstUserFeedbackSubmissions` | 带 `feedback` 标签的开放与已关闭 Issue 总数。 |
| 反馈 | `open`、`closed`、`total` | 带 `feedback` 标签的 Issue 状态汇总，不包含 PR。 |
| 贡献 | `pullRequestsOpened` | 创建时间不晚于 `asOf` 的 PR 数量。 |
| 贡献 | `pullRequestsMerged` | 上述 PR 中，合并时间不晚于 `asOf` 的数量。 |
| 贡献 | `pullRequestMergeRatePercent` | `已合并 / 已创建 × 100`，保留两位小数；没有 PR 时为 `null`。 |
| 贡献 | `agentTasksOpen`、`agentTasksClosed` | 带 `agent-task` 标签的 Issue 状态汇总。 |
| 质量 | `completedWorkflowRuns` | 以 `asOf` 结束、含首尾的 30 天窗口内创建且已完成的 Actions 运行数。 |
| 质量 | `successfulWorkflowRuns` | 纳入统计且结论为 `success` 的运行数。 |
| 质量 | `workflowSuccessRatePercent` | `成功 / 已完成 × 100`，保留两位小数；没有已完成运行时为 `null`。 |

`asOf` 表示采集时间，不保证能重建历史时点。Issue 状态与公开计数以采集时 API 的响应为准。Release 附件下载只能说明分发行为，不能证明安装、实际使用、收益或临床有效性。样本很少时，百分比必须与原始数量一起展示。

GitHub 对筛选后的工作流运行查询最多返回 1,000 条。采集器会校验 API 声明的总数；当 30 天窗口超过该上限或分页结果不一致时，它会失败且不写入快照，绝不会静默输出被截断的质量指标。

### 隐私边界与非目标

采集器只输出汇总计数，以及固定的格式版本、采集时间、时间窗口和隐私声明元数据；不写入仓库所有者、Release 标签、账号名、用户 ID、Issue/PR 标题与正文、评论、IP 地址、页面浏览量、Clone 记录、Cookie、追踪像素、患者数据、PHI、Prompt、日志或秘密；项目也不接入外部遥测服务或统计 SDK。

这些指标不能用于宣称临床有效、生产就绪、安全认证、第三方背书或真实用户数量。维护者只应用它们判断：项目能否被发现、体验者能否完成首次反馈、贡献者能否走完治理流程，以及 CI 是否稳定提供证据。
