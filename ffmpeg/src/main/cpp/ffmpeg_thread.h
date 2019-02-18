#ifndef ANDROID_FFMPEG_THREAD_H
#define ANDROID_FFMPEG_THREAD_H

#include "ffmpeg.h"

int ffmpeg_thread_run_command(int argc, char **argv);

void ffmpeg_thread_exit(int ret);

#endif
