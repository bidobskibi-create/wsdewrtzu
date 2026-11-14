WatchLauncher v7 - honeycomb launcher for a small round Android watch (Android 8.1, API 27)

LAUNCHER
  round icons on a hex grid with a fisheye lens, ambient glow, glossy icons, springy snap,
  pop-in intro, haptic ticks, swell-to-launch animation.
  Tap = launch. Back / Home = glide back to the centre.

LONG-PRESS MENU
  Long-press an app icon and a box pops up next to it with four options:
  - App info : Android's app info screen
  - Delete   : uninstall (system apps can't be removed, you get a message instead)
  - Hide     : removes it from the grid. Bring hidden apps back with
               Look & Feel > "Show N hidden apps"
  - Folder   : puts the app in a folder. The first time it makes "Folder 1"; after that you
               choose an existing folder or "+ New".
  Tap a folder to open it (use the Back icon, Back button or Home to leave).
  Long-press an app inside a folder for "Take out"; long-press a folder icon for Open / Ungroup.
  Tap outside the box (or press Back) to close it.

SETTINGS ("Look & Feel")
  Open it from the gear icon at the end of the grid, or long-press empty space.
  (It is also a normal app called "Launcher Settings" in other launchers.)
  - Theme:       Classic, Ocean, Sunset, Neon, Candy, Mono,
                 Flat (plain circles, no gloss/rim/glow), iOS (squircles), Samsung (rounded squares),
                 Original (icons exactly as Android draws them), Ultra Sigma (gold chrome on black),
                 Among Us (crewmate red with a visor)
  - Bounciness:  0 (no overshoot) ... 100 (very springy); release the slider to see a demo
  - Background:  Black, Midnight, Aurora, Graphite, Ember, Violet
  - Image:       "Choose image" sets any picture as the background (centre-cropped to the screen).
                 Opens the watch's gallery / file picker. If the watch has none, push the picture with
                   adb push background.jpg /sdcard/WatchLauncher/background.jpg
                 and tap "Choose image" again (it asks for storage permission once).
                 "Image dimming" darkens it so icons stay readable. Picking a colour swatch switches the
                 image off; "Use saved image" brings it back, "Remove image" deletes it.
  - Icon size:   70% .. 130% of the normal grid size
  - Lens:        fisheye strength (low = flatter grid, high = big centre icon)
  - Switches:    Glow, App names (label under the centre icon), Haptics (ticks / long-press buzz)
  - Hidden apps: "Show N hidden apps" restores anything you hid from the long-press menu
  Changes save instantly and apply when you go back to the launcher (icons pop in again).
  "Reset to defaults" restores everything.

ADD YOUR OWN
  Styles.java: add a line to THEMES or BGS and it shows up in the settings automatically.
  Defaults at the top of HoneycombView.java: K (fisheye), FILL, SPACING, EDGE_DIM, GLOW_ALPHA.

BUILD
1. Android Studio: File > Open > this folder. Let Gradle sync (accept the plugin upgrade prompt if shown).
2. Build > Build APK(s). Output: app/build/outputs/apk/debug/app-debug.apk

INSTALL
  adb install -r app-debug.apk
(Updating over the old version keeps it as your default launcher.)

IF SOMETHING GOES WRONG
  adb uninstall com.example.watchlauncher
puts the stock launcher back.
