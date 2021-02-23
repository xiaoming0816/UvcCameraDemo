
package com.xcbj.uvccamerademo.uvc;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaScannerConnection;
import android.media.SoundPool;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;

import com.serenegiant.encoder.MediaAudioEncoder;
import com.serenegiant.encoder.MediaEncoder;
import com.serenegiant.encoder.MediaMuxerWrapper;
import com.serenegiant.encoder.MediaSurfaceEncoder;
import com.serenegiant.glutils.RenderHolderCallback;
import com.serenegiant.glutils.RendererHolder;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

public final class UsbCameraServer extends Handler {
    private static final boolean DEBUG = true;
    private static final String TAG = UsbCameraServer.class.getSimpleName();

    private static final int DEFAULT_WIDTH = 1280;
    private static final int DEFAULT_HEIGHT = 720;

    private int mFrameWidth = DEFAULT_WIDTH, mFrameHeight = DEFAULT_HEIGHT;

    private static class CallbackCookie {
        boolean isConnected;
    }

    public interface Callback {
        void onCameraOpen();

        void onCameraClose();

        void onRecordFinish();
    }

    private final RemoteCallbackList<IUVCServiceCallback> mCallbacks
            = new RemoteCallbackList<IUVCServiceCallback>();
    private int mRegisteredCallbackCount;

    private IFrameCallback mFrameCallback;

    private RendererHolder mRendererHolder;
    private final WeakReference<CameraThread> mWeakThread;

    public static UsbCameraServer createServer(final Context context, final USBMonitor.UsbControlBlock ctrlBlock, final int vid, final int pid) {
        if (DEBUG) Log.d(TAG, "createServer:=== ctrlBlock=" + ctrlBlock);
        final CameraThread thread = new CameraThread(context, ctrlBlock);
        thread.start();
        return thread.getHandler();
    }

    private UsbCameraServer(final CameraThread thread) {
        if (DEBUG) Log.d(TAG, "Constructor:");
        mWeakThread = new WeakReference<CameraThread>(thread);
        mRegisteredCallbackCount = 0;
        mRendererHolder = new RendererHolder(mFrameWidth, mFrameHeight, mRenderHolderCallback);
    }

    @Override
    protected void finalize() throws Throwable {
        if (DEBUG) Log.i(TAG, "finalize:");
        release();
        super.finalize();
    }

    public void registerCallback(final IUVCServiceCallback callback) {
        if (DEBUG) Log.d(TAG, "registerCallback:");
        mCallbacks.register(callback, new CallbackCookie());
        mRegisteredCallbackCount++;
    }

    public void registerRecordFinishCallback(Callback callback) {
        mCallback = callback;
    }


    private Callback mCallback;


    public boolean unregisterCallback(final IUVCServiceCallback callback) {
        if (DEBUG) Log.d(TAG, "unregisterCallback:");
        mCallbacks.unregister(callback);
        mRegisteredCallbackCount--;
        if (mRegisteredCallbackCount < 0) mRegisteredCallbackCount = 0;
        return mRegisteredCallbackCount == 0;
    }

    public void release() {
        if (DEBUG) Log.d(TAG, "release:");
        mCallbacks.kill();
        disconnect();

        if (mRendererHolder != null) {
            mRendererHolder.release();
            mRendererHolder = null;
        }
    }

    public void setUsbCameraFrameCallback(IFrameCallback frameCallback){
        mFrameCallback = frameCallback;
        sendMessage(obtainMessage(MSG_SET_FRAME_CALLBACK, frameCallback));
    }

    //********************************************************************************
//********************************************************************************
    public void resize(final int width, final int height) {
        if (DEBUG) Log.d(TAG, String.format("resize(%d,%d)", width, height));
        if (!isRecording()) {
            mFrameWidth = width;
            mFrameHeight = height;
            if (mRendererHolder != null) {
                mRendererHolder.resize(width, height);
            }
        }
    }

    public void connect() {
        if (DEBUG) Log.d(TAG, "connect:");
        final CameraThread thread = mWeakThread.get();
        if (!thread.isCameraOpened()) {
            sendMessage(obtainMessage(MSG_OPEN));
            sendMessage(obtainMessage(MSG_PREVIEW_START, mFrameWidth, mFrameHeight, mRendererHolder.getSurface()));
        } else {
            if (DEBUG) Log.d(TAG, "already connected, just call callback");
            processOnCameraStart();
        }
    }

