# Necessary adjustments to SeleniumPlugin to run benchmarks

In order to run benchmarks for SeleniumPlugin, the following methods must be added to the SeleniumPlugin class:

```
    public void setHomeLocatorManually(String homeLocator) {
        StateController.setHomeLocator(homeLocator);
    }


    public static void setWebDriver(WebDriver driver) {
        System.out.println("Webdriver set");
        webDriver = driver;
    }
```

Additionally, the following conditional statement must be added to the end of the if-else chain in the ```getWebDriver()``` method:

```
        ...
        else if ("AndroidChrome".equalsIgnoreCase(browser)) {
                ChromeOptions options = new ChromeOptions();
                options.addArguments("--user-agent=Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/123.0.6312.80 Mobile Safari/537.36");

                if (headlessBrowser) {
                    //options.setHeadless(true);
                    options.addArguments("--headless=new");
                    String lang = StateController.getProductProperty("browser_language", null);
                    if (lang != null) {
                        options.addArguments("--lang=" + lang);
                    }
                    options.addArguments("--remote-allow-origins=*");
                }

                ChromeDriver chromeDrv = new ChromeDriver(options);

                String osName = System.getProperty("os.name");
                boolean isWin = osName.startsWith("Windows");
                if (isWin) {
                    // String chromeBrowserVersion = (String) chromeDrv.getCapabilities().getCapability(CapabilityType.BROWSER_VERSION);
                    String chromeBrowserVersion = getChromeVersionFromRegistry();
                    String chromeDriverInfo = chromeDrv.getCapabilities().getCapability("chrome").toString();
                    String chromeDriverVersion = chromeDriverInfo.split(" ")[0].split("=")[1];
                    Scout.log("chrome Driver Version:" + chromeDriverVersion);
                    Scout.log("chrome Browser Version:" + chromeBrowserVersion);
                }

                return chromeDrv;
            }
```

Finally, the following methods must have their access level changed to ```public```:

```getSeleniumCapture()```
```createCaptureAndReplaceWidgets()```
```verifyAndReplaceWidgets()```
