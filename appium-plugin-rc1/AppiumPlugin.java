package plugin;

import com.symatiq.Scout;
import com.symatiq.actions.*;
import com.symatiq.extension.PluginAbstract;
import com.symatiq.extension.PluginAbstractReport;
import com.symatiq.states.AppState;
import com.symatiq.states.StateController;
import com.symatiq.widgets.Widget;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Pause;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebElement;

import javax.imageio.ImageIO;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class AppiumPlugin extends PluginAbstract implements PluginAbstractReport {
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

    // ==============================================================================
    // =========================== PLUGIN CONTROLLER METHODS ========================
    // ==============================================================================
    /**
     * Called by Scout when the plugin is enabled.
     */
    public void enablePlugin() {
        System.out.println("Enabling AppiumPlugin...");
        startAppiumServer();

        // Check if the Appium server has started
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
        if (serverStarted) {
            StateController.displayMessage("Appium server started");
        } else {
            StateController.displayMessage("Failed to start Appium server");
        }
    }

    /**
     * Called by Scout when the plugin is disabled.
     */
    public void disablePlugin() {
        System.out.println("Disabling AppiumPlugin...");
        quitWebDriver();
        stopAppiumServer();
    }

    /**
     * Called by Scout when starting a test session with the plugin enabled.
     */
    public void startSession() {
        System.out.println("Starting session...");

        if(!appiumStatus()) {
            System.out.println("No Appium server when starting session");
            enablePlugin();
        }

        WidgetIdentifier.initIdentifiers();
        targetPackage = stripURL(StateController.getHomeLocator());
        webDriver = getAppiumDriver();
        if (webDriver != null) {
            System.out.println("Driver initialized");
            StateController.setSessionState(StateController.SessionState.RUNNING);
            StateController.displayMessage("Session started");
            currentState = StateController.getCurrentState();

            if (!Objects.equals(targetPackage, "")) { // TargetPackage was gathered from HomeLocator.
                webDriver.activateApp(targetPackage);
            } else { // Set targetPackage to the currently opened application this will be 'home' location.
                targetPackage = webDriver.getCurrentPackage() + "/" + webDriver.currentActivity();
            }
        } else {
            System.err.println("Driver is null");
        }
    }

    /**
     * Called by Scout when stopping a test session with the plugin enabled.
     */
    public void stopSession() {
        System.out.println("Stopping session...");
        StateController.setSessionState(StateController.SessionState.STOPPED);
        StateController.displayMessage("Session stopped");
        quitWebDriver();
    }

    /**
     * Called by Scout on a timer to get the current state of the SUT display.
     */
    public BufferedImage getCapture() throws WebDriverException {
        if (StateController.isOngoingSession()) {
            updateCapture();
            return lastCapture;
        }
        return null;
    }

    /**
     * Called by Scout's when an action is performed in the SUT representation.
     * @param action the action to perform.
     */
    public static void performAction(Action action) {
        if (!StateController.isRunningSession() || action.isToolbarAction()) {
            return;
        }
        performAppiumAction(action);
    }

    /**
     * Returns the product views supported by the plugin.
     * @return an array of product views.
     */
    public String[] getProductViews() {
        return new String[] {"Android"};
    }

    /**
     * Called from PluginController
     */
    public void changeState() {
        //System.out.println("State changed");
        currentState = StateController.getCurrentState();
    }

    // ==============================================================================
    // =========================== MIDDLEWARE CONFIGURATION =========================
    // ==============================================================================
    /**
     * Inits a WebDriver in the Appium server and returns it
     * @return AppiumDriver
     */
    private AndroidDriver getAppiumDriver() {
        try {
            AndroidDriver newDriver = new AndroidDriver(new URL("http://127.0.0.1:4723"), getDesiredCapabilities());
            newDriver.context("NATIVE_APP");
            return newDriver;
        } catch (Exception e) {
            System.err.println("Error occurred while initializing the WebDriver: " + e.getMessage());
            return null;
        }
    }

    /**
     * Starts the Appium server
     */
    private void startAppiumServer(){
        try {
            if (!appiumStatus()) {
                String command = "appium -a 127.0.0.1 -p 4723";
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
            System.out.println("Appium server stopped");
        } else {
            System.out.println("Appium server is not running");
        }
    }

    /**
     * Quits the WebDriver.
     */
    private void quitWebDriver() {
        System.out.println("Quitting WebDriver...");
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
            URL url = new URL("http://localhost:4723/status");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                System.out.println("Appium server started");
                return true;
            } else {
                System.err.println("Failed to start Appium server");
                return false;
            }
        } catch (IOException e) {
            System.err.println("Error occurred while checking Appium server status: " + e.getMessage());
            return false;
        }
    }

    /**
     * Returns the desired capabilities for the AppiumDriver.
     * @return DesiredCapabilities
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
     * Perform action(s).
     * @param action to perform.
     */
    private static void performAppiumAction(Action action) {
        if (action instanceof MouseScrollAction mouseScrollAction) {
            StateController.setSelectedWidgetNo(StateController.getSelectedWidgetNo() + mouseScrollAction.getRotation());
            return;
        }

        if (action instanceof LeftClickAction leftClickAction) {
            Widget widget = StateController.getWidgetAt(currentState, leftClickAction.getLocation());
            if (widget.getWidgetSubtype() == Widget.WidgetSubtype.GO_HOME_ACTION) {
                widget.setWidgetStatus(Widget.WidgetStatus.LOCATED);
                goHome();
                StateController.insertWidget(widget, StateController.getStateTree());
                sourceChanged = true;
                return;
            }

            if (widget.getWidgetSubtype() == Widget.WidgetSubtype.DRAG_ACTION) {
                performSwipe( (Point) widget.getMetadata("start"), (Point) widget.getMetadata("end"));
                StateController.performWidget(widget);
                sourceChanged = true;
                return;
            }

            if (widget.getWidgetType() == Widget.WidgetType.CHECK) {
                handleCheckWidget(widget);
                lastClickedWidget = widget;
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
                    handleTyping(action, widget);
                    //sourceChanged = true;
                    return;
                }
                widgetClickHandle(widget);
                sourceChanged = true;
            }
        } else if (action instanceof TypeAction typeAction) {
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
     * Changes the activity of the application using ADB command.
     */
    private static void goHome() {
        try {
            int response = Runtime.getRuntime().exec("adb shell am start -n " + targetPackage).waitFor();
            if (response != 0) {
                System.err.println("Command execution failed");
            }
        } catch (InterruptedException | IOException e) {
            System.err.println("Couldn't run command: " + e.getMessage());
        }
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
        }
        updatingCapture = false;
    }

    /**
     * Takes a screenshot of the SUT display.
     * @return BufferedImage
     */
    public BufferedImage getScreenshot() {
        //System.out.println("Taking screenshot...");

        BufferedImage image = null;
        try {
            image = byteArrayToBufferedImage(webDriver.getScreenshotAs(OutputType.BYTES));
        } catch (Exception e) {
            System.err.println("Error occurred while taking a screenshot of the emulated device: " + e.getMessage());
        }
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
    private void createWidgets() {
        List<WebElement> elements = webDriver.findElements(By.xpath("//*"));
        textElementNumber = 0;

        if (elements.isEmpty()) {
            return;
        }

        long start = System.currentTimeMillis();
        List<Widget> widgets = StateController.getCurrentState().getAllWidgets();
        Set<String> existingKeys = widgets.stream()
                .filter(widget -> widget.getMetadata("key") != null)
                .map(widget -> widget.getMetadata("key").toString())
                .collect(Collectors.toSet());

        //System.out.println("Creating keys took: " + (System.currentTimeMillis() - start));
        ConcurrentLinkedQueue<Widget> concurrentWidgets = new ConcurrentLinkedQueue<>();

        ConcurrentLinkedQueue<WebElement> webeles = new ConcurrentLinkedQueue<>(elements);
        int numberOfThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                while (!webeles.isEmpty()) {
                    WebElement element = webeles.poll();
                    if (element != null) {
                        String position = element.getAttribute("bounds");
                        String[] parts = position.split("\\D+");
                        int upperLeftX = Integer.parseInt(parts[1]);
                        int bottomRightY = Integer.parseInt(parts[4]);
                        if (upperLeftX == 0 && bottomRightY >= StateController.getProductViewWidth() * 0.9) { //TODO: Some better way of handling this, prevent element which covers to screen to be widgets
                            continue;
                        }

                        String resourceId = element.getAttribute("resource-id");
                        String desc = element.getAttribute("content-desc");
                        String text = element.getText();
                        String clickable = element.getAttribute("clickable");
                        String key = ((RemoteWebElement) element).getId();
                        if (!existingKeys.contains(key)) {
                            Widget widget = createWidgetFromElement(element, position, desc, text, clickable, resourceId, key);
                            concurrentWidgets.add(widget);
                            if (isCheckTag(element.getAttribute("class"))) {
                                if (!text.trim().isEmpty()) {
                                    widget = new Widget(widget);
                                    widget.setWidgetType(Widget.WidgetType.CHECK);
                                    widget.setValidExpression("{text} = " + text.trim());
                                    concurrentWidgets.add(widget);
                                } else if (!desc.equalsIgnoreCase("null") && !desc.trim().isEmpty()) {
                                    widget = new Widget(widget);
                                    widget.setWidgetType(Widget.WidgetType.CHECK);
                                    widget.setValidExpression("{desc} = " + desc.trim());
                                    concurrentWidgets.add(widget);
                                } else { //TODO: Not sure about this one
                                    widget = new Widget(widget);
                                    widget.setWidgetType(Widget.WidgetType.CHECK);
                                    widget.setValidExpression("{tag} = " + widget.getMetadata("type"));
                                    concurrentWidgets.add(widget);
                                }
                            }
                        }
                    }
                }
            });
        }
        shutdownAndAwaitTermination(executor);
        widgets.addAll(concurrentWidgets);
        updateLastWidgets(new ArrayList<>(widgets));
        System.out.println("Widgets created: " + widgets.size() + " in " + (System.currentTimeMillis() - start) + " ms");
    }

    private void shutdownAndAwaitTermination(ExecutorService pool) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                if (!pool.awaitTermination(60, TimeUnit.SECONDS))
                    System.out.println("Pool did not terminate");
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
        //System.out.println("Updating last widgets...");
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
                var id = widget.getMetadata("id").toString();
                if (id == null || !id.trim().equalsIgnoreCase("null")) {
                    element = webDriver.findElement(By.id(id));
                } else {
                    var textElements = webDriver.findElements(By.xpath("//android.widget.EditText"));
                    element = textElements.get(Integer.parseInt(widget.getMetadata("textElementNumber").toString()));
                }
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }

            if (element != null) {
                element.sendKeys(StateController.getKeyboardInput().split("\\[")[0].trim());
                widget.setComment(StateController.getKeyboardInput().trim());
                StateController.clearKeyboardInput();
                widget.setText(widget.getComment());
                widget.setWidgetSubtype(Widget.WidgetSubtype.TYPE_ACTION);
                widget.setWidgetStatus(Widget.WidgetStatus.LOCATED);
                StateController.insertWidget(widget, widget.getNextState());
                sourceChanged = true;
            }
        }  else if(keyCode == KeyEvent.VK_DELETE || keyCode == KeyEvent.VK_BACK_SPACE) {
            if (!StateController.getKeyboardInput().isEmpty()) {
                StateController.removeLastKeyboardInput();
            }
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