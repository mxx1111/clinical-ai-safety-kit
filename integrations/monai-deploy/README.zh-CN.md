# MONAI Deploy 文本安全门

[English](README.md) · [简体中文](README.zh-CN.md)

这个可选示例将 Clinical AI Safety Kit 放在“叙述性文本生成 Operator”和 MONAI Deploy App SDK 的 `DICOMTextSRWriterOperator` 等下游发布节点之间。

它**不评测**图像张量、分割结果、模型准确率或临床有效性，只评测发送给 Clinical AI Safety Kit HTTP API 的提示/上下文和生成文本。

## 数据流

```text
MONAI 图像推理 → 文本报告生成 → 安全门 → DICOM Text SR Writer
                              ├─ PASS：转发文本
                              └─ BLOCK/错误：停止发布
```

安全门默认阻断：`WARN`、`BLOCK`、响应格式错误、超时或服务不可用时都不会输出 `safe_text`。

## 文件

- [`safety_gate_client.py`](safety_gate_client.py)：无第三方依赖的 HTTP 客户端，限制输入长度、超时、响应大小并严格校验响应结构。
- [`safety_gate_operator.py`](safety_gate_operator.py)：MONAI Deploy `Operator`，提供 `prompt`、`response` 输入和 `safe_text`、`safety_decision` 输出。
- [`tests/`](tests)：使用本机假服务和 MONAI API 桩的合成测试，不需要 GPU、模型、DICOM 文件或安装 MONAI。

## 启动安全服务

在仓库根目录执行：

```bash
./mvnw spring-boot:run
```

示例默认连接 `http://127.0.0.1:8080`。除非显式配置 `allow_remote_http=True`，否则明文 HTTP 只能使用本机回环地址。多容器部署应使用 HTTPS 和经过认证的网络控制；禁止向不可信端点发送真实患者数据、PHI、DICOM 标签或任何标识符。

## 接入 MONAI Deploy 应用

上游文本生成 Operator 必须输出经过脱敏的上下文和生成报告，并将安全门放在 DICOM Writer 之前：

```python
from pathlib import Path

from monai.deploy.operators.dicom_text_sr_writer_operator import DICOMTextSRWriterOperator

from safety_gate_operator import ClinicalAiSafetyGateOperator


safety_gate = ClinicalAiSafetyGateOperator(
    self,
    base_url="https://clinical-ai-safety.internal",
    name="clinical_ai_safety_gate",
)
dicom_sr_writer = DICOMTextSRWriterOperator(
    self,
    output_folder=Path(app_context.output_path),
    model_info=model_info,
    copy_tags=True,
    name="dicom_text_sr_writer",
)

self.add_flow(
    narrative_generator,
    safety_gate,
    {("prompt_text", "prompt"), ("report_text", "response")},
)
self.add_flow(
    series_selector,
    dicom_sr_writer,
    {("study_selected_series_list", "study_selected_series_list")},
)
self.add_flow(safety_gate, dicom_sr_writer, {("safe_text", "text")})
```

`narrative_generator` 的具体输出名由应用决定。`series_selector` 代表应用中已有的 `DICOMSeriesSelectorOperator`；它的 DICOM 输出只应直接进入 Writer，绝不能连接到安全门。如果安全门阻断，下游 Writer 不会收到文本。如果工作流需要审计记录，可将 `safety_decision` 输出连接到只保存脱敏决策的审计 Operator。

## 测试

```bash
python3 -m unittest discover -s integrations/monai-deploy/tests -p 'test_*.py'
```

适配器面向 MONAI Deploy App SDK 3.0.0 的 `Fragment`、`Operator` 和 `OperatorSpec` 接口，并于 2026-08-12 对照了上游 `main` 分支公开 API。由于当前 SDK 的安装依赖 NVIDIA Holoscan/CUDA 运行环境，仓库 CI 使用 API 桩验证适配器逻辑。投入使用前，必须在自己的受支持 MONAI Deploy 环境中验证完整打包应用。

## 边界与归属

- 这是集成示例，不是临床工作流或医疗器械。
- 禁止向它提交真实患者数据或标识符。
- PASS 不代表获准临床使用，也不能证明报告正确。
- MONAI 是 MONAI Consortium 的项目；Clinical AI Safety Kit 是独立项目，与 Project MONAI 或 NVIDIA 没有关联，也未获得其背书。
- 本示例未包含 MONAI 源代码、模型、图像、DICOM 资产或数据集。
