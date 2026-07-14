# Build the phone-auth plugin into a Keycloak image (official base images).
#
# This is the we-meet.online copy — defaults to Keycloak 25 (the version running on
# id.we-meet.online). The upstream repo (Meeting/keycloak-phone-auth) stays on KC 26
# for the jusiai deployment; this copy is kept independent so the two don't collide.
#
#   docker build -t we-meet/keycloak:25.0-phone .
#   (override the line with --build-arg KC_VERSION=<ver> if needed)
ARG KC_REPO=quay.io/keycloak/keycloak
ARG KC_VERSION=25.0
ARG JDK_IMAGE=eclipse-temurin:17-jdk

# Keycloak image as a named stage so its libs / runtime can be referenced by name
# (avoids a variable image reference in `COPY --from=`).
FROM ${KC_REPO}:${KC_VERSION} AS kc

# Stage 1: compile the plugin using JDK + Keycloak libs
FROM ${JDK_IMAGE} AS builder

COPY --from=kc /opt/keycloak/lib /opt/keycloak/lib

WORKDIR /build
COPY src/ src/
COPY META-INF/ META-INF/

RUN mkdir -p classes/META-INF/services && \
    CP=$(find /opt/keycloak/lib -name "*.jar" | tr '\n' ':') && \
    javac --release 17 -d classes -cp "$CP" \
        src/we/meet/keycloak/PhoneAuthenticator.java \
        src/we/meet/keycloak/PhoneAuthenticatorFactory.java \
        src/we/meet/keycloak/SmsGatewayClient.java && \
    cp -r META-INF/. classes/META-INF/ && \
    jar cf phone-auth.jar -C classes .

# Stage 2: final Keycloak image (same base as the `kc` stage)
FROM kc

COPY --from=builder /build/phone-auth.jar /opt/keycloak/providers/
COPY theme/phone /opt/keycloak/themes/phone

RUN /opt/keycloak/bin/kc.sh build
