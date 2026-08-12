#!/bin/bash
set -e
mkdir -p soot-template/src/main/java/com/template/soot/analysis
mkdir -p soot-template/src/main/java/com/template/soot/target
mkdir -p soot-template/.vscode
cd soot-template

cat > pom.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.template.soot</groupId>
  <artifactId>soot-template</artifactId>
  <version>1.0-SNAPSHOT</version>
  <packaging>jar</packaging>

  <properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <exec.mainClass>com.template.soot.App</exec.mainClass>
  </properties>

  <repositories>
    <repository>
      <id>soot-snapshot</id>
      <url>https://soot-build.cs.uni-paderborn.de/nexus/repository/soot-snapshot/</url>
    </repository>
    <repository>
      <id>soot-release</id>
      <url>https://soot-build.cs.uni-paderborn.de/nexus/repository/soot-release/</url>
    </repository>
  </repositories>

  <dependencies>
    <!-- Soot core -->
    <dependency>
      <groupId>org.soot-oss</groupId>
      <artifactId>soot</artifactId>
      <version>4.4.1</version>
    </dependency>

    <!-- JUnit for tests (optional, remove if unused) -->
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>5.10.0</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <finalName>soot-template</finalName>
    <plugins>
      <!-- Compile plugin -->
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.11.0</version>
      </plugin>

      <!-- Run "mvn exec:java" directly -->
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>3.1.0</version>
        <configuration>
          <mainClass>${exec.mainClass}</mainClass>
        </configuration>
      </plugin>

      <!-- Build a fat/shaded jar with all deps bundled, so "java -jar" works standalone -->
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <version>3.5.1</version>
        <executions>
          <execution>
            <phase>package</phase>
            <goals>
              <goal>shade</goal>
            </goals>
            <configuration>
              <transformers>
                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                  <mainClass>${exec.mainClass}</mainClass>
                </transformer>
              </transformers>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
EOF

cat > README.md << 'EOF'
# Soot Analysis Template

A reusable starter project for writing Soot-based static analyses in Java,
set up for VS Code + Maven. Clone this for every new Soot project instead
of redoing the setup from scratch.

## One-time machine setup (do this once per machine, not per project)

```bash
# Java + Maven (skip if already installed)
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk maven
```

In VS Code, install the **Extension Pack for Java** (Microsoft) from the
Marketplace.

## Per-project usage

```bash
git clone <this-repo-url> my-new-analysis
cd my-new-analysis
code .
```

Then either:
- Press `F5` in VS Code (launch config is already set up in `.vscode/launch.json`), or
- Run `./run.sh` from the terminal, or
- Run manually: `mvn compile && mvn exec:java`

## Project layout

```
pom.xml                                  Soot dependency + build plugins, pre-configured
src/main/java/com/template/soot/
    App.java                             Entry point: loads target/classes into Soot,
                                          walks every method, prints Jimple + runs analysis
    analysis/
        MyConstantPropagation.java       Example forward dataflow analysis
                                          (constant propagation, per-statement granularity)
    target/
        Sample.java                      The code being ANALYZED (swap this out for
                                          whatever you actually want to run Soot on)
.vscode/
    settings.json, launch.json           VS Code Java/Maven + debug config
run.sh                                   One-command build + run
```

## How to point Soot at different code

Soot analyzes whatever's compiled under `target/classes`. By default this
template just analyzes its own `target/` package (see `Sample.java`) so you
have something to test against immediately. To analyze different code:

1. Drop your `.java` files under `src/main/java/...` (Maven compiles them
   automatically), **or**
2. Point `Options.v().set_process_dir(...)` in `App.java` at an external
   directory of already-compiled `.class` files, **or**
3. Point it at a `.jar` file instead of a directory.

## Writing your own analysis

Copy `MyConstantPropagation.java` as a starting point. The three methods
you need to fill in for any new forward dataflow analysis are:

- `newInitialFlow()` / `entryInitialFlow()` -- the lattice's initial value
- `merge(in1, in2, out)` -- the meet operator (how facts combine at a join)
- `flowThrough(in, unit, out)` -- the transfer function (effect of one statement)

For a **backward** analysis (e.g. liveness), extend
`BackwardFlowAnalysis<Unit, ...>` instead -- same three methods, Soot
handles the direction internally.

## Notes

