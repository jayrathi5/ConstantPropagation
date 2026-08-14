package com.template.soot;

import soot.Body;
import soot.BodyTransformer;
import soot.PackManager;
import soot.SootMethod;
import soot.Transform;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Map;

import com.template.soot.analysis.MyConstantPropagation;

/**
 * Entry point, driven the same way as cs6235-a1's A1.java:
 *   - build a soot.Main command-line args array (getOptions)
 *   - register the analysis as a BodyTransformer in the "jtp" pack
 *   - hand control to soot.Main.main(), which runs the whole Soot pipeline
 *     and invokes our transformer once per method body it processes
 *
 * Usage:
 *   Edit TARGET_CLASS below, then hit Run in VS Code (or `mvn compile exec:java`).
 *   Results go to output<ClassName>.txt in the soot-template/ directory.
 *
 * A command-line argument still overrides TARGET_CLASS if you pass one:
 *   mvn compile exec:java -Dexec.args="Test3"
 */
public class App {

    // ====================================================================
    // CHANGE THIS to pick which class under target/ gets analyzed.
    // Short name ("Test1") or fully qualified ("com.foo.Bar") both work.
    // Running it writes output<ClassName>.txt -- e.g. Test1 -> outputTest1.txt
    // ====================================================================
    private static final String TARGET_CLASS = "Test2";

    private static String targetClass;

    public static void main(String[] args) throws Exception {
        String[] mainArgs = getOptions(args);

        PackManager.v().getPack("jtp").add(new Transform("jtp.myConstantPropagation", new BodyTransformer() {
            @Override
            protected void internalTransform(Body body, String phaseName, Map<String, String> options) {
                SootMethod sm = body.getMethod();
                if (!sm.getDeclaringClass().getName().equals(targetClass)) {
                    return; // -app pulls in JDK-internal classes too; only analyze our target
                }

                System.out.println("=================================================");
                System.out.println("Method: " + sm.getSignature());
                System.out.println("=================================================");
                System.out.println(body);

                MyConstantPropagation cp = new MyConstantPropagation(sm);
                cp.printResults();
            }
        }));

        File outputFile = new File(projectDir(), "output" + shortName(targetClass) + ".txt");
        PrintStream console = System.out;

        try (PrintStream file = new PrintStream(new FileOutputStream(outputFile))) {
            System.setOut(file);
            soot.Main.main(mainArgs);
        } finally {
            System.setOut(console);
        }

        console.println("Wrote analysis for " + targetClass + " to " + outputFile.getAbsolutePath());
    }

    /**
     * The soot-template directory (the one holding pom.xml), found by walking up
     * from wherever App.class was loaded. Everything below is resolved against
     * this rather than the working directory, so the VS Code Run button, `mvn
     * exec:java`, and a bare `java` invocation all land in the same place.
     */
    private static File projectDir() {
        try {
            File dir = new File(App.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            while (dir != null) {
                if (new File(dir, "pom.xml").isFile()) {
                    return dir;
                }
                dir = dir.getParentFile();
            }
        } catch (Exception e) {
            // fall through to the working directory
        }
        return new File(".").getAbsoluteFile();
    }

    /** Bare names like "Test1" are assumed to live in the target package. */
    private static String qualify(String name) {
        String n = name.trim();
        if (n.endsWith(".java")) {
            n = n.substring(0, n.length() - 5);
        }
        return n.contains(".") ? n : "com.template.soot.target." + n;
    }

    private static String shortName(String className) {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? className : className.substring(dot + 1);
    }

    private static String[] getOptions(String[] args) {
        String classPath = new File(projectDir(), "target/classes").getAbsolutePath();
        targetClass = qualify(TARGET_CLASS);

        if (args.length != 0) {
            int i = 0;
            while (i < args.length) {
                if (args[i].equals("-cp")) {
                    classPath = args[i + 1];
                    i += 2;
                } else if (i == args.length - 1) {
                    targetClass = qualify(args[i]);
                    i++;
                } else {
                    i++;
                }
            }
        }

        // NOTE: -src-prec java (Soot's JastAdd source frontend) needs the
        // sun.boot.class.path JVM property, which JDK 9+ removed -- it NPEs
        // on any modern JDK. We analyze the compiled .class files instead
        // (default source precision); use-original-names:true still recovers
        // real variable names from the .class file's debug info.
        return new String[] {
            "-pp",
            "-cp", classPath,
            "-p", "jb", "use-original-names:true",
            // jb.sils ("split primitive locals used as different types") is what
            // collapses `x=3; y=x+4; z=y*2; return z;` all the way down to
            // `return 14` before any analysis runs. Turning it off keeps the
            // original statements so the dataflow analysis has real work to do.
            "-p", "jb.sils", "enabled:false",
            "-app",
            "-x", "jdk.",       // Soot's default exclude list predates JDK 9's jdk.* module
            "-x", "sun.",       // classes -- without this, -app drags in JVM-internal
            "-f", "none",       // reflection machinery as if it were application code.
            "-allow-phantom-refs",
            "-keep-line-number",
            targetClass
        };
    }
}
