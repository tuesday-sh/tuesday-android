#!/usr/bin/env bash
set -euo pipefail

# Standardized 5-Tier Testing Cadence Runner for tuesday-android
# Usage:
#   ./scripts/test-tier.sh [t1|t2|commit|t3|session|sprint|milestone|t5|release] [optional_test_filter]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

if [ -d "/home/aaron/Android/jdk/jdk-21.0.13+9" ]; then
  export JAVA_HOME="/home/aaron/Android/jdk/jdk-21.0.13+9"
fi

TIER="${1:-commit}"
FILTER="${2:-}"

case "$TIER" in
  t1|unit)
    echo "==> [Tier 1] Running Pure Unit & Token Invariant Tests (<1s)..."
    ./gradlew testDebugUnitTest \
      --tests "sh.tuesday.app.WcagPaletteContrastTest" \
      --tests "sh.tuesday.app.DeepLinkDestinationTest" \
      --tests "sh.tuesday.app.MonitoredDeviceTest" \
      --tests "sh.tuesday.app.services.GoldClueSuggestionRepositoryTest"
    ;;

  t2|ui)
    if [ -n "$FILTER" ]; then
      echo "==> [Tier 2] Running Filtered JVM Semantic Compose UI Test ($FILTER)..."
      ./gradlew testDebugUnitTest --tests "sh.tuesday.app.TuesdayComposeSemanticUiTest.$FILTER"
    else
      echo "==> [Tier 2] Running Full 8-Flow JVM Semantic Compose UI + WCAG Suite (~6s)..."
      ./gradlew testDebugUnitTest --tests "sh.tuesday.app.TuesdayComposeSemanticUiTest"
    fi
    echo "==> WCAG 2.2 Semantics Report: $PROJECT_DIR/app/build/reports/wcag_jvm_semantics_audit.md"
    ;;

  commit)
    echo "==> [Commit Gate: Tier 1 + Tier 2] Running All Unit + JVM Semantic Compose UI Tests..."
    ./gradlew testDebugUnitTest
    echo "==> WCAG 2.2 Semantics Report: $PROJECT_DIR/app/build/reports/wcag_jvm_semantics_audit.md"
    ;;

  t3|session)
    echo "==> [End-of-Session Gate: Tier 1 + Tier 2 + Tier 3 Hardware E2E]..."
    ./gradlew testDebugUnitTest connectedDebugAndroidTest
    ;;

  sprint)
    echo "==> [End-of-Sprint Gate: Tier 1-3 + Android Accessibility Lint + WCAG Audit Summary]..."
    ./gradlew testDebugUnitTest lintDebug
    if adb devices | grep -q "device$"; then
      ./gradlew connectedDebugAndroidTest
    else
      echo "WARN: No physical Android device attached via ADB; skipped Tier 3 connectedDebugAndroidTest."
    fi
    if [ -f "app/build/reports/wcag_jvm_semantics_audit.md" ]; then
      echo ""
      echo "=== WCAG 2.2 JVM Semantics Audit Summary ==="
      head -n 9 app/build/reports/wcag_jvm_semantics_audit.md
    fi
    ;;

  milestone)
    echo "==> [Milestone Gate: Tier 1-4 Automated Suite + Hardware E2E]..."
    ./gradlew testDebugUnitTest lintDebug connectedDebugAndroidTest
    echo ""
    echo "==> Automated T1-T3 complete. Perform Tier 4 Live Server & Sensor spot-check on Pixel 5:"
    echo "    1. adb shell am start -a android.intent.action.VIEW -d 'tuesday://api-endpoint/prod' sh.tuesday.app.debug"
    echo "    2. adb shell am start -a android.intent.action.VIEW -d 'tuesday://add-device?mode=photo&q=Ubiquiti&clues=UCG-Ultra' sh.tuesday.app.debug"
    ;;

  t5|release)
    echo "==> [Release Gate: Tier 1-2 + Lint + R8/ProGuard Minified Release Bundle]..."
    ./gradlew testDebugUnitTest lintDebug assembleRelease bundleRelease
    ;;

  *)
    echo "Usage: $0 {t1|t2|commit|t3|session|sprint|milestone|t5|release} [optional_t2_method_filter]"
    exit 1
    ;;
esac
