#include "pch.h"
#include "NametagsModule.h"
#include <algorithm>
#include <cmath>
#include <vector>
#include <jni.h>
namespace xing { namespace visuals {
std::array<double,4> NametagsModule::anchor(const double* player,int flags,double range,double offset,double delta,bool invisible) {
    if((flags&1)!=0 || (flags&2)!=0 || (!invisible&&(flags&4)!=0) || !std::isfinite(player[7]) || player[7]>range*range) return {};
    for(int i=0;i<7;i++) if(!std::isfinite(player[i])) return {};
    return {player[0]+(player[3]-player[0])*delta,
        player[1]+(player[4]-player[1])*delta+player[6]+offset,
        player[2]+(player[5]-player[2])*delta,1.0};
}
} }
extern "C" JNIEXPORT void JNICALL Java_dev_xingclient_module_NativeNametagsModule_plan(
        JNIEnv* env,jclass,jdoubleArray snapshots,jintArray flagsArray,jdoubleArray result,
        jdouble range,jdouble offset,jdouble delta,jboolean invisible) {
    if(!snapshots||!flagsArray||!result||!std::isfinite(range)||range<1||range>256||!std::isfinite(offset)||offset<0||offset>2||!std::isfinite(delta)) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),"Invalid Nametags snapshot");return;
    }
    const int count=env->GetArrayLength(flagsArray);
    if(env->GetArrayLength(snapshots)!=count*8||env->GetArrayLength(result)!=count*4) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),"Invalid Nametags array lengths");return;
    }
    std::vector<double> players(count*8),output(count*4);std::vector<int> flags(count);
    env->GetDoubleArrayRegion(snapshots,0,count*8,players.data());env->GetIntArrayRegion(flagsArray,0,count,flags.data());
    if(env->ExceptionCheck()) return;
    const double progress=(std::max)(0.0,(std::min)(1.0,delta));
    for(int i=0;i<count;i++) {
        const auto anchor=xing::visuals::NametagsModule::anchor(players.data()+i*8,flags[i],range,offset,progress,invisible);
        std::copy(anchor.begin(),anchor.end(),output.begin()+i*4);
    }
    env->SetDoubleArrayRegion(result,0,count*4,output.data());
}

extern "C" JNIEXPORT jint JNICALL Java_dev_xingclient_module_NativeNametagsModule_healthColor(JNIEnv*,jclass,jfloat health,jfloat maximum) {
    if (!std::isfinite(health)||!std::isfinite(maximum)||maximum<=0) return 0xf08187;
    const float fraction=health/maximum;
    return fraction>0.66f?0x84e1ae:fraction>0.33f?0xf1ce76:0xf08187;
}
