package com.template.soot;

import soot.Body;
import soot.G;
import soot.options.Options;
import soot.PackManager;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Transform;

import java.util.Collections;

import com.template.soot.analysis.MyConstantPropagation;

/**
 * Entry point / boilerplate for any new Soot analysis project.
 *
 * Usage:
 *   1. Compile your target code (the code you want to analyze) so its
 *      .class files land under target/classes (Maven does this automatically
 *      for anything under src/main/java, e.g. com.template.soot.target.*).
 *   2. Run this App. It will:
 *        - point Soot at target/classes
 *        - load every application class
 *        - retrieve each method's Jimple body
 *        - run your custom analysis (MyConstantPropagation) on it
 *        - print the Jimple + analysis results
 */
public class App {

    private static String normalizeClassName(String input) {
        String name = input.trim();

        if (name.endsWith(".java")) {
            name = name.substring(0, name.length() - 5);
        }

        name = name.replace('/', '.').replace('\\', '.');

        for (SootClass sc : Scene.v().getApplicationClasses()) {
            if (sc.getShortName().equals(name) || sc.getName().equals(name)) {
                return sc.getName();
            }
        }

        return name;
    }

    private static void printMethodsForClass(SootClass sc) {
        System.out.println("Class: " + sc.getName());
        for (SootMethod sm : sc.getMethods()) {
            System.out.println(sm.getName() + " -> " + sm.getSignature());
        }
    }

    public static void main(String[] args) {
        // Reset Soot's global state -- important if you ever run main() more
        // than once in the same JVM (e.g. from a test suite).
        G.reset();

        // ---- Standard Soot options ----
        Options.v().set_process_dir(Collections.singletonList("target/classes"));
        Options.v().set_output_format(Options.output_format_none); // we print manually
        Options.v().set_whole_program(false);           // set true for interprocedural analyses
        Options.v().set_allow_phantom_refs(true);        // avoid crashes on missing JDK classes
        Options.v().set_keep_line_number(true);
        Options.v().set_prepend_classpath(true);

        // Load all classes found under target/classes
        Scene.v().loadNecessaryClasses();

        if (args.length > 0) {
            String targetClassName = normalizeClassName(args[0]);
            SootClass targetClass = null;

            for (SootClass sc : Scene.v().getApplicationClasses()) {
                if (sc.getName().equals(targetClassName) || sc.getShortName().equals(targetClassName)) {
                    targetClass = sc;
                    break;
                }
            }

            if (targetClass == null) {
                System.out.println("Class not found: " + targetClassName);
                return;
            }

            printMethodsForClass(targetClass);
            return;
        }

        for (SootClass sc : Scene.v().getApplicationClasses()) {
            for (SootMethod sm : sc.getMethods()) {
                if (!sm.isConcrete()) {
                    continue; // skip abstract/native methods -- no body to analyze
                }

                Body body = sm.retrieveActiveBody();

                System.out.println("=================================================");
                System.out.println("Method: " + sm.getSignature());
                System.out.println("=================================================");
                System.out.println(body);

                // ---- Run your custom analysis here ----
                MyConstantPropagation cp = new MyConstantPropagation(sm);
                cp.printResults();
            }
        }
    }
}
