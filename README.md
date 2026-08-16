# Constant Propagation with Soot

A forward dataflow analysis that computes, at every program point, which local
variables hold a known integer constant. Built on [Soot](https://github.com/soot-oss/soot)
4.4.1, set up for VS Code + Maven.

Example — for this input:

```java
public int run(int a) {
    int x = 3;
    int y = x + 4;
    int z = y * 2;
    return z;
}
```

the analysis reports:

```
Block 0 this := @this .. return z
  IN:  {x=TOP, a=TOP, this=TOP, y=TOP, z=TOP}
    this := @this: com.template.soot.target.Test1
    a := @parameter0: int
    x = 3
    y = x + 4
    z = y * 2
    return z
  OUT: {x=3, a=BOT, this=BOT, y=7, z=14}
```

`x=3, y=7, z=14` are folded by *this* analysis, not by Soot. `a` and `this` are
`BOT` because a parameter's value is unknown to an intraprocedural analysis.

---

## Requirements

| | |
|---|---|
| **JDK 17** | Required. Newer JDKs **do not work** — see below. |
| Maven | `sudo apt-get install -y maven` |
| VS Code | Install the **Extension Pack for Java** (Microsoft) |

> **JDK 17 is not optional.** On JDK 21/25 the run fails with
> `Unsupported class file major version 69`. Soot 4.4.1 cannot read class files
> newer than it. If `java -version` reports anything above 17:
>
> ```bash
> sudo apt-get install -y openjdk-17-jdk
> export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64   # add to ~/.bashrc
> ```
>
> `run.sh` auto-detects a JDK 17 install and uses it.

## Running an analysis

**1.** Put the code you want to analyze in `src/main/java/com/template/soot/target/`.
Maven compiles it to `target/classes` automatically.

**2.** Open [`App.java`](src/main/java/com/template/soot/App.java) and set the class name:

```java
private static final String TARGET_CLASS = "Test1";   // ← change this
```

Short names (`"Test1"`) resolve against `com.template.soot.target`. Fully
qualified names (`"com.example.MyClass"`) work too.

**3.** Run it, any of these ways:

```bash
./run.sh                                        # terminal
mvn compile exec:java                           # terminal, manual
mvn compile exec:java -Dexec.args="Test3"       # override TARGET_CLASS for one run
```

or press **Run** / **F5** in VS Code (`.vscode/launch.json` is preconfigured).

**4.** Read the results in `output<ClassName>.txt` — e.g. `Test1` writes
`outputTest1.txt`. Each method gets its Jimple body followed by the `IN`/`OUT`
map for every basic block.

Output paths are resolved from the location of `pom.xml`, not the working
directory, so all four launch methods write to the same place.

## Reading the output

| Value | Meaning |
|---|---|
| `TOP` | No information yet — the variable hasn't been reached on any path. |
| `5` | Definitely the constant 5 on every path reaching this point. |
| `BOT` | Not a constant — either unknown (a parameter, a field, a method return) or two paths disagree. |

`IN` is the state entering the block; `OUT` is the state leaving it.

## What gets folded — and what does not

**The analysis folds integer arithmetic only: `+`, `-`, `*`, `/`, `%`.**
Every other expression is treated as `BOT` (not-a-constant), even when its value
is obvious at compile time. This is a deliberate limitation, not a bug — the
result stays *sound* (never claims a wrong constant), just imprecise.

Folded:

| Form | Example | Result |
|---|---|---|
| Integer literal | `x = 5` | `x=5` |
| Copy | `y = x` | `y` takes `x`'s value |
| Add / Sub / Mul | `y = x + 4`, `x - 1`, `x * 2` | folded |
| Div / Rem | `y = x / 2`, `x % 3` | folded (`/0` and `%0` → `BOT`) |

**Not** folded — always `BOT`:

| Form | Example | Why |
|---|---|---|
| Unary minus | `y = -x` | `NegExpr` is a `UnopExpr`, not a `BinopExpr` |
| Bitwise | `x & 3`, `x \| 1`, `x ^ 2` | `AndExpr` / `OrExpr` / `XorExpr` not handled |
| Shifts | `x << 2`, `x >> 1`, `x >>> 1` | `ShlExpr` / `ShrExpr` / `UshrExpr` not handled |
| Comparison | `x cmp y` | `CmpExpr` / `CmpgExpr` / `CmplExpr` not handled |
| `long` / `double` / `float` | `long L = 100L` | only `IntConstant` is tracked |
| `String` / `null` / class literals | `String s = "hi"` | not an `IntConstant` |
| Field reads | `x = this.f`, `x = Sys.out` | could be changed by other code |
| Array reads | `x = arr[0]` | no array modelling |
| Method calls | `x = foo()` | intraprocedural analysis |
| `new` / cast / `instanceof` / `.length` | `x = new A()` | not modelled |
| Parameters and `this` | `a := @parameter0` | unknown to the caller-agnostic analysis |

`byte`, `short` and `char` **are** covered — Jimple represents their literals as
`IntConstant`, which is why `Test1`'s `byte x = 3` folds correctly.

`Sample.java` has a runnable case for each gap (`datatypeGap`, `unaryAndBitwise`,
`divByZeroConst`).

To add an operator, extend `evaluate()` in `MyConstantPropagation.java` — it has
four branches (const∘const, const∘local, local∘const, local∘local), and each
lists the five supported `BinopExpr` types.

## Writing test cases that actually reach the analysis

Two layers of optimisation run *before* your code ever sees the program, and
both can silently empty out a test case. Neither is a bug — but if you don't
account for them, a perfectly good test looks like the analysis is broken.

### 1. javac folds pure-literal expressions

The Java Language Spec requires javac to evaluate *constant expressions*
(operands all literals or `final`s) at compile time. They are folded in the
`.class` file, so nothing reaches Soot or the analysis:

```java
int k = 1 * 3 + 1;        // compiled as `k = 4` -- nothing left to fold
int y = 5;
y = y + 2 - (1 * 3 + 1);  // `(1*3+1)` folded to 4 by javac, but `y + 2` and
                          // `- 4` are real work: the analysis computes y = 3
```

**Rule: involve a variable.** Any expression with a local in it survives to the
analysis. Pure literal arithmetic never does.

### 2. Soot deletes locals it can fully resolve

A local that Soot can pin to one constant, and that is never reassigned, gets
folded away and dropped entirely — even a whole method body can collapse to a
single `return 14`. To keep variables alive, reassign them on a path Soot cannot
resolve. A parameter works, because its value is unknown at compile time:

```java
public int run(int a) {
    int x = 3;
    int y = x + 4;
    int z = y * 2;
    if (a > 0) { x = a; y = a; z = a; }   // forces x, y, z to survive
    return x + y + z;
}
```

Without the `if`, this method reduces to `return 14` and there is nothing to
observe. (`-p jb.sils enabled:false` in `App.java` already prevents the worst
of this — see [Gotchas](#gotchas) — but Soot's other passes still apply.)

### Nested expressions need no special handling

Jimple is three-address code, so `BinopExpr` operands are always `Immediate`
(a `Local` or a `Constant`) — never another expression. Soot splits nested
source expressions into `$stack` temporaries automatically:

```java
int w = (x + 4) * 2;
```
```
$stack5 = x + 4      →  $stack5 = 7
w = $stack5 * 2      →  w = 14
```

The statements are processed in sequence and `def` is updated in place, so
arbitrarily deep expressions fold correctly. **Do not add recursion to
`evaluate()`** for this — `operand instanceof Expr` can never be true, and
`evaluate` returns lattice values (`Integer`/`TOP`/`BOT`), not Soot `Value`s,
so feeding its result back in would break the `instanceof` checks below it.

### Why a variable can appear twice (`y` and `y#2`)

Soot's local splitter (`jb.ls`) gives each def-use web its own local, so one
source variable can show up as `y` and `y#2` in the output with different
values at the same time. That is expected, not a double-count.

`TestNested.java` exercises all of the above.

## Project layout

```
pom.xml                              Soot 4.4.1 dependency + build config
run.sh                               Build + run (auto-selects JDK 17)
src/main/java/com/template/soot/
    App.java                         Entry point. Sets TARGET_CLASS, builds Soot's
                                     option array, registers the analysis in the
                                     "jtp" pack, redirects stdout to the output file.
    analysis/
        MyConstantPropagation.java   The analysis itself (worklist algorithm)
    target/                          The code being ANALYZED -- put your inputs here
        Sample.java                  Broad feature/limitation tour
        Test1.java                   Straight-line code
        Test2.java                   if/else: branches agreeing vs disagreeing
        Test3.java                   Loops: constant surviving vs not
        Test4.java                   Infinite loop (termination check)
        TestNested.java              Nested / compound expressions, all 5 operators
.vscode/launch.json                  VS Code run/debug config
```

## How it works

`App.java` follows the standard Soot driver pattern:

1. Build a `soot.Main` command-line argument array (`getOptions`).
2. Register the analysis as a `BodyTransformer` in the **`jtp`** pack
   (*Jimple transformation pack, intraprocedural*), so Soot calls it once per
   method body.
3. Hand control to `soot.Main.main(...)`, which runs the whole pipeline.

`MyConstantPropagation` is a hand-written worklist algorithm (it does **not**
extend Soot's `ForwardFlowAnalysis`):

- **Lattice** — flat, height 3: `TOP` → any constant → `BOT`. All constants sit
  at the same level and are mutually incomparable.
- **Meet** — `meet(c, c) = c`, `meet(TOP, v) = v`, `meet(c1, c2) = BOT` when
  `c1 ≠ c2`. Applied only where a block has multiple predecessors.
- **Transfer** — per statement, over a `BriefBlockGraph`. `IN[b]` is recomputed
  from predecessors on each visit and `def` is reseeded from it, so `OUT[b]`
  stays a pure function of `IN[b]`.
- **Termination** — guaranteed. Each variable can only descend `TOP → c → BOT`,
  i.e. change at most twice, so the worklist drains after at most `2 × (#locals)`
  updates. `Test4` confirms this on a `while(true)` loop.

Only `IntConstant` and the operators `+ - * / %` are folded — see
[What gets folded — and what does not](#what-gets-folded--and-what-does-not).

## Gotchas

Four non-obvious things, each of which silently produces wrong or confusing
output rather than an error.

**`jb.sils` destroys straight-line test cases.** By default Soot collapses
`x=3; y=x+4; z=y*2; return z;` all the way down to `return 14` *before* any
analysis runs, leaving nothing to analyze. The culprit is the `jb.sils` phase
("split primitive locals used as different types"), which fires because `3` fits
in a `byte`. `App.java` disables it:

```java
"-p", "jb.sils", "enabled:false",
```

Without this, constants vanish from the Jimple and every test looks broken.
Note Soot's *other* optimizations still run, so a local it can fully resolve and
that is never reassigned may still be eliminated.

**`IdentityStmt` is not an `AssignStmt`.** They are siblings — both extend
`DefinitionStmt`, neither is a subtype of the other. So this ordering silently
drops every parameter binding, leaving `this` and all parameters at `TOP`:

```java
if (!(s instanceof AssignStmt)) continue;   // ← skips IdentityStmt entirely
if (s instanceof IdentityStmt) { ... }      // ← now unreachable
```

Always test `IdentityStmt` **first**.

**`-src-prec java` crashes on modern JDKs.** Soot's JastAdd source frontend needs
the `sun.boot.class.path` property, removed in JDK 9. It throws an NPE inside
`soot.JastAddJ.Program.initPaths`. This template analyzes compiled `.class`
files instead; `-p jb use-original-names:true` still recovers the original
variable names from the class file's debug info.

**`-app` drags in JDK internals.** Soot's default exclude list predates the
`jdk.*` module namespace, so application mode pulls in `jdk.internal.reflect.*`
and prints thousands of methods. `App.java` passes `-x jdk. -x sun.` and also
filters by declaring class inside the transformer.

## Writing your own analysis

Replace the body of `MyConstantPropagation`, or drop in a new class and swap the
call in `App.java`'s transformer. To use Soot's built-in framework instead of a
hand-written worklist, extend `ForwardFlowAnalysis<Unit, T>` and implement:

- `newInitialFlow()` / `entryInitialFlow()` — the lattice's initial value
- `merge(in1, in2, out)` — the meet operator
- `flowThrough(in, unit, out)` — the transfer function

For a backward analysis (e.g. liveness) extend `BackwardFlowAnalysis` — same
three methods, Soot handles the direction.
