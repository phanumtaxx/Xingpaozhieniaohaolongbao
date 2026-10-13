#pragma once

namespace xing { namespace visuals {
// User-supplied effects adapted to the native compositor. Author: uint32.
inline const char* shaderPatterns() {
    return R"GLSL(
float spaceField(vec3 p, float time) {
    float strength=7.0+0.03*log(1.e-6+fract(sin(time)*4373.11));
    float mag=dot(p,p);
    return max(0.0,5.0*exp(-strength*pow(abs(mag),2.3))-0.7);
}
vec3 fractalSpace(vec2 uv, float time) {
    vec2 mouse=vec2(sin(time),cos(time))/48.0;
    float speed=0.01*cos(time*0.02+3.1415926/4.0);
    float axz=0.9, ayz=-0.6, axy=0.9+time*0.08;
    mat2 rxz=mat2(cos(axz),sin(axz),-sin(axz),cos(axz));
    mat2 ryz=mat2(cos(ayz),sin(ayz),-sin(ayz),cos(ayz));
    mat2 rxy=mat2(cos(axy),sin(axy),-sin(axy),cos(axy));
    vec3 dir=vec3(uv*0.9,1.0), from=vec3(0.0), forward=vec3(0.0,0.0,1.0);
    from.xy-=5.0*(mouse-0.5);
    from.x+=1.8*cos(0.01*time)+0.001*time;
    from.y+=1.8*sin(0.01*time)+0.001*time;
    from.z+=0.003*time;
    dir.xy*=rxy; forward.xy*=rxy; dir.xz*=rxz; forward.xz*=rxz;
    dir.yz*=ryz; forward.yz*=ryz;
    from.xy*=-rxy; from.xz*=rxz; from.yz*=ryz;
    float travel=(time-3311.0)*speed;
    from+=forward*travel;
    float shift=mod(travel,0.390), offset=-shift;
    shift/=0.390;
    float s=0.24, s3=s+0.390/2.0;
    vec3 v=vec3(0.0), background=vec3(0.0);
    for(int r=0;r<5;r++) {
        vec3 p2=from+(s+offset)*dir, p3=from+(s3+offset)*dir;
        p2=abs(vec3(0.850)-mod(p2,vec3(1.700)));
        p3=abs(vec3(0.850)-mod(p3,vec3(1.700)));
        float field=spaceField(p3,time), previous=0.0, a=0.0;
        for(int i=0;i<14;i++) {
            p2=abs(p2)/max(dot(p2,p2),1.e-6)-0.79;
            float magnitude=length(p2), difference=abs(magnitude-previous);
            a+=i>7?min(12.0,difference):difference;
            previous=magnitude;
        }
        a*=a*a;
        float distance=s+offset, fade=pow(0.560,max(0.0,float(r)-shift));
        v+=fade;
        if(r==0) fade*=1.0-shift;
        if(r==4) fade*=shift;
        v+=vec3(distance,distance*distance,pow(distance,4.0))*a*0.003*fade;
        background+=vec3(1.8*field*field*field,1.4*field*field,field)*fade;
        s+=0.390; s3+=0.390;
    }
    v=mix(vec3(length(v)),v,0.800);
    background*=0.11;
    background.b*=1.8; background.r*=0.05;
    background.b=0.5*mix(background.g,background.b,0.8); background.g=0.0;
    background.bg=mix(background.gb,background.bg,0.5*(cos(time*0.01)+1.0));
    return v*0.01+background;
}
float galaxyHash(vec2 p) { return fract(sin(dot(p*0.05,vec2(14.5,76.38)))*43256.2895); }
float galaxyNoise(vec2 pos) {
    vec2 p=floor(pos), f=fract(pos);
    f=f*f*f*(3.0-2.0*f);
    return mix(mix(galaxyHash(p),galaxyHash(p+vec2(1.0,0.0)),f.x),
        mix(galaxyHash(p+vec2(0.0,1.0)),galaxyHash(p+vec2(1.0)),f.x),f.y);
}
float galaxyCloud(vec2 p, bool ridged) {
    float h=0.0,a=0.5;
    for(int i=0;i<6;i++) {
        float n=galaxyNoise(p);
        h+=(ridged?abs(n-0.5)*2.0:n)*a;
        p*=ridged?2.5:1.9; a*=ridged?0.7:0.4;
    }
    return h;
}
vec3 milkyWay(vec2 uv, vec2 resolution) {
    const vec3 sky=vec3(0.1,0.2,0.4);
    vec2 pixel=(uv/vec2(resolution.x/resolution.y,1.0)+1.0)*0.5*resolution;
    float band=abs((uv.x*0.5-1.0)*resolution.y/resolution.x+uv.y);
    vec2 clouds=uv*vec2(resolution.y/resolution.x,0.0)-vec2(1.0,-uv.y);
    vec3 col=vec3(smoothstep(0.7+0.3*smoothstep(0.0,0.35,band),1.0,galaxyHash(pixel))*galaxyHash(pixel*2.0));
    vec3 gradient=sky*(abs(uv.y-1.2)*0.4);
    col+=gradient;
    col=mix(vec3(1.0,1.0,0.8),col,0.5+0.5*smoothstep(0.0,0.07,band*galaxyCloud(5.0*clouds,false)));
    col=mix(sky*1.2,col,0.8+0.2*smoothstep(0.0,0.15,band*galaxyCloud(4.0*clouds,false)));
    float ridges=galaxyCloud(15.0*clouds,true);
    col=mix(gradient,col,0.1+0.5*smoothstep(0.0,0.1,band*ridges));
    col=mix(gradient,col,smoothstep(0.0,0.03,band*0.02+0.03*ridges));
    return col+vec3(smoothstep(0.95,1.0,galaxyHash(pixel))*galaxyHash(pixel*2.0));
}
vec3 caustics(vec2 surfacePosition, float time) {
    vec2 p=surfacePosition*5.0-vec2(10.0), i=p;
    float c=1.0;
    for(int n=0;n<4;n++) {
        float t=time*(1.0-3.0/float(n+1));
        i=p+vec2(cos(t-i.x)+sin(t+i.y),sin(t-i.y)+cos(t+i.x));
        // Algebraically equivalent to the supplied reciprocal, without dividing by sine zeros.
        c+=inversesqrt(max(dot(p*vec2(sin(i.x+t),cos(i.y+t))/0.01,
            p*vec2(sin(i.x+t),cos(i.y+t))/0.01),1.e-8));
    }
    c=1.5-sqrt(c/4.0);
    return vec3(c*c*c*c)+vec3(0.0,11.3,0.5);
}
float warpRandom(vec2 p) { return fract(sin(dot(p,vec2(13.9898,78.233)))*43758.5453123); }
float warpNoise(vec2 p) {
    vec2 i=floor(p), f=fract(p), u=f*f*(3.0-2.0*f);
    float a=warpRandom(i),b=warpRandom(i+vec2(1.0,0.0)),c=warpRandom(i+vec2(0.0,1.0)),d=warpRandom(i+vec2(1.0));
    return mix(a,b,u.x)+(c-a)*u.y*(1.0-u.x)+(d-b)*u.x*u.y;
}
float warpFbm(vec2 p,float time) {
    float v=0.0,a=0.5;
    mat2 rot=mat2(cos(0.5),sin(0.5),-sin(0.5),cos(0.5));
    for(int i=0;i<6;i++) {
        float dir=mod(float(i),2.0)>0.5?1.0:-1.0;
        v+=a*warpNoise(p-0.05*dir*time);
        p=rot*p*2.0+vec2(100.0); a*=0.5;
    }
    return v;
}
vec3 domainWarp(vec2 uv,vec2 resolution,float time) {
    vec2 pixel=(uv/vec2(resolution.x/resolution.y,1.0)+1.0)*0.5*resolution;
    vec2 p=(pixel*3.0-resolution)/min(resolution.x,resolution.y)-vec2(12.0,0.0);
    vec2 q=vec2(warpFbm(p,time),warpFbm(p+vec2(1.0),time));
    vec2 r=vec2(warpFbm(p+q+vec2(1.7,1.2)+0.15,time),warpFbm(p+q+vec2(8.3,2.8)+0.126,time));
    float f=warpFbm(p+r,time);
    vec3 color=mix(vec3(1.0,1.0,2.0),vec3(1.0),clamp(f*f*5.5,1.2,15.5));
    color=mix(color,vec3(1.0),2.0);
    color=mix(color,vec3(0.3,0.2,1.0),clamp(abs(r.x),0.0,5.0));
    return (f*f*f+0.9*f)*color;
}
float interferenceField(vec2 p,float time) {
    float t=time/100.0;
    float d1=distance(p,vec2(0.5-sin(t)/100.0+tan(2.0*p.y),0.5-cos(t)/135.0));
    float d2=distance(p,vec2(sin(t)/100.0,1.0+cos(t)/135.0)-tan(2.0-p.y));
    float d3=distance(p,vec2(1.0-sin(t)/100.0+tan(2.0*(1.0-p.y)),cos(t)/135.0));
    float wave=(sin(3.14159*(d1+t/50.0)*10.0)+cos(3.14159*(d2+t/50.0)*10.0)+sin(3.14159*(d3+t/50.0)*10.0))/3.0;
    return pow(max(0.0,1.0-abs(wave)),500.0);
}
vec3 interference(vec2 uv,float time) {
    vec2 p=uv; p.y=p.y*0.5+0.25;
    float base=interferenceField(p,time);
    return base*(1.0-vec3(interferenceField(p+vec2(-0.001),time),
        interferenceField(p+vec2(0.001),time),interferenceField(p+vec2(-0.002,0.002),time)));
}
vec3 liquidChrome(vec2 position, float time) {
    const float tau = 6.28318530718;
    vec2 tile = fract(position * 0.8);
    float x = tile.x * tau;
    float y = tile.y * tau;
    float t = time;
    float warp = sin(x + t) * 0.35 + sin(y - t * 0.8) * 0.35
        + sin(x + y + t * 0.6) * 0.25 + sin(2.0 * x - y - t * 0.5) * 0.18;
    float phase = 0.35 * sin(x + warp) + 0.35 * sin(y - warp)
        + 0.20 * sin(x + y + warp) + 0.10 * sin(2.0 * x - y - warp);
    vec3 color = vec3(0.75, 0.55, 0.74)
        + vec3(0.32, 0.41, 0.34) * cos(tau * (vec3(phase * 0.7 + 0.15) + vec3(0.03, 0.36, 0.68)));
    float stripes = sin(phase * 2.0 * 5.0 + warp * 2.2);
    float brightBands = pow(max(stripes, 0.0), 6.0);
    float darkBands = pow(max(-stripes, 0.0), 3.5);
    color += vec3(0.90, 0.96, 1.00) * brightBands * 0.95;
    color = mix(color, vec3(0.03, 0.04, 0.12), darkBands * 0.55);
    float fine = sin(x * 2.0 + y * 1.7 + warp * 3.0 + t * 0.4);
    color += vec3(0.75, 0.92, 1.0) * pow(max(fine, 0.0), 18.0) * 0.28;
    vec2 p = tile - 0.5;
    float hotspot = 0.18 / (dot(p, p) + 0.08);
    color += vec3(1.0, 0.92, 0.97) * hotspot * 0.12;
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(vec3(luma), color, 1.6);
    float vignette = 1.0 - smoothstep(0.08, 0.78, length(p));
    color *= 0.86 + 0.14 * vignette;
    return 1.0 - exp(-max(color, vec3(0.0)) * 1.2);
}
vec3 easterPastel(vec2 position, float time) {
    const float tau = 6.28318530718;
    const vec3 pink = vec3(1.00, 0.79, 0.87);
    const vec3 lavender = vec3(0.86, 0.79, 1.00);
    const vec3 blue = vec3(0.74, 0.88, 1.00);
    const vec3 mint = vec3(0.75, 1.00, 0.84);
    const vec3 yellow = vec3(1.00, 0.96, 0.70);
    const vec3 peach = vec3(1.00, 0.86, 0.76);
    vec2 q = fract(position * 0.8) * tau;
    float t = time * 0.12;
    float a = sin(q.x + t + 0.7 * sin(q.y - t));
    float b = sin(q.y - t * 0.7 + 0.6 * sin(q.x + t));
    float c = sin(q.x + q.y + t * 0.5);
    float d = cos(q.x - q.y - t * 0.4);
    float blend1 = 0.5 + 0.5 * a;
    float blend2 = 0.5 + 0.5 * b;
    float blend3 = 0.5 + 0.5 * c;
    float blend4 = 0.5 + 0.5 * d;
    vec3 color1 = mix(pink, lavender, blend1);
    vec3 color2 = mix(blue, mint, blend2);
    vec3 color3 = mix(yellow, peach, blend3);
    vec3 color = mix(color1, color2, blend4);
    color = mix(color, color3, blend3 * 0.45);
    return mix(color, vec3(1.0), 0.12);
}
vec3 suppliedPattern(vec2 p,int mode,float time) {
    if(mode==4) return fractalSpace(p*2.0,time);
    if(mode==5) return milkyWay(p*2.0,Resolution);
    if(mode==6) return caustics(p*2.0,time);
    if(mode==7) return domainWarp(p*2.0,Resolution,time);
    return interference(p/vec2(Resolution.x/Resolution.y,1.0)+0.5,time);
}
)GLSL";
}
} }
