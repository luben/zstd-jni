#!/bin/bash
#
# Checks that the packaged jars are working Multi-Release JARs: on Java 22 every
# class under META-INF/versions/22 must be the one that name resolves to, while
# Java 8 still gets the base copy from the jar root.
#
# `javap --multi-release <n>` applies the JDK's own resolution, manifest included,
# so this catches a lost `Multi-Release: true`, versioned classes at the wrong
# path, and a jar packaged on a JDK too old to build them.
#
# Every classified jar is checked, not just the main one: build.sbt has to set the
# attribute and the ffmApiCheck ordering per config, and those are what actually
# get published.
#
# The discriminator is the class file major version: build.sbt compiles the base
# tree --release 8 and the versioned tree --release 22.
#
# Usage: check-multi-release-jar.sh [jar...]
#        defaults to every target/zstd-jni-$(cat version)*.jar that should be one

set -euo pipefail

# FFM_RELEASE must match ffmRelease in build.sbt. Major version is release + 44.
readonly FFM_RELEASE=22
readonly FFM_MAJOR=66
readonly BASE_RELEASE=8
readonly BASE_MAJOR=52

readonly prefix=META-INF/versions/$FFM_RELEASE/

jars=("$@")
if [ ${#jars[@]} -eq 0 ]; then
    # The sources jar carries META-INF/versions/22/*.java, not classes, and the
    # javadoc jar carries neither; neither is a multi-release artifact.
    shopt -s nullglob
    for f in "target/zstd-jni-$(cat version)"*.jar; do
        case $f in *-sources.jar | *-javadoc.jar) continue ;; esac
        jars+=("$f")
    done
    shopt -u nullglob
fi

if [ ${#jars[@]} -eq 0 ]; then
    echo "no jars to check; run \`sbt package packageClassified\` first" >&2
    exit 1
fi

# Echoes the class file major version of class $3 as jar $1 resolves it for
# release $2.
major_at_release() {
    local jar=$1 release=$2 fqcn=$3 output major
    if ! output=$(javap -v --multi-release "$release" -cp "$jar" "$fqcn" 2>&1); then
        echo "javap could not read $fqcn at release $release:" >&2
        echo "$output" >&2
        return 1
    fi
    major=$(sed -n 's/^ *major version: *\([0-9][0-9]*\).*/\1/p' <<<"$output" | head -1)
    if [ -z "$major" ]; then
        echo "no major version in javap output for $fqcn at release $release:" >&2
        echo "$output" >&2
        return 1
    fi
    echo "$major"
}

# Checks one jar, non-zero if anything about it is wrong.
check_jar() {
    local jar=$1 listing versioned entry path fqcn got base
    local bad=0 versionedCount=0 baseCount=0

    listing=$(jar tf "$jar")
    # module-info is skipped: javap cannot be asked for it by class name.
    versioned=$(grep -E "^$prefix.+\.class$" <<<"$listing" \
                | grep -v '/module-info\.class$' | sort || true)

    if [ -z "$versioned" ]; then
        echo "::error::$jar has no $prefix*.class entries; it was most likely" \
             "packaged on a JDK below $FFM_RELEASE" >&2
        return 1
    fi

    while IFS= read -r entry; do
        path=${entry#"$prefix"}
        fqcn=${path%.class}
        fqcn=${fqcn//\//.}

        if ! got=$(major_at_release "$jar" "$FFM_RELEASE" "$fqcn"); then
            bad=1
            continue
        fi
        if [ "$got" -ne "$FFM_MAJOR" ]; then
            echo "::error::$jar: $fqcn at release $FFM_RELEASE resolved to major" \
                 "version $got, expected $FFM_MAJOR - the copy in $prefix" >&2
            bad=1
            continue
        fi
        versionedCount=$((versionedCount + 1))

        # A versioned-only helper has no base copy to fall back to.
        grep -qxF "$path" <<<"$listing" || continue

        if ! base=$(major_at_release "$jar" "$BASE_RELEASE" "$fqcn"); then
            bad=1
            continue
        fi
        if [ "$base" -ne "$BASE_MAJOR" ]; then
            echo "::error::$jar: $fqcn at release $BASE_RELEASE resolved to major" \
                 "version $base, expected $BASE_MAJOR - the base copy from the jar root" >&2
            bad=1
            continue
        fi
        baseCount=$((baseCount + 1))
    done <<<"$versioned"

    [ $bad -eq 0 ] || return 1
    echo "  $(basename "$jar"): $versionedCount versioned at $FFM_RELEASE," \
         "$baseCount falling back at $BASE_RELEASE"
}

echo "checking ${#jars[@]} jar(s)"
status=0
for jar in "${jars[@]}"; do
    check_jar "$jar" || status=1
done

if [ $status -eq 0 ]; then
    echo "OK: every jar dispatches to $prefix on $FFM_RELEASE and to the root below"
fi
exit $status
