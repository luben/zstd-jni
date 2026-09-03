/*
 * Runs the test suite against the packaged jar instead of against target/classes.
 *
 * This is the only thing that exercises Multi-Release dispatch the way a consumer
 * does: the JDK picks the implementation out of the jar itself, so which one runs
 * is decided by the runtime's feature version and by nothing this build controls.
 * Putting META-INF/versions/22 on the classpath as a *directory* - how an earlier
 * version of this worked - proves nothing, because a directory entry only ever
 * resolves the base name.
 *
 * The suite runs through scalatest's own runner rather than through `sbt test` so
 * that the JVM under test can be chosen freely; sbt would run it in its own JVM,
 * pinning the feature version to whatever built the jar.
 *
 * Usage: java .github/scripts/RunTestsFromJar.java <java-home> [jvm-opts...]
 *
 *   ... <java-home-25>                                       # FFM
 *   ... <java-home-25> -Djdk.util.jar.enableMultiRelease=false  # JNI on 22+
 *   ... <java-home-11>                                       # JNI on 8-21
 *
 * The three differ only in the runtime, never in the artifact. Run
 * `sbt testFromJarSetup` first: it packages the jar - which is where the
 * Multi-Release dispatch check lives, so a green FFM run cannot mean "resolved to
 * the base class because there was nothing else to resolve to" - and writes the
 * classpath this reads.
 *
 * A single-file Java program (JEP 330) rather than a shell script: it needs a JDK
 * and nothing else, which every job in the matrix already has, and it sidesteps
 * the whole Windows layer - File.pathSeparator instead of sniffing for ';',
 * ProcessBuilder instead of MSYS2_ARG_CONV_EXCL, Path instead of cygpath.
 */
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class RunTestsFromJar {

    private static final Path SETUP = Paths.get("target", "test-from-jar.properties");

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: java .github/scripts/RunTestsFromJar.java <java-home> [jvm-opts...]");
            System.exit(2);
        }

        Properties setup = new Properties();
        if (!Files.isRegularFile(SETUP)) {
            System.err.println("no " + SETUP + "; run `./sbt testFromJarSetup` first");
            System.exit(1);
        }
        try (Reader r = Files.newBufferedReader(SETUP, StandardCharsets.UTF_8)) {
            setup.load(r);
        }

        Path java = javaBinary(Paths.get(args[0]));
        int release = featureVersion(java);

        List<String> cmd = new ArrayList<>();
        cmd.add(java.toString());
        // JEP 472: restricted methods warn on 24 and are to be blocked in a later
        // release. Both implementations need this - System.load for JNI, the
        // downcalls for FFM - and JDKs below 22 reject the flag outright.
        if (release >= 22) {
            cmd.add("--enable-native-access=ALL-UNNAMED");
        }
        for (int i = 1; i < args.length; i++) {
            cmd.add(args[i]);
        }
        // The jar goes first; the setup task has already taken the base classes out
        // of what follows.
        cmd.add("-cp");
        cmd.add(setup.getProperty("jar") + File.pathSeparator + setup.getProperty("classpath"));
        cmd.add("org.scalatest.tools.Runner");
        cmd.add("-R");
        cmd.add(setup.getProperty("testClasses"));
        cmd.add("-o");

        List<String> jvmOpts = Arrays.asList(args).subList(1, args.length);
        System.out.println("running the suite on JDK " + release + " against " + setup.getProperty("jar")
                + (jvmOpts.isEmpty() ? "" : " with " + String.join(" ", jvmOpts)));

        System.exit(new ProcessBuilder(cmd).inheritIO().start().waitFor());
    }

    private static Path javaBinary(Path javaHome) {
        Path bin = javaHome.resolve("bin").resolve("java");
        Path exe = javaHome.resolve("bin").resolve("java.exe");
        if (Files.isExecutable(bin)) return bin;
        if (Files.isExecutable(exe)) return exe;
        throw new IllegalArgumentException("no java executable under " + javaHome);
    }

    /** Asks the target JVM itself, rather than guessing from the path or a release file. */
    private static int featureVersion(Path java) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(java.toString(), "-XshowSettings:properties", "-version")
                .redirectErrorStream(true)
                .start();
        String output;
        try (InputStream in = p.getInputStream()) {
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (p.waitFor() != 0) {
            throw new IOException(java + " exited non-zero:\n" + output);
        }
        for (String line : output.split("\\R")) {
            int eq = line.indexOf('=');
            if (eq < 0 || !line.substring(0, eq).trim().equals("java.specification.version")) continue;
            String value = line.substring(eq + 1).trim();
            // "1.8" on 8, "11" / "25" from 9 onwards.
            return Integer.parseInt(value.startsWith("1.") ? value.substring(2) : value);
        }
        throw new IOException("no java.specification.version in the output of " + java + ":\n" + output);
    }
}
