#include "pch.h"
#include "ShaderEffects.h"
#include "ShaderPatterns.h"
#include <cmath>
#include <string>
#include <jni.h>

namespace xing { namespace visuals {
bool captures(int category, unsigned targets, double distanceSquared, double range,
              bool invisible, bool includeInvisible, bool friendPlayer, bool includeFriends) {
    return category >= 0 && category < 6 && (targets & (1u << category)) != 0
        && std::isfinite(distanceSquared) && distanceSquared >= 0
        && std::isfinite(range) && range > 0 && distanceSquared <= range * range
        && (!invisible || includeInvisible) && (!friendPlayer || includeFriends);
}

const char* vertexShader() {
    return R"GLSL(#version 330 core
out vec2 uv;
void main() {
    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    uv = p;
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
)GLSL";
}

static const std::string common = std::string(R"GLSL(#version 330 core
in vec2 uv;
out vec4 fragColor;
uniform sampler2D Mask, Glow;
uniform vec2 Resolution;
uniform float Time, Speed, Brightness, Saturation;
uniform vec3 Primary, Secondary, Accent;
uniform int Material, CrystalMaterial, HandMaterial, Quality, CompositePass;
uniform float FillOpacity, OutlineOpacity, OutlineWidth, GlowStrength, GlowRadius;
uniform vec3 CategoryStrength;
uniform int SkyMode;
uniform float SkyIntensity, StarDensity;
uniform vec4 Animation[4], Motion[4];
uniform vec3 TargetPrimary[4], TargetSecondary[4], TargetAccent[4];
uniform float TargetFill[4], EdgePulse, GlowFalloff, StarSpeed;
uniform int AnimatedOutline;
vec3 finishColor(vec3 color) {
    float grey=dot(color,vec3(0.2126,0.7152,0.0722));
    return clamp(mix(vec3(grey),color,Saturation)*Brightness,0.0,1.0);
}
)GLSL") + shaderPatterns() + R"GLSL(
vec3 surface(vec2 p,int mode,int target) {
    vec3 primary=TargetPrimary[target],secondary=TargetSecondary[target],accent=TargetAccent[target];
    float angle=Animation[target].w;
    vec2 q=mat2(cos(angle),-sin(angle),sin(angle),cos(angle))*p;
    float animationTime=Time*Speed*Animation[target].x;
    if (mode == 0 || mode == 9) {
        vec2 squarePosition = (q * Resolution.y + Resolution * 0.5) / min(Resolution.x, Resolution.y);
        float tileDensity = mode == 9 ? 4.0 : 8.0;
        squarePosition = squarePosition * tileDensity * Animation[target].y + Motion[target].xy * animationTime;
        return finishColor(mode == 0 ? easterPastel(squarePosition, animationTime) : liquidChrome(squarePosition, animationTime));
    }
    if(mode>=4) {
        vec2 patternPosition=q*Animation[target].y+Motion[target].xy*animationTime;
        return finishColor(suppliedPattern(patternPosition,mode,animationTime));
    }
    float drift=sin(animationTime*0.2)*0.04;
    float gradient=smoothstep(-0.6,0.6,q.y+drift);
    vec3 color=mix(primary,secondary,gradient);
    if(mode==1) color=primary;
    else if(mode==3) {
        float sheen=exp(-pow((q.x+q.y*0.4+drift)*2.0,2.0));
        color=mix(color,vec3(1.0),sheen*0.16);
    }
    return finishColor(color);
}
)GLSL";

const char* fragmentShader(bool sky) {
    static const std::string entity = std::string(common) + R"GLSL(
void main() {
    vec4 center=texture(Mask,uv);
    float coverage=center.a, edge=0.0;
    vec4 blurred=texture(Glow,uv);
    vec4 nearest=center;
    vec2 pixel=1.0/Resolution;
    for(int i=0;i<32;i++) {
        if(CompositePass==1 || i>=Quality) break;
        float angle=6.2831853*(float(i)+0.5)/float(Quality);
        vec2 d=vec2(cos(angle),sin(angle));
        vec4 ring=texture(Mask,uv+d*pixel*OutlineWidth);
        edge=max(edge,ring.a);
        if(ring.a>nearest.a) nearest=ring;
    }
    if(nearest.a<0.001 && blurred.a<0.001) discard;
    float categoryCode=nearest.a>0.001?nearest.r/max(nearest.a,0.001):blurred.r/max(blurred.a,0.001);
    int category=clamp(int(round(categoryCode*255.0/32.0)),1,6);
    int mode=category==3?CrystalMaterial:(category==4?HandMaterial:Material);
    float strength=category==3?CategoryStrength.y:(category==4?CategoryStrength.z:CategoryStrength.x);
    vec2 p=(uv-0.5)*vec2(Resolution.x/Resolution.y,1.0);
    int target=category==3?1:(category==4?2:0);
    vec3 c=surface(p,mode,target);
    bool opaqueHand = category == 4 && FillOpacity >= 0.999 && TargetFill[2] >= 0.999 && strength > 0.0;
    if (CompositePass == 1) {
        if (!opaqueHand || coverage < 0.001) discard;
        fragColor = vec4(c, coverage);
        return;
    }
    float outside=1.0-coverage;
    float outline=max(edge-coverage,0.0)*OutlineOpacity;
    outline*=1.0-EdgePulse*0.5+EdgePulse*0.5*sin(Time*Speed*3.0);
    float glow=pow(clamp(blurred.a,0.0,1.0),GlowFalloff)*GlowStrength*Motion[target].w*outside;
    float fillAlpha=opaqueHand ? 0.0 : clamp(coverage*FillOpacity*TargetFill[target]*strength,0.0,1.0);
    float edgeAlpha=clamp(outline*strength,0.0,1.0);
    float glowAlpha=clamp(glow*0.45*strength,0.0,1.0);
    vec3 edgeColor=AnimatedOutline!=0?c:finishColor(TargetAccent[target]);
    vec3 glowColor=finishColor(mix(TargetSecondary[target],TargetAccent[target],0.5));
    vec3 premultiplied=glowColor*glowAlpha;
    float alpha=glowAlpha;
    premultiplied=edgeColor*edgeAlpha+premultiplied*(1.0-edgeAlpha);
    alpha=edgeAlpha+alpha*(1.0-edgeAlpha);
    premultiplied=c*fillAlpha+premultiplied*(1.0-fillAlpha);
    alpha=fillAlpha+alpha*(1.0-fillAlpha);
    if(alpha<0.001) discard;
    fragColor=vec4(premultiplied/alpha,alpha);
}
)GLSL";
    static const std::string skySource = std::string(common) + R"GLSL(
void main() {
    if(SkyMode==0 || SkyMode>=2) {
        vec2 p=(uv-0.5)*vec2(Resolution.x/Resolution.y,1.0);
        int mode = SkyMode == 0 ? 0 : SkyMode + 2;
        fragColor=vec4(clamp(surface(p,mode,3)*SkyIntensity,0.0,1.0),1.0);
        return;
    }
    float height=smoothstep(0.0,1.0,uv.y);
    vec3 horizon=mix(TargetPrimary[3],vec3(1.0),0.3);
    vec3 zenith=TargetSecondary[3];
    if(SkyMode==1) { horizon*=0.45; zenith*=0.18; }
    vec3 color=mix(horizon,zenith,height)*SkyIntensity;
    fragColor=vec4(clamp(color,0.0,1.0),1.0);
}
)GLSL";
    return sky ? skySource.c_str() : entity.c_str();
}
const char* blurShader() {
    return R"GLSL(#version 330 core
in vec2 uv;
out vec4 fragColor;
uniform sampler2D Mask;
uniform vec2 BlurDirection;
uniform float GlowRadius;
void main() {
    vec4 sum=vec4(0.0); float weight=0.0;
    for(int i=-8;i<=8;i++) {
        float x=float(i)/8.0;
        float w=exp(-x*x*4.5);
        sum+=texture(Mask,uv+BlurDirection*GlowRadius*x)*w;
        weight+=w;
    }
    fragColor=sum/weight;
}
)GLSL";
}
} }

extern "C" JNIEXPORT jstring JNICALL
Java_dev_xingclient_render_NativeShaderEffects_source(JNIEnv* env, jclass, jint stage) {
    if(stage < 0 || stage > 3) return nullptr;
    if(stage == 3) return env->NewStringUTF(xing::visuals::blurShader());
    return env->NewStringUTF(stage == 0 ? xing::visuals::vertexShader() : xing::visuals::fragmentShader(stage == 2));
}
extern "C" JNIEXPORT jboolean JNICALL
Java_dev_xingclient_render_NativeShaderEffects_captures(JNIEnv*, jclass, jint category, jint targets,
        jdouble distance, jdouble range, jboolean invisible, jboolean includeInvisible,
        jboolean friendPlayer, jboolean includeFriends) {
    return xing::visuals::captures(category, targets, distance, range, invisible, includeInvisible, friendPlayer, includeFriends) ? JNI_TRUE : JNI_FALSE;
}
