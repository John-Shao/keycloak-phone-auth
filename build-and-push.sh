#!/usr/bin/env bash
# build-and-push.sh — 构建 phone-auth 定制 Keycloak 镜像并推火山 CR。
#
# 镜像 = 原版 Keycloak 25 + 本仓库的 phone-auth 认证器（手机号 OTP / 扫码 / 统一
# 登录页）+ we-meet 登录页主题（五语）。编译在 Docker 多阶段构建里完成，宿主机
# 不需要 JDK / Maven。
#
# ⚠️ 在哪里跑：**工程师 PC**（装了 docker 即可）。
#    不要在 aliyun-zlm 那台 2C2G ECS 上构建 —— `kc.sh build` 会 OOM（exit 137）；
#    那台只负责 docker pull。见 we-meet 仓库 deploy/aliyun/keycloak/compose.yaml。
#
# 部署目标：aliyun-zlm 上的 **docker compose**（不是 k8s）。compose.yaml 的
#    keycloak.image 指向本脚本产出的镜像，两处 tag 必须逐字符一致。
#
# 用法：
#   bash build-and-push.sh                        # 构建 + 推 25.0-phone
#   bash build-and-push.sh --build-only           # 只构建，不推
#   TAG=25.0-phone-test bash build-and-push.sh    # 换 tag（灰度 / 回滚验证）
#   REGISTRY_USER=<火山CR用户> REGISTRY_PASS=<密码> bash build-and-push.sh   # 非交互登录
#
# 注：keycloak 的构建**不在** we-meet 仓库的 deploy/aliyun/build-and-push.sh 里 ——
#    它源码独立、版本与发布节奏独立、部署方式也不同（compose vs k8s），故自成一路。

set -euo pipefail

REGISTRY="${REGISTRY:-jusi-cn-guangzhou.cr.volces.com}"
NS="${NS:-we-meet}"
# KC_VERSION 需与 Dockerfile 的 `ARG KC_VERSION` 默认值一致；TAG 需与 we-meet 仓库
# deploy/aliyun/keycloak/compose.yaml 的 keycloak.image 一致。
KC_VERSION="${KC_VERSION:-25.0}"
TAG="${TAG:-${KC_VERSION}-phone}"
IMAGE="${REGISTRY}/${NS}/keycloak:${TAG}"

DO_PUSH=1
case "${1:-}" in
  --build-only) DO_PUSH=0 ;;
  "") ;;
  *) echo "未知选项 '$1'（只支持 --build-only）"; exit 1 ;;
esac

cd "$(dirname "${BASH_SOURCE[0]}")"

command -v docker >/dev/null || { echo "✗ 缺 docker"; exit 1; }

echo "==> Building $IMAGE"
echo "    Keycloak ${KC_VERSION} + phone-auth 认证器 + we-meet 登录页主题"
docker build --build-arg "KC_VERSION=${KC_VERSION}" -t "$IMAGE" .

if [[ $DO_PUSH == 0 ]]; then
  echo
  echo "==> 只构建完成（--build-only）。推送：bash build-and-push.sh"
  exit 0
fi

# 显式给了凭据就非交互登录；否则沿用宿主已有的 docker login 会话。
if [[ -n "${REGISTRY_USER:-}" && -n "${REGISTRY_PASS:-}" ]]; then
  echo "==> Logging in to $REGISTRY"
  echo "$REGISTRY_PASS" | docker login -u "$REGISTRY_USER" --password-stdin "$REGISTRY"
fi

echo "==> Pushing $IMAGE"
docker push "$IMAGE"

cat <<EOF

=====================================================
 DONE: $IMAGE

 部署（aliyun-zlm，docker compose）：
   cd ~/we-meet && git pull
   cd deploy/aliyun/keycloak
   docker compose pull keycloak && docker compose up -d keycloak

 若改了 TAG，记得同步 we-meet 仓库 deploy/aliyun/keycloak/compose.yaml
 的 keycloak.image，否则拉的还是旧镜像。

 回滚：compose.yaml 改回 quay.io/keycloak/keycloak:${KC_VERSION}
 （provider / 主题不影响原有流程）。
=====================================================
EOF