    public void connectSlave() {
        if (DEBUG) Log.d(TAG, "connectSlave:");
        final CameraThread thread = mWeakThread.get();
        if (thread.isCameraOpened()) {
            processOnCameraStart();
        }
    }

    public void disconnect() {
        if (DEBUG) Log.d(TAG, "disconnect:");
//        stopRecording();
        final CameraThread thread = mWeakThread.get();
        if (thread == null) return;
        if (DEBUG) Log.d(TAG, "disconnect and wait thread lock");
//        synchronized (thread.mSync) {
//            sendEmptyMessage(MSG_PREVIEW_STOP);
            sendEmptyMessage(MSG_CLOSE);
            // wait for actually preview stopped to avoid releasing Surface/SurfaceTexture
            // while preview is still running.
            // therefore this method will take a time to execute
//            try {
//                thread.mSync.wait();
//            } catch (final InterruptedException e) {
//            }
//        }
    }

    public boolean isConnected() {
        final CameraThread thread = mWeakThread.get();
        return (thread != null) && thread.isCameraOpened();
    }

    public boolean isRecording() {
        final CameraThread thread = mWeakThread.get();
        return (thread != null) && thread.isRecording();
    }

    public void addSurface(final int id, final Surface surface, final boolean isRecordable, final IUVCServiceOnFrameAvailable onFrameAvailableListener) {
        if (DEBUG) Log.d(TAG, "addSurface:id=" + id + ",surface=" + surface);
        if (mRendererHolder != null) {
            mRendererHolder.addSurface(id, surface, isRecordable);
        }
    }

    public void removeSurface(final int id) {
        if (DEBUG) Log.d(TAG, "removeSurface:id=" + id);
        if (mRendererHolder != null)
            mRendererHolder.removeSurface(id);
    }

    public void startRecording(String outputFile) {
        Log.v(TAG,"----startRecording(); isRecording()："+isRecording());
        if (!isRecording()) {
            Message msg = obtainMessage(MSG_CAPTURE_START);
            msg.what = MSG_CAPTURE_START;
            msg.obj = outputFile;
            sendMessage(msg);
        }
    }

    public void stopRecording() {
        Log.v(TAG,"----stopRecording(); isRecording()："+isRecording());
        if (isRecording())
            sendEmptyMessage(MSG_CAPTURE_STOP);
    }

    public void captureStill(final String path) {
        if (mRendererHolder != null) {
            mRendererHolder.captureStill(path);
            sendMessage(obtainMessage(MSG_CAPTURE_STILL, path));
        }
    }

    public void startPreview(){
        sendMessage(obtainMessage(MSG_PREVIEW_START, mFrameWidth, mFrameHeight, mRendererHolder.getSurface()));
    }

    public void stopPreview(){
        sendEmptyMessage(MSG_PREVIEW_STOP);
    }

    //********************************************************************************
    private void processOnCameraStart() {
        if (DEBUG) Log.d(TAG, "processOnCameraStart:");
        try {
            final int n = mCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                if (!((CallbackCookie) mCallbacks.getBroadcastCookie(i)).isConnected)
                    try {
                        mCallbacks.getBroadcastItem(i).onConnected();
                        ((CallbackCookie) mCallbacks.getBroadcastCookie(i)).isConnected = true;
                    } catch (final Exception e) {
                        Log.e(TAG, "failed to call IOverlayCallback#onFrameAvailable");
                    }
            }
            mCallbacks.finishBroadcast();
        } catch (final Exception e) {
            Log.w(TAG, e);
        }

