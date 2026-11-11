#!/usr/bin/env bash
set -euo pipefail
for class in iecxiv.meshfabric.ui.management.AgentEnrollmentFormTest iecxiv.meshfabric.ui.management.AgentInviteLinkFormTest iecxiv.meshfabric.ui.navigation.NavigationRegressionTest; do
  bash ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class="$class" --no-daemon --stacktrace
  mkdir -p "/tmp/agent-enrollment-android/$class"
  cp -a app/build/outputs/androidTest-results/connected/. "/tmp/agent-enrollment-android/$class/"
done
