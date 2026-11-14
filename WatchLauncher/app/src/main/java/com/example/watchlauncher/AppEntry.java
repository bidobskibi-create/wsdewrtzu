package com.example.watchlauncher;

import android.content.ComponentName;
import android.graphics.Bitmap;

/** One launchable item: an app, a folder, or a built-in entry (Look & Feel / Back). */
class AppEntry {
    String label;
    String pkg;
    ComponentName component;   // null for folders and the Back entry
    Bitmap bubble;
    int glow; // dominant colour of the icon, used for the ambient background glow

    boolean isFolder;
    boolean isBack;
    boolean isSettings;
    String folderName;         // set when isFolder

    /** Stable id used to store hidden apps and folder members. */
    String key() {
        return component == null ? null : component.flattenToString();
    }
}
