项目结构：


D:\workspace\Meeting\keycloak-phone-auth\
├── src\com\jusiai\keycloak\
│   ├── PhoneAuthenticatorFactory.java   # SPI 工厂，4个配置项
│   ├── PhoneAuthenticator.java          # 主逻辑（手机号输入→发OTP→验证）
│   └── SmsGatewayClient.java           # HTTP 调用 meet-backend /keycloak-sms/send/
├── META-INF\services\
│   └── org.keycloak.authentication.AuthenticatorFactory
├── theme\phone\login\
│   ├── theme.properties                 # parent=keycloak
│   ├── phone-input.ftl                  # 手机号输入页
│   ├── phone-otp.ftl                    # 验证码输入页（含重发按钮）
│   └── messages\messages_zh_CN.properties
├── Dockerfile                           # 不含 netzbegruenung JAR
└── build.sh                            # 在 pod 内编译 → 打包 → 构建推送镜像
部署步骤：

将整个目录 scp 到 k8s-master
在 Server1 执行 bash build.sh（会在 Keycloak pod 内编译 Java、打包 JAR、build+push 镜像）
更新 keycloak-deploy.yaml image 为 26.0.0-phone，kubectl apply
Keycloak Admin → meet realm → Authentication → 新建 flow，加入 Phone OTP Authentication 步骤
配置步骤参数：sms_gateway_url=https://meet.jusiai.com/keycloak-sms/send/，sms_gateway_token=<token>
Realm Settings → Themes → Login theme 选 phone
把新 flow 设为 Browser flow