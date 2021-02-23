package com.xcbj.uvccamerademo.module;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;

import com.serenegiant.usb.DeviceFilter;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.USBMonitor;
import com.xcbj.uvccamerademo.R;
import com.xcbj.uvccamerademo.constants.MyConstants;
import com.xcbj.uvccamerademo.utils.FileUtil;
import com.xcbj.uvccamerademo.uvc.UsbCameraServer;

import java.io.File;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * @Func UsbCamera相关功能模块
 * @Date 2021.01.28
 */
public class UsbCameraModule {
        private static String TAG = UsbCameraModule.class.getSimpleName();
    public static UsbCameraModule mInstance = null;
    public static Context mContext = null;

    private static int DEFAULT_WIDTH = 1280;
    private static int DEFAULT_HEIGHT = 720;

    private USBMonitor mUSBMonitor;
    private UsbCameraServer mUsbCameraServer;
    private boolean isCameraViewAttach;
    private USBMonitor.OnDeviceConnectListener mUsbDeviceConnectListener;
    private Surface mPreviewSurface;
    private int mPrevieWidth = DEFAULT_WIDTH;
    private int mPreviewHeight = DEFAULT_HEIGHT;
    private USBMonitor.UsbControlBlock mUsbControlBlock;
    private UsbDevice mConnectUsbDevice;
    private MyConstants.UsbCamState mUsbCamState = MyConstants.UsbCamState.IDLE;
    private MyConstants.UsbDeviceState mUsbDeviceState = MyConstants.UsbDeviceState.DISCONNECT;
    private IUsbFrameCallback mIUsbCameraFrameCallback = null;

    public interface IUsbFrameCallback{
         void onStreamUpdate(byte[] frameByte,int format,int width,int height);
    }

