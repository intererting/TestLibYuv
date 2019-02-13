package com.example.android.camera2basic;

public class YuvUtil {

    static {
        System.loadLibrary("native-lib");
    }

    /**
     * YUV数据的基本的处理
     *
     * @param src        原始数据
     * @param width      原始的宽
     * @param height     原始的高
     * @param dst        输出数据
     * @param dst_width  输出的宽
     * @param dst_height 输出的高
     * @param mode       压缩模式。这里为0，1，2，3 速度由快到慢，质量由低到高，一般用0就好了，因为0的速度最快
     * @param degree     旋转的角度，90，180和270三种
     * @param isMirror   是否镜像，一般只有270的时候才需要镜像
     **/
    public static native void compressYUV(byte[] src, int width, int height, byte[] dst, int dst_width, int dst_height, int mode, int degree, boolean isMirror);


    /**
     * const uint8 *src_y, int src_stride_y,
     * const uint8 *src_uv, int src_stride_uv,
     * uint8 *dst_y, int dst_stride_y,
     * uint8 *dst_u, int dst_stride_u,
     * uint8 *dst_v, int dst_stride_v,
     * int src_width, int src_height, enum RotationMode mode
     */

    public static native void rotateYUV(byte[] src, int strideY, byte[] srcUV, int strideUV, byte[] dstY, int dstStrideY
            , byte[] dstU, int dstStrideU
            , byte[] dstV, int dstStrideV
            , int width, int height, int rotate);
}
