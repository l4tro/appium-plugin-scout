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
- Appium and the various Android SDK tools (```adb```, ```emulator``` etc.) must be added to the PATH environment variable

## Usage
In order to use any version the plugin it must be placed into the plugins folder of the Scout/Hivemind release (Scout/src/main/java/plugin). The default Scout/build.gradle.kts file must also be replaced with the build.gradle.kts file provided in the relevant release candidate folder.
Scout/Hivemind can then be built using the ```gradle build``` command.


## A note on SeleniumPlugin
Due to unresolved questions (at the time of writing) regarding the precise licensing terms of SeleniumPlugin, our tweaked version of that plugin is not included in this repository.
We have made two categories of changes to the SeleniumPlugin code:
- Necessary simple adjustments to the SeleniumPlugin code to run benchmarks, these can be found in the selenium-plugin-tweaks.md file in the benchmarks/selenium-plugin folder of this repository.
- Adjustments in order to (re-)enable scrolling using page-down and page-up keys in the SeleniumPlugin code. These are more complex in nature and not strictly necessary for benchmarking purposes.

Our version of the SeleniumPlugin code will be made available once the licensing terms have been clarified. 