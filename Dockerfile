# Custom Keycloak image with phone-auth plugin
FROM quay.io/keycloak/keycloak:26.0.0

# Plugin JAR (compiled by build.sh)
COPY phone-auth.jar /opt/keycloak/providers/

# Custom login theme
COPY theme/phone /opt/keycloak/themes/phone

# Pre-build Quarkus augmented application
RUN /opt/keycloak/bin/kc.sh build
