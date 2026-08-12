# Clinical AI Safety Kit launch kit

This document contains truthful, reusable launch copy for maintainers. Replace no claims with estimated or invented numbers. Publish only after the referenced release is available on the default branch.

## Primary links

- Repository: https://github.com/mxx1111/clinical-ai-safety-kit
- First-user feedback: https://github.com/mxx1111/clinical-ai-safety-kit/issues/new?template=first-user-feedback.yml
- AI Agent tasks: https://github.com/mxx1111/clinical-ai-safety-kit/issues?q=is%3Aissue+is%3Aopen+label%3Aagent-task
- Contribution policy: https://github.com/mxx1111/clinical-ai-safety-kit/blob/main/AI_CONTRIBUTION_POLICY.md

## English — GitHub and developer communities

### Short

Clinical AI Safety Kit is an open-source, deterministic safety evaluation gateway for clinical and medical AI applications.

The current toolkit checks generated text for missed emergency escalation, unsupported medication dosage, privacy echo, and unjustified diagnostic certainty. It also provides privacy-safe FHIR® R4 Bundle structure checks, reproducible synthetic evidence, a CycloneDX SBOM, and an optional fail-closed MONAI Deploy text gate.

The project is AI-written and human-governed: AI agents implement and independently review contributions, while humans retain legal, risk, and final-merge responsibility.

This is engineering safety infrastructure, not a medical device or a clinical-validity claim. We are looking for first users willing to test with synthetic or properly de-identified data, report gaps, and propose AI Agent tasks.

Repository: https://github.com/mxx1111/clinical-ai-safety-kit

### Social

Launching Clinical AI Safety Kit: an open-source, deterministic safety gateway for medical AI output and FHIR® R4 structure checks.

- ✓ explainable rule findings
- ✓ reproducible synthetic benchmark
- ✓ CycloneDX SBOM
- ✓ optional fail-closed MONAI Deploy text gate
- ✓ AI-written, human-governed contributions

Not a medical device. No clinical-validity claim. First-user feedback and AI Agent contributions are welcome:
https://github.com/mxx1111/clinical-ai-safety-kit

## 中文 — GitHub 与开发者社区

### 标准版

Clinical AI Safety Kit 是一个面向临床与医疗 AI 应用的开源确定性安全评测网关。

当前工具箱可以检查生成文本中的紧急情况漏报、无来源药物剂量、敏感标识回显和过度确定诊断，同时提供注重隐私的 FHIR® R4 Bundle 结构校验、可复现的合成评测证据、CycloneDX SBOM，以及一个可选的默认阻断型 MONAI Deploy 文本安全门。

项目采用“AI 编写、人类治理”模式：AI Agent 负责编码和独立审查，人类承担法律、风险决策和最终合并责任。

这是工程安全基础设施，不是医疗器械，也不代表经过临床有效性验证。我们正在寻找第一批愿意使用合成数据或经过合规脱敏数据进行测试的体验者，也欢迎提出缺口和 AI Agent 任务。

项目地址：https://github.com/mxx1111/clinical-ai-safety-kit

### 社交平台短版

Clinical AI Safety Kit 开源了：一个面向医疗 AI 输出和 FHIR® R4 结构检查的确定性安全网关。

- ✓ 可解释规则与证据
- ✓ 可复现合成评测基准
- ✓ CycloneDX 依赖清单
- ✓ 可选的 MONAI Deploy 默认阻断型文本安全门
- ✓ AI 编写、人类治理的开源协作模式

项目不是医疗器械，也不主张临床有效性。欢迎第一批体验者和 AI Agent 贡献者：
https://github.com/mxx1111/clinical-ai-safety-kit

## Suggested rollout

1. Publish the GitHub v0.2 release and verify the evidence artifact.
2. Update the repository social preview with [`assets/social-preview.jpg`](../assets/social-preview.jpg).
3. Post the standard Chinese copy to the maintainer's existing Chinese developer channels.
4. Post the English copy to relevant open-source and medical-AI developer communities that permit project announcements.
5. Reply to every substantive first-user report, but never request patient data or production logs.
6. Review the privacy-safe repository metrics after 7 and 30 days; report zeros as zeros.

## Claims checklist

Before each post, confirm:

- no user, download, accuracy, or adoption number is stated unless it comes from a reproducible public metric snapshot;
- no clinical validation, treatment quality, production readiness, certification, or regulatory clearance is implied;
- no affiliation or endorsement by HL7, Project MONAI, NVIDIA, or another organization is implied;
- no patient data, PHI, production log, secret, private prompt, or identifiable example is included;
- the linked release and documentation exist on the default branch.
