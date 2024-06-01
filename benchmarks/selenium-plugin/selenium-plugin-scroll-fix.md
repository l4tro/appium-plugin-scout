In order to enable scrolling using page-down and page-up keys in the SeleniumPlugin code, the following changes must be made:

The following fields must be added to the SeleniumPlugin class as member variables:

```
    private long xOffset;
    private long yOffset;
```

In ```getCapture()``` the following code must be added within the ```if (StateController.isOngoingSession())``` conditional block:

```
    JavascriptExecutor executor = (JavascriptExecutor) webDriver;
    xOffset = (long) executor.executeScript("return window.pageXOffset;");
    yOffset = (long) executor.executeScript("return window.pageYOffset;");

```

In ```verifyAndReplaceWidgets()``` at the top of the ```if (widgetMatch != null)``` conditional block the following line must be added:
```
    widget.setLocationArea(widgetMatch.getWidget2().getLocationArea());
```

In ```getAvailableWidgets()``` the following code must be added inside the ```for (int i = 0; i < jsonArray.size(); i++)``` loop immediately after the line ```Long height = object2Long(jsonObject.get("height"));```:
```
    x -= (int) xOffset;
    y -= (int) yOffset;
```

In ```performAction()``` the following code must be added at the end of the if-else chain that starts with ```if (keyCode == KeyEvent.VK_ENTER) {```:

```
    else if (keyCode == KeyEvent.VK_PAGE_UP) {
        JavascriptExecutor js = (JavascriptExecutor)webDriver;
        js.executeScript("window.scrollBy(0,-100)");
    } else if (keyCode == KeyEvent.VK_PAGE_DOWN) {
        JavascriptExecutor js = (JavascriptExecutor)webDriver;
        js.executeScript("window.scrollBy(0,100)");
    }
```

The actual value of the length of the scroll can be adjusted as needed.

Following this procedure should result in being able to scroll using the page-down and page-up keys. Some inconstencies with how augmentations are displayed may occur with the plugin failing to match the position of augmentations with the widget they're meant to augment resulting in the normally coloured frame being displayed in grey, however this has no effect on the basic functionality of the plugin.

