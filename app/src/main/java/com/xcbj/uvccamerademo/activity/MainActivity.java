package com.xcbj.uvccamerademo.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.serenegiant.usb.USBMonitor;
import com.xcbj.uvccamerademo.R;
import com.xcbj.uvccamerademo.constants.MyConstants;
import com.xcbj.uvccamerademo.module.UsbCameraModule;
import com.xcbj.uvccamerademo.view.AutoFitTextureView;

/**
 * @author york.zhou
 * @date 2021.02.18
 * */
public class MainActivity extends AppCompatActivity {
    private static String TAG = MainActivity.class.getSimpleName();
    private AutoFitTextureView mTextureView;
    private Button mBtnStartRecord;
    private Button mBtnStopRecord;
    private Button mBtnCapture;
    private Button mBtnStartRequestRealStream;
    private Button mBtnStopRequestRealStream;

    private UsbCameraModule mUsbCameraModule;
    private Surface mPreviewSurface;
    private static final int REQUEST_PERMISSIONS = 1;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initUsbCameraModule();

        if (!hasPermissionsGranted(new String[]{
                Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO})) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS, Manifest.permission.SYSTEM_ALERT_WINDOW,Manifest.permission.WRITE_EXTERNAL_STORAGE
            ,Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSIONS);
            return;
        }
    }

    private boolean hasPermissionsGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(getApplicationContext(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult");
    }

    private void initView() {
        mBtnStartRecord = findViewById(R.id.btn_start_record);
        mBtnStartRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecord();
            }
        });

        mBtnStopRecord = findViewById(R.id.btn_stop_record);
        mBtnStopRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecord();
            }
        });

        mBtnCapture = findViewById(R.id.btn_capture);
        mBtnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                capture();
            }
        });

        mBtnStartRequestRealStream = findViewById(R.id.btn_start_request_real_stream);
        mBtnStartRequestRealStream.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRequestRealStream();
            }
        });

        mBtnStopRequestRealStream = findViewById(R.id.btn_stop_request_real_stream);
        mBtnStopRequestRealStream.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRequestRealStream();
            }
        });

        mTextureView = (AutoFitTextureView) findViewById(R.id.textureView);
        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
    }

    @Override
    protected void onResume() {
        super.onResume();

        registerUsbMonitot(true);
        if(mUsbCameraModule != null){
            if(mPreviewSurface != null && mPreviewSurface.isValid()){
                startPreview(mPreviewSurface);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPreview();
        registerUsbMonitot(false);
    }

    private void initUsbCameraModule(){
        Log.v(TAG,"---- initUsbCameraModule();mUsbCameraModule:"+mUsbCameraModule);
        if(mUsbCameraModule == null) {
            mUsbCameraModule = UsbCameraModule.getInstance(getApplicationContext());
            mUsbCameraModule.init(MyConstants.DEFAULT_USB_CAM_WIDTH, MyConstants.DEFAULT_USB_CAM_HEIGHT);
        }
    }

    private void registerUsbMonitot(boolean register){
        if(mUsbCameraModule != null){
            if(register){
                mUsbCameraModule.registerUsbMonitor(null,true);
            }else{
                mUsbCameraModule.registerUsbMonitor(null,false);
            }
        }
    }
    /**
     * Start Preview
     * @param surface
     */
    private void startPreview(Surface surface){
        Log.v(TAG,"--- startPreview();");
        if(mUsbCameraModule != null){
            mUsbCameraModule.startPreview(surface);
        }
    }

    /**
     * Stop Preview
     */
    private void stopPreview(){
        if(mUsbCameraModule != null){
            mUsbCameraModule.stopPreview();
        }
    }

    /**
     * Start Record
     */
    private void startRecord() {
        if(mUsbCameraModule != null){
            mUsbCameraModule.startUsbCamRecording();
            showToast(R.string.start_record);
        }
    }

    /**
     * Stop Record
     */
    private void stopRecord() {
         if(mUsbCameraModule != null){
             mUsbCameraModule.stopUsbCamRecording();
             showToast(R.string.stop_record);
         }
    }

    /**
     * Take Picture
     */
    private void capture() {
        if(mUsbCameraModule != null){
            mUsbCameraModule.capture();
            showToast(R.string.capture);
        }
    }

    /**
     * Set RealStream CallBack
     */
    private void startRequestRealStream() {
        if(mUsbCameraModule != null){
            mUsbCameraModule.setUsbCameraFrameCallback(new UsbCameraModule.IUsbFrameCallback() {
                @Override
                public void onStreamUpdate(byte[] frameByte, int format, int width, int height) {
                    Log.v(TAG,"----onStreamUpdate(); width:"+width + ";height:"+height);
                }
            });
        }
    }

    private void stopRequestRealStream() {
       if(mUsbCameraModule != null){
           mUsbCameraModule.setUsbCameraFrameCallback(null);
       }
    }

    private void disConncetUsbCam(){
        if(mUsbCameraModule != null){
            mUsbCameraModule.destory();
        }
    }

    TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            mPreviewSurface = new Surface(surface);
            startPreview(mPreviewSurface);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private void showToast(int textId){
        Toast.makeText(getApplicationContext(),getResources().getString(textId),Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disConncetUsbCam();
    }
}
