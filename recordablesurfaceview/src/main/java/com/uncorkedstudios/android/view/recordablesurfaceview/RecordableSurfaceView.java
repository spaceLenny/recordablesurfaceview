/*
 * Copyright 2017 Uncorked Studios Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.uncorkedstudios.android.view.recordablesurfaceview;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaRecorder;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Used to record video of the content of a SurfaceView, backed by a GL render loop.
 *
 * Intended as a near-drop-in replacement for {@link GLSurfaceView}, but reliant on callbacks
 * instead of
 * an explicit {@link GLSurfaceView.Renderer}.
 *
 *
 * <p><strong>Note:</strong> Currently, RecordableSurfaceView does not record video on the emulator
 * due to a dependency on {@link MediaRecorder}.</p>
 *
 */
public class RecordableSurfaceView extends SurfaceView {

    @SuppressWarnings({"UnusedDeclaration"})
    private static final String TAG = RecordableSurfaceView.class.getSimpleName();

    /**
     * The renderer only renders when the surface is created, or when @link{requestRender} is
     * called.
     */
    public static int RENDERMODE_WHEN_DIRTY = GLSurfaceView.RENDERMODE_WHEN_DIRTY;


    /**
     * The renderer is called continuously to re-render the scene.
     */
    public static int RENDERMODE_CONTINUOUSLY = GLSurfaceView.RENDERMODE_CONTINUOUSLY;

    private Surface mSurface;

    private AtomicInteger mRenderMode = new AtomicInteger(RENDERMODE_CONTINUOUSLY);

    private int mWidth = 0;

    private int mHeight = 0;

    private boolean mPaused = false;

    private MediaRecorder mMediaRecorder;

    private ARRenderThread mARRenderThread;

    private AtomicBoolean mIsRecording = new AtomicBoolean(false);

    private AtomicBoolean mHasGLContext = new AtomicBoolean(false);

    private WeakReference<RendererCallbacks> mRendererCallbacksWeakReference;

    private AtomicBoolean mSizeChange = new AtomicBoolean(false);

    private AtomicBoolean mRenderRequested = new AtomicBoolean(false);

    /**
     * @param context -
     */
    public RecordableSurfaceView(Context context) {
        super(context);
    }

    /**
     * @param context -
     * @param attrs   -
     */
    public RecordableSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * @param context      -
     * @param attrs        -
     * @param defStyleAttr -
     */
    public RecordableSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * @param context      -
     * @param attrs        -
     * @param defStyleAttr -
     * @param defStyleRes  -
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public RecordableSurfaceView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    /**
     * Performs necessary setup operations such as creating a MediaCodec persistent surface and
     * setting up initial state.
     *
     * Also links the SurfaceHolder that manages the Surface View to the render thread for lifecycle
     * callbacks
     *
     * @see MediaCodec
     * @see SurfaceHolder.Callback
     */
    public void doSetup() {

        if (!mHasGLContext.get()) {
            mSurface = MediaCodec.createPersistentInputSurface();
            mARRenderThread = new ARRenderThread();
        }

        this.getHolder().addCallback(mARRenderThread);

        if (getHolder().getSurface().isValid()) {
            mARRenderThread.surfaceCreated(null);
        }

        mPaused = true;

    }

    /**
     * Pauses the render thread.
     */
    public void pause() {
        mPaused = true;
    }

    /**
     * Resumes a paused render thread, or in the case of an interrupted or terminated
     * render thread, re-calls {@link #doSetup()} to build/start the GL context again.
     *
     * This method is useful for use in conjunction with the Activity lifecycle
     */
    public void resume() {
        doSetup();
        mPaused = false;
    }

    /**
     * Pauses rendering, but is nondestructive at the moment.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public void stop() {
        mPaused = true;
    }


    /**
     * @return int representing the current render mode of this object
     * @see RecordableSurfaceView#RENDERMODE_WHEN_DIRTY
     * @see RecordableSurfaceView#RENDERMODE_CONTINUOUSLY
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public int getRenderMode() {
        return mRenderMode.get();
    }

    /**
     * Set the rendering mode. When renderMode is {@link RecordableSurfaceView#RENDERMODE_CONTINUOUSLY},
     * the renderer is called repeatedly to re-render the scene. When renderMode is {@link
     * RecordableSurfaceView#RENDERMODE_WHEN_DIRTY}, the renderer only rendered when the surface is
     * created, or when {@link RecordableSurfaceView#requestRender()} is called. Defaults to {@link
     * RecordableSurfaceView#RENDERMODE_CONTINUOUSLY}.
     *
     * Using {@link RecordableSurfaceView#RENDERMODE_WHEN_DIRTY} can improve battery life and
     * overall system performance by allowing the GPU and CPU to idle when the view does not need
     * to
     * be updated.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public void setRenderMode(int mode) {
        mRenderMode.set(mode);
    }

    /**
     * Request that the renderer render a frame.
     * This method is typically used when the render mode has been set to {@link
     * RecordableSurfaceView#RENDERMODE_WHEN_DIRTY},  so that frames are only rendered on demand.
     * May be called from any thread.
     *
     * Must not be called before a renderer has been set.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public void requestRender() {
        mRenderRequested.set(true);
    }

    /**
     * Iitializes the {@link MediaRecorder} ad relies on its lifecycle and requirements.
     *
     * @param saveToFile    the File object to record into. Assumes the calling program has
     *                      permission to write to this file
     * @param displayWidth  the Width of the display
     * @param displayHeight the Height of the display
     * @param errorListener optional {@link MediaRecorder.OnErrorListener} for recording state callbacks
     * @param infoListener  optional {@link MediaRecorder.OnInfoListener} for info callbacks
     * @see MediaRecorder
     */
    @SuppressWarnings({"all"})
    public void initRecorder(File saveToFile, int displayWidth, int displayHeight,
            MediaRecorder.OnErrorListener errorListener, MediaRecorder.OnInfoListener infoListener)
            throws IOException {

        mMediaRecorder = new MediaRecorder();

        mMediaRecorder.setOnInfoListener(infoListener);

        mMediaRecorder.setOnErrorListener(errorListener);

        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setInputSurface(mSurface);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder.setAudioSamplingRate(44100);
        mMediaRecorder.setAudioEncodingBitRate(96000);


        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);

