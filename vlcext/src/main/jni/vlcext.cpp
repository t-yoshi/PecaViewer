#include <jni.h>
#include <android/log.h>

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cstdarg>
#include <cinttypes>


using namespace std;

#define TAG "VLCExtNt"

#define LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGF(...)  __android_log_print(ANDROID_LOG_FATAL, TAG, __VA_ARGS__)


static JavaVM *sVM;

#define CHECK_PTR(ptr) do { \
        if ((ptr) == nullptr) {\
            LOGF("[%s] %s is NULL", __func__, #ptr);\
            abort(); /*__noreturn*/ \
        }\
    } while(0)

extern "C" {
/*****************************************************************************
  * Copyright (C) 2003-2005 VLC authors and VideoLAN
  *
  * This program is free software; you can redistribute it and/or modify it
  * under the terms of the GNU Lesser General Public License as published by
  * the Free Software Foundation; either version 2.1 of the License, or
  * (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  * GNU Lesser General Public License for more details.
  *
  * You should have received a copy of the GNU Lesser General Public License
  * along with this program; if not, write to the Free Software Foundation,
  * Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
  *****************************************************************************/
typedef struct {
    uintptr_t i_object_id; /**< Emitter (temporaly) unique object ID or 0 */
    const char *psz_object_type; /**< Emitter object type name */
    const char *psz_module; /**< Emitter module (source code) */
    const char *psz_header; /**< Additional header (used by VLM media) */
    const char *file; /**< Source code file name or NULL */
    int line; /**< Source code file line number or -1 */
    const char *func; /**< Source code calling function name or NULL */
} libvlc_log_t;


struct libvlc_instance_t;
struct libvlc_media_t;
struct libvlc_media_list_t;
struct libvlc_media_discoverer_t;
struct libvlc_media_player_t;
struct vlcjni_object_owner;
struct vlcjni_object_sys;

struct vlcjni_object {
    /* Pointer to parent libvlc: NULL if the VLCObject is a LibVLC */
    libvlc_instance_t *p_libvlc;

    /* Current pointer to native vlc object */
    union {
        libvlc_instance_t *p_libvlc;
        libvlc_media_t *p_m;
        libvlc_media_list_t *p_ml;
        libvlc_media_discoverer_t *p_md;
        libvlc_media_player_t *p_mp;
    } u;
    /* Used by vlcobject */
    vlcjni_object_owner *p_owner;
    /* Used by media, medialist, mediadiscoverer... */
    vlcjni_object_sys *p_sys;
};

typedef void(*libvlc_log_cb)(void *data, int level, const libvlc_log_t *ctx, const char *fmt, va_list args);

void libvlc_log_set(libvlc_instance_t *p_instance, libvlc_log_cb cb, void *data);
void libvlc_log_unset(libvlc_instance_t *p_instance);
int libvlc_video_take_snapshot(libvlc_media_player_t *p_mi,
                               unsigned num,
                               const char *psz_filepath,
                               unsigned int i_width,
                               unsigned int i_height
);
int libvlc_video_get_size(libvlc_media_player_t *p_mi,
                                  unsigned num,
                                  unsigned *px,
                                  unsigned *py
);

#define likely(p)      (!!(p))

size_t vlc_towc(const char *str, uint32_t *pwc);

static inline const char *IsUTF8(const char *str) {
    size_t n;
    uint32_t cp;

    while ((n = vlc_towc(str, &cp)) != 0)
        if (likely(n != (size_t) -1))
            str += n;
        else
            return nullptr;
    return str;
}
static void logCallback(void *, int, const libvlc_log_t *, const char *, va_list);

} //extern "C"


static struct VLCExtClassCache {
    jclass jc_VLCLogger;
    jclass jc_VLCLoggerContext;

    jmethodID mid_VLCLogger_log;
    jmethodID mid_VLCLoggerContext_init;

    jfieldID fid_VLCObject_mInstance;

    jclass jc_Point;
    jmethodID mid_Point_init;

    void init(JNIEnv *env) {
        jc_String = (jclass) env->NewGlobalRef(
                env->FindClass("java/lang/String"));
        CHECK_PTR(jc_String);

        jclass org_videolan_libvlc_VLCObject = env->FindClass("org/videolan/libvlc/VLCObject");
        CHECK_PTR(org_videolan_libvlc_VLCObject);

        fid_VLCObject_mInstance =
                env->GetFieldID(org_videolan_libvlc_VLCObject,
                                "mInstance", "J");
        CHECK_PTR(fid_VLCObject_mInstance);

        jc_VLCLogger = (jclass) env->NewGlobalRef(
                env->FindClass("com/github/t_yoshi/vlcext/VLCLogger"));
        CHECK_PTR(jc_VLCLogger);

        mid_String_init = env->GetMethodID(
                jc_String, "<init>", "([BLjava/lang/String;)V"
        );
        CHECK_PTR(mid_String_init);

        mid_VLCLogger_log =
                env->GetMethodID(jc_VLCLogger,
                                 "log",
                                 "(ILcom/github/t_yoshi/vlcext/VLCLogContext;Ljava/lang/String;)V");
        CHECK_PTR(mid_VLCLogger_log);

        jc_VLCLoggerContext = (jclass) env->NewGlobalRef(
                env->FindClass("com/github/t_yoshi/vlcext/VLCLogContext"));
        CHECK_PTR(jc_VLCLoggerContext);

        mid_VLCLoggerContext_init =
                env->GetMethodID(jc_VLCLoggerContext,
                                 "<init>",
                                 "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;)V"
                );
        CHECK_PTR(mid_VLCLoggerContext_init);


        jc_Point = (jclass) env->NewGlobalRef(
                env->FindClass("android/graphics/Point"));
        CHECK_PTR(jc_Point);

        mid_Point_init = env->GetMethodID(jc_Point,
                                 "<init>",
                                 "(II)V");
        CHECK_PTR(mid_Point_init);
    }

