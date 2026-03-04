/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.borkozic.location;
public interface ILocationCallback extends android.os.IInterface
{
  /** Default implementation for ILocationCallback. */
  public static class Default implements com.borkozic.location.ILocationCallback
  {
    @Override public void onLocationChanged(android.location.Location loc, boolean continous, boolean geoid, float smoothspeed, float avgspeed) throws android.os.RemoteException
    {
    }
    @Override public void onProviderChanged(java.lang.String provider) throws android.os.RemoteException
    {
    }
    @Override public void onProviderDisabled(java.lang.String provider) throws android.os.RemoteException
    {
    }
    @Override public void onProviderEnabled(java.lang.String provider) throws android.os.RemoteException
    {
    }
    @Override public void onGpsStatusChanged(java.lang.String provider, int status, int fsats, int tsats) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.borkozic.location.ILocationCallback
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.borkozic.location.ILocationCallback interface,
     * generating a proxy if needed.
     */
    public static com.borkozic.location.ILocationCallback asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.borkozic.location.ILocationCallback))) {
        return ((com.borkozic.location.ILocationCallback)iin);
      }
      return new com.borkozic.location.ILocationCallback.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      switch (code)
      {
        case INTERFACE_TRANSACTION:
        {
          reply.writeString(descriptor);
          return true;
        }
      }
      switch (code)
      {
        case TRANSACTION_onLocationChanged:
        {
          android.location.Location _arg0;
          _arg0 = _Parcel.readTypedObject(data, android.location.Location.CREATOR);
          boolean _arg1;
          _arg1 = (0!=data.readInt());
          boolean _arg2;
          _arg2 = (0!=data.readInt());
          float _arg3;
          _arg3 = data.readFloat();
          float _arg4;
          _arg4 = data.readFloat();
          this.onLocationChanged(_arg0, _arg1, _arg2, _arg3, _arg4);
          break;
        }
        case TRANSACTION_onProviderChanged:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          this.onProviderChanged(_arg0);
          break;
        }
        case TRANSACTION_onProviderDisabled:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          this.onProviderDisabled(_arg0);
          break;
        }
        case TRANSACTION_onProviderEnabled:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          this.onProviderEnabled(_arg0);
          break;
        }
        case TRANSACTION_onGpsStatusChanged:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          int _arg1;
          _arg1 = data.readInt();
          int _arg2;
          _arg2 = data.readInt();
          int _arg3;
          _arg3 = data.readInt();
          this.onGpsStatusChanged(_arg0, _arg1, _arg2, _arg3);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.borkozic.location.ILocationCallback
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
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      @Override public void onLocationChanged(android.location.Location loc, boolean continous, boolean geoid, float smoothspeed, float avgspeed) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _Parcel.writeTypedObject(_data, loc, 0);
          _data.writeInt(((continous)?(1):(0)));
          _data.writeInt(((geoid)?(1):(0)));
          _data.writeFloat(smoothspeed);
          _data.writeFloat(avgspeed);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onLocationChanged, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      @Override public void onProviderChanged(java.lang.String provider) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(provider);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onProviderChanged, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      @Override public void onProviderDisabled(java.lang.String provider) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(provider);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onProviderDisabled, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      @Override public void onProviderEnabled(java.lang.String provider) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(provider);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onProviderEnabled, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      @Override public void onGpsStatusChanged(java.lang.String provider, int status, int fsats, int tsats) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(provider);
          _data.writeInt(status);
          _data.writeInt(fsats);
          _data.writeInt(tsats);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onGpsStatusChanged, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_onLocationChanged = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_onProviderChanged = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_onProviderDisabled = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_onProviderEnabled = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
    static final int TRANSACTION_onGpsStatusChanged = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
  }
  public static final java.lang.String DESCRIPTOR = "com.borkozic.location.ILocationCallback";
  public void onLocationChanged(android.location.Location loc, boolean continous, boolean geoid, float smoothspeed, float avgspeed) throws android.os.RemoteException;
  public void onProviderChanged(java.lang.String provider) throws android.os.RemoteException;
  public void onProviderDisabled(java.lang.String provider) throws android.os.RemoteException;
  public void onProviderEnabled(java.lang.String provider) throws android.os.RemoteException;
  public void onGpsStatusChanged(java.lang.String provider, int status, int fsats, int tsats) throws android.os.RemoteException;
  /** @hide */
  static class _Parcel {
    static private <T> T readTypedObject(
        android.os.Parcel parcel,
        android.os.Parcelable.Creator<T> c) {
      if (parcel.readInt() != 0) {
          return c.createFromParcel(parcel);
      } else {
          return null;
      }
    }
    static private <T extends android.os.Parcelable> void writeTypedObject(
        android.os.Parcel parcel, T value, int parcelableFlags) {
      if (value != null) {
        parcel.writeInt(1);
        value.writeToParcel(parcel, parcelableFlags);
      } else {
        parcel.writeInt(0);
      }
    }
  }
}
