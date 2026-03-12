#!/bin/bash
# Build phone-auth.jar by compiling inside the running Keycloak pod.
# Run on Server1:  bash build.sh
set -e

NAMESPACE=meet
IMAGE=jusi-cn-guangzhou.cr.volces.com/meet/keycloak:26.0.0-phone

# ---------------------------------------------------------------------------
# 1. Find a running Keycloak pod
# ---------------------------------------------------------------------------
POD=$(kubectl -n "$NAMESPACE" get pods --no-headers \
      | grep -i keycloak | grep Running | head -1 | awk '{print $1}')
if [ -z "$POD" ]; then
    echo "ERROR: No running Keycloak pod found in namespace $NAMESPACE"
    exit 1
fi
echo "==> Using pod: $POD"

# ---------------------------------------------------------------------------
# 2. Set up build directory inside the pod
# ---------------------------------------------------------------------------
kubectl -n "$NAMESPACE" exec "$POD" -- bash -c \
    "rm -rf /tmp/phone-auth-build && \
     mkdir -p /tmp/phone-auth-build/src/com/jusiai/keycloak \
              /tmp/phone-auth-build/META-INF/services"

# ---------------------------------------------------------------------------
# 3. Copy Java source files
# ---------------------------------------------------------------------------
for f in PhoneAuthenticator.java PhoneAuthenticatorFactory.java SmsGatewayClient.java; do
    kubectl cp "src/com/jusiai/keycloak/$f" \
        "$POD:/tmp/phone-auth-build/src/com/jusiai/keycloak/$f" -n "$NAMESPACE"
done

# SPI registration
kubectl cp "META-INF/services/org.keycloak.authentication.AuthenticatorFactory" \
    "$POD:/tmp/phone-auth-build/META-INF/services/org.keycloak.authentication.AuthenticatorFactory" \
    -n "$NAMESPACE"

# ---------------------------------------------------------------------------
# 4. Compile inside the pod (Keycloak JARs are already on the classpath)
# ---------------------------------------------------------------------------
echo "==> Compiling..."
kubectl -n "$NAMESPACE" exec "$POD" -- bash -c '
  CP=$(find /opt/keycloak/lib -name "*.jar" 2>/dev/null | tr "\n" ":")
  cd /tmp/phone-auth-build
  mkdir -p classes
  javac --release 17 -d classes -cp "$CP" $(find src -name "*.java")
  cp -r META-INF classes/
'

# ---------------------------------------------------------------------------
# 5. Package JAR
# ---------------------------------------------------------------------------
echo "==> Packaging JAR..."
kubectl -n "$NAMESPACE" exec "$POD" -- bash -c \
    "cd /tmp/phone-auth-build/classes && jar cf /tmp/phone-auth.jar ."

# ---------------------------------------------------------------------------
# 6. Copy JAR back to local directory
# ---------------------------------------------------------------------------
kubectl cp "$POD:/tmp/phone-auth.jar" ./phone-auth.jar -n "$NAMESPACE"
echo "==> phone-auth.jar ready"

# ---------------------------------------------------------------------------
# 7. Build Docker image
# ---------------------------------------------------------------------------
echo "==> Building Docker image: $IMAGE"
docker build -t "$IMAGE" .

echo "==> Pushing to VCR..."
docker push "$IMAGE"

echo ""
echo "Done! Update keycloak-deploy.yaml to use image: $IMAGE"
echo "Then: kubectl -n meet apply -f keycloak-deploy.yaml"
