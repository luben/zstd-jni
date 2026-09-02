#!/bin/bash
#
# Runs the test suite against a packaged jar instead of against target/classes.
#
# This is the only thing that exercises Multi-Release dispatch the way a consumer
# does: the JDK picks the implementation out of the jar itself, so which one runs
# is decided by the runtime's feature version and by nothing this build controls.
# Putting META-INF/versions/22 on the classpath as a *directory* - how an earlier
# version of this worked - proves nothing, because a directory entry only ever
# resolves the base name.
#
# The suite runs through scalatest's own runner rather than through `sbt test` so
# that the JVM under test can be chosen freely; sbt would run it in its own JVM,
# pinning the feature version to whatever built the jar.
#
# Usage: run-tests-from-jar.sh <java-home> [jvm-opts...]
#
#   run-tests-from-jar.sh "$JAVA25"                                       # FFM
#   run-tests-from-jar.sh "$JAVA25" -Djdk.util.jar.enableMultiRelease=false  # JNI on 22+
#   run-tests-from-jar.sh "$JAVA11"                                       # JNI on 8-21
#
# The three differ only in the runtime, never in the artifact. Run
# check-multi-release-jar.sh on the jar first: it is what guarantees the versioned
# classes are in there, so that a green FFM run cannot mean "resolved to the base
# class because there was nothing else to resolve to".
#
# JAR= overrides the jar; it defaults to target/zstd-jni-$(cat version).jar.

set -euo pipefail

if [ $# -lt 1 ]; then
    echo "usage: $0 <java-home> [jvm-opts...]" >&2
    exit 1
fi

javaHome=$1
shift

# On the msys2 runners JAVA_HOME is a Windows path, which bash cannot execute out
# of. Argument mangling has to be turned off too, but only around the java call at
# the bottom - doing it here would break the ./sbt call in between, which hands its
# launcher jar to java as a POSIX path and depends on msys2 rewriting it.
if command -v cygpath >/dev/null 2>&1; then
    javaHome=$(cygpath -u "$javaHome")
fi
readonly javaHome
readonly java=$javaHome/bin/java

readonly jar=target/zstd-jni-$(cat version).jar
readonly classpathFile=target/test-classpath.txt

[ -x "$java" ] || [ -x "$java.exe" ] || { echo "no java at $java" >&2; exit 1; }
[ -f "${JAR:=$jar}" ] || { echo "no jar at $JAR; run \`sbt package\` first" >&2; exit 1; }

# The dependency classpath is a build-time fact, so it is exported once and
# reused for every JVM. `export` prints it as a bare path list; sbt's own log
# lines are all bracket-prefixed and gcc warnings reach stdout at error level, so
# the classpath is the last line that is neither.
if [ ! -s "$classpathFile" ]; then
    echo "exporting the test classpath"
    ./sbt -batch --error 'export Test/fullClasspath' \
        | grep -v '^\[' | grep -E '[:;]' | tail -1 | tr -d '\r' > "$classpathFile"
fi

# File.pathSeparator is ';' on Windows, where the entries are also backslashed
# C:\... paths that no amount of $PWD matching will recognise.
sep=:
if grep -q ';' "$classpathFile"; then
    sep=';'
fi
readonly sep

# Drop the base classes: the whole point is that they come from the jar now.
# target/jacoco/instrumented-classes is the same classes under another name -
# sbt-jacoco swaps it in for target/classes in its own Test / fullClasspath
# wrapper - so it has to go too, or a `jacoco` run in the same session would put
# the base classes back in front of the jar. The match is on the normalised tail
# rather than on an absolute path, because on Windows these are C:\... entries.
readonly baseClasses='(^|[/\])target[/\](classes|jacoco[/\]instrumented-classes)[/\]?$'

cp=$(tr "$sep" '\n' < "$classpathFile" | grep -v -E "$baseClasses" | paste -sd"$sep" -)
cp=$JAR$sep$cp

# If that filter ever misses, the base classes stay ahead of the jar and every run
# quietly tests the JNI implementation - including the one whose job is to prove
# the FFM path works. Green, and meaningless.
if tr "$sep" '\n' <<<"$cp" | grep -q -E "$baseClasses"; then
    echo "::error::the base classes are still on the classpath; the jar would not be used" >&2
    exit 1
fi

release=$("$java" -XshowSettings:properties -version 2>&1 \
          | sed -n 's/^ *java\.specification\.version = \([0-9][0-9]*\).*/\1/p')

# Built as one array that is never empty, and never expanded when it could be:
# macOS ships bash 3.2, where `set -u` rejects "${arr[@]}" and "$@" if there is
# nothing in them. bash only stopped doing that in 4.4.
cmd=("$java")

# JEP 472: restricted methods warn on 24 and are to be blocked in a later release.
# Both implementations need this - System.load for JNI, the downcalls for FFM -
# and JDKs below 22 reject the flag outright.
if [ -n "$release" ] && [ "$release" -ge 22 ]; then
    cmd+=(--enable-native-access=ALL-UNNAMED)
fi
if [ $# -gt 0 ]; then
    cmd+=("$@")
fi
cmd+=(-cp "$cp" org.scalatest.tools.Runner -R target/test-classes -o)

echo "running the suite on JDK ${release:-?} against $JAR ${*:+with $*}"

# Now, and not before: msys2 rewrites anything in an argument that looks like a
# POSIX path, which would mangle the ';'-separated list of C:\... entries below.
# Everything that needed the rewriting - ./sbt above - has already run.
export MSYS2_ARG_CONV_EXCL='*'

exec "${cmd[@]}"
