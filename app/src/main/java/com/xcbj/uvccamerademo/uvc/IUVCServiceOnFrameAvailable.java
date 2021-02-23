/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: D:\\AndroidStudioWorkSpace\\po_dsj_3035_vt980\\po_dsj\\v2\\poc_android\\src\\com\\serenegiant\\service\\IUVCServiceOnFrameAvailable.aidl
 */
package com.xcbj.uvccamerademo.uvc;
public interface IUVCServiceOnFrameAvailable extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements com.xcbj.uvccamerademo.uvc.IUVCServiceOnFrameAvailable
{
private static final String DESCRIPTOR = "com.xcbj.uvccamerademo.uvc.IUVCServiceOnFrameAvailable";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an com.xcbj.uvccamerademo.uvc.IUVCServiceOnFrameAvailable interface,
 * generating a proxy if needed.
 */
public static com.xcbj.uvccamerademo.uvc.IUVCServiceOnFrameAvailable asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof com.xcbj.uvccamerademo.uvc.IUVCServiceOnFrameAvailable))) {
return ((com.xcbj.uvccamerademo.uvc.IUVCServiceOnFrameAvailable)iin);
}
return new com.xcbj.uvccamerademo.uvc.IUVCServiceOnFrameAvailable.Stub.Proxy(obj);
}
@Override public android.os.IBinder asBinder()
{
return this;
}
@Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
{
String descriptor = DESCRIPTOR;
switch (code)
{
case INTERFACE_TRANSACTION:
{
reply.writeString(descriptor);
return true;
}
case TRANSACTION_onFrameAvailable:
{
data.enforceInterface(descriptor);
this.onFrameAvailable();
return true;
}
default:
{
return super.onTransact(code, data, reply, flags);
}
}
}
private static class Proxy implements com.xcbj.uvccamerademo.uvc.IUVCServiceOnFrameAvailable
{
private android.os.IBinder mRemote;
Proxy(android.os.IBinder remote)
{
mRemote = remote;
}
@Override public android.os.IBinder asBinder()
{
return mRemote;
}
public String getInterfaceDescriptor()
{
return DESCRIPTOR;
}
@Override public void onFrameAvailable() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_onFrameAvailable, _data, null, android.os.IBinder.FLAG_ONEWAY);
}
finally {
_data.recycle();
}
}
}
static final int TRANSACTION_onFrameAvailable = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
}
public void onFrameAvailable() throws android.os.RemoteException;
}
