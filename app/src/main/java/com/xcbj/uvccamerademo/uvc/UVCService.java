

package com.xcbj.uvccamerademo.uvc;

import android.app.NotificationManager;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;
import android.view.Surface;

import com.serenegiant.common.BaseService;
import com.serenegiant.usb.USBMonitor;

public class UVCService extends BaseService {
    private static String TAG = UVCService.class.getSimpleName();
    private static final boolean DEBUG = true;

    private static final int NOTIFICATION = 2017053021;

    private USBMonitor mUSBMonitor;
    private NotificationManager mNotificationManager;

    public UVCService() {
        if (DEBUG) Log.d(TAG, "Constructor:");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.d(TAG, "onCreate:");
        if (mUSBMonitor == null) {
            mUSBMonitor = new USBMonitor(getApplicationContext(), mOnDeviceConnectListener);
            mUSBMonitor.register();
        }
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        //showNotification("My-UVC");
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy:");
        if (mUSBMonitor != null) {
            mUSBMonitor.unregister();
            mUSBMonitor = null;
        }

        stopForeground(true/*removeNotification*/);
        if (mNotificationManager != null) {
            mNotificationManager.cancel(NOTIFICATION);
            mNotificationManager = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(final Intent intent) {
        if (DEBUG) Log.d(TAG, "onBind:" + intent);
        final String action = intent != null ? intent.getAction() : null;
        if (IUVCService.class.getName().equals(action)) {
            Log.i(TAG, "return mBasicBinder");
            return mBasicBinder;
        }
        if (IUVCSlaveService.class.getName().equals(action)) {
            Log.i(TAG, "return mSlaveBinder");
            return mSlaveBinder;
        }
        return null;
    }

    @Override
    public void onRebind(final Intent intent) {
        if (DEBUG) Log.d(TAG, "onRebind:" + intent);
    }

    @Override
    public boolean onUnbind(final Intent intent) {
        if (DEBUG) Log.d(TAG, "onUnbind:" + intent);
        if (checkReleaseService()) {
        }
        removeService(null);
        checkReleaseService();
        if (DEBUG) Log.d(TAG, "onUnbind:exit all service");
        stopSelf();
        if (DEBUG) Log.d(TAG, "onUnbind:finished");
        return true;
    }

//********************************************************************************


    private final USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {
            if (DEBUG) Log.d(TAG, "OnDeviceConnectListener#onAttach:");
        }

        @Override
        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
            if (DEBUG) Log.d(TAG, "OnDeviceConnectListener#onConnect: ctrlBlock=" + ctrlBlock);

            queueEvent(new Runnable() {
                @Override
                public void run() {
                    final int key = device.hashCode();
                    UsbCameraServer service;
                    synchronized (sServiceSync) {
                        service = sUsbCameraServers.get(key);
                        if (service == null) {
                            service = UsbCameraServer.createServer(UVCService.this, ctrlBlock, device.getVendorId(), device.getProductId());
                            sUsbCameraServers.append(key, service);
                        } else {
                            Log.w(TAG, "service already exist before connection");
                        }
                        sServiceSync.notifyAll();
                    }
                }
            }, 0);
        }

        @Override
        public void onDisconnect(final UsbDevice usbDevice, USBMonitor.UsbControlBlock usbControlBlock) {
            if (DEBUG) Log.d(TAG, "OnDeviceConnectListener#onDisconnect:");
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    removeService(usbDevice);
                }
            }, 0);
        }

        @Override
        public void onDettach(final UsbDevice device) {
            if (DEBUG) Log.d(TAG, "OnDeviceConnectListener#onDettach:");
        }

        @Override
        public void onCancel(final UsbDevice device) {
            if (DEBUG) Log.d(TAG, "OnDeviceConnectListener#onCancel:");
            synchronized (sServiceSync) {
                sServiceSync.notifyAll();
            }
        }
    };

    private void removeService(final UsbDevice device) {
        if (device == null) {
            int size = sUsbCameraServers.size();
            for (int i = 0, nsize = sUsbCameraServers.size(); i < nsize; i++) {
                final UsbCameraServer service = sUsbCameraServers.valueAt(i);
                if (service != null) {
                    service.release();
                }
            }
            sUsbCameraServers.clear();
            return;
        }
        final int key = device.hashCode();
        synchronized (sServiceSync) {
            final UsbCameraServer service = sUsbCameraServers.get(key);
            if (service != null)
                service.release();
            sUsbCameraServers.remove(key);
            sServiceSync.notifyAll();
        }
        if (checkReleaseService()) {
            stopSelf();
        }
    }

    //********************************************************************************
    private static final Object sServiceSync = new Object();
    private static final SparseArray<UsbCameraServer> sUsbCameraServers = new SparseArray<UsbCameraServer>();

    /**
     * get CameraService that has specific ID<br>
     * if zero is provided as ID, just return top of UsbCameraServer instance(non-blocking method) if exists or null.<br>
     * if non-zero ID is provided, return specific CameraService if exist. block if not exists.<br>
     * return null if not exist matched specific ID<br>
     *
     * @param serviceId
     * @return
     */
    private static UsbCameraServer getUsbCameraServer(final int serviceId) {
        synchronized (sServiceSync) {
            UsbCameraServer server = null;
            if ((serviceId == 0) && (sUsbCameraServers.size() > 0)) {
                server = sUsbCameraServers.valueAt(0);
            } else {
                server = sUsbCameraServers.get(serviceId);
                if (server == null)
                    try {
                        Log.i(TAG, "waiting for service is ready");
                        sServiceSync.wait();
                    } catch (final InterruptedException e) {
                    }
                server = sUsbCameraServers.get(serviceId);
            }
            return server;
        }
    }

    /**
     * @return true if there are no camera connection
     */
    private static boolean checkReleaseService() {
        UsbCameraServer server = null;
        synchronized (sServiceSync) {
            final int n = sUsbCameraServers.size();
            if (DEBUG) Log.d(TAG, "checkReleaseService:number of service=" + n);
            for (int i = 0; i < n; i++) {
                server = sUsbCameraServers.valueAt(i);
                Log.i(TAG, "checkReleaseService:server=" + server + ",isConnected=" + (server != null && server.isConnected()));
                if (server != null && !server.isConnected()) {
                    sUsbCameraServers.removeAt(i);
                    server.disconnect();
                    server.release();
                    Log.i(TAG, "checkReleaseService:server remove " + server);
                }
            }
            return sUsbCameraServers.size() == 0;
        }
    }

    //********************************************************************************
    private final IUVCService.Stub mBasicBinder = new IUVCService.Stub() {
        private IUVCServiceCallback mCallback;

        @Override
        public int select(final UsbDevice device, final IUVCServiceCallback callback) throws RemoteException {
            if (DEBUG)
                Log.d(TAG, "mBasicBinder#select:device=" + (device != null ? device.getDeviceName() : null));
            mCallback = callback;
            final int serviceId = device.hashCode();
            UsbCameraServer server = null;
            synchronized (sServiceSync) {
                server = sUsbCameraServers.get(serviceId);
                if (server == null) {
                    Log.i(TAG, "request permission");
                    mUSBMonitor.requestPermission(device);
                    Log.i(TAG, "wait for getting permission");
                    try {
                        sServiceSync.wait();
                    } catch (final Exception e) {
                        Log.e(TAG, "connect:", e);
                    }
                    Log.i(TAG, "check service again");
                    server = sUsbCameraServers.get(serviceId);
                    if (server == null) {
                        throw new RuntimeException("failed to open USB device(has no permission)");
                    }
                }
            }
            if (server != null) {
                Log.i(TAG, "success to get service:serviceId=" + serviceId);
                server.registerCallback(callback);
            }
            return serviceId;
        }

        @Override
        public void release(final int serviceId) throws RemoteException {
            if (DEBUG) Log.d(TAG, "mBasicBinder#release:");
            synchronized (sServiceSync) {
                final UsbCameraServer server = sUsbCameraServers.get(serviceId);
                if (server != null) {
                    if (server.unregisterCallback(mCallback)) {
                        if (!server.isConnected()) {
                            sUsbCameraServers.remove(serviceId);
                            if (server != null) {
                                server.release();
                            }
                            final UsbCameraServer srv = sUsbCameraServers.get(serviceId);
                            Log.w(TAG, "srv=" + srv);
                        }
                    }
                }
            }
            mCallback = null;
        }

        @Override
        public boolean isSelected(final int serviceId) throws RemoteException {
            return getUsbCameraServer(serviceId) != null;
        }

        @Override
        public void releaseAll() throws RemoteException {
            if (DEBUG) Log.d(TAG, "mBasicBinder#releaseAll:");
            UsbCameraServer server;
            synchronized (sServiceSync) {
                final int n = sUsbCameraServers.size();
                for (int i = 0; i < n; i++) {
                    server = sUsbCameraServers.valueAt(i);
                    sUsbCameraServers.removeAt(i);
                    if (server != null) {
                        server.release();
                    }
                }
            }
        }

        @Override
        public void resize(final int serviceId, final int width, final int height) {
            if (DEBUG) Log.d(TAG, "mBasicBinder#resize:");
            final UsbCameraServer server = getUsbCameraServer(serviceId);
            if (server == null) {
                throw new IllegalArgumentException("invalid serviceId");
            }
            server.resize(width, height);
        }

        @Override
        public void connect(final int serviceId) throws RemoteException {
            if (DEBUG) Log.d(TAG, "mBasicBinder#connect:");
            final UsbCameraServer server = getUsbCameraServer(serviceId);
            if (server == null) {
                throw new IllegalArgumentException("invalid serviceId");
            }
            server.connect();
        }

        @Override
        public void disconnect(final int serviceId) throws RemoteException {
            if (DEBUG) Log.d(TAG, "mBasicBinder#disconnect:");
            final UsbCameraServer server = getUsbCameraServer(serviceId);
            if (server == null) {
                throw new IllegalArgumentException("invalid serviceId");
            }
            server.disconnect();
        }

        @Override
        public boolean isConnected(final int serviceId) throws RemoteException {
            final UsbCameraServer server = getUsbCameraServer(serviceId);
            return (server != null) && server.isConnected();
        }

        @Override
        public void addSurface(final int serviceId, final int id_surface, final Surface surface, final boolean isRecordable) throws RemoteException {
            if (DEBUG)
                Log.d(TAG, "mBasicBinder#addSurface:id=" + id_surface + ",surface=" + surface);
            final UsbCameraServer server = getUsbCameraServer(serviceId);
            if (server != null)
                server.addSurface(id_surface, surface, isRecordable, null);
        }

        @Override
        public void removeSurface(final int serviceId, final int id_surface) throws RemoteException {
            if (DEBUG) Log.d(TAG, "mBasicBinder#removeSurface:id=" + id_surface);
            final UsbCameraServer server = getUsbCameraServer(serviceId);
            if (server != null)
                server.removeSurface(id_surface);
        }

        @Override
        public boolean isRecording(final int serviceId) throws RemoteException {
            final UsbCameraServer server = getUsbCameraServer(serviceId);
            return server != null && server.isRecording();
        }

        @Override
        public void startRecording(final int serviceId, String path) throws RemoteException {
            if (DEBUG) Log.d(TAG, "mBasicBinder#startRecording:");
            final UsbCameraServer server = getUsbCameraServer(serviceId);
            if ((server != null) && !server.isRecording()) {
                server.startRecording(path);
            }
        }

        @Override
        public void stopRecording(final int serviceId) throws RemoteException {
            if (DEBUG) Log.d(TAG, "mBasicBinder#stopRecording:");
            final UsbCameraServer server = getUsbCameraServer(serviceId);
            if ((server != null) && server.isRecording()) {
                server.stopRecording();
            }
        }

        @Override
        public void captureStillImage(final int serviceId, final String path) throws RemoteException {
            if (DEBUG) Log.d(TAG, "mBasicBinder#captureStillImage:" + path);
            final UsbCameraServer server = getUsbCameraServer(serviceId);
            if (server != null) {
                server.captureStill(path);
            }
        }

    };

    //********************************************************************************
    private final IUVCSlaveService.Stub mSlaveBinder = new IUVCSlaveService.Stub() {
        @Override
        public boolean isSelected(final int serviceID) throws RemoteException {
            return getUsbCameraServer(serviceID) != null;
        }

        @Override
        public boolean isConnected(final int serviceID) throws RemoteException {
            final UsbCameraServer server = getUsbCameraServer(serviceID);
            return server != null && server.isConnected();
        }

        @Override
        public void addSurface(final int serviceID, final int id_surface, final Surface surface, final boolean isRecordable, final IUVCServiceOnFrameAvailable callback) throws RemoteException {
            if (DEBUG)
                Log.d(TAG, "mSlaveBinder#addSurface:id=" + id_surface + ",surface=" + surface);
            final UsbCameraServer server = getUsbCameraServer(serviceID);
            if (server != null) {
                server.addSurface(id_surface, surface, isRecordable, callback);
            } else {
                Log.e(TAG, "failed to get UsbCameraServer:serviceID=" + serviceID);
            }
        }

        @Override
        public void removeSurface(final int serviceID, final int id_surface) throws RemoteException {
            if (DEBUG) Log.d(TAG, "mSlaveBinder#removeSurface:id=" + id_surface);
            final UsbCameraServer server = getUsbCameraServer(serviceID);
            if (server != null) {
                server.removeSurface(id_surface);
            } else {
                Log.e(TAG, "failed to get UsbCameraServer:serviceID=" + serviceID);
            }
        }
    };

}
