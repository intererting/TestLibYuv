#include <jni.h>
#include <string>
#include <malloc.h>
#include <cstring>
#include "include/librtmp/rtmp.h"
#include "include/log.h"
#include "include/FlvUtil.h"
#include <time.h>

extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_android_camera2basic_rtmp_RtmpClient_open(JNIEnv *env, jclass type, jstring url_,
                                                           jboolean isPublishMode) {
    const char *url = env->GetStringUTFChars(url_, 0);

    LOGD("RTMP_OPENING:%s", url);
    RTMP *rtmp = RTMP_Alloc();
    if (rtmp == NULL) {
        LOGD("RTMP_Alloc=NULL");
        return NULL;
    }

    RTMP_Init(rtmp);
    int ret = RTMP_SetupURL(rtmp, const_cast<char *>(url));

    if (!ret) {
        RTMP_Free(rtmp);
        rtmp = NULL;
        LOGD("RTMP_SetupURL=ret");
        return NULL;
    }
    if (isPublishMode) {
        RTMP_EnableWrite(rtmp);
    }

    ret = RTMP_Connect(rtmp, NULL);
    if (!ret) {
        RTMP_Free(rtmp);
        rtmp = NULL;
        LOGD("RTMP_Connect=ret");
        return NULL;
    }
    ret = RTMP_ConnectStream(rtmp, 0);

    if (!ret) {
        ret = RTMP_ConnectStream(rtmp, 0);
        RTMP_Close(rtmp);
        RTMP_Free(rtmp);
        rtmp = NULL;
        LOGD("RTMP_ConnectStream=ret");
        return NULL;
    }
    env->ReleaseStringUTFChars(url_, url);
    LOGD("RTMP_OPENED");
    return reinterpret_cast<jlong>(rtmp);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_android_camera2basic_rtmp_RtmpClient_write264(JNIEnv *env, jclass type_,
                                                               jlong rtmp,
                                                               jbyteArray data_, jint size,
                                                               jlong times, jint type) {
    jbyte *buffer = env->GetByteArrayElements(data_, NULL);
    RTMPPacket *packet = (RTMPPacket *) malloc(sizeof(RTMPPacket));
    RTMPPacket_Alloc(packet, size);
    RTMPPacket_Reset(packet);
    if (type == RTMP_PACKET_TYPE_INFO) { // metadata
        packet->m_nChannel = 0x03;
    } else if (type == RTMP_PACKET_TYPE_VIDEO) { // video
        packet->m_nChannel = 0x04;
    } else if (type == RTMP_PACKET_TYPE_AUDIO) { //audio
        packet->m_nChannel = 0x05;
    } else {
        packet->m_nChannel = -1;
    }

    packet->m_nInfoField2 = ((RTMP *) rtmp)->m_stream_id;

    memcpy(packet->m_body, buffer, static_cast<size_t>(size));
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_hasAbsTimestamp = FALSE;
    packet->m_nTimeStamp = static_cast<uint32_t>(times);
    packet->m_packetType = static_cast<uint8_t>(type);
    packet->m_nBodySize = static_cast<uint32_t>(size);
    int ret = RTMP_SendPacket((RTMP *) rtmp, packet, 0);
    RTMPPacket_Free(packet);
    free(packet);
    env->ReleaseByteArrayElements(data_, buffer, 0);
    if (!ret) {
        LOGD("end write error %d", sockerr);
        return sockerr;
    } else {
//        LOGD("end write success");
        return 0;
    }
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_android_camera2basic_rtmp_RtmpClient_write(JNIEnv *env, jclass type_, jlong rtmp,
                                                            jlong flv,
                                                            jboolean keyFrame,
                                                            jbyteArray data_, jint size,
                                                            jlong times, jint type) {
    jbyte *buffer = env->GetByteArrayElements(data_, NULL);
    flv_t *handle = reinterpret_cast<flv_t *>(flv);
    //合成FLV
    flv_write_video_packet(handle, keyFrame, reinterpret_cast<unsigned char *>(buffer), size,
                           static_cast<unsigned int>(times));

    RTMPPacket *packet = (RTMPPacket *) malloc(sizeof(RTMPPacket));
    RTMPPacket_Alloc(packet, handle->buff_index);
    RTMPPacket_Reset(packet);
    if (type == RTMP_PACKET_TYPE_INFO) { // metadata
        packet->m_nChannel = 0x03;
    } else if (type == RTMP_PACKET_TYPE_VIDEO) { // video
        packet->m_nChannel = 0x04;
    } else if (type == RTMP_PACKET_TYPE_AUDIO) { //audio
        packet->m_nChannel = 0x05;
    } else {
        packet->m_nChannel = -1;
    }

    packet->m_nInfoField2 = ((RTMP *) rtmp)->m_stream_id;
    memcpy(packet->m_body, handle->buff, static_cast<size_t>(handle->buff_index));
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_hasAbsTimestamp = FALSE;
    packet->m_nTimeStamp = static_cast<uint32_t>(times);
    packet->m_packetType = static_cast<uint8_t>(type);
    packet->m_nBodySize = static_cast<uint32_t>(handle->buff_index);
    int ret = RTMP_SendPacket((RTMP *) rtmp, packet, 0);
    RTMPPacket_Free(packet);
    free(packet);
    env->ReleaseByteArrayElements(data_, buffer, 0);
    flushBuff(handle);
    if (!ret) {
        LOGD("end write error %d", sockerr);
        return sockerr;
    } else {
        LOGD("end write success");
        return 0;
    }
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_android_camera2basic_rtmp_RtmpClient_flvInit(JNIEnv *env, jclass type,
                                                              jstring path_) {
    const char *path = env->GetStringUTFChars(path_, 0);

    flv_t *handle = flv_init(path, 30, 720, 1280);
    env->ReleaseStringUTFChars(path_, path);
    return reinterpret_cast<jlong>(handle);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_android_camera2basic_rtmp_RtmpClient_writeMetadata(JNIEnv *env, jclass type_,
                                                                    jlong rtmp,
                                                                    jbyteArray data_, jint size,
                                                                    jlong times,
                                                                    jint type) {
    jbyte *data = env->GetByteArrayElements(data_, NULL);

    RTMPPacket *packet = (RTMPPacket *) malloc(sizeof(RTMPPacket));
    RTMPPacket_Alloc(packet, size);
    RTMPPacket_Reset(packet);
    packet->m_nChannel = 0x03;
    packet->m_nInfoField2 = ((RTMP *) rtmp)->m_stream_id;
    memcpy(packet->m_body, data, static_cast<size_t>(size));
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_hasAbsTimestamp = FALSE;
    packet->m_nTimeStamp = static_cast<uint32_t>(times);
    packet->m_packetType = static_cast<uint8_t>(type);
    packet->m_nBodySize = static_cast<uint32_t>(size);
    int ret = RTMP_SendPacket((RTMP *) rtmp, packet, 0);
    RTMPPacket_Free(packet);
    free(packet);
    env->ReleaseByteArrayElements(data_, data, 0);
    if (!ret) {
        LOGD("end write error %d", sockerr);
        return sockerr;
    } else {
        LOGD("end write matedata success");
        return 0;
    }
}


extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_android_camera2basic_rtmp_RtmpClient_close(JNIEnv *env, jclass type,
                                                            jlong rtmp) {
    RTMP_Close((RTMP *) rtmp);
    RTMP_Free((RTMP *) rtmp);
    return 0;

}