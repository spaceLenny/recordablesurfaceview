package com.example.android.opengl;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;

public class PermissionsHelper {


    private static final String WRITE_EXTERNAL_STORAGE_PERMISSION
            = Manifest.permission.WRITE_EXTERNAL_STORAGE;

    private static final String RECORD_AUDIO_PERMISSION
            = Manifest.permission.RECORD_AUDIO;


    private static final int GRANT_REQUEST_CODE = 1;


    public static boolean hasPermissions(Activity activity) {
        return (activity.checkSelfPermission(WRITE_EXTERNAL_STORAGE_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED) &&
                (activity.checkSelfPermission(RECORD_AUDIO_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED);

    }

    /**
     * Check to see we have the necessary permissions for this app, and ask for them if we don't.
     */
    public static void requestPermissions(Activity activity) {
        activity.requestPermissions(new String[]{WRITE_EXTERNAL_STORAGE_PERMISSION, RECORD_AUDIO_PERMISSION},
                GRANT_REQUEST_CODE);
    }

}
