package dev.xingclient.ui;

/** Pixel-space drag and gentle gravity, independent of Minecraft's GUI scale. */
public final class MascotMotion {
    private static final double GRAVITY=320, TERMINAL_SPEED=420;
    private double viewportWidth,viewportHeight,x,y,width,height,velocity,grabX,grabY;
    private boolean initialized,held;
    public double x(){return x;}
    public double y(){return y;}
    public double width(){return width;}
    public double height(){return height;}
    public boolean held(){return held;}
    public boolean airborne(){return initialized&&y<floor()-.01;}
    private double floor(){return Math.max(0,viewportHeight-height);}
    public void layout(double w,double h){
        if(initialized&&w==viewportWidth&&h==viewportHeight)return;
        double oldMaxX=Math.max(0,viewportWidth-width),oldFloor=floor();
        double fractionX=oldMaxX>0?x/oldMaxX:1,fractionY=oldFloor>0?y/oldFloor:1;
        viewportWidth=w;viewportHeight=h;
        height=Math.min(h,Math.clamp(h*.43,250,440));width=height*419/595.0;
        if(width>w*.29){width=w*.29;height=width*595/419.0;}
        if(!initialized){x=Math.max(0,w-18-width);y=floor();initialized=true;}
        else{x=Math.clamp(fractionX*(w-width),0,Math.max(0,w-width));y=Math.clamp(fractionY*floor(),0,floor());release();}
        if(!airborne())velocity=0;
    }
    public boolean contains(double px,double py){return initialized&&px>=x&&px<x+width&&py>=y&&py<y+height;}
    public void grab(double px,double py){held=true;velocity=0;grabX=px-x;grabY=py-y;}
    public void drag(double px,double py){if(held){x=Math.clamp(px-grabX,0,Math.max(0,viewportWidth-width));y=Math.clamp(py-grabY,0,floor());}}
    public void release(){held=false;}
    public void advance(double seconds){
        if(held||!airborne()||seconds<=0)return;
        // Integrate acceleration exactly, including the terminal-speed transition.
        double accelerating=Math.min(seconds,Math.max(0,(TERMINAL_SPEED-velocity)/GRAVITY));
        y+=velocity*accelerating+.5*GRAVITY*accelerating*accelerating+TERMINAL_SPEED*(seconds-accelerating);
        velocity=Math.min(TERMINAL_SPEED,velocity+GRAVITY*seconds);
        if(y>=floor()){y=floor();velocity=0;}
    }
}
