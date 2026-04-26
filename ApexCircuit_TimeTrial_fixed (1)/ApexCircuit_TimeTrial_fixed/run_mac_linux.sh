#!/bin/bash
cd "$(dirname "$0")/src"
echo "Compiling ApexCircuit..."
javac *.java || { echo "Compilation failed! Install JDK: sudo apt install default-jdk"; exit 1; }
echo "Running ApexCircuit..."
java Main