        mMediaRecorder.setVideoEncodingBitRate(12000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(displayWidth, displayHeight);

        mMediaRecorder.setOutputFile(saveToFile.getPath());
        mMediaRecorder.prepare();

    }
    
    /**
     * @see MediaRecorder#start()
     * @return true if the recording started successfully and false if not
     */
    public boolean startRecording() {
        boolean success = true;
        try {
            mMediaRecorder.start();
            mIsRecording.set(true);
        } catch (IllegalStateException e) {
            success = false;
            mIsRecording.set(false);
            mMediaRecorder.reset();
            mMediaRecorder.release();
        }
        return success;
    }

    /**
     * Stops the {@link MediaRecorder} and sets the internal state of this object to 'Not
     * recording'
     * It is important to call this before attempting to play back the video that has been
     * recorded.
     *
     * @return true if the recording stopped successfully and false if not
     * @throws IllegalStateException if not recording when called
     */
    public boolean stopRecording() throws IllegalStateException {
        if (mIsRecording.get()) {
            boolean success = true;
            try {
                mMediaRecorder.stop();
                mIsRecording.set(false);
            } catch (RuntimeException e) {
                success = false;
            } finally {
                mMediaRecorder.release();
            }
            return success;
        } else {
            throw new IllegalStateException("Cannot stop. Is not recording.");
        }

    }

    /**
     * Returns the reference (if any) to the {@link RendererCallbacks}
     *
     * @return the callbacks if registered
     * @see RendererCallbacks
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public RendererCallbacks getRendererCallbacks() {
        if (mRendererCallbacksWeakReference != null) {
            return mRendererCallbacksWeakReference.get();
        }

        return null;
    }

    /**
     * Add a {@link RendererCallbacks} object to handle rendering. Not setting one of these is not
     * necessarily an error, but is usually necessary.
     *
     * @param surfaceRendererCallbacks - the object to call back to
     */
    public void setRendererCallbacks(RendererCallbacks surfaceRendererCallbacks) {

        mRendererCallbacksWeakReference = new WeakReference<>(surfaceRendererCallbacks);
    }




    /**
     * Lifecycle events for the SurfaceView and renderer. These callbacks (unless specified)
     * are executed on the GL thread.
     */
    public interface RendererCallbacks {

        /**
         * The surface has been created and bound to the GL context.
         *
         * A GL context is guaranteed to exist when this function is called.
         */
        void onSurfaceCreated();

        /**
         * The surface has changed width or height.
         *
         * This callback will only be called when there is a change to either or both params
         *
         * @param width  width of the surface
         * @param height height of the surface
         */
        void onSurfaceChanged(int width, int height);

        /**
         * Called just before the GL Context is torn down.
         */
        void onSurfaceDestroyed();


        /**
         * Called when the GL context has been created and has been bound.
         */
        void onContextCreated();

        /**
         * Called before onDrawFrame, each time as a hook to adjust a global clock for rendering,
         * or other pre-frame modifications that need to be made before rendering.
         */
        void onPreDrawFrame();

        /**
         * Render call. Called twice when recording: first for screen display, second for video
         * file.
         */
        void onDrawFrame();
    }


    private class ARRenderThread extends Thread implements SurfaceHolder.Callback2 {

        EGLDisplay mEGLDisplay;

        EGLContext mEGLContext;

        EGLSurface mEGLSurface;

        EGLSurface mEGLSurfaceMedia;


        int[] config = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                0x3142, 1,
                EGL14.EGL_NONE
        };