        if (mCallback != null) {
            mCallback.onCameraOpen();
        }
    }

    private void processOnCameraStop() {
        if (DEBUG) Log.d(TAG, "processOnCameraStop:");
        final int n = mCallbacks.beginBroadcast();
        for (int i = 0; i < n; i++) {
            if (((CallbackCookie) mCallbacks.getBroadcastCookie(i)).isConnected)
                try {
                    mCallbacks.getBroadcastItem(i).onDisConnected();
                    ((CallbackCookie) mCallbacks.getBroadcastCookie(i)).isConnected = false;
                } catch (final Exception e) {
                    Log.e(TAG, "failed to call IOverlayCallback#onDisConnected");
                }
        }
        mCallbacks.finishBroadcast();

        if (mCallback != null) {
            mCallback.onCameraClose();
        }
    }

    private void processOnCameraRecordFinish() {
        if (DEBUG) Log.d(TAG, "processOnCameraRecordFinish:");
        if (mCallbacks != null) {
            try {
                final int n = mCallbacks.beginBroadcast();
                for (int i = 0; i < n; i++) {
                    try {
                        if (DEBUG) Log.d(TAG, "getBroadcastItem(i).onRecordFinished()>>>>>");
                        mCallbacks.getBroadcastItem(i).onRecordFinished();
                    } catch (Exception e) {
                        Log.e(TAG, "failed to call #onRecordFinished");
                    }
                }
                mCallbacks.finishBroadcast();
            } catch (final Exception e) {
                Log.w(TAG, e);
            }
        }

        if (mCallback != null) {
            mCallback.onRecordFinish();
        }
    }

    //**********************************************************************
    private static final int MSG_OPEN = 0;
    private static final int MSG_CLOSE = 1;
    private static final int MSG_PREVIEW_START = 2;
    private static final int MSG_PREVIEW_STOP = 3;
    private static final int MSG_CAPTURE_STILL = 4;
    private static final int MSG_CAPTURE_START = 5;
    private static final int MSG_CAPTURE_STOP = 6;
    private static final int MSG_MEDIA_UPDATE = 7;
    private static final int MSG_RELEASE = 9;
    private static final int MSG_RECORD_FINISH = 10;
    private static final int MSG_SET_FRAME_CALLBACK = 11;


    @Override
    public void handleMessage(final Message msg) {
        final CameraThread thread = mWeakThread.get();
        if (thread == null) return;
        switch (msg.what) {
            case MSG_OPEN:
                thread.handleOpen();
                break;
            case MSG_CLOSE:
                thread.handleClose();
                break;
            case MSG_PREVIEW_START:
                if(mFrameCallback != null){
                    sendMessage(obtainMessage(MSG_SET_FRAME_CALLBACK, mFrameCallback));
                }
                thread.handleStartPreview(msg.arg1, msg.arg2, (Surface) msg.obj);
                break;
            case MSG_PREVIEW_STOP:
                thread.handleStopPreview();
                break;
            case MSG_CAPTURE_STILL:
                thread.handleCaptureStill((String) msg.obj);
                break;
            case MSG_CAPTURE_START:
                thread.handleStartRecording((String) msg.obj);
                break;
            case MSG_CAPTURE_STOP:
                thread.handleStopRecording();
                break;
            case MSG_MEDIA_UPDATE:
                thread.handleUpdateMedia((String) msg.obj);
                break;
            case MSG_RELEASE:
                thread.handleRelease();
                break;
            case MSG_RECORD_FINISH:
                Log.d(TAG, "receiver MSG_RECORD_FINISH");
                processOnCameraRecordFinish();
                break;
            case MSG_SET_FRAME_CALLBACK:
                thread.setFrameCallback((IFrameCallback) msg.obj);
                break;
            default:
                throw new RuntimeException("unsupported message:what=" + msg.what);
        }
    }

    private final RenderHolderCallback mRenderHolderCallback
            = new RenderHolderCallback() {
        @Override
        public void onCreate(final Surface surface) {
        }

        @Override
        public void onFrameAvailable() {
            final CameraThread thread = mWeakThread.get();
            if ((thread != null) && (thread.mVideoEncoder != null)) {
                try {
                    thread.mVideoEncoder.frameAvailableSoon();
                } catch (final Exception e) {
                    //
                }
            }
        }

        @Override
        public void onDestroy() {
        }
    };

    private static final class CameraThread extends Thread {
        private static final String TAG_THREAD = "Grid-CameraThread";
        private final Object mSync = new Object();
        private boolean mIsRecording;
        private final WeakReference<Context> mWeakContext;
        private int mEncoderSurfaceId;
        private int mFrameWidth, mFrameHeight;
        /**
         * shutter sound
         */
        private SoundPool mSoundPool;
        private int mSoundId;
        private UsbCameraServer mHandler;
        private USBMonitor.UsbControlBlock mCtrlBlock;

        private IFrameCallback mFrameCallback;
        /**
         * for accessing UVC camera
         */
        private volatile UVCCamera mUVCCamera;
        /**
         * muxer for audio/video recording
         */
        private MediaMuxerWrapper mMuxer;
        private MediaSurfaceEncoder mVideoEncoder;

        private CameraThread(final Context context, final USBMonitor.UsbControlBlock ctrlBlock) {
            super("CameraThread");
            if (DEBUG) Log.d(TAG_THREAD, "Constructor: ctrlBlock=" + ctrlBlock);
            mWeakContext = new WeakReference<Context>(context);
            mCtrlBlock = ctrlBlock;
            loadShutterSound(context);
        }

        @Override
        protected void finalize() throws Throwable {
            Log.i(TAG_THREAD, "CameraThread#finalize");
            super.finalize();
        }

        public UsbCameraServer getHandler() {
            if (DEBUG) Log.d(TAG_THREAD, "getHandler:");
            synchronized (mSync) {
                if (mHandler == null)
                    try {
                        mSync.wait();
                    } catch (final InterruptedException e) {
                    }
            }
            return mHandler;
        }

        public boolean isCameraOpened() {
            return mUVCCamera != null;
        }

        public boolean isRecording() {
            return (mUVCCamera != null) && (mMuxer != null);
        }

        public void handleOpen() {
            Log.d(TAG_THREAD, "handleOpen:");
            handleClose();
            synchronized (mSync) {
                mUVCCamera = new UVCCamera();
                try {
                    if (DEBUG)
                        Log.i(TAG, "Begin to open camera= usbmonitor" + mCtrlBlock.getUSBMonitor());
                    mUVCCamera.open(mCtrlBlock);
                } catch (Exception e) {
                    if (DEBUG) Log.i(TAG, "mUVCCamera.open exception=" + e);
                }
                if (DEBUG) Log.i(TAG, "supportedSize:" + mUVCCamera.getSupportedSize());
            }
            mHandler.processOnCameraStart();
        }

        public void handleClose() {
            if (DEBUG) Log.d(TAG_THREAD, "handleClose:");
            handleStopRecording();
            boolean closed = false;
            synchronized (mSync) {
                if (mUVCCamera != null) {
                    mUVCCamera.setFrameCallback(null,0);
                    mUVCCamera.stopPreview();
                    mUVCCamera.destroy();
                    mUVCCamera = null;
                    closed = true;
                }
                mSync.notifyAll();
            }
            if (closed)
                mHandler.processOnCameraStop();
            if (DEBUG) Log.d(TAG_THREAD, "handleClose:finished");
        }

        public void handleStartPreview(final int width, final int height, final Surface surface) {
            if (DEBUG) Log.d(TAG_THREAD, "handleStartPreview:");
            synchronized (mSync) {
                if (mUVCCamera == null) return;
                try {
                    mUVCCamera.setPreviewSize(DEFAULT_WIDTH, DEFAULT_HEIGHT, UVCCamera.PIXEL_FORMAT_YUV420SP, 1.0f);
//                    mUVCCamera.setPreviewSize(width, height, UVCCamera.FRAME_FORMAT_MJPEG);
                } catch (final IllegalArgumentException e) {
                    try {
                        // fallback to YUV mode
                        mUVCCamera.setPreviewSize(width, height, UVCCamera.DEFAULT_PREVIEW_MODE);
                    } catch (final IllegalArgumentException e1) {
                        mUVCCamera.destroy();
                        mUVCCamera = null;
                    }
                }
                if (mUVCCamera == null) {
                    return;
                }
                mUVCCamera.setFrameCallback(mFrameCallback, UVCCamera.PIXEL_FORMAT_YUV420SP);
                /*mUVCCamera.setFrameCallback(new IFrameCallback() {
                    @Override
                    public void onFrame(ByteBuffer byteBuffer) {
                        Log.v(TAG,"---- onFrame();");
                    }
                }, UVCCamera.PIXEL_FORMAT_YUV);*/

                mFrameWidth = width;
                mFrameHeight = height;
                mUVCCamera.setPreviewDisplay(surface);
                mUVCCamera.startPreview();
            }
        }

        public void handleStopPreview() {
            if (DEBUG) Log.d(TAG_THREAD, "handleStopPreview:");
            synchronized (mSync) {
                if (mUVCCamera != null) {
                    mUVCCamera.setFrameCallback(null,-1);
                    mUVCCamera.stopPreview();
                }
            }
        }

        private void handleResize(final int width, final int height, final Surface surface) {
            synchronized (mSync) {
                if (mUVCCamera != null) {
                    final Size sz = mUVCCamera.getPreviewSize();
                    if ((sz != null) && ((width != sz.width) || (height != sz.height))) {
                        mUVCCamera.stopPreview();
                        try {
//                            mUVCCamera.setPreviewSize(width, height);
                            mUVCCamera.setPreviewSize(width, height, UVCCamera.PIXEL_FORMAT_YUV420SP, 1.0f);
                        } catch (final IllegalArgumentException e) {
                            try {
                                mUVCCamera.setPreviewSize(sz.width, sz.height);
                            } catch (final IllegalArgumentException e1) {
                                // unexpectedly #setPreviewSize failed
                                mUVCCamera.destroy();
                                mUVCCamera = null;
                            }
                        }
                        if (mUVCCamera == null) return;
                        mFrameWidth = width;
                        mFrameHeight = height;
                        mUVCCamera.setPreviewDisplay(surface);
                        mUVCCamera.startPreview();
                    }
                }
            }
        }

        public void handleCaptureStill(final String path) {
            if (DEBUG) Log.d(TAG_THREAD, "handleCaptureStill:");

//            mSoundPool.play(mSoundId, 0.2f, 0.2f, 0, 0, 1.0f);    // play shutter sound
        }

        public void handleStartRecording(String outputFile) {
            if (DEBUG) Log.d(TAG_THREAD, "handleStartRecording:");
            try {
                if ((mUVCCamera == null) || (mMuxer != null)) return;
                mMuxer = new MediaMuxerWrapper(outputFile);    // if you record audio only, ".m4a" is also OK.
                // new MediaSurfaceEncoder(mFrameWidth, mFrameHeight, mMuxer, mMediaEncoderListener);
                new MediaSurfaceEncoder(mMuxer, mFrameWidth, mFrameHeight, mMediaEncoderListener);
                if (true) {
                    // for audio capturing
                    new MediaAudioEncoder(mMuxer, mMediaEncoderListener);
                }
                mMuxer.prepare();
                mMuxer.startRecording();
            } catch (final IOException e) {
                Log.e(TAG, "startCapture:", e);
            }
        }

        public void handleStopRecording() {
            if (DEBUG) Log.d(TAG_THREAD, "handleStopRecording:mMuxer=" + mMuxer);
            if (mMuxer != null) {
                synchronized (mSync) {
                    if (mUVCCamera != null) {
                        mUVCCamera.stopCapture();
                    }
                }
                mMuxer.stopRecording();
                mMuxer = null;
                // you should not wait here
            }
        }

        public void handleUpdateMedia(final String path) {
            if (DEBUG) Log.d(TAG_THREAD, "handleUpdateMedia:path=" + path);
            final Context context = mWeakContext.get();
            if (context != null) {
                try {
                    if (DEBUG) Log.i(TAG, "MediaScannerConnection#scanFile");
                    MediaScannerConnection.scanFile(context, new String[]{path}, null, null);
                } catch (final Exception e) {
                    Log.e(TAG, "handleUpdateMedia:", e);
                }
            } else {
                Log.w(TAG, "MainActivity already destroyed");
                // give up to add this movice to MediaStore now.
                // Seeing this movie on Gallery app etc. will take a lot of time.
                handleRelease();
            }

            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_RECORD_FINISH), 100);
        }

        public void handleRelease() {
            if (DEBUG) Log.d(TAG_THREAD, "handleRelease:");
            handleClose();
            if (mCtrlBlock != null) {
                mCtrlBlock.close();
                mCtrlBlock = null;
            }
            if (!mIsRecording)
                Looper.myLooper().quit();
        }

        public void setFrameCallback(IFrameCallback frameCallback){
            Log.v(TAG,"---- setFrameCallback();frameCallback:"+frameCallback);
            this.mFrameCallback = frameCallback;
        }

