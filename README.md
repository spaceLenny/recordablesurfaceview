# RecordableSurfaceView

RecordableSurfaceView is a lightweight library designed to bring MP4 recording of an OpenGL renderer on Android to your app with ease.

  - Functionally a drop-in replacement for GLSurfaceView
  - Full lifecycle callbacks in a View tree.
  - Minimal effort to implement.

## How to get set up

  - Clone this repo
  - Add the following permissions to your Manifest (and request them!)
```xml   
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
```

  - Add RecordableSurfaceview to your project
  - in your main GL Activity or top-level renderer, implement         

```java
public class SomeActivity extends Activity implements RecordableSurfaceView.RendererCallbacks
```

  - Set the callbacks on the ```RecordableSurfaceView``` and  init the recording stack:
 
        mSurfaceView.setRendererCallbacks(this);
        mSurfaceView.doSetup();

# How to record:
  - Ensure you've called ```doSetup``` and requested (and have been granted!) permissions.
  - Init the recording itself by creating a ```File``` object for output, and calling ```initRecorder``` on the surface. You will also need to supply the dimensions of the output movie file, and optionally implement the callbacks provided by the MediaRecorder 
  - Call ```startRecording``` when looking to capture, and ```stopRecording``` when done. 
  - ***NOTE*** the underlying MediaRecorder still abides by the lifecycle state machine as defined by the MediaRecorder in Android. In order to record again aftering calling ```stopRecording``` you will need to re-init by calling ```initRecorder``` again.

## See also: 
[MediaRecorder](https://developer.android.com/reference/android/media/MediaRecorder.html)

[MediaRecorder.OnErrorListener](https://developer.android.com/reference/android/media/MediaRecorder.OnErrorListener.html)

[MediaRecorder.OnInfoListener](https://developer.android.com/reference/android/media/MediaRecorder.OnInfoListener.html)

## TODO:
  - build ```aar``` to distribute library via gradle/maven
  - expose configuration options of media recorder



License
----

Apache 2.0


**Useful Android Stuff from the community!**
