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

当前项目在 `src/main/resources/application.yml` 中配置的默认聊天路径为 `/v1/chat/completions`。对于聊天接口位于 `/openai/v1/chat/completions` 的代理，可以这样组合：

| 配置项 | 示例 |
| --- | --- |
| `OPENAI_BASE_URL` | `https://proxy.example.com/openai` |
| 聊天接口路径 | `/v1/chat/completions` |
| 最终请求地址 | `https://proxy.example.com/openai/v1/chat/completions` |

项目也支持通过 `OPENAI_CHAT_COMPLETIONS_PATH` 覆盖聊天接口路径。如果已将它设置为 `/openai/v1/chat/completions`，则基础地址应只填写 `https://proxy.example.com`。

**基础地址与接口路径需要配套配置，避免重复拼接 `/openai` 或 `/v1`。** 直连 OpenAI 时，基础地址可使用 `https://api.openai.com`，搭配默认的 `/v1/chat/completions` 路径。