- `G.reset()` in `App.java` resets Soot's global state -- needed if you ever
  call `main()` more than once in the same JVM (e.g. in a test suite).
- `Options.v().set_allow_phantom_refs(true)` avoids crashes when Soot
  encounters JDK classes it hasn't explicitly loaded.
- If you hit `ClassNotFoundException`/module errors on JDK 17+, check that
  `maven.compiler.source/target` in `pom.xml` match your installed JDK.
EOF

cat > .gitignore << 'EOF'
target/
*.class
.classpath
.project
.settings/
*.iml
.idea/
sootOutput/
*.log
EOF

cat > run.sh << 'EOF'
#!/bin/bash
# Convenience script: compile the target code, then run the Soot analysis on it.
set -e
mvn -q compile
mvn -q exec:java -Dexec.mainClass="com.template.soot.App"
EOF
chmod +x run.sh

cat > .vscode/settings.json << 'EOF'
{
    "java.configuration.updateBuildConfiguration": "automatic",
    "java.compile.nullAnalysis.mode": "automatic",
    "maven.executable.preferMavenWrapper": false,
    "java.debug.settings.hotCodeReplace": "auto"
}
EOF

cat > .vscode/launch.json << 'EOF'
{
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "Run Soot App",
            "request": "launch",
            "mainClass": "com.template.soot.App",
            "projectName": "soot-template"
        }
    ]
}
EOF

cat > src/main/java/com/template/soot/App.java << 'EOF'
package com.template.soot;

import soot.Body;
import soot.G;
import soot.Options;
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
                MyConstantPropagation cp = new MyConstantPropagation(
                        new soot.toolkits.graph.BriefUnitGraph(body));
                cp.printResults();
            }
        }
    }
}
EOF

cat > src/main/java/com/template/soot/analysis/MyConstantPropagation.java << 'EOF'
package com.template.soot.analysis;

import soot.Local;
import soot.Unit;
import soot.Value;
import soot.jimple.AddExpr;
import soot.jimple.AssignStmt;
import soot.jimple.DivExpr;
import soot.jimple.IntConstant;
import soot.jimple.MulExpr;
import soot.jimple.SubExpr;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.ForwardFlowAnalysis;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple intraprocedural constant propagation, implemented as a forward
 * dataflow analysis over Jimple statements (per-Unit granularity, not
 * per-basic-block -- this matters, see below).
 *
 * Lattice per variable:
 *      TOP  (undetermined, not yet analyzed)
 *      c    (a concrete constant value)
 *      NAC  (Not A Constant / bottom -- proven not to be a single constant)
 *
 * Why per-statement and not per-basic-block:
 *   A variable can be assigned more than once within a single basic block
 *   (e.g. "x = 5; y = x + 1; x = y * 2;"). Per-block granularity would only
 *   let you track ONE value for x across the whole block, forcing NAC as
 *   soon as x changes -- even though each individual statement actually has
 *   a well-defined, foldable value. Soot's ForwardFlowAnalysis naturally
 *   gives per-Unit (per-statement) IN/OUT maps, which is exactly what we
 *   want here.
 */
public class MyConstantPropagation extends ForwardFlowAnalysis<Unit, Map<Local, Object>> {

    // Sentinel for "Not A Constant" (bottom of the lattice).
    // TOP is simply represented by "key absent from the map".
    public static final Object NAC = new Object() {
        @Override
        public String toString() {
            return "NAC";
        }
    };

    public MyConstantPropagation(UnitGraph graph) {
        super(graph);
        doAnalysis();
    }

    // ---- Lattice plumbing required by Soot's framework ----

    @Override
    protected Map<Local, Object> newInitialFlow() {
        // Empty map == every variable implicitly TOP.
        return new HashMap<>();
    }

    @Override
    protected Map<Local, Object> entryInitialFlow() {
        return new HashMap<>();
    }

    @Override
    protected void copy(Map<Local, Object> source, Map<Local, Object> dest) {
        dest.clear();
        dest.putAll(source);
    }

    // ---- Meet: how flow facts combine at a join point (multiple preds) ----

