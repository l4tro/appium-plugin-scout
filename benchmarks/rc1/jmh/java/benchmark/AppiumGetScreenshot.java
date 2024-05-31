package benchmark;

import io.appium.java_client.android.AndroidDriver;
import org.openjdk.jmh.annotations.*;
import org.openqa.selenium.remote.DesiredCapabilities;
import plugin.AppiumPlugin;

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;


// ============================== Pre-requisites =======================================================================
// A running emulated Android device with the desired SUT opened and navigated to the desired screen.
// The Appium server must be manually started on the default port 4723.
// A class file named BenchmarkConstants must be present in the same directory as this file defining the constants
// used by the class level decorators.
// =====================================================================================================================

@Fork(BenchmarkConstants.FORKS)
@State(Scope.Benchmark)
@Warmup(iterations = BenchmarkConstants.WARMUP_ITERATIONS, time = BenchmarkConstants.WARMUP_TIME, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = BenchmarkConstants.ITERATIONS, time = BenchmarkConstants.ITERATION_TIME, timeUnit = TimeUnit.SECONDS)
@Timeout(time = BenchmarkConstants.TIMEOUT, timeUnit = TimeUnit.MINUTES)
public class AppiumGetScreenshot {
    private static final AppiumPlugin appiumPlugin = new AppiumPlugin();
    AndroidDriver driver;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        Method appiumStatusMethod = AppiumPlugin.class.getDeclaredMethod("appiumStatus");
        appiumStatusMethod.setAccessible(true);

        Method getDesiredCapabilitiesMethod = AppiumPlugin.class.getDeclaredMethod("getDesiredCapabilities");
        getDesiredCapabilitiesMethod.setAccessible(true);
        Object desiredCapabilities = getDesiredCapabilitiesMethod.invoke(appiumPlugin);

        try {
            driver = new AndroidDriver(new URL("http://127.0.0.1:4723"), (DesiredCapabilities) desiredCapabilities);
            driver.context("NATIVE_APP");
            appiumPlugin.setWebDriver(driver);
            Thread.sleep(3000);

        } catch (MalformedURLException e) {
            System.err.println("Error occurred while initializing the WebDriver: " + e.getMessage());
        }
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @BenchmarkMode(Mode.AverageTime)
    public void getScreenshotBenchmark() {
        appiumPlugin.getScreenshot();
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        driver.quit();
    }
}