/*		// if you need frame data as ByteBuffer on Java side, you can use this callback method with UVCCamera#setFrameCallback
        private final IFrameCallback mIFrameCallback = new IFrameCallback() {
			@Override
			public void onFrame(final ByteBuffer frame) {
			}
		}; */

        private final IUVCServiceOnFrameAvailable mOnFrameAvailable = new IUVCServiceOnFrameAvailable() {
            @Override
            public IBinder asBinder() {
                if (DEBUG) Log.d(TAG_THREAD, "asBinder:");
                return null;
            }

            @Override
            public void onFrameAvailable() throws RemoteException {
//				if (DEBUG) Log.d(TAG_THREAD, "onFrameAvailable:");
                if (mVideoEncoder != null)
                    mVideoEncoder.frameAvailableSoon();
            }
        };

        private final MediaEncoder.MediaEncoderListener mMediaEncoderListener = new MediaEncoder.MediaEncoderListener() {
            @Override
            public void onPrepared(final MediaEncoder encoder) {
                if (DEBUG) Log.d(TAG, "onPrepared:encoder=" + encoder);
                mIsRecording = true;
                if (encoder instanceof MediaSurfaceEncoder)
                    try {
                        mVideoEncoder = (MediaSurfaceEncoder) encoder;
                        final Surface encoderSurface = mVideoEncoder.getInputSurface();
                        mEncoderSurfaceId = encoderSurface.hashCode();
                        mHandler.mRendererHolder.addSurface(mEncoderSurfaceId, encoderSurface, true);
                    } catch (final Exception e) {
                        Log.e(TAG, "onPrepared:", e);
                    }
            }

            @Override
            public void onStopped(final MediaEncoder encoder) {
                if (DEBUG) Log.v(TAG_THREAD, "onStopped:encoder=" + encoder);
                //notify mediaencoder stop
                //mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_RECORD_FINISH), 100);
                if ((encoder instanceof MediaSurfaceEncoder))
                    try {
                        mIsRecording = false;
                        if (mEncoderSurfaceId > 0 && mHandler.mRendererHolder != null) {
                            try {
                                mHandler.mRendererHolder.removeSurface(mEncoderSurfaceId);
                            } catch (final Exception e) {
                                Log.w(TAG, e);
                            }
                        }
                        mEncoderSurfaceId = -1;
                        synchronized (mSync) {
                            if (mUVCCamera != null) {
                                mUVCCamera.stopCapture();
                            }
                        }
                        mVideoEncoder = null;
                        final String path = encoder.getOutputPath();
                        if (!TextUtils.isEmpty(path)) {
                            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_MEDIA_UPDATE, path), 1000);
                        } else {
                            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_RECORD_FINISH), 0);
                        }
                    } catch (final Exception e) {
                        Log.e(TAG, "onPrepared:", e);
                    }
            }
        };

        /**
         * prepare and load shutter sound for still image capturing
         */
        @SuppressWarnings("deprecation")
        private void loadShutterSound(final Context context) {
            if (DEBUG) Log.d(TAG_THREAD, "loadShutterSound:");
            // get system stream type using refrection
            int streamType;
            try {
                final Class<?> audioSystemClass = Class.forName("android.media.AudioSystem");
                final Field sseField = audioSystemClass.getDeclaredField("STREAM_SYSTEM_ENFORCED");
                streamType = sseField.getInt(null);
            } catch (final Exception e) {
                streamType = AudioManager.STREAM_SYSTEM;    // set appropriate according to your app policy
            }
            if (mSoundPool != null) {
                try {
                    mSoundPool.release();
                } catch (final Exception e) {
                }
                mSoundPool = null;
            }
            // load sutter sound from resource
            mSoundPool = new SoundPool(2, streamType, 0);
            mSoundId = 0;//mSoundPool.load(context, R.raw.camera_click, 1);
        }

        @Override
        public void run() {
            if (DEBUG) Log.d(TAG_THREAD, "run:");
            // Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            Looper.prepare();
            synchronized (mSync) {
                mHandler = new UsbCameraServer(this);
                mSync.notifyAll();
            }
            Looper.loop();
            synchronized (mSync) {
                mHandler = null;
                mSoundPool.release();
                mSoundPool = null;
                mSync.notifyAll();
            }
            if (DEBUG) Log.d(TAG_THREAD, "run:finished");
        }
    }
}
