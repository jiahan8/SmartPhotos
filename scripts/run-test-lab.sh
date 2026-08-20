#!/usr/bin/env bash
# Builds the instrumented tests and runs them on Firebase Test Lab (classic
# device farm), reusing the same HiltTestRunner-based app/src/androidTest
# suite that `./gradlew connectedDebugAndroidTest` runs locally.
#
# One-time setup, not done by this script:
#   1. Install the Google Cloud CLI: https://cloud.google.com/sdk/docs/install
#   2. gcloud auth login
#   3. gcloud config set project smartcamerafirebase
#   4. Confirm the project is on the Blaze plan -- Test Lab's Spark-plan free
#      quota does not cover physical devices and is very limited even for
#      virtual ones.
#
# Usage: ./scripts/run-test-lab.sh
set -euo pipefail

cd "$(dirname "$0")/.."

./gradlew assembleDebug assembleDebugAndroidTest

gcloud firebase test android run \
  --type instrumentation \
  --app app/build/outputs/apk/debug/app-debug.apk \
  --test app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
  --device model=Pixel7,version=34,locale=en,orientation=portrait \
  --use-orchestrator
