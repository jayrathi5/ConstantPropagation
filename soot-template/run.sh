#!/bin/bash
# Convenience script: compile the target code, then run the Soot analysis on it.
#
# Analyzes whatever class TARGET_CLASS names in App.java, and writes the results
# to output<ClassName>.txt. Pass a class name to override it for this run:
#     ./run.sh Test3
set -e
cd "$(dirname "$0")"

# Soot 4.4.1 cannot read class files newer than JDK 17 ("Unsupported class file
# major version"). Prefer a JDK 17 install if the default java is newer.
if [ -z "$JAVA_HOME" ] && [ -d /usr/lib/jvm/java-17-openjdk-amd64 ]; then
    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
fi

mvn -q compile
if [ $# -gt 0 ]; then
    mvn -q exec:java -Dexec.args="$1"
else
    mvn -q exec:java
fi
