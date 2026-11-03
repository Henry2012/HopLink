#ifdef __ANDROID__
#include <jni.h>
#include <stdlib.h>
#include <string.h>
extern char *CorePrepare(char*, char*);
extern char *CoreValidate(char*, char*);
extern long long CoreBeginBatch(void);
extern void CoreCancelBatch(long long);
extern int CoreMeasure(long long,char*);
extern int CoreSelect(char*);
extern int CoreStartTun(int);
extern void CoreStopTun(void);

static char *utf8(JNIEnv *e,jbyteArray input){
    jsize size=(*e)->GetArrayLength(e,input);char *out=malloc((size_t)size+1);
    if(!out)return NULL;
    (*e)->GetByteArrayRegion(e,input,0,size,(jbyte*)out);out[size]=0;return out;
}
JNIEXPORT jbyteArray JNICALL Java_cn_hqq_simplenode_Core_prepareBytes(JNIEnv *e,jclass c,jbyteArray home,jbyteArray raw) {
    char *h=utf8(e,home),*r=utf8(e,raw);if(!h||!r){free(h);free(r);return NULL;}
    char *out=CorePrepare(h,r);free(h);free(r);
    jsize size=(jsize)strlen(out);jbyteArray result=(*e)->NewByteArray(e,size);
    if(result)(*e)->SetByteArrayRegion(e,result,0,size,(jbyte*)out);free(out);return result;
}
JNIEXPORT jbyteArray JNICALL Java_cn_hqq_simplenode_Core_validateBytes(JNIEnv *e,jclass c,jbyteArray home,jbyteArray raw) {
    char *h=utf8(e,home),*r=utf8(e,raw);if(!h||!r){free(h);free(r);return NULL;}
    char *out=CoreValidate(h,r);free(h);free(r);
    jsize size=(jsize)strlen(out);jbyteArray result=(*e)->NewByteArray(e,size);
    if(result)(*e)->SetByteArrayRegion(e,result,0,size,(jbyte*)out);free(out);return result;
}
JNIEXPORT jlong JNICALL Java_cn_hqq_simplenode_Core_beginBatch(JNIEnv *e,jclass c){return CoreBeginBatch();}
JNIEXPORT void JNICALL Java_cn_hqq_simplenode_Core_cancelBatch(JNIEnv *e,jclass c,jlong batch){CoreCancelBatch(batch);}
JNIEXPORT jint JNICALL Java_cn_hqq_simplenode_Core_measureBytes(JNIEnv *e,jclass c,jlong batch,jbyteArray name) {
    char *s=utf8(e,name);if(!s)return -1;int r=CoreMeasure(batch,s);free(s);return r;
}
JNIEXPORT jboolean JNICALL Java_cn_hqq_simplenode_Core_selectBytes(JNIEnv *e,jclass c,jbyteArray name) {
    char *s=utf8(e,name);if(!s)return JNI_FALSE;int r=CoreSelect(s);free(s);return r!=0;
}
JNIEXPORT jboolean JNICALL Java_cn_hqq_simplenode_Core_startTun(JNIEnv *e,jclass c,jint fd) { return CoreStartTun(fd)!=0; }
JNIEXPORT void JNICALL Java_cn_hqq_simplenode_Core_stopTun(JNIEnv *e,jclass c) { CoreStopTun(); }
#endif
