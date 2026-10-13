#pragma once
namespace xing::web {
inline const char* surfaceVertex() {
    return R"GLSL(#version 330 core
out vec2 uv;
void main() {
    vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);
    uv=vec2(p.x,1.0-p.y);
    gl_Position=vec4(p*2.0-1.0,0.0,1.0);
}
)GLSL";
}
inline const char* surfaceFragment() {
    return R"GLSL(#version 330 core
in vec2 uv;
out vec4 color;
uniform sampler2D Page, Scene;
uniform vec2 Resolution;
uniform vec2 BlurDirection;
uniform float BlurRadius,CornerRadius;
uniform vec4 BackdropRect;
uniform int Pass;
float gaussianWeight(float distance, float sigma) {
    return exp(-0.5 * distance * distance / (sigma * sigma));
}
vec3 gaussianBlur(vec2 position) {
    vec2 sourceSize = vec2(textureSize(Scene, 0));
    float scale = BlurDirection.x > 0.0 ? sourceSize.x / Resolution.x : sourceSize.y / Resolution.y;
    float sigma = max(0.5, BlurRadius * 0.5 * scale);
    int support = int(ceil(3.0 * sigma));
    vec2 stepSize = BlurDirection / sourceSize;
    vec3 result = texture(Scene, position).rgb;
    float total = 1.0;
    for (int i = 1; i <= support; i += 2) {
        float first = gaussianWeight(float(i), sigma);
        float second = i + 1 <= support ? gaussianWeight(float(i + 1), sigma) : 0.0;
        float weight = first + second;
        vec2 offset = stepSize * (float(i) + second / weight);
        result += (texture(Scene, position + offset).rgb + texture(Scene, position - offset).rgb) * weight;
        total += 2.0 * weight;
    }
    return result / total;
}
void main() {
    if(Pass==0) {color=texture(Page,uv);return;}
    vec2 worldUv = vec2(uv.x, 1.0 - uv.y);
    if (Pass == 2) { color = vec4(gaussianBlur(worldUv), 1.0); return; }
    vec2 point=uv*Resolution;
    float radius=min(CornerRadius,min(BackdropRect.z,BackdropRect.w)*0.5);
    vec2 edge=abs(point-(BackdropRect.xy+BackdropRect.zw*0.5))-BackdropRect.zw*0.5+radius;
    float distance=length(max(edge,vec2(0.0)))+min(max(edge.x,edge.y),0.0)-radius;
    float coverage=1.0-smoothstep(-0.5,0.5,distance);
    if(coverage<=0.0) discard;
    color = vec4(texture(Scene, worldUv).rgb * coverage, coverage);
}
)GLSL";
}
}