    //NewStringUTFは内容によってはクラッシュする
    jstring safeNewString(JNIEnv *env, const char *s) {
        if (s == nullptr)
            return nullptr;

        const char *charset = IsUTF8(s) ? "utf8" : "shift-jis";
        const jsize len = ::strlen(s);
        jbyteArray ba = env->NewByteArray(len);
        if (!ba)
            return nullptr;
        env->SetByteArrayRegion(ba, 0, len, (jbyte *) s);

        return (jstring) env->NewObject(
                jc_String, mid_String_init,
                ba, env->NewStringUTF(charset));
    }

private:
    jclass jc_String;
    jmethodID mid_String_init;
} sClassCache;


static void logCallback(void *data, int level, const libvlc_log_t *ctx, const char *fmt, va_list args) {
    JNIEnv *env;
    jint ret = sVM->GetEnv((void **) &env, JNI_VERSION_1_6);
    if (ret == JNI_EDETACHED) {
        //ret = sVM->AttachCurrentThread((JNIEnv**)&env, NULL);
    }

    char msgBuf[4096];
    if (::vsnprintf(msgBuf, sizeof(msgBuf), fmt, args) < 0)
        return;

    if (ret != JNI_OK) {
        //LOGE("FAILED: VM::GetEnv() || VM::AttachCurrentThread() err=%d", ret);
        LOGI("NoAttachedThread: %s", msgBuf);
        return;
    }

    auto vlcLogger = (jobject) data;
    if (env->IsSameObject(vlcLogger, nullptr)) {
        LOGI("VLCLoggerReleased: %s", msgBuf);
        return;
    }

    env->PushLocalFrame(128);

    jobject logContext = env->NewObject(
            sClassCache.jc_VLCLoggerContext,
            sClassCache.mid_VLCLoggerContext_init,

            (jint) ctx->i_object_id,
            sClassCache.safeNewString(env, ctx->psz_object_type),
            sClassCache.safeNewString(env, ctx->psz_module),
            sClassCache.safeNewString(env, ctx->psz_header),
            sClassCache.safeNewString(env, ctx->file),
            (jint) ctx->line,
            sClassCache.safeNewString(env, ctx->func)
    );

    env->CallVoidMethod(
            vlcLogger,
            sClassCache.mid_VLCLogger_log,
            (jint) level, logContext,
            sClassCache.safeNewString(env, msgBuf)
    );

    env->PopLocalFrame(nullptr);
}

extern "C"
JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }
    sVM = vm;
    LOGI("vlcext: Build(%s %s)", __DATE__, __TIME__);

    return JNI_VERSION_1_6;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_github_t_1yoshi_vlcext_VLCExt_initClasses(JNIEnv *env, jclass type) {
    sClassCache.init(env);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_github_t_1yoshi_vlcext_VLCExt_setLoggerCallback(JNIEnv *env, jclass type, jobject libVlc, jobject logger) {
    auto pLibVlcInstance = (vlcjni_object *) env->GetLongField(
            libVlc,
            sClassCache.fid_VLCObject_mInstance);
    //LOGI("pLibVlcInstance=%p", pLibVlcInstance->u.p_libvlc);

    if (logger != nullptr) {
        ::libvlc_log_set(
                pLibVlcInstance->u.p_libvlc,
                logCallback,
                env->NewWeakGlobalRef(logger));
    } else {
        ::libvlc_log_unset(pLibVlcInstance->u.p_libvlc);
    }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_github_t_1yoshi_vlcext_VLCExt_videoTakeSnapshot(JNIEnv *env, jclass type, jobject player, jstring filepath_,
                                                         jint width, jint height) {
    const char *filepath = env->GetStringUTFChars(filepath_, nullptr);
    auto pPlayerInstance = (vlcjni_object *) env->GetLongField(
            player,
            sClassCache.fid_VLCObject_mInstance);

    bool ret = 0 == ::libvlc_video_take_snapshot(pPlayerInstance->u.p_mp, 0, filepath, width, height);

    env->ReleaseStringUTFChars(filepath_, filepath);

    return ret;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_github_t_1yoshi_vlcext_VLCExt_videoGetSize(JNIEnv *env, jclass clazz, jobject player) {
    auto * pPlayerInstance = (vlcjni_object *) env->GetLongField(
            player,
            sClassCache.fid_VLCObject_mInstance);
    unsigned x, y;
    int r = libvlc_video_get_size(pPlayerInstance->u.p_mp, 0, &x, &y);
    //LOGI("%p: %d (%d,%d)", pPlayerInstance->u.p_mp, r, x, y);
    if (r)
        return nullptr;
    return env->NewObject(
            sClassCache.jc_Point, sClassCache.mid_Point_init,
            x, y);
}

