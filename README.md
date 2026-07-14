# keycloak-phone-auth

Keycloak 26 手机号 + 短信验证码（OTP）无密码登录插件。用户输入手机号即可登录，若账号不存在则自动创建。短信通过 meet-backend 短信网关下发。

## 特性

- **无密码登录**：手机号 + 短信 OTP，无需密码。
- **自动建号**：首次登录的手机号自动创建 Keycloak 用户，并写入 `phoneNumber` 属性。
- **可配置**：OTP 位数、有效期、最大重试次数、网关地址与令牌均可在 Admin Console 中配置。
- **重发验证码**：验证码页支持重新发送。
- **中文主题**：内置 `phone` 登录主题（手机号输入页 + 验证码输入页）。
- **零外部依赖**：仅使用 JDK 标准库，编译在 Docker 多阶段构建内完成，无需宿主机安装 JDK。

## 项目结构

```
keycloak-phone-auth/
├── src/we/meet/keycloak/
│   ├── PhoneAuthenticatorFactory.java   # SPI 工厂，注册 5 个配置项
│   ├── PhoneAuthenticator.java          # 主逻辑：手机号输入 → 发送 OTP → 校验
│   └── SmsGatewayClient.java            # HTTP 客户端，调用 meet-backend 短信网关
├── META-INF/services/
│   └── org.keycloak.authentication.AuthenticatorFactory   # SPI 注册
├── theme/phone/login/
│   ├── theme.properties                 # parent=keycloak
│   ├── phone-input.ftl                  # 手机号输入页
│   ├── phone-otp.ftl                    # 验证码输入页（含重发按钮）
│   └── messages/messages_zh_CN.properties
├── Dockerfile                           # 多阶段构建：编译插件 → 打包进 Keycloak 镜像
└── build.sh                             # 构建并推送镜像
```

## 认证流程

1. `authenticate()` → 渲染 `phone-input.ftl`（手机号输入页）。
2. `action()` → 校验手机号格式（`^1[3-9]\d{9}$`），查找或创建用户，生成并发送 OTP，渲染 `phone-otp.ftl`。
3. `action()` → 校验 OTP：成功则登录，失败则提示剩余次数；超过最大重试或过期则中止。

用户按 `phoneNumber` 属性检索，因此即使用户名被修改后仍可正常登录。重发验证码通过 `phone-otp.ftl` 提交 `action=resend` 触发，会重新生成并下发 OTP。

## 配置项

在 Keycloak Admin Console 的 Authentication 步骤中配置：

| 参数 | 说明 | 默认值 |
| --- | --- | --- |
| `sms_gateway_url` | 短信网关 POST 端点，如 `https://meet.we-meet.online/keycloak-sms/send/` | — |
| `sms_gateway_token` | 网关 Bearer 令牌（`KEYCLOAK_SMS_GATEWAY_TOKEN`），留空则不鉴权 | — |
| `otp_length` | OTP 位数（4–8） | `6` |
| `otp_expiry_seconds` | OTP 有效期（秒） | `300` |
| `otp_max_attempts` | 允许的错误尝试次数，超出后中止会话 | `3` |

## 短信网关契约

`SmsGatewayClient` 向网关 POST 如下 JSON：

```json
{ "msisdn": "138xxxxxxxx", "message": "您的验证码是：123456，5分钟内有效，请勿泄露。" }
```

网关（`keycloak_sms.py`）以正则从 `message` 中提取验证码，再转发至火山引擎 SendSms 按配置模板下发。网关返回 HTTP 2xx 视为发送成功。

## 构建与部署

### 1. 构建并推送镜像

在 Server1（可访问 Docker 与镜像仓库的机器）执行：

```bash
bash build.sh
```

脚本通过 Docker 多阶段构建完成编译、打包并推送镜像：

```
jusi-cn-guangzhou.cr.volces.com/meet/keycloak:26.0.0-phone
```

> 编译在构建镜像的 `builder` 阶段内进行，宿主机无需安装 JDK。

### 2. 更新部署

将 `keycloak-deploy.yaml` 的镜像更新为上述标签并应用：

```bash
kubectl -n meet apply -f keycloak-deploy.yaml
```

### 3. 配置 Keycloak

1. **Admin Console → `meet` realm → Authentication**：新建 flow，加入 **Phone OTP Authentication** 步骤。
2. 配置步骤参数（见[配置项](#配置项)），至少设置 `sms_gateway_url` 与 `sms_gateway_token`。
3. **Realm Settings → Themes → Login theme** 选择 `phone`。
4. 将新建的 flow 设为 **Browser flow**。

## 环境要求

- Keycloak 26.0.0
- Java 17（仅构建阶段镜像内需要）
- 可用的 meet-backend 短信网关

## License

[MIT](LICENSE) © 2026 John-Shao
