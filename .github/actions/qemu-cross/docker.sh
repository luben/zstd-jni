#!/bin/bash
set -e

docker run --rm \
    --platform ${PLATFORM_NAME} \
    --name qemu-cross-${PLATFORM_NAME} \
    --mount type=bind,source=${HOST_WORKSPACE_DIR},target=/github_workspace \
    --workdir /github_workspace \
    ${PLATFORM_NAME}/ubuntu:20.04 ./.github/actions/qemu-cross/build.sh