    @Override
    protected void merge(Map<Local, Object> in1, Map<Local, Object> in2, Map<Local, Object> out) {
        out.clear();
        java.util.Set<Local> keys = new java.util.HashSet<>();
        keys.addAll(in1.keySet());
        keys.addAll(in2.keySet());

        for (Local l : keys) {
            Object v1 = in1.get(l); // null == TOP
            Object v2 = in2.get(l); // null == TOP

            if (v1 == null) {
                if (v2 != null) out.put(l, v2);
                // else: both TOP -> stay TOP (absent from map)
            } else if (v2 == null) {
                out.put(l, v1);
            } else if (v1.equals(v2)) {
                out.put(l, v1); // same constant (or both NAC) on both paths
            } else {
                out.put(l, NAC); // conflicting constants -> not constant
            }
        }
    }

    // ---- Transfer function: effect of a single Jimple statement ----

    @Override
    protected void flowThrough(Map<Local, Object> in, Unit unit, Map<Local, Object> out) {
        copy(in, out);

        if (!(unit instanceof AssignStmt)) {
            return; // branches, returns, etc. don't change the value lattice
        }

        AssignStmt stmt = (AssignStmt) unit;
        Value lhs = stmt.getLeftOp();
        Value rhs = stmt.getRightOp();

        if (!(lhs instanceof Local)) {
            return; // e.g. assignment to a field or array element -- ignore for now
        }
        Local x = (Local) lhs;

        Object value = evaluate(rhs, in);
        if (value == null) {
            out.remove(x); // TOP
        } else {
            out.put(x, value);
        }
    }

    /**
     * Evaluate the RHS of an assignment under the current flow facts.
     * Returns:
     *   an IntConstant  -> known constant value
     *   NAC              -> proven not a single constant
     *   null              -> TOP (not yet determined -- shouldn't normally
     *                        escape this function, but kept for safety)
     */
    private Object evaluate(Value v, Map<Local, Object> in) {
        if (v instanceof IntConstant) {
            return v;
        }

        if (v instanceof Local) {
            Object val = in.get(v);
            return (val == null) ? null : val; // propagate TOP or the known value
        }

        if (v instanceof AddExpr || v instanceof SubExpr
                || v instanceof MulExpr || v instanceof DivExpr) {
            soot.jimple.BinopExpr bin = (soot.jimple.BinopExpr) v;
            Object l = evaluate(bin.getOp1(), in);
            Object r = evaluate(bin.getOp2(), in);

            if (l == null || r == null) return null; // either side still TOP
            if (l == NAC || r == NAC) return NAC;

            int lv = ((IntConstant) l).value;
            int rv = ((IntConstant) r).value;

            if (v instanceof AddExpr) return IntConstant.v(lv + rv);
            if (v instanceof SubExpr) return IntConstant.v(lv - rv);
            if (v instanceof MulExpr) return IntConstant.v(lv * rv);
            if (v instanceof DivExpr) {
                if (rv == 0) return NAC; // avoid div-by-zero at analysis time
                return IntConstant.v(lv / rv);
            }
        }

        // Anything else (method calls, field/array reads, casts, etc.)
        // is conservatively treated as unknown.
        return NAC;
    }

    // ---- Convenience: dump results for every statement in the method ----

    public void printResults() {
        for (Unit u : graph) {
            Map<Local, Object> in = getFlowBefore(u);
            System.out.println("  IN" + in + "  " + u);
        }
    }
}
EOF

cat > src/main/java/com/template/soot/target/Sample.java << 'EOF'
package com.template.soot.target;

/**
 * This is the code being ANALYZED, not the analysis code itself.
 * App.java points Soot at target/classes and Soot will pick up Sample.class
 * automatically since it's an "application class" (compiled from src/main/java).
 *
 * Feel free to replace this with whatever you actually want to analyze --
 * this file is just a hook to prove the pipeline (compile -> Soot -> Jimple
 * -> your analysis) works end-to-end.
 */
public class Sample {

    public int compute(int a, int b) {
        int x = 5;
        int y = x + 1;   // constant-foldable: y = 6
        x = y * 2;       // constant-foldable: x = 12
        int z = x + b;   // NOT foldable -- depends on parameter b
        return a + z;
    }

    public int fib(int m) {
        int f0 = 0, f1 = 1, f2 = 0, i;
        if (m <= 1) {
            return m;
        }
        for (i = 2; i <= m; ++i) {
            f2 = f0 + f1;
            f0 = f1;
            f1 = f2;
        }
        return f2;
    }
}
EOF

echo "Project created in $(pwd)"
echo "Next: mvn compile && ./run.sh"
