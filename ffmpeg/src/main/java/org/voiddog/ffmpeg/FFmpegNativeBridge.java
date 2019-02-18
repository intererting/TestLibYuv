package org.voiddog.ffmpeg;

public class FFmpegNativeBridge {

    static {
        System.loadLibrary("ffmpeg-lib");
    }

    public static int runCommand(String[] command) {
        int ret;
        synchronized (FFmpegNativeBridge.class) {
            // 不允许多线程访问
            ret = innerRunCommand(command);
        }
        return ret;
    }

    public static native byte[] compressYUVWithFFmpeg(int width, int heigh, byte[] y, byte[] u, byte[] v, long timestamp);

    /**
     * 设置是否处于调试状态
     */
    public static native void setDebug(boolean debug);

    /**
     * 执行指令
     */
    private static native int innerRunCommand(String[] command);
}
