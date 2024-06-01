# Pre-defined tasks
This file presents the pre-defined tasks used for JFR data collection and effectiveness testing of AppiumPlugin and SeleniumPlugin.

## Cross-Plugin Comparative tasks
For the comparative effectiveness testing of AppiumPlugin and SeleniumPlugin five pre-defined tests were done and are presented here.

### Test Case 1: Add a Caffe Americano to your basket
**Test Procedure:**
1. Description: Visit the Starbucks PWA https://app.starbucks.com/
   - Expected Result: Successfully connected to Starbucks and widgets getting created. 
2. Description: Locate the beverage Caffe Americano.
   - Expected Result: Caffe Americano loaded and the widget is intractable.
3. Description: Add the Caffe Americano to your basket.
   - Expected Result: The item is added to your basket

### Test Case 2: Play a Spotify track
**Test Procedure:**
1. Description: Visit the Spotify PWA https://open.spotify.com/
   - Expected Result: Successfully connected to Spotify and widgets getting created.
2. Description: Use the search bar to search for Metallica and play Nothing Else Matters and click on the track.
   - Expected Result: The search bar is clickable and typing is possible, the found track is found and clickable.
3. Description: Play the music track and adjust the volume
   - Expected Result: Playing the music track is possible and the volume slider is interactable
4. Description: Fast-forward the music track using the time remaining slider
   - Expected Result: The music track can be fast-forwarded.

### Test Case 3: Brew a Classic French Press
**Test Procedure:**
1. Description: Visit the 2brew PWA https://2brew.github.io/#/
   - Expected Result: Successfully connected to 2brew and widgets getting created.
2. Description: Click on the “French Press” icon.
   - Expected Result: “French Press” is clickable and a new page is displayed.
3. Description: Click on the “Classic” bar.
   - Expected Result: The “Classic” bar is clickable
4. Description: Press the “play” button to start the timer
   - Expected Result: The “play” button is interactable and a timer is started.
5. Description: Press the “fast-forward” button to skip the current step of the brewing process.
   - Expected Result: The “fast-forward” button is interactable and the next step is initiated.
6. Description: Press the “stop” button to stop the process.
   - Expected Result: The “stop” button is interactable and the process is stopped.

### Test Case 4: Calculate GPA score
**Test Procedure:**
1. Description: Visit the gpacalculator PWA https://gpacalculator.memorymaps.io/
   - Expected Result: Successfully connected to gpacalculator and widgets getting created.
2. Description: Locate the table “Semester 1” and enter the course name “DT002G” in the course name field
   - Expected Result: DT002G is written in the course name field.
3. Description: Use the dropdown menu and select the grade “C” for the course “DT002G”
   - Expected Result: The dropdown menu is clickable and the grade “C” is chosen and displayed.
4. Description: In the credits field, write “7.5”
   - Expected Result: The credit field displays 7.5 credits
5. Description: Press the button “Add course”
   - Expected Result: The button is intractable and a new row is displayed.
6. Description: In the new row created repeat steps 1-5 but use “DT0015G” as the course name “E” as the grade and 15 as the credit.
   - Expected Result: All fields are filled in the new row.

### Test Case 5: Use the Intershop webshop
**Test Procedure:**
1. Description: Visit the Intershop PWA https://intershoppwa.azurewebsites.net/home
   - Expected Result: Successfully connected to Intershop PWA and widgets getting created.
2. Description: Go to Network -> Firewalls -> D-Link DFL-160 and click it.
   - Expected Result: The firewall is found.
3. Description: Use buttons to increase the quantity make it 5 and add it to your cart.
   - Expected Result: The 5 of the given firewalls are added to your cart.
4. Description: View your cart and decrease the quantity to 3 and click checkout.
   - Expected Result: The cart is viewable and the quantity is 3.

### Test Case 6: Test goHome functionality
**Test Procedure:**
1. Description: Visit the Starbucks PWA https://app.starbucks.com/
   - Expected Result: Successfully connected to Starbucks and widgets getting created.
2. Description: Press the “Start an order” button.
   - Expected Result: A new page is opened.
3. Description: Use the Scout toolbar and press Bookmarks -> “Go Home”
   - Expected Result: The starting point of the SUT.

## Native Android application tasks
For the native Android application effectiveness testing five pre-defined tests were done and are presented here.

## Test Case 1: Watch YouTube shorts
**Test Procedure:**
1. Description: Start YouTube application
   - Expected Result: Successfully started YouTube and widgets getting created.
2. Description: Watch YouTube shorts.
    - Expected Result: YouTube shorts is watchable.

## Test Case 2: Use Android settings
**Test Procedure:**
1. Description: Start Android settings application
    - Expected Result: Successfully started Android settings application and widgets getting created.
2. Description: Scroll down
    - Expected Result: Scrolling is working as intended
3. Description: Add a check
   - Expected Result: The check was successfully added and is visible.
4. Description: Scroll up and left-click profile icon
   - Expected Result: Profile icon was found and is clickable.
5. Description: Use the Scout toolbar and press Bookmarks -> “Go Home”
   -  Expected Result: The starting point of the SUT

## Test Case 3: Use Google Maps
**Test Procedure:**
1. Description: Start Google Maps application
    - Expected Result: Successfully started Google Maps application and widgets getting created.
2. Description: Move the map around
   - Expected Result: The map is moved around
3. Description: Click on the map
   - Expected Result: The map is clickable and a "pin" is created.

## Test Case 4: Calendar Application
**Test Procedure:**
1. Description: Open the Calendar application.
   - Expected Result: The Calendar application started successfully and widgets are created.
2. Description: Create a new event.
   - Expected Result: A new event is created and displayed on the calendar.
3. Description: Delete an event.
   - Expected Result: The selected event is deleted from the calendar

## Test Case 5: Search for an application
**Test Procedure:**
1. Description: Open the Google Play Store application.
   - Expected Result: The Google Play Store application started successfully and widgets are created.
2. Description: Search for an app.
   - Expected Result: The search results are displayed with relevant apps.