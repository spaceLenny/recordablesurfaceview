/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.opengl;

import com.uncorkedstudios.android.view.recordablesurfaceview.RecordableSurfaceView;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import android.util.Log;
import android.util.Size;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowInsets;
import android.view.WindowMetrics;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import static android.content.ContentValues.TAG;

public class OpenGLES20Activity extends Activity {

    private RecordableSurfaceView mGLView;

    private boolean mIsRecording;

    private File mOutputFile;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create a GLSurfaceView instance and set it
        // as the ContentView for this Activity
        mGLView = new MyGLSurfaceView(this);
        setContentView(mGLView);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // The following call pauses the rendering thread.
        // If your OpenGL application is memory intensive,
        // you should consider de-allocating objects that
        // consume significant memory here.
        mGLView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The following call resumes a paused rendering thread.
        // If you de-allocated graphic objects for onPause()
        // this is a good place to re-allocate them.

        if (PermissionsHelper.hasPermissions(this)) {
            // Note that order matters - see the note in onPause(), the reverse applies here.
            mGLView.resume();
            try {
                mOutputFile = createVideoOutputFile();
                android.graphics.Point size = getScreenSize();
                mGLView.initRecorder(mOutputFile, size.x, size.y, null, null);
            } catch (IOException ioex) {
                Log.e(TAG, "Couldn't re-init recording", ioex);
            }
        } else {
            PermissionsHelper.requestPermissions(this);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    private Point getScreenSize(){

        Point size = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            final WindowMetrics metrics = getWindowManager().getCurrentWindowMetrics();
            // Gets all excluding insets
            final WindowInsets windowInsets = metrics.getWindowInsets();
            Insets insets = windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars());

            int insetsWidth = insets.right + insets.left;
            int insetsHeight = insets.top + insets.bottom;

            // Legacy size that Display#getSize reports
            final Rect bounds = metrics.getBounds();
            size = new Point(bounds.width() - insetsWidth,
                    bounds.height() - insetsHeight);
        } else {
            size = new android.graphics.Point();
            getWindowManager().getDefaultDisplay().getRealSize(size);
        }

        return size;
    }

    private File createVideoOutputFile() {
        File tempFile = null;
        String filename = new Date().getTime() + "";
        String filesDir = "";

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                filesDir = getExternalFilesDir(null).getPath();
            } else {
                filesDir = getFilesDir().getCanonicalPath();
            }

            File dirCheck = new File(
                    filesDir + "/" + "captures");

            if (!dirCheck.exists()) {
                dirCheck.mkdirs();
            }

            tempFile = new File(
                    filesDir + "/" + "captures" + "/"
                            + filename + ".mp4");
        } catch (IOException ioex) {
            Log.e(TAG, "Couldn't create output file", ioex);
        }

        return tempFile;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Note that order matters - see the note in onPause(), the reverse applies here.
        mGLView.resume();
        try {
            mOutputFile = createVideoOutputFile();
            android.graphics.Point size = getScreenSize();

            mGLView.initRecorder(mOutputFile, size.x, size.y, null, null);
        } catch (IOException ioex) {
            Log.e(TAG, "Couldn't re-init recording", ioex);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mIsRecording) {
            mGLView.stopRecording();

            Uri contentUri = FileProvider.getUriForFile(this,
                    "com.example.android.opengl.fileprovider", mOutputFile);

            share(contentUri);
            mIsRecording = false;
            mOutputFile = createVideoOutputFile();

            try {
                int screenWidth = mGLView.getWidth();
                int screenHeight = mGLView.getHeight();
                mGLView.initRecorder(mOutputFile, (int) screenWidth, (int) screenHeight, null,
                        null);
            } catch (IOException ioex) {
                Log.e(TAG, "Couldn't re-init recording", ioex);
            }
            item.setTitle("Record");

        } else {

            mGLView.startRecording();
            Log.v(TAG, "Recording Started");

            item.setTitle("Stop");
            mIsRecording = true;

        }
        return true;
    }


    private void share(Uri contentUri) {
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.setType("video/mp4");
        shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Share with"));

    }

}