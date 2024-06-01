# Data
This folder contains the gathered data from JFR profiling sessions and JMH benchmarks.

## Structure
This folder is structured as the following:

├── JFR/ <br>
│ ├── Appium/ <br>
│ │ ├── [PWA name]/ <br>
│ │ │ ├── CPU/ <br>
│ │ │ ├── Memory/ <br>
│ │ │ ├── RC2_CPU/ <br>
│ │ │ │ ├── [PWA name]/ <br>
│ ├── Selenium/ <br>
│ │ ├── [PWA name]/ <br>
│ │ │ ├── CPU/ <br>
│ │ │ ├── Memory/ <br>
├── JMH/ <br>
│ ├── Native/ <br>
│ │ ├── RC1/ <br>
│ │ ├── RC2/ <br>
│ ├── PWA/ <br>
│ │ ├── RC1/ <br>
│ │ ├── RC2/ <br>

## Instructions
VisualVM was used to analyse the JFR data with the aim of getting valuable insights of where the bottlnecks were present in the plugins to allow for further benchmarking of these specifc methods.

### Analysing JFR Data
1. **Open VisualVM:**
    - Launch VisualVM.
2. **Load JFR Files:**
    - Navigate to the `JFR` folder in the project directory.
    - Load the desired JFR files from the `CPU` or `Memory` subdirectories.
3. **Analyze CPU Profiling Data:**
    - Open the `CPU` profiling data for the relevant PWA under the `Appium` or `Selenium` directories.
    - Look for methods with high CPU usage to identify potential bottlenecks.
4. **Analyze Memory Profiling Data:**
    - Open the `Memory` profiling data for the relevant PWA under the `Appium` or `Selenium` directories.
    - Examine the memory usage patterns and identify any potential memory leaks or excessive memory consumption.

### Analysing JMH Benchmarks
1. **Navigate to JMH Directory:**
    - Navigate to the `JMH` folder in the project directory.
2. **Load Benchmark Data:**
    - Open the `Native` or `PWA` subdirectories, and then choose between `RC1` or `RC2`.
    - Load the benchmark data files for analysis.
3. **Analyze Benchmark Results:**
    - Examine the benchmark results for different plugins.
    - Compare the performance in the form of execution time.
    - This information is used to guide further optimisation and refinements of the AppiumPlugin.

By following these steps, the reproducibility of our results are possible.