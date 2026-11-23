# OpenAI 环境变量与代理配置笔记

运行项目前，需要配置 **`OPENAI_API_KEY`** 和 **`OPENAI_BASE_URL`**。使用代理服务时，还需要确认基础地址包含正确的 API 路由前缀。

## 配置环境变量

### 通过终端 export 配置

在终端中执行以下命令，再从**同一个终端**启动项目：

```bash
export OPENAI_API_KEY='替换为实际的 API Key'
export OPENAI_BASE_URL='https://proxy.example.com/openai'
mvn spring-boot:run
```

**`export` 设置的变量由当前终端及其启动的子进程继承**，不会自动同步到已经打开的 IntelliJ。上面的地址为代理配置示例，请替换为实际地址。

### 通过 IntelliJ 配置

1. 打开 **Run → Edit Configurations**，进入项目的 **Run/Debug Configurations**。
2. 选择启动本项目的运行配置，在 **Environment Variables** 中手工添加以下变量；如果没有显示该字段，可在 **Modify options** 中启用：

   | 变量名 | 配置内容 |
   | --- | --- |
   | **`OPENAI_API_KEY`** | 实际的 API Key |
   | **`OPENAI_BASE_URL`** | OpenAI 或代理服务的 API 基础地址 |

3. 保存配置，**重新启动应用**使环境变量生效。

## 代理地址中的 API 路由前缀

**如果 `OPENAI_BASE_URL` 指向代理服务，可能需要在末尾添加 `/openai` 等后缀，以标明使用哪家的 API。** 具体前缀由代理服务的接口约定决定；缺少前缀可能导致请求路由错误，出现 **HTTP 404** 等调用错误。

当前项目通过 Spring AI `ChatClient` 和自定义 `ChatModel` 适配器调用 **Responses API**。在 `src/main/resources/application.yml` 中配置的默认路径为 `/v1/responses`。对于 Responses 接口位于 `/openai/v1/responses` 的代理，可以这样组合：

| 配置项 | 示例 |
| --- | --- |
| `OPENAI_BASE_URL` | `https://proxy.example.com/openai` |
| Responses 接口路径 | `/v1/responses` |
| 最终请求地址 | `https://proxy.example.com/openai/v1/responses` |

项目也支持通过 **`OPENAI_RESPONSES_PATH`** 覆盖接口路径。如果已将它设置为 `/openai/v1/responses`，则基础地址应只填写 `https://proxy.example.com`。旧的 `OPENAI_CHAT_COMPLETIONS_PATH` 不再使用。

**基础地址与接口路径需要配套配置，避免重复拼接 `/openai` 或 `/v1`。** 直连 OpenAI 时，基础地址可使用 `https://api.openai.com`，搭配默认的 `/v1/responses` 路径。

## Responses 请求与项目接口

项目对外仍提供 **`POST /api/v1/chat`**，请求为 `{"message":"你好"}`，响应为 `{"reply":"模型回答"}`。

适配器向上游发送 `model`、`input` 和 `temperature`，其中 `input` 为包含 `role` 和 `content` 的文本消息数组；收到响应后，从 `output` 中提取助手消息的 `output_text`，并处理拒绝回答的 `refusal` 内容。**当前适配器支持同步文本问答**，未实现流式、多模态或工具执行。

Responses 的请求和响应结构与 Chat Completions 不同，**不能仅修改旧接口的 URL 来完成迁移**。适配器会检查响应状态，遇到未完成的响应或缺少回答文本时抛出异常，避免将异常结果作为正常回答返回。

## 成语接龙

向 `POST /api/v1/idiomChain` 发送 JSON。接口沿用上述 OpenAI 配置，通过 Responses API 选出成语。

- `{"action":"init"}` 随机选取开局成语。请求可省略 `message`，传入时也会忽略。
- `{"action":"relay","message":"春华秋实"}` 接一个四字成语，例如 `{"message":"石沉大海"}`。输入成语末字和输出成语首字在各自词中的汉语拼音必须相同，忽略声调。

缺少或传入其他 `action`、接龙时输入为空或词库未收录，均返回 HTTP 400。词库中没有可接的成语时返回 HTTP 422。OpenAI 调用失败或返回的成语不符合要求时返回 HTTP 502。

词库从 [chinese-xinhua 的成语数据](https://github.com/pwxcoo/chinese-xinhua/blob/master/data/idiom.json) 提取四字词条和首尾字的无声调拼音，许可证随词库分发。
