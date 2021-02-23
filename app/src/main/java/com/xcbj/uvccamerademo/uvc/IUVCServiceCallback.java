/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: D:\\AndroidStudioWorkSpace\\po_dsj_3035_vt980\\po_dsj\\v2\\poc_android\\src\\com\\serenegiant\\service\\IUVCServiceCallback.aidl
 */
package com.xcbj.uvccamerademo.uvc;
public interface IUVCServiceCallback extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements com.xcbj.uvccamerademo.uvc.IUVCServiceCallback
{
private static final String DESCRIPTOR = "com.xcbj.uvccamerademo.uvc.IUVCServiceCallback";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an com.xcbj.uvccamerademo.uvc.IUVCServiceCallback interface,
 * generating a proxy if needed.
 */
public static com.xcbj.uvccamerademo.uvc.IUVCServiceCallback asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof com.xcbj.uvccamerademo.uvc.IUVCServiceCallback))) {
return ((com.xcbj.uvccamerademo.uvc.IUVCServiceCallback)iin);
}
return new com.xcbj.uvccamerademo.uvc.IUVCServiceCallback.Stub.Proxy(obj);
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
case TRANSACTION_onConnected:
{
data.enforceInterface(descriptor);
this.onConnected();
return true;
}
case TRANSACTION_onDisConnected:
{
data.enforceInterface(descriptor);
this.onDisConnected();
return true;
}
case TRANSACTION_onRecordFinished:
{
data.enforceInterface(descriptor);
this.onRecordFinished();
return true;
}
default:
{
return super.onTransact(code, data, reply, flags);
}
}
}
private static class Proxy implements com.xcbj.uvccamerademo.uvc.IUVCServiceCallback
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
@Override public void onConnected() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_onConnected, _data, null, android.os.IBinder.FLAG_ONEWAY);
}
finally {
_data.recycle();
}
}
@Override public void onDisConnected() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_onDisConnected, _data, null, android.os.IBinder.FLAG_ONEWAY);
}
finally {
_data.recycle();
}
}
@Override public void onRecordFinished() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_onRecordFinished, _data, null, android.os.IBinder.FLAG_ONEWAY);
}
finally {
_data.recycle();
}
}
}
static final int TRANSACTION_onConnected = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_onDisConnected = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
static final int TRANSACTION_onRecordFinished = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
}
public void onConnected() throws android.os.RemoteException;
public void onDisConnected() throws android.os.RemoteException;
public void onRecordFinished() throws android.os.RemoteException;
}
