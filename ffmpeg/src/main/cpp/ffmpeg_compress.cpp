#include <stdio.h>
#include <jni.h>

#define __STDC_CONSTANT_MACROS

#ifdef _WIN32
//Windows
extern "C"
{
#include "libavutil/opt.h"
#include "libavcodec/avcodec.h"
#include "libavutil/imgutils.h"
};
#else
//Linux...
#ifdef __cplusplus
extern "C"
{
#endif
#include <libavutil/opt.h>
#include <libavcodec/avcodec.h>
#include <libavutil/imgutils.h>
#ifdef __cplusplus
};
#endif
#endif

//test different codec
#define TEST_H264  1
#define TEST_HEVC  0

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_org_voiddog_ffmpeg_FFmpegNativeBridge_compressYUVWithFFmpeg(JNIEnv *env, jclass type, jint width, jint heigh,
                                                                 jbyteArray y_, jbyteArray u_, jbyteArray v_,
                                                                 jlong timestamp) {
    jbyte *y = env->GetByteArrayElements(y_, NULL);
    jbyte *u = env->GetByteArrayElements(u_, NULL);
    jbyte *v = env->GetByteArrayElements(v_, NULL);

    AVCodec *pCodec;
    AVCodecContext *pCodecCtx = NULL;
    int ret, got_output;
    AVFrame *pFrame;
    AVPacket pkt;

#if TEST_HEVC
    AVCodecID codec_id=AV_CODEC_ID_HEVC;
    char filename_out[]="ds.hevc";
#else
    AVCodecID codec_id = AV_CODEC_ID_H264;
#endif


    int in_w = 1280, in_h = 720;

    avcodec_register_all();

    pCodec = avcodec_find_encoder(codec_id);
    if (!pCodec) {
        printf("Codec not found\n");
        return NULL;
    }
    pCodecCtx = avcodec_alloc_context3(pCodec);
    if (!pCodecCtx) {
        printf("Could not allocate video codec context\n");
        return NULL;
    }
    pCodecCtx->bit_rate = 3500 * 1000;
    pCodecCtx->width = in_w;
    pCodecCtx->height = in_h;
//    pCodecCtx->time_base.num = 1;
//    pCodecCtx->time_base.den = 30;
    pCodecCtx->gop_size = 30;
    pCodecCtx->max_b_frames = 1;
    pCodecCtx->pix_fmt = AV_PIX_FMT_YUV420P;

    if (codec_id == AV_CODEC_ID_H264)
        av_opt_set(pCodecCtx->priv_data, "preset", "slow", 0);

    if (avcodec_open2(pCodecCtx, pCodec, NULL) < 0) {
        printf("Could not open codec\n");
        return NULL;
    }

    pFrame = av_frame_alloc();
    if (!pFrame) {
        printf("Could not allocate video frame\n");
        return NULL;
    }
    pFrame->format = pCodecCtx->pix_fmt;
    pFrame->width = pCodecCtx->width;
    pFrame->height = pCodecCtx->height;

    ret = av_image_alloc(pFrame->data, pFrame->linesize, pCodecCtx->width, pCodecCtx->height,
                         pCodecCtx->pix_fmt, 16);
    if (ret < 0) {
        printf("Could not allocate raw picture buffer\n");
        return NULL;
    }
    //Encode
    av_init_packet(&pkt);
    pkt.data = NULL;    // packet data will be allocated by the encoder
    pkt.size = 0;
    //Read raw YUV data
    pFrame->data[0] = reinterpret_cast<uint8_t *>(y);
    pFrame->data[1] = reinterpret_cast<uint8_t *>(u);
    pFrame->data[2] = reinterpret_cast<uint8_t *>(v);
    pFrame->pts = timestamp;
    /* encode the image */
    ret = avcodec_encode_video2(pCodecCtx, &pkt, pFrame, &got_output);
    if (ret < 0) {
        printf("Error encoding frame\n");
        return NULL;
    }
    jbyteArray outArray = NULL;
    if (got_output) {
        outArray = env->NewByteArray(pkt.size * 8);
        env->SetByteArrayRegion(outArray, 0, pkt.size * 8, reinterpret_cast<const jbyte *>(pkt.data));
        av_free_packet(&pkt);
    }
    //Flush Encoder
//    ret = avcodec_encode_video2(pCodecCtx, &pkt, NULL, &got_output);
//    if (ret < 0) {
//        printf("Error encoding frame\n");
//        return NULL;
//    }
//    if (got_output) {
//        printf("Flush Encoder: Succeed to encode 1 frame!\tsize:%5d\n", pkt.size);
//        jbyteArray outArray = env->NewByteArray(pkt.size);
//        env->SetByteArrayRegion(outArray, 0, pkt.size, reinterpret_cast<const jbyte *>(pkt.data));
//        av_free_packet(&pkt);
//    }

    avcodec_close(pCodecCtx);
    av_free(pCodecCtx);
    av_freep(&pFrame->data[0]);
    av_freep(&pFrame->data[1]);
    av_freep(&pFrame->data[2]);
    av_frame_free(&pFrame);

    env->ReleaseByteArrayElements(y_, y, 0);
    env->ReleaseByteArrayElements(u_, u, 0);
    env->ReleaseByteArrayElements(v_, v, 0);
    if (outArray) {
        env->ReleaseByteArrayElements(outArray, NULL, 0);
    }
    return outArray;
}