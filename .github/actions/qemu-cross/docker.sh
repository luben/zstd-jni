#!/bin/bash
set -e

SECURITY_OPTS=()
if [ "${PLATFORM_NAME}" = "linux/386" ]; then
    # Docker 29.4.2, which the ubuntu-26.04 runner image ships, blocks
    # socketcall(2) in its default seccomp profile. 32-bit glibc makes every
    # socket call through it, so the guest cannot even resolve host names.
    # Use the profile that Docker's release notes give as the workaround
    # (https://docs.docker.com/engine/release-notes/29/): it allows
    # socketcall, still blocks AF_ALG sockets, and is the same profile that
    # Docker 29.4.3 uses by default. Remove once the runner image ships
    # Docker 29.4.3 or later (actions/runner-images#14790).
    SECCOMP_PROFILE=$(mktemp)
    # moby/profiles seccomp/v0.2.1
    curl -fsSL -o "${SECCOMP_PROFILE}" \
        https://raw.githubusercontent.com/moby/profiles/5ad5f40ecde90d78e4a7c861c003fe92747d5518/seccomp/default.json
    SECURITY_OPTS=(--security-opt "seccomp=${SECCOMP_PROFILE}")
fi

docker run --rm \
    --platform ${PLATFORM_NAME} \
    --name qemu-cross-${PLATFORM_NAME//\//-} \
    "${SECURITY_OPTS[@]}" \
    --mount type=bind,source=${HOST_WORKSPACE_DIR},target=/github_workspace \
    --workdir /github_workspace \
    ${IMAGE_NAME} ./.github/actions/qemu-cross/${BUILD_SCRIPT}
