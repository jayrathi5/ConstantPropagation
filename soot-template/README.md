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
