LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := ciadpi
LOCAL_SRC_FILES := packets.c main.c conev.c proxy.c desync.c mpool.c extend.c
LOCAL_CFLAGS := -D_DEFAULT_SOURCE -std=c99 -O2 -Wall -Wno-unused -Wextra -Wno-unused-parameter -fPIE
LOCAL_LDFLAGS := -pie -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
include $(BUILD_EXECUTABLE)
