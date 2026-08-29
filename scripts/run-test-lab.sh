#!/usr/bin/env bash
# Builds the instrumented tests and runs them on Firebase Test Lab (classic
# device farm), reusing the same HiltTestRunner-based app/src/androidTest
# suite that `./gradlew connectedDebugAndroidTest` runs locally.
#
# Scope: :app only. Since the :core:data extraction, three instrumented test
# classes (NoteDaoTest, PhotoDaoTest, DefaultUserPreferencesRepositoryTest) live in
# core/data/src/androidTest and build into their own self-instrumenting library
# test APK, which `gcloud firebase test android run` cannot take as part of the
# --app/--test pair below. A bare `./gradlew connectedDebugAndroidTest` still
# runs them on a locally attached device -- unqualified task names reach every
# module -- so they are covered locally but NOT on the device farm. Adding them
# here needs a second `gcloud` invocation for that APK; do that rather than
# assuming this script's green run covered them.
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

./gradlew :app:assembleDebug :app:assembleDebugAndroidTest

gcloud firebase test android run \
  --type instrumentation \
  --app app/build/outputs/apk/debug/app-debug.apk \
  --test app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
  --device model=Pixel7,version=34,locale=en,orientation=portrait \
  --use-orchestrator