    public static UsbCameraModule getInstance(Context context){
        mContext = context;
        if(mInstance == null){
            mInstance = new UsbCameraModule();
        }
        return mInstance;
    }

    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    };

    public void init(int previewWith,int previewHeight){
        mPrevieWidth = previewWith;
        mPreviewHeight = previewHeight;

        setUsbMonitor(mOnDeviceConnectListener);
    }

    public MyConstants.UsbCamState getUsbCamState(){
        return mUsbCamState;
    }

    /**
     * UsbMonitor初始化及状态监听注册
     * @param listener
     */
    private void setUsbMonitor(USBMonitor.OnDeviceConnectListener listener){
        Log.v(TAG,"---- setUsbMonitor();listener: " + listener);
        mUSBMonitor = new USBMonitor(mContext, listener);
        final List<DeviceFilter> filters = DeviceFilter.getDeviceFilters(mContext, R.xml.device_filter);
        mUSBMonitor.setDeviceFilter(filters);
        mUSBMonitor.register();
    }

    public void registerUsbMonitor(USBMonitor.OnDeviceConnectListener listener,boolean register){
        Log.v(TAG,"----registerUsbMonitor(); listener:"+listener + ";register:"+register + ";mUSBMonitor:"+mUSBMonitor);
        if(mUSBMonitor != null){
            mUsbDeviceConnectListener = listener;
            if(register) {
                mUSBMonitor.register();
            }else{
                mUSBMonitor.unregister();
            }
        }
    }

    /**
     * 连接usbCamera设备,并进行打开和开始预览
     * @param ctrlBlock
     * @param vendorId
     * @param productId
     */
    private void doConnectUsbCamera(final USBMonitor.UsbControlBlock ctrlBlock,int vendorId,int productId){
        Log.v(TAG,"---doConnectUsbCamera(); mUsbCameraServer:"+mUsbCameraServer);

        if(mUsbCameraServer == null) {
            mUsbCameraServer = UsbCameraServer.createServer(mContext, ctrlBlock, vendorId, productId);
            mUsbCameraServer.setUsbCameraFrameCallback(mFrameCallback);

            mUsbCameraServer.resize(MyConstants.DEFAULT_USB_CAM_WIDTH, MyConstants.DEFAULT_USB_CAM_HEIGHT);
            if (mUSBMonitor != null && mUSBMonitor.isRegistered()) {
                final List<UsbDevice> list = mUSBMonitor.getDeviceList();
                if (list.size() > 0 && mUsbCameraServer != null) {
                    mUsbCameraServer.connect();
                }
            }
        }
    }

    /**
     * 断开usbCamera连接
     */
    public void doDisconnectUsbCamera(){
        mUsbCamState = MyConstants.UsbCamState.IDLE;
        if (mUsbCameraServer != null) {
            mUsbCameraServer.release();
            mUsbCameraServer = null;
        }
    }

    /**
     * 获取usbcamera设备的挂载状态
     * @return
     */
    public MyConstants.UsbDeviceState getUsbDeviceState(){
        return mUsbDeviceState;
    }

    /**
     * 进行预览的显示
     * @param attach
     */
    private void attachUsbCamPreview(boolean attach){
        Log.v(TAG,"---- attachUsbCamPreview();attach:"+attach);
        if(mPreviewSurface != null && mPreviewSurface.isValid()){
            attachUsbCamPreview(attach,mPreviewSurface);
        }
    }

    /**
     * 进行预览的显示
     * @param attach
     * @param surface
     */
    public void attachUsbCamPreview(boolean attach,Surface surface){
        Log.v(TAG,"----attachUsbCamPreview();attach: "+attach + ";isCameraViewAttach:"+isCameraViewAttach + ";surface: "+surface);
        mPreviewSurface = surface;
        if (mUsbCameraServer != null && surface != null && isCameraViewAttach != attach) {
            if (attach) {
                mUsbCameraServer.addSurface(surface.hashCode(), surface, false, null);
            } else {
                mUsbCameraServer.removeSurface(surface.hashCode());
            }
        }
        isCameraViewAttach = attach;
    }

    private String generVideoFilePath(){
        String filePath = FileUtil.getDefaultVideoPath(mContext);

        long startTime = System.currentTimeMillis();
        Date date = new Date(startTime);
        SimpleDateFormat format = new SimpleDateFormat("-yyyy-MM-dd-HHmmss");
        String title = format.format(date);
        SimpleDateFormat data_format = new SimpleDateFormat(
                    "yyyyMMdd");
        String mDatefolder = data_format.format(date);

        String fileName = title + ".mp4";

        String parent = filePath + "/" + mDatefolder;
        File folder = new File(parent);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return parent + "/" + fileName;
    }

    private String generImageFilePath(){
        String filePath = FileUtil.getDefaultImagePath(mContext);

        long startTime = System.currentTimeMillis();
        Date date = new Date(startTime);
        SimpleDateFormat format = new SimpleDateFormat("-yyyy-MM-dd-HHmmss");
        String title = format.format(date);
        SimpleDateFormat data_format = new SimpleDateFormat(
                "yyyyMMdd");
        String mDatefolder = data_format.format(date);

        String fileName = title + ".jpg";

        String parent = filePath + "/" + mDatefolder;
        File folder = new File(parent);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return parent + "/" + fileName;
    }

    /**
     * 开始录像
     */
    public void startUsbCamRecording(){
        mUsbCamState = MyConstants.UsbCamState.RECORDING;
        final String filePath = generVideoFilePath();
        if(!isCameraViewAttach) {
            startPreview(mPreviewSurface);
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mUsbCameraServer != null) {
                        mUsbCameraServer.startRecording(filePath);
                    }
                }
            },500);
        }else {
            if (mUsbCameraServer != null) {
                mUsbCameraServer.startRecording(filePath);
            }
        }
    }

    /**
     * 停止录像
     */
    public void stopUsbCamRecording(){
        Log.v(TAG,"---- stopUsbCamRecording();");
        mUsbCamState = MyConstants.UsbCamState.WAIT_SURE_VIDEO;
        if (mUsbCameraServer != null) {
            mUsbCameraServer.stopRecording();
        }
    }

    /**
     * 开始预览
     * @param surface
     */
    public void startPreview(Surface surface){
        Log.v(TAG,"----- startPreview();mUsbCameraServer:"+mUsbCameraServer);
        mPreviewSurface = surface;
        if(mUsbCameraServer != null){
            attachUsbCamPreview(true);
            mUsbCameraServer.startPreview();
        }
    }

    /**
     * 停止预览
     */
    public void stopPreview(){
        Log.v(TAG,"---- stopPreview();");
        if(mUsbCameraServer != null){
            attachUsbCamPreview(false);
            mUsbCameraServer.stopPreview();
        }
    }

    /**
     * 循环录像
     */
    public void doCycleRecord(){
        Log.v(TAG,"---- doCycleRecord();mUsbCamState: " + mUsbCamState);
        if(mUsbCamState == MyConstants.UsbCamState.RECORDING) {
            stopUsbCamRecording();
        }

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                startUsbCamRecording();
            }
        },700);
    }

    /**
     * 拍照
     */
    public void capture(){
        final String filePath = generImageFilePath();

        if(!isCameraViewAttach) {
            startPreview(mPreviewSurface);
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if(mUsbCameraServer != null){
                        mUsbCameraServer.captureStill(filePath);
                    }
                }
            },500);
        }else {
            if (mUsbCameraServer != null) {
                mUsbCameraServer.captureStill(filePath);
            }
        }
    }

    /**
     * 设置预览帧回调
     */
    public void setUsbCameraFrameCallback(IUsbFrameCallback usbFrameCallback){
        mIUsbCameraFrameCallback = usbFrameCallback;
        if(mUsbCameraServer != null){
            if(mIUsbCameraFrameCallback != null) {
                mUsbCameraServer.setUsbCameraFrameCallback(mFrameCallback);
            }else{
                mUsbCameraServer.setUsbCameraFrameCallback(null);
            }
        }
    }

    private IFrameCallback mFrameCallback = new IFrameCallback() {
        @Override
        public void onFrame(ByteBuffer byteBuffer) {
            if(mIUsbCameraFrameCallback != null && byteBuffer != null){
                byte[] frameByte = new byte[byteBuffer.remaining()];
                byteBuffer.get(frameByte);
                mIUsbCameraFrameCallback.onStreamUpdate(frameByte,MyConstants.FORMAT_YUV,DEFAULT_WIDTH,DEFAULT_HEIGHT);
            }
        }
    };

    private UsbCameraServer.Callback mUsbCameraCallback = new UsbCameraServer.Callback() {
        @Override
        public void onCameraOpen() {
            Log.v(TAG,"---- onCameraOpen();mPreviewSurface:"+mPreviewSurface);
            attachUsbCamPreview(true,mPreviewSurface);
        }

        @Override
        public void onCameraClose() {
            attachUsbCamPreview(false,mPreviewSurface);
        }

        @Override
        public void onRecordFinish() {

        }
    };

    private final USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {
            Log.d(TAG, "---- xixi,device onAttach();"+mUSBMonitor);
            mUsbDeviceState = MyConstants.UsbDeviceState.ATTACH;

            if(mUSBMonitor != null) {
                mUSBMonitor.requestPermission(device);
                Log.v(TAG,"----- after requestPermission!!!");
            }

            if(mUsbDeviceConnectListener != null) {
                mUsbDeviceConnectListener.onAttach(device);
            }
        }

        @Override
        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
            Log.d(TAG, "device onConnect:mUsbDeviceConnectListener:"+mUsbDeviceConnectListener);
            mUsbDeviceState = MyConstants.UsbDeviceState.CONNECT;
            mUsbControlBlock = ctrlBlock;
            mConnectUsbDevice = device;

            doConnectUsbCamera(ctrlBlock,device.getVendorId(), device.getProductId());
            mUsbCameraServer.resize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
            mUsbCameraServer.registerRecordFinishCallback(mUsbCameraCallback);

            if(mUsbDeviceConnectListener != null) {
                mUsbDeviceConnectListener.onConnect(mConnectUsbDevice,ctrlBlock,createNew);
            }
        }

        @Override
        public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
            Log.d(TAG, "device onDisconnect");
            mUsbDeviceState = MyConstants.UsbDeviceState.DISCONNECT;
            doDisconnectUsbCamera();

            if(mUsbDeviceConnectListener != null) {
                mUsbDeviceConnectListener.onDisconnect(device,ctrlBlock);
            }
        }

        @Override
        public void onDettach(final UsbDevice device) {
            Log.d(TAG, "device onDetach");
            mUsbDeviceState = MyConstants.UsbDeviceState.DETTACH;
            if(mUsbDeviceConnectListener != null) {
                mUsbDeviceConnectListener.onDettach(device);
            }
        }

        @Override
        public void onCancel(final UsbDevice device) {
            if(mUsbDeviceConnectListener != null) {
                mUsbDeviceConnectListener.onCancel(device);
            }
        }
    };

    public void destory(){
        if(mUSBMonitor != null){
            mUSBMonitor.destroy();
            mUSBMonitor = null;
        }
    }
}
