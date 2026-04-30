#!/bin/bash

# =================================================================
# Health-ID Vault – Cross-Platform Execution Script
# =================================================================

# Ensure script is run from the project root
cd "$(dirname "$0")"

echo "Step 1: Detecting Maven..."

# Try to find Maven in the following order:
# 1. Maven Wrapper (./mvnw)
# 2. Local apache-maven-3.9.6 folder
# 3. System-wide mvn command

if [ -f "./mvnw" ]; then
    MVN="./mvnw"
    chmod +x "$MVN"
    echo "Using Maven Wrapper: $MVN"
elif [ -d "./apache-maven-3.9.6" ]; then
    MVN="./apache-maven-3.9.6/bin/mvn"
    chmod +x "$MVN"
    echo "Using Local Maven: $MVN"
elif command -v mvn &> /dev/null; then
    MVN="mvn"
    echo "Using System Maven: $MVN"
else
    echo "❌ Error: Maven not found! Please install Maven or ensure the 'apache-maven-3.9.6' folder is present."
    exit 1
fi

echo "Step 2: Cleaning and Compiling project..."
$MVN clean compile

if [ $? -ne 0 ]; then
    echo "❌ Error: Compilation failed. Please check the logs above."
    exit 1
fi

echo "Step 3: Launching Health-ID Vault UI..."
$MVN javafx:run
