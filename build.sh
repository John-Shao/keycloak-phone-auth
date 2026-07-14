#!/bin/bash
# Build and push the custom Keycloak image with phone-auth plugin.
# Compilation happens inside the Docker multi-stage build (no JDK needed on host).
# Run on Server1: bash build.sh
set -e

IMAGE=jusi-cn-guangzhou.cr.volces.com/meet/keycloak:26.0.0-phone

echo "==> Building Docker image: $IMAGE"
docker build -t "$IMAGE" .

echo "==> Pushing to VCR..."
docker push "$IMAGE"

echo ""
echo "====================================================="
echo " Done!"
echo " Update keycloak-deploy.yaml:"
echo "   image: $IMAGE"
echo " Then: kubectl -n meet apply -f keycloak-deploy.yaml"
echo "====================================================="
