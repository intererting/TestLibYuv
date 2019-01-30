package com.example.android.camera2basic.rtmp;


public class RtmpClient {

    static {
        System.loadLibrary("native-lib");
    }

    public static native long open(String url, boolean isPublishMode);

    public static native long flvInit(String path);

    //    public static native int read(long rtmpPointer, byte[] data, int offset, int size);
//
    public static native int write(long rtmpPointer, long flvPointer, String path, boolean keyFrame, byte[] data, int size, long time, int type);
//
//    public static native int close(long rtmpPointer);
//
//    public static native String getIpAddr(long rtmpPointer);

}
