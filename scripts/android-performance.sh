#!/usr/bin/env bash
# Capture a local snapshot from a connected Android device (no uploads).
# Usage: bash scripts/android-performance.sh before
#        Exercise the same feed/reels workload for 60 seconds.
#        bash scripts/android-performance.sh after
# Repeat on the baseline APK and candidate APK, same phone/network/brightness,
# both warm/cold cache and data-saver on/off. Use three runs per scenario.
# Compare gfxinfo jank/frame timings, meminfo PSS, and per-app batterystats.
# Battery/network counters are cumulative: compare deltas, not raw totals.
# Firebase opt-in traces supplement this with feed_first_post_layout and
# video_visible_session (first_frame_ms, buffer_ms, dropped_frames).
set -euo pipefail
command -v adb >/dev/null || { echo 'Install Android platform-tools (adb) first.' >&2; exit 1; }
label="${1:-snapshot}"
[[ "$label" =~ ^[a-zA-Z0-9_-]+$ ]] || { echo 'Use a simple alphanumeric label.' >&2; exit 1; }
adb get-state >/dev/null
package=com.vivid.app
output=".cache/android-performance/$(date -u +%Y%m%dT%H%M%SZ)-$label"
mkdir -p "$output"
adb shell getprop ro.product.model > "$output/device.txt"
adb shell getprop ro.build.version.release >> "$output/device.txt"
adb shell dumpsys meminfo "$package" > "$output/memory.txt"
adb shell dumpsys gfxinfo "$package" framestats > "$output/frames.txt"
adb shell dumpsys batterystats --charged "$package" > "$output/battery-network.txt"
printf 'Saved local diagnostic snapshot to %s\n' "$output"
printf 'No benchmark was run automatically. Compare equivalent workloads and counter deltas.\n'
