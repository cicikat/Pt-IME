# 语音输入与三小时草稿库接入设计

长按空格开始录音，松开停止并提交识别文本。语音结果进入三小时草稿库；复制粘贴不进入。库中仅将阿拉伯数字 `0-9`（含全角形式）替换为 `*`，中文数字保留。每条记录保存时间、应用包名、来源（键盘/语音）和脱敏文本。

模块：`voice/VoiceInputController` 管理 SpeechRecognizer、权限和 partial/final；`VoiceLocaleResolver` 按中英文模式选择语言；Room `DraftEntry` 保存 createdAt、appPackage、source、redactedText；`DraftRedactor` 只替换阿拉伯数字；`DraftSyncRepository` 按 id 增量 HTTPS 回传，默认关闭。后端交接协议见 [`docs/ime_draft_sync_api.md`](../docs/ime_draft_sync_api.md)。

敏感 inputType、锁屏、用户切换和无焦点编辑器不记录；复制/剪切/粘贴不记录。实际输入框仍接收完整识别文本，脱敏只用于库和回传。设置页开启回传并填写内网/组网地址，电脑端不得记录原文日志。

实施顺序：Room 表与脱敏测试；系统 SpeechRecognizer（保留 sherpa-onnx 替换接口）和 RECORD_AUDIO 权限；Service 将 partial 显示到 composing、final commit 并写库；设置页与增量 reporter；最后真机验证权限拒绝、语言切换、敏感字段和断网恢复。

验收：普通文本长按空格可中英文语音输入；库内阿拉伯数字为 `*`、中文数字不变；复制粘贴无记录；记录可按时间和应用查看；回传关闭时无网络请求。后端按接口文档实现 HTTPS、Bearer 鉴权和 `(device_id, id)` 幂等去重。
