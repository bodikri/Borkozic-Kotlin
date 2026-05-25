#!/bin/bash
# compile_wsl.sh — WSL build wrapper for Borkozic
# Запазва Windows local.properties, ползва WSL пътища за билд, ВИНАГИ връща обратно

REPO="/mnt/d/Borkozic_Versions/Borkozic-Java_Oki"
SDK="/home/pc_bodi/.android-sdk-wsl"
JAVA="/usr/lib/jvm/java-21-openjdk-amd64"

cd "$REPO"

# Винаги възстановяваме при изход (успех или FAIL)
restore_properties() {
    if [ -f "$REPO/local.properties.windows" ]; then
        cp "$REPO/local.properties.windows" "$REPO/local.properties"
        rm -f "$REPO/local.properties.windows"
        echo "RESTORED local.properties to Windows paths"
    fi
}
trap restore_properties EXIT

# запазваме оригинала
cp local.properties local.properties.windows

# слагаме WSL конфиг
cp local.properties_wsl local.properties

# билд — ако FAIL-не, trap възстановява
ANDROID_HOME="$SDK" JAVA_HOME="$JAVA" ./gradlew :borkozic:assembleDebug "$@"

# ако успеем — връщаме въпреки че trap ще го направи пак
cp local.properties.windows local.properties
rm -f local.properties.windows

echo "BUILD SUCCESSFUL — local.properties restored to Windows paths"
