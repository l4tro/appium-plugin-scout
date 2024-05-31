package benchmark;

import com.symatiq.states.StateController;
import com.symatiq.widgets.Widget;
import org.openjdk.jmh.annotations.*;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import plugin.SeleniumPlugin;
import plugin.WidgetIdentifier;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.TimeUnit;

// ============================== Pre-requisites =======================================================================
// A class file named BenchmarkConstants must be present in the same directory as this file defining the constants
// used by the class level decorators as well as the variable TEST_URL containing the URL of the desired SUT and
// the variable RESOLUTION containing the desired resolution of SUT browser window as a openqa.selenium.Dimension object.
// =====================================================================================================================

@Fork(BenchmarkConstants.FORKS)
@State(Scope.Benchmark)
@Warmup(iterations = BenchmarkConstants.WARMUP_ITERATIONS, time = BenchmarkConstants.WARMUP_TIME, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = BenchmarkConstants.ITERATIONS, time = BenchmarkConstants.ITERATION_TIME, timeUnit = TimeUnit.SECONDS)
@Timeout(time = BenchmarkConstants.TIMEOUT, timeUnit = TimeUnit.MINUTES)
public class SeleniumGetSeleniumCapture {
    private static final SeleniumPlugin seleniumPlugin = new SeleniumPlugin();
    static WebDriver driver;
    static List<Widget> widgets;

    @Setup(Level.Trial)
    public static void setup() {
        String testUrl;
        try {
            testUrl = System.getenv("TEST_URL");
            if (testUrl == null || testUrl.isEmpty()) {
                testUrl = BenchmarkConstants.TEST_URL;
            }
        } catch (Exception e) {
            testUrl = BenchmarkConstants.TEST_URL;
        }
        System.out.println("Test URL: " + testUrl);

        //boolean headless = true; // Modified the getWebDriver method in the SeleniumPlugin to always run in headless mode in this branch.
        try {
            Field currentState = SeleniumPlugin.class.getDeclaredField("currentState");
            currentState.setAccessible(true);

            WidgetIdentifier.initIdentifiers();
            seleniumPlugin.setHomeLocatorManually(testUrl); //Set this is in the BenchmarkConstants file

            driver = SeleniumPlugin.getWebDriver("AndroidChrome"); //Possibly change this to "Chrome" if the useragent spoofing doesn't do anything.

            if (driver != null) {
                driver.get(testUrl);
                driver.manage().window().setSize(BenchmarkConstants.RESOLUTION); //Set this is in the BenchmarkConstants file
                SeleniumPlugin.setWebDriver(driver);
                StateController.setSessionState(StateController.SessionState.RUNNING);
                currentState.set(seleniumPlugin, StateController.getCurrentState());
            } else {
                System.out.println("Driver is null");
            }
        } catch (Exception e) {
            System.out.println("Error when performing setup in SeleniumBenchmark" + e.getMessage());;
        }
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @BenchmarkMode(Mode.AverageTime)
    public void getSeleniumCaptureBenchmark() {
        seleniumPlugin.getSeleniumCapture();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        driver.quit();
    }
}
