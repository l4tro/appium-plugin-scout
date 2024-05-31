# JMH benchmarks

This folder contains JMH benchmarks used to evaluate the performance of different versions of the Appium plugin for Scout. Included are versions of both release candidates of the plugin with the required changes and tweaks to the plugin code to enable accurate benchmarking. Also included are build files that can be used to run the benchmarks.
Also included are benchmarks for SeleniumPlugin which were used in a trade-off analysis on the performance efficiency of AppiumPlugin.

## SeleniumPlugin
Due to unresolved questions (at the time of writing) regarding the precise licensing terms of SeleniumPlugin, our tweaked version of that plugin is not included in this repository. However, the benchmarks for SeleniumPlugin can still be run by applying the steps outlined in the selenium-plugin-tweaks.md file in the selenium-plugin subfolder.

## Instructions

The contents of each subfolder in this directory is to be placed in the /Scout directory of the Scout/Hivemind release.
The number of iterations and other settings can be adjusted in BenchmarkConstants.java file.

### Running AppiumPlugin benchmarks
Before running the benchmarks for AppiumPlugin, the following steps must be taken:
- The Appium server must be started manually on port 4723
- The Android emulator must be running and have the chosen SUT open in the foreground and in the desired state before running the benchmarks

Benchmarks can then be run using the following command:
```./gradlew jmh```

### Running SeleniumPlugin benchmarks
Before running the benchmarks for SeleniumPlugin, the following steps must be taken:
- A compatible version of Chrome/Chromium must be installed on the system
- The fields RESOLUTION and TEST_URL in BenchmarkConstants.java must be set. For a comparison with AppiumPlugin, these should be set to the resolution of the device being used for the benchmarks and the URL of the SUT respectively. 

Benchmarks can then be run using the following command:
```./gradlew jmh```