        ARRenderThread() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                config[10] = EGLExt.EGL_RECORDABLE_ANDROID;
            }
        }

        private AtomicBoolean mLoop = new AtomicBoolean(false);

        EGLConfig chooseEglConfig(EGLDisplay eglDisplay) {
            int[] configsCount = new int[]{0};
            EGLConfig[] configs = new EGLConfig[1];
            EGL14.eglChooseConfig(eglDisplay, config, 0, configs, 0, configs.length, configsCount,
                    0);
            return configs[0];
        }

        @Override
        public void run() {
            if (mHasGLContext.get()) {
                return;
            }
            mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] version = new int[2];
            EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1);
            EGLConfig eglConfig = chooseEglConfig(mEGLDisplay);
            mEGLContext = EGL14
                    .eglCreateContext(mEGLDisplay, eglConfig, EGL14.EGL_NO_CONTEXT,
                            new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);

            int[] surfaceAttribs = {
                    EGL14.EGL_NONE
            };

            mEGLSurface = EGL14
                    .eglCreateWindowSurface(mEGLDisplay, eglConfig, RecordableSurfaceView.this,
                            surfaceAttribs, 0);
            EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext);

            // guarantee to only report surface as created once GL context
            // associated with the surface has been created, and call on the GL thread
            // NOT the main thread but BEFORE the codec surface is attached to the GL context
            if (mRendererCallbacksWeakReference != null
                    && mRendererCallbacksWeakReference.get() != null) {

                mRendererCallbacksWeakReference.get().onSurfaceCreated();

            }

            mEGLSurfaceMedia = EGL14
                    .eglCreateWindowSurface(mEGLDisplay, eglConfig, mSurface,
                            surfaceAttribs, 0);

            GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

            mHasGLContext.set(true);

            if (mRendererCallbacksWeakReference != null
                    && mRendererCallbacksWeakReference.get() != null) {
                mRendererCallbacksWeakReference.get().onContextCreated();
            }

            mLoop.set(true);

            while (mLoop.get()) {

                if (!mPaused) {
                    boolean shouldRender = false;

                    //we're just rendering when requested, so check that no one
                    //has requested and if not, just continue
                    if (mRenderMode.get() == RENDERMODE_WHEN_DIRTY) {

                        if (mRenderRequested.get()) {
                            mRenderRequested.set(false);
                            shouldRender = true;
                        }

                    } else {
                        shouldRender = true;
                    }

                    if (mSizeChange.get()) {

                        GLES20.glViewport(0, 0, mWidth, mHeight);

                        if (mRendererCallbacksWeakReference != null
                                && mRendererCallbacksWeakReference.get() != null) {
                            mRendererCallbacksWeakReference.get()
                                    .onSurfaceChanged(mWidth, mHeight);
                        }

                        mSizeChange.set(false);
                    }

                    if (shouldRender) {

                        if (mRendererCallbacksWeakReference != null
                                && mRendererCallbacksWeakReference.get() != null) {
                            mRendererCallbacksWeakReference.get().onPreDrawFrame();
                        }

                        if (mRendererCallbacksWeakReference != null
                                && mRendererCallbacksWeakReference.get() != null) {
                            mRendererCallbacksWeakReference.get().onDrawFrame();
                        }

                        EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface);

                        if (mIsRecording.get()) {
                            EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurfaceMedia, mEGLSurfaceMedia,
                                    mEGLContext);
                            if (mRendererCallbacksWeakReference != null
                                    && mRendererCallbacksWeakReference.get() != null) {
                                mRendererCallbacksWeakReference.get().onDrawFrame();
                            }
                            EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurfaceMedia);
                            EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface,
                                    mEGLContext);
                        }
                    }
                }

                try {
                    Thread.sleep((long) (1f / 60f * 1000f));
                } catch (InterruptedException intex) {
                    if (mRendererCallbacksWeakReference != null
                            && mRendererCallbacksWeakReference.get() != null) {
                        mRendererCallbacksWeakReference.get().onSurfaceDestroyed();
                    }

                    if (mEGLDisplay != null) {
                        EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE,
                                EGL14.EGL_NO_SURFACE,
                                EGL14.EGL_NO_CONTEXT);

                        if (mEGLSurface != null) {
                            EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface);
                        }

                        if (mEGLSurfaceMedia != null) {
                            EGL14.eglDestroySurface(mEGLDisplay, mEGLSurfaceMedia);
                        }

                        EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
                        mHasGLContext.set(false);
                        EGL14.eglReleaseThread();
                        EGL14.eglTerminate(mEGLDisplay);
                        mSurface.release();

                    }
                    return;
                }
            }
        }


        @Override
        public void surfaceRedrawNeeded(SurfaceHolder surfaceHolder) {

        }

        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {

            if (!this.isAlive() && !this.isInterrupted() && this.getState() != State.TERMINATED) {
                this.start();
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int width, int height) {

            if (mWidth != width) {
                mWidth = width;
                mSizeChange.set(true);
            }

            if (mHeight != height) {
                mHeight = height;
                mSizeChange.set(true);
            }


        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
            mLoop.set(false);
            this.interrupt();
            getHolder().removeCallback(ARRenderThread.this);
        }
    }

}