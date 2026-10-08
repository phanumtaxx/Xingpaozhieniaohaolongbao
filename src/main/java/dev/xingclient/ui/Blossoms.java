package dev.xingclient.ui;

import java.util.Random;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;

/** Native counterpart of blossoms.js, using the same four cached sprite images. */
public final class Blossoms {
    private static final Identifier[] SPRITES = new Identifier[4];
    static { for(int i=0;i<4;i++)SPRITES[i]=GuiDraw.texture("blossom-"+i); }
    private final Random random = new Random();
    private Particle[] particles = new Particle[0];
    private double width,height,time;
    private long last;
    private static final class Particle { double x,y,size,speed,fall,angle,spin,phase,flutter,opacity,depth;int sprite;boolean flower; }
    private double random(double low,double high) { return low+random.nextDouble()*(high-low); }
    private Particle create(int index,boolean initial) {
        var p=new Particle();p.flower=index%6==0;p.sprite=p.flower?3:index%3;
        // A few larger, slower foreground blossoms among the smaller drifting petals.
        p.depth=index%5==0?random(.8,1):index%5==2?random(0,.25):random(.35,.65);
        p.x=initial?random(0,width):random(width*.15,width+100);p.y=initial?random(0,height):random(-110,-35);
        p.size=(p.flower?random(29,36):random(29,43))*(.9+.4*p.depth);
        p.speed=random(27,48)*(1-.22*p.depth);p.fall=random(18,34)*(1-.2*p.depth);
        p.angle=random(0,Math.PI*2);p.spin=random(-.34,.34);p.phase=random(0,Math.PI*2);p.flutter=random(.6,1.2)*(1-.15*p.depth);p.opacity=random(.62,.82);
        return p;
    }
    public void pause() { last=0; }
    public void draw(DrawContext ctx,double w,double h) {
        if(w!=width||h!=height) {width=w;height=h;particles=new Particle[w<650?6:w<1100?9:12];for(int i=0;i<particles.length;i++)particles[i]=create(i,true);}
        long now=System.nanoTime();double dt=last==0?0:Math.min((now-last)/1_000_000_000.0,.05);last=now;time+=dt;
        double breeze=Math.sin(time*.23)*11;
        for(int i=0;i<particles.length;i++) {
            var p=particles[i];
            // Smooth, occasional gusts pass through the layers without abrupt direction changes.
            double gust=Math.pow(Math.max(0,Math.sin(time*.34-.9-p.depth*.3)),4);
            p.x-=(p.speed+breeze+Math.sin(time*.8+p.phase)*9+gust*(12+p.depth*10))*dt;
            p.y+=(p.fall+Math.cos(time*.65+p.phase)*7-gust*5)*dt;p.angle+=p.spin*(1+gust*.45)*dt;
            if(p.x< -70||p.y>height+70) {particles[i]=create(i,false);continue;}
            double fade=Math.max(0,Math.min(1,Math.min((p.x+35)/65,Math.min((height+35-p.y)/65,(p.y+35)/65))));
            double tilt=Math.abs(Math.cos(time*p.flutter+p.phase));
            ctx.getMatrices().push();ctx.getMatrices().translate(p.x,p.y,0);
            ctx.getMatrices().multiply(new Quaternionf().rotationZ((float)(p.angle+Math.sin(time*.7+p.phase)*.24)));
            ctx.getMatrices().scale((float)(p.flower?.8+.2*tilt:.25+.75*tilt),1,1);
            GuiDraw.texture(ctx,SPRITES[p.sprite],(float)-p.size/2,(float)-p.size/2,(float)p.size,(float)p.size,((int)(p.opacity*fade*255)<<24)|0xffffff);
            ctx.getMatrices().pop();
        }
    }
}
