package benchmark;

import com.symatiq.states.AppState;
import com.symatiq.states.StateController;
import io.appium.java_client.android.AndroidDriver;
import org.openjdk.jmh.annotations.*;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.DesiredCapabilities;
import plugin.AppiumPlugin;
import plugin.SeleniumPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;

// ============================== Pre-requisites =======================================================================
// A running emulated Android device with the desired SUT opened and navigated to the desired screen.
// The Appium server must be manually started on the default port 4723.
// A class file named BenchmarkConstants must be present in the same directory as this file definining the constants
// used by the class level decorators.
// =====================================================================================================================

@Fork(BenchmarkConstants.FORKS)
@State(Scope.Benchmark)
@Warmup(iterations = BenchmarkConstants.WARMUP_ITERATIONS, time = BenchmarkConstants.WARMUP_TIME, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = BenchmarkConstants.ITERATIONS, time = BenchmarkConstants.ITERATION_TIME, timeUnit = TimeUnit.SECONDS)
@Timeout(time = BenchmarkConstants.TIMEOUT, timeUnit = TimeUnit.MINUTES)
public class AppiumCreateWidgets {
    private static final AppiumPlugin appiumPlugin = new AppiumPlugin();
    AndroidDriver driver;
    private static List<WebElement> elements;
    private static Field currentState;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        Method appiumStatusMethod = AppiumPlugin.class.getDeclaredMethod("appiumStatus");
        appiumStatusMethod.setAccessible(true);

        currentState = AppiumPlugin.class.getDeclaredField("currentState");
        currentState.setAccessible(true);

        Method getDesiredCapabilitiesMethod = AppiumPlugin.class.getDeclaredMethod("getDesiredCapabilities");
        getDesiredCapabilitiesMethod.setAccessible(true);
        Object desiredCapabilities = getDesiredCapabilitiesMethod.invoke(appiumPlugin);

        try {
            driver = new AndroidDriver(new URL("http://127.0.0.1:4723"), (DesiredCapabilities) desiredCapabilities);
            driver.context("NATIVE_APP");
            appiumPlugin.setWebDriver(driver);
            StateController.setSessionState(StateController.SessionState.RUNNING);
            currentState.set(appiumPlugin, StateController.getCurrentState());
            Thread.sleep(3000);
            elements = driver.findElements(By.xpath("//*"));
        } catch (MalformedURLException e) {
            System.err.println("Error occurred while initializing the WebDriver: " + e.getMessage());
        }
    }

    @Setup(Level.Invocation)
    public void setupInvocation() throws IllegalAccessException {
        ((AppState) currentState.get(appiumPlugin)).removeWidgets("AppiumPlugin");
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @BenchmarkMode(Mode.AverageTime)
    public void createWidgetsBenchmark() {
        try {
            if (elements.isEmpty()) {
                throw new Exception("No elements");
            }
            appiumPlugin.createWidgets(elements);
        } catch (Exception e) {
            System.out.println("Error in createWidgets: " + e.getMessage());
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        driver.quit();
    }
}
