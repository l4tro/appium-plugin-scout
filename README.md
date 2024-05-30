# appium-plugin-scout

## Release Candidates

Each release candidate folder (appium-plugin-rc1 and appium-plugin-rc2) contains the latest version of the respective candidates along with updated build files.

## JMH Benchmarks
The benchmark folder contains JMH benchmarks used to evaluate the performance of different versions of the Appium plugin for Scout. Included are versions of both release candidates of the plugin with the required changes and tweaks to the plugin code to enable accurate benchmarking. Also included are build files that can be used to run the benchmarks.
For more details on how to run the benchmarks, see the README.md file in the benchmark folder.

## Requirements
- Access to a release Scout/Hivemind (for more information, see https://symatiq.com/)
- Java 17 SDK
- Gradle
- Appium along with the UiAutomator2 driver
- Android SDK
- Appium and the various Android SDK tools (```adb``` etc.) must be added to the PATH environment variable

## Usage
In order to use any version the plugin it must be placed into the plugins folder of the Scout/Hivemind release (Scout/src/main/java/plugin). The default Scout/build.gradle.kts file must also be replaced with the build.gradle.kts file provided in the release candidate folder.
Scout/Hivemind can then be built using the ```gradle build``` command.