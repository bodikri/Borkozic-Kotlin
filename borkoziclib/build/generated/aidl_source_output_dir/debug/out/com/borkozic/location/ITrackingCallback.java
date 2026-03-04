/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.borkozic.location;
public interface ITrackingCallback extends android.os.IInterface
{
  /** Default implementation for ITrackingCallback. */
  public static class Default implements com.borkozic.location.ITrackingCallback
  {
    @Override public void onNewPoint(boolean continous, double lat, double lon, double elev, double speed, double track, double accuracy, long time) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.borkozic.location.ITrackingCallback
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.borkozic.location.ITrackingCallback interface,
     * generating a proxy if needed.
     */
    public static com.borkozic.location.ITrackingCallback asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.borkozic.location.ITrackingCallback))) {
        return ((com.borkozic.location.ITrackingCallback)iin);
      }
      return new com.borkozic.location.ITrackingCallback.Stub.Proxy(obj);
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
        case TRANSACTION_onNewPoint:
        {
          boolean _arg0;
          _arg0 = (0!=data.readInt());
          double _arg1;
          _arg1 = data.readDouble();
          double _arg2;
          _arg2 = data.readDouble();
          double _arg3;
          _arg3 = data.readDouble();
          double _arg4;
          _arg4 = data.readDouble();
          double _arg5;
          _arg5 = data.readDouble();
          double _arg6;
          _arg6 = data.readDouble();
          long _arg7;
          _arg7 = data.readLong();
          this.onNewPoint(_arg0, _arg1, _arg2, _arg3, _arg4, _arg5, _arg6, _arg7);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.borkozic.location.ITrackingCallback
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
      @Override public void onNewPoint(boolean continous, double lat, double lon, double elev, double speed, double track, double accuracy, long time) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(((continous)?(1):(0)));
          _data.writeDouble(lat);
          _data.writeDouble(lon);
          _data.writeDouble(elev);
          _data.writeDouble(speed);
          _data.writeDouble(track);
          _data.writeDouble(accuracy);
          _data.writeLong(time);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onNewPoint, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_onNewPoint = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
  }
  public static final java.lang.String DESCRIPTOR = "com.borkozic.location.ITrackingCallback";
  public void onNewPoint(boolean continous, double lat, double lon, double elev, double speed, double track, double accuracy, long time) throws android.os.RemoteException;
}
