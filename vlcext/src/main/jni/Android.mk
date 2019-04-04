LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := vlcext
LOCAL_SRC_FILES := vlcext.cpp


LOCAL_LDLIBS := -L$(LOCAL_PATH)/../../../../vlc/nativelibs/$(TARGET_ARCH_ABI)/ -lvlc  -llog

include $(BUILD_SHARED_LIBRARY)
