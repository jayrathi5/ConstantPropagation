#!/bin/bash
# Convenience script: compile the target code, then run the Soot analysis on it.
set -e
mvn -q compile
mvn -q exec:java -Dexec.mainClass="com.template.soot.App"
