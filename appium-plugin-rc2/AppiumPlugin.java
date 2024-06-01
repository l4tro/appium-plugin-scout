package plugin;

import com.symatiq.Scout;
import com.symatiq.actions.*;
import com.symatiq.extension.PluginAbstract;
import com.symatiq.extension.PluginAbstractReport;
import com.symatiq.states.AppState;
import com.symatiq.states.StateController;
import com.symatiq.widgets.Widget;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Pause;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class AppiumPlugin extends PluginAbstract implements PluginAbstractReport {
    private static String appiumServerUrl = "http://127.0.0.1:"; // Possibly add a way to change this in the future if changes are made to the StartSessionDialog.
    private static AndroidDriver webDriver = null;
    private static Widget lastClickedWidget = null;
    private static boolean sourceChanged = false;
    private static BufferedImage lastCapture = null;
    private static boolean updatingCapture = false;
    private static String lastSource = null;
    private static Point start = null;
    private static Point end = null;
    private static AppState currentState = null;
    private static Process appiumServerProcess;
    private static String targetPackage = null;
    private static long lastExecutionTime = 0;
    private static final String[] CHECK_TAGS = {
            "TextView",
            "EditText",
            "Button",
            "CheckBox",
            "RadioButton",
            "Spinner",
            "ListView",
            "RecyclerView",
            "LinearLayout",
            "RelativeLayout",
            "FrameLayout",
            "ConstraintLayout",
            "ScrollView",
            "TableLayout",
            "TableRow",
            "ImageView",
            "WebView"
    };

    private static int textElementNumber = 0;
    private static boolean isPWA = false;
    private static List<Widget> previouslyVisible = new CopyOnWriteArrayList<>();
    private static boolean performReady = true;
    private static int windowWidth = 1080; //Purpose of this? Not a fan of hard coding. We can use the session-dialogue resolution fields.
    private static int windowHeight = 1920;
    private static boolean typingInProgress = false;
    private static long timeSinceLastCapture = 0;
    private static String emulatorString;
    private static final boolean debugMode = false; // Set to true to enable additional console output

    // ==============================================================================
    // =========================== PLUGIN CONTROLLER METHODS ========================
    // ==============================================================================
    /**
     * Called by Scout when the plugin is enabled.
     */
    public void enablePlugin() {
        if(debugMode) System.out.println("Enabling AppiumPlugin...");
        startAppiumServer(4723); //Hardcoded for now. Ideally this would be set by a modified StartSessionDialog

        // Check if the Appium server has started
        boolean serverStarted = waitForAppiumServer();
        if (serverStarted) {
            StateController.displayMessage("Appium server started");
        } else {
            StateController.displayMessage("Failed to start Appium server");
        }
    }

    /**
     * Called by Scout when the plugin is disabled. Teardown of any active dependencies.
     */
    public void disablePlugin() {
        if(debugMode) System.out.println("Disabling AppiumPlugin...");
        quitWebDriver();
        stopEmulator(emulatorString);
        stopAppiumServer();
    }

    /**
     * Called by Scout when starting a test session with the plugin enabled.
     */
    public void startSession() {
        if(debugMode) System.out.println("Starting session...");

        // Ensure that the Appium server is running and start it if it is not
        if(!appiumStatus()) {
            if(debugMode) System.out.println("No Appium server when starting session");
            startAppiumServer(4723); 

            // Check if the Appium server has started
            boolean serverStarted = waitForAppiumServer();
            if (serverStarted) {
                StateController.displayMessage("Appium server started");
            } else {
                StateController.displayMessage("Failed to start Appium server");
            }
        }

        //StartSession ProductView field sets the desired AVD name
        String avdName = StateController.getProductView();

        // If no emulator matching the AVD name from ProductView is running, start it.
        // TODO:Look into handling running but not matching emulators.
        //if(!emulatorStatus()) {
        if(!emulatorStatus(avdName)) {
            startEmulator(avdName);
        }

        WidgetIdentifier.initIdentifiers();
        targetPackage = stripURL(StateController.getHomeLocator()); // Get the target package (and activity) from HomeLocator
        webDriver = initAppiumDriver();
        if (webDriver != null) {
            if(debugMode) System.out.println("Driver initialized");
            StateController.setSessionState(StateController.SessionState.RUNNING);
            StateController.displayMessage("Session started");
            currentState = StateController.getCurrentState();

            if (!Objects.equals(targetPackage, "")) { // TargetPackage was gathered from HomeLocator.
                webDriver.activateApp(targetPackage);
            } else { // Set targetPackage to the currently opened application this will be 'home' location.
                String currentPackage = webDriver.getCurrentPackage();
                if (currentPackage != null && currentPackage.contains("chrome")) {
                    isPWA = true;
                    if (swapToWebViewContext()) {
                        targetPackage = webDriver.getCurrentUrl();
                        webDriver.context("NATIVE_APP");
                    }
                } else {
                    targetPackage = currentPackage + "/" + webDriver.currentActivity();
                }
            }
            Dimension windowSize = webDriver.manage().window().getSize();
            windowWidth = windowSize.getWidth();
            windowHeight = windowSize.getHeight();
        } else {
            System.err.println("Driver is null");
        }
    }

    /**
     * Called by Scout when stopping a test session with the plugin enabled.
     */
    public void stopSession() {
        if(debugMode) System.out.println("Stopping session...");
        StateController.setSessionState(StateController.SessionState.STOPPED);
        StateController.displayMessage("Session stopped");
        quitWebDriver();
    }

    /**
     * Called by Scout on a timer to get the current state of the SUT display.
     * @return a BufferedImage of the SUT display.
     */
    public BufferedImage getCapture() throws WebDriverException {
        if (StateController.isOngoingSession()) {
            String avdName = StateController.getProductView();
            boolean statusOK = true;

            if(!appiumStatus()) {
                StateController.displayMessage("Lost connection to the Appium server");
                statusOK = false;
                StateController.stopSession();
            } else if(!emulatorStatus(avdName)) {
                StateController.displayMessage("Lost connection to the emulated device");
                statusOK = false;
                StateController.stopSession();
            }

            if (statusOK) {
                updateCapture();
                return lastCapture;
            }
        }
        return null;
    }

    /**
     * Called by Scout's when an action is performed in the SUT representation.
     * @param action the action to perform.
     */
    public void performAction(Action action) {
        if (!StateController.isRunningSession() || action.isToolbarAction()) {
            return;
        }
        if (performReady) {
            performAppiumAction(action);
        }
    }

    /**
     * Returns the product views supported by the plugin.
     * @return an array of product views.
     */
    public String[] getProductViews() {
        List<String> avdList = new ArrayList<>();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("emulator", "-list-avds");
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                avdList.add(line.trim());
            }
            process.waitFor();
        } catch (Exception e) {
            System.err.println("Error occurred while getting the list of Android Virtual Devices: " + e.getMessage());
        }
        return avdList.toArray(new String[0]);
    }

    /**
     * Called from PluginController
     */
    public void changeState() {
        if(debugMode) System.out.println("State changed");
        currentState = StateController.getCurrentState();
    }

    // ==============================================================================
    // =========================== DEPENDENCY CONFIGURATION =========================
    // ==============================================================================
    /**
     * Inits a WebDriver in the Appium server and returns it
     * @return the WebDriver.
     */
    private AndroidDriver initAppiumDriver() {
        try {
            AndroidDriver newDriver = new AndroidDriver(new URL(appiumServerUrl), getDesiredCapabilities());
            newDriver.context("NATIVE_APP");
            return newDriver;
        } catch (Exception e) {
            System.err.println("Error occurred while initializing the WebDriver: " + e.getMessage());
            return null;
        }
    }

    /**
     * Starts the Appium server
     * @param port the port to start the server on.
     */
    private void startAppiumServer(int port){
        try {
            if (!appiumStatus()) {
                String command = "appium -a 127.0.0.1 -p " + port;
                appiumServerUrl += port;
                appiumServerProcess = Runtime.getRuntime().exec(command);
            }
        } catch (Exception e) {
            System.err.println("Error occurred while starting the Appium server: " + e.getMessage());
        }
    }

    /**
     * Stops the Appium server.
     */
    private void stopAppiumServer() {
        if (appiumServerProcess != null) {
            appiumServerProcess.destroy();
            if(debugMode) System.out.println("Appium server stopped");
        } else {
            if(debugMode) System.out.println("Appium server instance not found");
        }
    }

    /**
     * Waits for the Appium server to start.
     * @return true if the server has started, false otherwise.
     */
    private boolean waitForAppiumServer() {
        boolean serverStarted = false;
        int attempts = 0;
        
        while (!serverStarted && attempts < 6) {
            try {
                Thread.sleep(1000);
                serverStarted = appiumStatus();
            } catch (InterruptedException e) {
                System.err.println("Error occurred while waiting for the Appium server to start: " + e.getMessage());
            }
            attempts++;
        }
        return serverStarted;
    }

    /**
     * Quits the WebDriver.
     */
    private void quitWebDriver() {
        if(debugMode) System.out.println("Quitting WebDriver...");
        if  (webDriver != null) {
            webDriver.quit();
        }
    }

    /**
     * Checks if the Appium server is running.
     * @return true if the server is running, false otherwise.
     */
    private boolean appiumStatus() {
        try {
            URL url = new URL(appiumServerUrl + "/status");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                if(debugMode) System.out.println("Appium server started");
                return true;
            } else {
                System.err.println("Appium server is not running");
                return false;
            }
        } catch (IOException e) {
            System.err.println("Error occurred while checking Appium server status: " + e.getMessage());
            return false;
        }
    }

    /**
     * Starts an Android emulator matching the given AVD name.
     * @param avdName the name of the emulator.
     */
    private void startEmulator(String avdName) {
        if(debugMode) System.out.println("Starting emulator...");
        List<String> command = new ArrayList<>();
        command.add("emulator");
        command.add("-avd");
        command.add(avdName);

        if (StateController.isHeadlessBrowser()) {
            command.add("-no-window");
        }

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();

            CountDownLatch latch = new CountDownLatch(1);
            new Thread(() -> waitForEmulatorBoot(avdName, latch)).start();
            latch.await();
            if(debugMode) System.out.println("Waiting for boot completed...");
        } catch (IOException | InterruptedException e) {
            System.err.println("Error occurred while starting the emulator: " + e.getMessage());
        }
    }

    /**
     * Stops a currently running emulator.
     * @param emulatorId the ID of the emulator to stop.
     */
    private void stopEmulator(String emulatorId) {
        if (debugMode) System.out.println("Stopping emulator...");
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("adb", "-s", emulatorId, "emu", "kill");
            processBuilder.start();
        } catch (IOException e) {
            System.err.println("Error occurred while stopping the emulator: " + e.getMessage());
        }
    }

    /**
     * Takes over processing until the emulator has booted or the boot process times out.
     * @param avdName the name of the emulator.
     * @param latch the CountDownLatch to signal when the boot process is complete.
     */
    private void waitForEmulatorBoot(String avdName, CountDownLatch latch) {
        boolean bootCompleted = false;
        int maxRetries = 240;
        int retryCount = 0;

        while (!bootCompleted && retryCount < maxRetries) {
            StateController.displayMessage("Waiting for emulator to boot...");
            if(debugMode) System.out.println("Waiting for emulator to boot... Retries: " + retryCount);
            if(emulatorStatus(avdName)) {
                bootCompleted = true;
            }
            retryCount++;

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                System.err.println("Sleep interrupted while waiting for emulator to boot: " + e.getMessage());
            }
        }

        if (bootCompleted) {
            StateController.displayMessage("Emulator boot completed.");
            if(debugMode) System.out.println("Emulator boot completed.");
        } else {
            StateController.displayMessage("Emulator boot failed or timed out.");
            if(debugMode) System.out.println("Emulator boot failed or timed out.");
        }
        latch.countDown();
    }

    /**
     * Checks the current status of the emulator.
     * @return true if any emulator is running and has finished booting, false otherwise.
     */
    private static boolean emulatorStatus() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("adb", "devices");
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("emulator-")) {
                    String emulatorId = line.split("\\s")[0];
                    if (bootOk(emulatorId)) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error occurred while checking the status of the emulator: " + e.getMessage());
        }
        return false;
    }

    /**
     * Checks the current status of the emulator.
     * @param expectedAvdName the name of the emulator (as displayed by 'emulator -list-avds').
     * @return true if the emulator is running, false otherwise.
     */
    private static boolean emulatorStatus(String expectedAvdName) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("adb", "devices");
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("emulator-")) {
                    String emulatorId = line.split("\\s")[0];
                    String avdName = getActualAvdName(emulatorId);
                    if(debugMode) System.out.println("Name of the emulator: " + avdName + " Expected name: " + expectedAvdName);
                    if (avdName.equals(expectedAvdName)) {
                        if(debugMode) System.out.println("Name matched");
                        emulatorString = emulatorId;
                        // Check if the emulator has finished booting
                        if (bootOk(emulatorId)) return true;
                    }
                    // Consider how to handle the case where an emulator is running but not the one matching ProductView
                }
            }
        } catch (IOException e) {
            System.err.println("Error occurred while checking the status of the emulator: " + e.getMessage());
        }
        return false;
    }

    /**
     * Checks if the emulator has finished booting.
     * @param emulatorId the ID of the emulator.
     * @return true if the emulator has finished booting, false otherwise.
     */
    private static boolean bootOk(String emulatorId) throws IOException {
        if (debugMode) System.out.println("Checking if boot is completed... Emulator ID: " + emulatorId);
        ProcessBuilder bootCompletedProcessBuilder = new ProcessBuilder("adb", "-s", emulatorId, "shell", "getprop", "sys.boot_completed");
        Process bootCompletedProcess = bootCompletedProcessBuilder.start();
        BufferedReader bootCompletedReader = new BufferedReader(new InputStreamReader(bootCompletedProcess.getInputStream()));
        String bootCompleted;
        while ((bootCompleted = bootCompletedReader.readLine()) != null) {
            if (debugMode) System.out.println("Current line: " + bootCompleted);
            if (bootCompleted.trim().equals("1")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the name of a currently running emulator.
     * @param emulatorId the ID of the emulator.
     * @return the name of the emulator.
     */
    private static String getActualAvdName(String emulatorId) throws IOException {
        String port = emulatorId.split("-")[1];
        Socket socket = new Socket("localhost", Integer.parseInt(port));
        BufferedReader socketReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter socketWriter = new PrintWriter(socket.getOutputStream());
        String avdName;

        socketWriter.println("avd name");
        socketWriter.flush();

        while (!(avdName = socketReader.readLine()).equals("OK")) {
            // Keep reading until we encounter the "OK" line
            if (debugMode) System.out.println("Waiting for OK from emulator... Current line: " + avdName);
        }
        if (debugMode) System.out.println("OK given...");
        avdName = socketReader.readLine();
        return avdName;
    }

    /**
     * Returns the desired capabilities for the AppiumDriver.
     * @return the desired capabilities.
     */
    private DesiredCapabilities getDesiredCapabilities() {
        DesiredCapabilities desiredCapabilities = new DesiredCapabilities();

        try {
            Process process = Runtime.getRuntime().exec("adb devices -l");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            String deviceUdid = null;

            while ((line = reader.readLine()) != null) {
                if (line.contains("emulator")) {
                    deviceUdid = line.split("\\s")[0];
                    break;
                }
            }

            if (deviceUdid != null) {
                desiredCapabilities.setCapability("udid", deviceUdid);

                process = Runtime.getRuntime().exec("adb -s " + deviceUdid + " shell getprop ro.product.model");
                reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String deviceName = reader.readLine();
                desiredCapabilities.setCapability("deviceName", deviceName);

                process = Runtime.getRuntime().exec("adb -s " + deviceUdid + " shell getprop ro.build.version.release");
                reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String platformVersion = reader.readLine();
                desiredCapabilities.setCapability("platformVersion", platformVersion);

                process = Runtime.getRuntime().exec("adb emu avd name");
                reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String avdName = reader.readLine();
                desiredCapabilities.setCapability("avd", avdName);
            }

            desiredCapabilities.setCapability("platformName", "Android");
            desiredCapabilities.setCapability("automationName", "UiAutomator2");
            desiredCapabilities.setCapability("noReset", "true");
            desiredCapabilities.setCapability("unicodeKeyboard", true);
            desiredCapabilities.setCapability("resetKeyboard", true);
        } catch (IOException e) {
            System.err.println("Error occurred when detecting and attempting to get info from emulated device: " + e.getMessage());
        }

        return desiredCapabilities;
    }

    // ==============================================================================
    // =========================== SUT INTERACTION ==================================
    // ==============================================================================
    /**
     * Perform action(s) on the SUT via Appium.
     * @param action to perform.
     */
    private static void performAppiumAction(Action action) { //TODO: Make sure that the clicked widget is actually only clicking on the element corresponding to the widget.
        performReady = false;
        if (action instanceof MouseScrollAction mouseScrollAction) {
            StateController.setSelectedWidgetNo(StateController.getSelectedWidgetNo() + mouseScrollAction.getRotation());
            performReady = true;
            return;
        }

        if (!(action instanceof TypeAction)) {
            typingInProgress = false;
        }

        if (action instanceof LeftClickAction leftClickAction) {
            Widget widget = StateController.getWidgetAt(currentState, leftClickAction.getLocation());
            if (widget.getWidgetSubtype() == Widget.WidgetSubtype.GO_HOME_ACTION) {
                widget.setWidgetStatus(Widget.WidgetStatus.LOCATED);
                goHome();
                StateController.insertWidget(widget, StateController.getStateTree());
                sourceChanged = true;
                performReady = true;
                return;
            }

            if (widget.getWidgetSubtype() == Widget.WidgetSubtype.DRAG_ACTION) {
                performSwipe( (Point) widget.getMetadata("start"), (Point) widget.getMetadata("end"));
                StateController.performWidget(widget);
                sourceChanged = true;
                performReady = true;
                return;
            }

            if (widget.getWidgetType() == Widget.WidgetType.CHECK) {
                handleCheckWidget(widget);
                lastClickedWidget = widget;
                performReady = true;
                return;
            }

            Rectangle area = widget.getLocationArea();
            if (area.contains(leftClickAction.getLocation().x, leftClickAction.getLocation().y)) {
                StateController.setSelectedWidgetNo(0);
                lastClickedWidget = widget;

                PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
                int centerX = area.x + area.width / 2;
                int centerY = area.y + area.height / 2;
                Sequence tapSequence = new Sequence(finger, 1)
                        .addAction(finger.createPointerMove(Duration.ofMillis(0), PointerInput.Origin.viewport(), centerX, centerY))
                        .addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                        .addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));

                webDriver.perform(Collections.singletonList(tapSequence));

                if (widget.getWidgetSubtype() == Widget.WidgetSubtype.TYPE_ACTION) {
                    performReady = true;
                    handleTyping(action, widget);
                    //sourceChanged = true;
                    return;
                }
                widgetClickHandle(widget);
                sourceChanged = true;
            }
        } else if (action instanceof TypeAction typeAction) {
            performReady = true;
            handleTyping(typeAction, lastClickedWidget);
            //sourceChanged = true;
        } else if (action instanceof DragDropAction dragDropAction) {
            long currentTimeNano = System.nanoTime();
            long throttleInterval = 500_000_000L;
            if ((currentTimeNano - lastExecutionTime) > throttleInterval) {
                lastExecutionTime = currentTimeNano;
                end = dragDropAction.getLocation();
                performSwipe(start, end);

                Widget scrollWidget = new Widget();
                scrollWidget.setLocationArea(new Rectangle(10, 10, 80, 40));
                scrollWidget.setWidgetType(Widget.WidgetType.ACTION);
                scrollWidget.setWidgetSubtype(Widget.WidgetSubtype.DRAG_ACTION);
                scrollWidget.putMetadata("id", "Swipe");
                scrollWidget.putMetadata("start", start);
                scrollWidget.putMetadata("end", end);
                previouslyVisible.addAll(StateController.getCurrentState().getVisibleWidgets());
                StateController.insertWidget(scrollWidget, scrollWidget.getNextState());
                scrollWidget.setWidgetStatus(Widget.WidgetStatus.LOCATED);

                sourceChanged = true;
            }
        } else if (action instanceof DragStartAction dragStartAction) {
            start = dragStartAction.getLocation();
        } else if (action instanceof GoHomeAction) {
            goHome();
            Widget goHome = new Widget();
            goHome.setWidgetType(Widget.WidgetType.ACTION);
            goHome.setWidgetSubtype(Widget.WidgetSubtype.GO_HOME_ACTION);
            goHome.setLocationArea(new Rectangle(10, 10, 80, 40));
            goHome.putMetadata("id", "Home");
            StateController.insertWidget(goHome, StateController.getStateTree());
            goHome.setWidgetStatus(Widget.WidgetStatus.LOCATED);
            sourceChanged = true;
        } else if (action instanceof LongClickAction longClickAction) {
            Widget widget = StateController.getWidgetAt(currentState, longClickAction.getLocation());
            Rectangle area = widget.getLocationArea();
            PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
            Sequence longClickSequence = new Sequence(finger, 0)
                    .addAction(finger.createPointerMove(Duration.ofMillis(0), PointerInput.Origin.viewport(), area.x, area.y))
                    .addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                    .addAction(new Pause(finger, Duration.ofMillis(1000)))
                    .addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
            webDriver.perform(Collections.singletonList(longClickSequence));
            widgetClickHandle(widget);
            sourceChanged = true;
        }
        performReady = true;
    }

    /**
     * Handles the information needed when a click action is performed on a widget.
     * @param widget the widget to handle.
     */
    private static void widgetClickHandle(Widget widget) {
        if (widget.getWidgetVisibility() == Widget.WidgetVisibility.HIDDEN || widget.getWidgetVisibility() == Widget.WidgetVisibility.SUGGESTION) {
            widget.setWidgetVisibility(Widget.WidgetVisibility.VISIBLE);
            widget.setComment(null);
            widget.setWidgetStatus(Widget.WidgetStatus.LOCATED);
            StateController.insertWidget(widget, widget.getNextState());
        } else { // This happens when the widget is visible (clicked when creating the test).
            StateController.performWidget(widget);
        }
    }

    /**
     * Returns to the initial activity of the session.
     */
    private static void goHome() {
        if (isPWA && (swapToWebViewContext())) {
            ((JavascriptExecutor) webDriver).executeScript("window.location.href='" + targetPackage + "';");
            webDriver.context("NATIVE_APP");
        } else {
            //webDriver.activateApp(targetPackage);
            try {
                Runtime.getRuntime().exec("adb shell am force-stop " + targetPackage.split("/")[0]);
                Thread.sleep(1000); // TODO: Hopefully this increases stability. Might need a more robust solution like actually checking if the app is closed.
                webDriver.activateApp(targetPackage); // TODO: Possibly change this to an adb command to keep things consistent.
            } catch (Exception e) {
                System.err.println(e.getMessage());
           }
        }
        previouslyVisible.clear();
    }

    // ==============================================================================
    // =========================== SUT REPRESENTATION ===============================
    // ==============================================================================
    /**
     * Gets a new screenshot of the SUT display and performs a call to update widgets.
     */
    private void updateCapture() {
        if (updatingCapture) {
            return;
        }
        updatingCapture = true;

        if(!sourceChanged) {
            hasSourceChanged();
        }

        if(sourceChanged) {
            lastCapture = getScreenshot();

            createWidgets();
            sourceChanged = false;
        } else if (Math.abs(System.currentTimeMillis() - timeSinceLastCapture) > 750*5) { //TODO:Is this really needed?
            lastCapture = getScreenshot();
        }
        updatingCapture = false;
    }

    /**
     * Takes a screenshot of the SUT display.
     * @return BufferedImage
     */
    public BufferedImage getScreenshot() {
        if(debugMode) System.out.println("Taking screenshot...");

        BufferedImage image = null;
        try {
            image = byteArrayToBufferedImage(webDriver.getScreenshotAs(OutputType.BYTES));
        } catch (Exception e) {
            System.err.println("Error occurred while taking a screenshot of the emulated device: " + e.getMessage());
        }
        timeSinceLastCapture = System.currentTimeMillis();
        return image;
    }

    /**
     * Checks if the source of the SUT display has changed.
     */
    private void hasSourceChanged() {
        String currentSource = webDriver.getPageSource();

        if (!currentSource.equals(lastSource)) {
            lastSource = currentSource;
            sourceChanged = true;
        }
    }

    /**
     * Creates widgets from the elements found in the SUT display.
     */
    public void createWidgets() { //TODO: Possibly utilise the executor service from RC1 here.
        if (typingInProgress) {
            return;
        }

        textElementNumber = 0;
        long start = System.currentTimeMillis();
        String pageSource = webDriver.getPageSource();
        Document doc = null;

        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            doc = builder.parse(new InputSource(new StringReader(pageSource)));
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }

        if (doc == null) {
            System.err.println("Failed to build document");
            return;
        }
        doc.getDocumentElement().normalize();
        ConcurrentLinkedQueue<Widget> widgets = new ConcurrentLinkedQueue<>();
        int numberOfThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);

        NodeList nodeList = doc.getElementsByTagName("*");
        ConcurrentLinkedQueue<Node> elements = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < nodeList.getLength(); i++) {
            elements.add(nodeList.item(i));
        }

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
               while (!elements.isEmpty()) {
                   Node node = elements.poll();
                   if (node != null) {
                       if (node.getNodeName().equals("hierarchy")) {
                           continue;
                       }

                       //if (clickable.equalsIgnoreCase("true")) { //TODO: If only clickable elements should become widgets.
                       var n = node.getAttributes();
                       String[] parts = n.getNamedItem("bounds").getNodeValue().split("\\D+");
                       int upperLeftX = Integer.parseInt(parts[1]);
                       int upperLeftY = Integer.parseInt(parts[2]);
                       int bottomRightX = Integer.parseInt(parts[3]);
                       int bottomRightY = Integer.parseInt(parts[4]);
                       int elementWidth = bottomRightX - upperLeftX;
                       int elementHeight = bottomRightY - upperLeftY;

                       if (elementWidth >= windowWidth * 0.9 && elementHeight >= windowHeight * 0.9) {
                           continue;
                       }

                       var clickable = node.getAttributes().getNamedItem("clickable").getNodeValue();
                       Widget widget = new Widget();
                       widget.setLocationArea(new Rectangle(upperLeftX, upperLeftY, bottomRightX - upperLeftX, bottomRightY - upperLeftY));

                       if (n.getNamedItem("resource-id") != null) {
                           widget.putMetadata("id", node.getAttributes().getNamedItem("resource-id").getNodeValue());
                       } else {
                           widget.putMetadata("id", null);
                       }

                       String text = n.getNamedItem("text").getNodeValue();
                       widget.putMetadata("text", text);
                       widget.putMetadata("type", n.getNamedItem("class").getNodeValue());
                       widget.putMetadata("clickable", clickable);

                       String desc;
                       if (n.getNamedItem("content-desc") != null) {
                           desc = n.getNamedItem("content-desc").getNodeValue();
                       } else {
                           desc = "null";
                       }
                       widget.putMetadata("content-desc", desc);

                       if ("true".equalsIgnoreCase(clickable)) {
                           widget.setWidgetType(Widget.WidgetType.ACTION);
                           widget.setWidgetSubtype("true".equals(n.getNamedItem("long-clickable").getNodeValue()) ? Widget.WidgetSubtype.LONG_CLICK_ACTION : Widget.WidgetSubtype.LEFT_CLICK_ACTION);
                       }

                       if ("android.widget.EditText".equalsIgnoreCase(widget.getMetadata("type").toString())) {
                           widget.setWidgetType(Widget.WidgetType.ACTION);
                           widget.setWidgetSubtype(Widget.WidgetSubtype.TYPE_ACTION);
                           widget.putMetadata("textElementNumber", textElementNumber);
                           textElementNumber++;
                       }

                       widget.setWidgetVisibility(Widget.WidgetVisibility.VISIBLE);
                       try {
                           widget.putMetadata("key", (widget.getMetadata("id") + widget.getMetadata("text").toString() + widget.getMetadata("content-desc").toString() + widget.getMetadata("type").toString()));
                       } catch (Exception e) {
                           System.err.println(e.getMessage());
                       }
                       widgets.add(widget);

                       AtomicBoolean createCheck = new AtomicBoolean(true);
                       try {
                           Widget finalWidget = widget;
                           previouslyVisible.stream()
                                   .filter(wid -> (wid.getWidgetStatus().equals(Widget.WidgetStatus.LOCATED)
                                           && wid.getValidExpression() != null
                                           && !wid.getValidExpression().isEmpty())
                                           || !wid.getWidgetStatus().equals(Widget.WidgetStatus.LOCATED))
                                   .forEach(prev -> {
                                       var key1 = finalWidget.getMetadata("key");
                                       var key2 = prev.getMetadata("key");
                                       if (key1 != null && key2 != null && (key1.toString().equalsIgnoreCase(key2.toString()))) {
                                           finalWidget.setWidgetVisibility(prev.getWidgetVisibility());
                                           finalWidget.setValidExpression(prev.getValidExpression());
                                           finalWidget.setWidgetStatus(prev.getWidgetStatus());
                                           finalWidget.setWidgetType(prev.getWidgetType());
                                           finalWidget.setReportedText(prev.getReportedText());
                                           StateController.getCurrentState().addWidget(finalWidget);
                                           previouslyVisible.remove(prev);
                                           widgets.remove(finalWidget);
                                           createCheck.set(false);
                                       }
                                   });
                       } catch (Exception e) {
                           System.err.println(e.getMessage());
                       }

                       if (createCheck.get() && (isCheckTag(widget.getMetadata("type").toString()))) {
                           if (!text.trim().isEmpty()) {
                               widget = new Widget(widget);
                               widget.setWidgetType(Widget.WidgetType.CHECK);
                               widget.setValidExpression("{text} = " + text.trim());
                               widgets.add(widget);
                           } else if (!desc.equalsIgnoreCase("null") && !desc.trim().isEmpty()) {
                               widget = new Widget(widget);
                               widget.setWidgetType(Widget.WidgetType.CHECK);
                               widget.setValidExpression("{desc} = " + desc.trim());
                               widgets.add(widget);
                           }
                       }
                   }
               }
            });
        }
        shutdownAndAwaitTermination(executor);
        if(debugMode) System.out.println("Widgets created: " + widgets.size() + " in " + (System.currentTimeMillis() - start) + " ms");
        updateLastWidgets(new ArrayList<>(widgets));
    }

    /**
     * Shuts down the executor service.
     * @param pool the executor service to shut down.
     */
    private void shutdownAndAwaitTermination(ExecutorService pool) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                if (!pool.awaitTermination(60, TimeUnit.SECONDS))
                    System.err.println("Pool did not terminate");
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Updates the last widgets.
     * @param widgets the widgets to update.
     */
    private void updateLastWidgets(List<Widget> widgets) {
        if(debugMode) System.out.println("Updating last widgets...");
        StateController.getCurrentState().replaceHiddenWidgets(widgets, "AppiumPlugin");
    }

    /**
     * Creates a widget from an element.
     * @param element the element to create a widget from.
     * @param position the position of the element.
     * @param desc the content description of the element.
     * @param text the text of the element.
     * @param clickable the clickable attribute of the element.
     * @param resourcId the resource id of the element.
     * @return Widget
     */
    public Widget createWidgetFromElement(WebElement element, String position, String desc, String text, String clickable, String resourcId, String uniqueId) {
        Widget widget = new Widget();
        String[] parts = position.split("\\D+");
        int upperLeftX = Integer.parseInt(parts[1]);
        int upperLeftY = Integer.parseInt(parts[2]);
        int bottomRightX = Integer.parseInt(parts[3]);
        int bottomRightY = Integer.parseInt(parts[4]);
        widget.setLocationArea(new Rectangle(upperLeftX, upperLeftY, bottomRightX - upperLeftX, bottomRightY - upperLeftY));
        //widget.putMetadata("key", resourcId + desc + text + position); // Creates a key.
        widget.putMetadata("key", uniqueId);
        Map<String, String> attributes = Map.ofEntries(
                Map.entry("id", resourcId),
                Map.entry("text", text),
                Map.entry("type", element.getAttribute("class")),
                Map.entry("content-desc", desc),
                Map.entry("clickable", clickable),
                Map.entry("long-clickable", element.getAttribute("long-clickable"))
        );

        attributes.forEach(widget::putMetadata);

        if ("true".equalsIgnoreCase(clickable)) {
            widget.setWidgetType(Widget.WidgetType.ACTION);
            widget.setWidgetSubtype("true".equals(attributes.get("long-clickable")) ? Widget.WidgetSubtype.LONG_CLICK_ACTION : Widget.WidgetSubtype.LEFT_CLICK_ACTION);
        }

        if ("android.widget.EditText".equalsIgnoreCase(attributes.get("type"))) {
            widget.setWidgetType(Widget.WidgetType.ACTION);
            widget.setWidgetSubtype(Widget.WidgetSubtype.TYPE_ACTION);
            widget.putMetadata("textElementNumber", textElementNumber);
            textElementNumber++;
        }

        widget.setWidgetVisibility(Widget.WidgetVisibility.VISIBLE);

        return widget;
    }

    /**
     * Handles typing for the selected widget.
     * @param action the action
     * @param widget the widget to type on.
     */
    private static void handleTyping(Action action, Widget widget) {
        if (widget.getComment() != null && StateController.getMode() == StateController.Mode.AUTO) { // Typing and perform the widget when auto mode is activated.
            webDriver.findElement(By.id(widget.getMetadata("id").toString())).sendKeys(widget.getComment());
            StateController.performWidget(widget);
            return;
        }
        typingInProgress = true;

        TypeAction typeAction = (TypeAction) action;
        KeyEvent keyEvent = typeAction.getKeyEvent();
        int keyCode = keyEvent.getKeyCode();
        char keyChar = keyEvent.getKeyChar();
        if(keyCode == KeyEvent.VK_ENTER) {
            if (widget.getWidgetType() == Widget.WidgetType.CHECK) {
                handleCheckWidget(widget);
                return;
            }
            WebElement element = null;
            try {
                var id = widget.getMetadata("id");
                if (id != null && !id.toString().trim().equalsIgnoreCase("null")) {
                    element = webDriver.findElement(By.id(id.toString()));
                } else { //TODO: This needs to be worked on as it seems to be incorrect sometimes and fails in this approach to mapping widgets.
                    var textElements = webDriver.findElements(By.xpath("//android.widget.EditText"));
                    String indexStr = widget.getMetadata("textElementNumber").toString();
                    int index = Integer.parseInt(indexStr);
                    if (index >= 0 && index < textElements.size()) {
                        element = textElements.get(index);
                    }
                }
            } catch (Exception e) {
                StateController.clearKeyboardInput();
                System.err.println(e.getMessage());
            }

            if (element != null) {
                element.sendKeys(StateController.getKeyboardInput().split("\\[")[0].trim());
                widget.setComment(StateController.getKeyboardInput().trim());
                StateController.clearKeyboardInput();
                widget.setText(widget.getComment());
                widget.setWidgetSubtype(Widget.WidgetSubtype.TYPE_ACTION);
                widget.setWidgetStatus(Widget.WidgetStatus.LOCATED);
                StateController.insertWidget(widget, widget.getNextState());
                lastClickedWidget = null;
                sourceChanged = true;
                typingInProgress = false;
            }
        }  else if(keyCode == KeyEvent.VK_DELETE || keyCode == KeyEvent.VK_BACK_SPACE) {
            if (!StateController.getKeyboardInput().isEmpty()) {
                StateController.removeLastKeyboardInput();
            } else {
                StateController.clearKeyboardInput();
            }
            typingInProgress = false;
        } else if (keyCode >= 44 || keyCode == KeyEvent.VK_SPACE){
            StateController.addKeyboardInput(String.valueOf(keyChar));
        }
        //sourceChanged = true;
    }

    /**
     * Performs a Swipe/Scroll action.
     * @param start the starting Point.
     * @param end the end Point.
     */
    private static void performSwipe(Point start, Point end) {
        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence scrollSequence = new Sequence(finger, 1);
        scrollSequence.addAction(finger.createPointerMove(Duration.ofMillis(0), PointerInput.Origin.viewport(), start.x, start.y));
        scrollSequence.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
        scrollSequence.addAction(finger.createPointerMove(Duration.ofMillis(600), PointerInput.Origin.viewport(), end.x, end.y));
        scrollSequence.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));

        webDriver.perform(List.of(scrollSequence));
    }

    /**
     * Check if a class is available for "Check"
     * @param className the name of the class
     * @return true if available for check, false if not.
     */
    private boolean isCheckTag(String className) {
        for (String tag : CHECK_TAGS) {
            if (className.contains(tag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Handles the correct types of a CHECK type widget.
     * @param widget the check type widget to handle.
     */
    private static void handleCheckWidget(Widget widget) {
        if (!StateController.getKeyboardInput().isEmpty() && widget.getWidgetVisibility() == Widget.WidgetVisibility.VISIBLE) {
            if (StateController.getKeyboardInput().contains("{") && StateController.getKeyboardInput().contains("}")) {
                // An expression
                widget.setValidExpression(StateController.getKeyboardInput().trim());
                if (widget.isValid(widget.getValidExpression())) {
                    widget.setWidgetStatus(Widget.WidgetStatus.VALID);
                } else {
                    widget.setWidgetStatus(Widget.WidgetStatus.LOCATED);
                }
            } else {
                // Report an issue
                widget.setWidgetType(Widget.WidgetType.ISSUE);
                widget.setWidgetVisibility(Widget.WidgetVisibility.VISIBLE);
                widget.setReportedText(StateController.getKeyboardInput().trim());
                widget.setReportedBy(StateController.getTesterName());
                widget.setReportedDate(new Date());
                widget.setReportedProductVersion(StateController.getProductVersion());
                widget.setWidgetStatus(Widget.WidgetStatus.VALID);
            }
            StateController.clearKeyboardInput();
        } else if (widget.getWidgetType() == Widget.WidgetType.ISSUE) {
            if (widget.getWidgetVisibility() == Widget.WidgetVisibility.VISIBLE) {
                // Convert issue into check
                widget.setWidgetType(Widget.WidgetType.CHECK);
                widget.setResolvedBy(StateController.getTesterName());
                widget.setResolvedDate(new Date());
                widget.setResolvedProductVersion(StateController.getProductVersion());
                Widget matchingWidget = (Widget) widget.getMetadata("matching_widget");
                String text = (String) matchingWidget.getMetadata("text");
                String value = (String) matchingWidget.getMetadata("value");
                String placeholder = (String) matchingWidget.getMetadata("placeholder");
                if (text != null && !text.trim().isEmpty()) {
                    widget.setValidExpression("{text} = " + text);
                    widget.putMetadata("text", text);
                } else if (value != null && !value.trim().isEmpty()) {
                    widget.setValidExpression("{value} = " + value);
                    widget.putMetadata("value", value);
                } else if (placeholder != null && !placeholder.trim().isEmpty()) {
                    widget.setValidExpression("{placeholder} = " + placeholder);
                    widget.putMetadata("placeholder", placeholder);
                }
                StateController.clearKeyboardInput();
            }
        } else {
            if (widget.getWidgetVisibility() == Widget.WidgetVisibility.HIDDEN) {
                widget.setWidgetVisibility(Widget.WidgetVisibility.VISIBLE);
                String expression = widget.getValidExpression();
                if (expression.contains("text") && expression.contains("{") && expression.contains("}")) {
                    if (widget.isValid(widget.getValidExpression())) {
                        widget.setWidgetStatus(Widget.WidgetStatus.VALID);
                    } else {
                        widget.setWidgetStatus(Widget.WidgetStatus.LOCATED);
                    }
                } else {
                    widget.setCreatedBy(StateController.getTesterName());
                    widget.setCreatedDate(new Date());
                    widget.setCreatedProductVersion(StateController.getProductVersion());
                }
            }
        }
    }

    // ==============================================================================
    // =========================== REPORT GENERATION ================================
    // ==============================================================================
    /**
     * Generates a report for the test session.
     */
    @Override
    public void generateReport() {
        File file = new File("reports/" + StateController.getProduct());
        file.mkdirs();

        String filename = Scout.APP_HOME + "reports" + File.separator + StateController.getProduct() + File.separator + StateController.getCurrentState().getId() + ".txt";
        String filenamePerformance1 = Scout.APP_HOME + "reports" + File.separator + StateController.getProduct() + File.separator + StateController.getCurrentState().getId() + "_performance_multi.txt";
        String filenamePerformance2 = Scout.APP_HOME + "reports" + File.separator + StateController.getProduct() + File.separator + StateController.getCurrentState().getId() + "_performance_execute.txt";
        String filenamePerformance3 = Scout.APP_HOME + "reports" + File.separator + StateController.getProduct() + File.separator + StateController.getCurrentState().getId() + "_performance_match.txt";
        StateController.displayMessage("Generating performance test to: " + filename);

        writeLine(filename, "Locators:", false);
        checkLocators(StateController.getCurrentState(), filename, filenamePerformance1, filenamePerformance2, filenamePerformance3);
    }

    /**
     * Checks the locators of the widgets.
     * @param state the state to check.
     * @param filename the name of the file to write to.
     * @param filenamePerformance1 the name of the file to write performance data to.
     * @param filenamePerformance2 the name of the file to write performance data to.
     * @param filenamePerformance3 the name of the file to write performance data to.
     */
    private void checkLocators(AppState state, String filename, String filenamePerformance1, String filenamePerformance2, String filenamePerformance3) {
        List<Widget> visibleWidgets = state.getVisibleWidgets();
        for (Widget widget : visibleWidgets) {
            long start = System.currentTimeMillis();
            String xpath = (String) widget.getMetadata("xpath");
            if (xpath != null) {
                List<WebElement> elements = webDriver.findElements(By.xpath(xpath));
                String elementId = elements.size() == 1 ? " LOCATED" : " BROKEN: " + elements.size() + " located";
                log(filename, "Xpath: " + xpath + elementId);
            }
            xpath = (String) widget.getMetadata("xpath2");
            if (xpath != null) {
                List<WebElement> elements = webDriver.findElements(By.xpath(xpath));
                String elementId = elements.size() == 1 ? " LOCATED" : " BROKEN: " + elements.size() + " located";
                log(filename, "IdXpath: " + xpath + elementId);
            }
            xpath = (String) widget.getMetadata("xpath3");
            if (xpath != null) {
                List<WebElement> elements = webDriver.findElements(By.xpath(xpath));
                String elementId = elements.size() == 1 ? " LOCATED" : " BROKEN: " + elements.size() + " located";
                log(filename, "RobulaXpath: " + xpath + elementId);
            }
            xpath = (String) widget.getMetadata("xpath4");
            if (xpath != null) {
                List<WebElement> elements = webDriver.findElements(By.xpath(xpath));
                String elementId = elements.size() == 1 ? " LOCATED" : " BROKEN: " + elements.size() + " located";
                log(filename, "RobulaPlusXpath: " + xpath + elementId);
            }
            xpath = (String) widget.getMetadata("xpath5");
            if (xpath != null) {
                List<WebElement> elements = webDriver.findElements(By.xpath(xpath));
                String elementId = elements.size() == 1 ? " LOCATED" : " BROKEN: " + elements.size() + " located";
                log(filename, "MonotoXpath: " + xpath + elementId);
            }
            long delta = System.currentTimeMillis() - start;
            log(filenamePerformance1, "" + delta);
            long executeTime = (long) widget.getMetadata("execute_time");
            log(filenamePerformance2, "" + executeTime);
            long matchTime = (long) widget.getMetadata("match_time");
            log(filenamePerformance3, "" + matchTime);

            log(filename, "");
        }

        log(filenamePerformance1, "");
        log(filenamePerformance2, "");
        log(filenamePerformance3, "");
    }

    // ==============================================================================
    // =========================== UTILITY METHODS ==================================
    // ==============================================================================
    /**
     * Converts a byte array to a BufferedImage.
     * @param bytes byte array.
     * @return BufferedImage
     */
    private BufferedImage byteArrayToBufferedImage(byte[] bytes) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            return ImageIO.read(bais);
        } catch (IOException e) {
            System.err.println("Error occurred while converting byte array to BufferedImage: " + e.getMessage());
            return null;
        }
    }

    /**
     * Strips an URL of its protocol.
     * Hack used to get package name using Scout's start-dialog which requires a "proper" URL.
     * @param url URL to strip.
     * @return string without protocol prefix.
     */
    private static String stripURL(String url) {
        return url.replaceFirst("^(http://|https://)", "");
    }

    /**
     * Logs a message to a file.
     * @param logFilename the name of the log file.
     * @param message the message to log.
     */
    private void log(String logFilename, String message) {
        writeLine(logFilename, message, true);
    }

    /**
     * Writes a line to a file.
     * @param filename the name of the file.
     * @param text the text to write.
     * @param append true to append, false to overwrite.
     */
    private void writeLine(String filename, String text, boolean append) {
        String logMessage = text + "\r\n";
        File file = new File(filename);
        try {
            FileOutputStream o = new FileOutputStream(file, append);
            o.write(logMessage.getBytes());
            o.close();
        } catch (Exception e) {
            System.err.println("Error occurred while writing to file: " + e.getMessage());
        }
    }

    /**
     * Swaps context to web view.
     * @return true if swap was done, false if not.
     */
    private static boolean swapToWebViewContext() {
        for (String context: webDriver.getContextHandles()) {
            if (context.contains("WEBVIEW")) {
                if(debugMode) System.out.println(context + " context");
                webDriver.context(context);
                return true;
            }
        }
        return false;
    }

    // ==============================================================================
    // ========================== UNUSED/OBSOLETE METHODS ===========================
    // ==============================================================================
    /**
     * Partitions a list into sublists of a specified size.
     * @param elements the list to partition.
     * @param partitionSize the size of the partitions.
     * @return a list of sublists.
     */
    private <T> List<List<T>> partitionList(List<T> elements, int partitionSize) {
        List<List<T>> partitions = new LinkedList<>();
        for (int i = 0; i < elements.size(); i += partitionSize) {
            partitions.add(elements.subList(i, Math.min(i + partitionSize, elements.size())));
        }
        return partitions;
    }
}