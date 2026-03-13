# Stage 1: compile the plugin using JDK + Keycloak libs
FROM docker.xuanyuan.run/library/eclipse-temurin:17-jdk AS builder

COPY --from=docker.xuanyuan.run/keycloak/keycloak:26.0.0 /opt/keycloak/lib /opt/keycloak/lib

WORKDIR /build
COPY src/ src/
COPY META-INF/ META-INF/

RUN mkdir -p classes/META-INF/services && \
    CP=$(find /opt/keycloak/lib -name "*.jar" | tr '\n' ':') && \
    javac --release 17 -d classes -cp "$CP" \
        src/com/jusiai/keycloak/PhoneAuthenticator.java \
        src/com/jusiai/keycloak/PhoneAuthenticatorFactory.java \
        src/com/jusiai/keycloak/SmsGatewayClient.java && \
    cp -r META-INF/. classes/META-INF/ && \
    jar cf phone-auth.jar -C classes .

# Stage 2: final Keycloak image
FROM docker.xuanyuan.run/keycloak/keycloak:26.0.0

COPY --from=builder /build/phone-auth.jar /opt/keycloak/providers/
COPY theme/phone /opt/keycloak/themes/phone

RUN /opt/keycloak/bin/kc.sh build
