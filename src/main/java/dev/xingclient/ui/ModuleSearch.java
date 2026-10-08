package dev.xingclient.ui;

import dev.xingclient.MenuData;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

/** Modal, automatically focused module finder. No panel input leaks through its closing animation. */
public final class ModuleSearch {
    public record Result(int category,String name,String key) {}
    private final Consumer<Result> choose;
    private final List<Result> all=new ArrayList<>();
    private List<Result> results=List.of();
    private String query="";
    private boolean open,selectAll;
    private int selected,first,visible=8;
    private double progress,x,y,width,listY,cardHeight;
    private long lastFrame;
    public ModuleSearch(Consumer<Result> choose){
        this.choose=choose;
        for(int i=0;i<MenuData.CATEGORIES.size();i++){
            var c=MenuData.CATEGORIES.get(i);
            for(String name:c.modules())all.add(new Result(i,name,MenuData.key(c,name)));
        }
    }
    public boolean isOpen(){return open;}
    public boolean blocksInput(){return open||progress>.001;}
    public String query(){return query;}
    public int resultCount(){return results.size();}
    public void open(){open=true;query="";selectAll=false;changed();lastFrame=System.nanoTime();}
    public void close(){open=false;selectAll=false;}
    public void reset(){close();progress=0;lastFrame=0;}
    private void changed(){
        String needle=query.toLowerCase(Locale.ROOT).trim();
        results=all.stream().filter(r->r.name.toLowerCase(Locale.ROOT).contains(needle))
            .sorted(Comparator.comparingInt(r->r.name.toLowerCase(Locale.ROOT).startsWith(needle)?0:1)).toList();
        selected=0;first=0;
    }
    private int alpha(int color){return ((int)((color>>>24)*progress)<<24)|(color&0xffffff);}
    public void draw(DrawContext ctx,double screenWidth,double screenHeight,int pink,int blue,double mouseX,double mouseY){
        long now=System.nanoTime();double dt=lastFrame==0?0:Math.min(.05,(now-lastFrame)/1e9);lastFrame=now;
        progress=Math.clamp(progress+(open?1:-1)*dt/0.18,0,1);
        if(progress==0)return;
        double ease=1-Math.pow(1-progress,3);
        GuiDraw.rect(ctx,0,0,screenWidth,screenHeight,((int)(185*ease)<<24));
        width=Math.min(520,screenWidth-48);x=(screenWidth-width)/2;y=Math.max(42,screenHeight*.19)-18*(1-ease);
        visible=Math.max(1,Math.min(9,(int)((screenHeight-y-87)/34)));
        first=Math.clamp(first,0,Math.max(0,results.size()-visible));
        int rows=Math.max(1,Math.min(visible,results.size()));
        cardHeight=55+rows*34;listY=y+47;
        for(int i=5;i>0;i--)GuiDraw.rect(ctx,x-i*3,y-i*2,width+i*6,cardHeight+i*5,alpha(0x07000000));
        GuiDraw.rect(ctx,x,y,width,cardHeight,alpha(0xff18151e));
        GuiDraw.border(ctx,x,y,width,cardHeight,alpha(0xff554557));
        GuiDraw.rect(ctx,x,y,width/2,2,alpha(0xff000000|pink));
        GuiDraw.rect(ctx,x+width/2,y,width/2,2,alpha(0xff000000|blue));
        ctx.enableScissor((int)x+16,(int)y+10,(int)(x+width-16),(int)y+39);
        String label=query.isEmpty()?"Type a module name...":query;
        double textX=x+18-Math.max(0,JostFont.width(query,16)-(width-44));
        if(selectAll&&!query.isEmpty())GuiDraw.rect(ctx,textX,y+11,JostFont.width(query,16),23,alpha(0xff4a3a53));
        JostFont.draw(ctx,label,textX,y+24,16,alpha(query.isEmpty()?0xff857b8e:0xffeee5f1));
        if(open&&System.currentTimeMillis()%1000<550)GuiDraw.rect(ctx,textX+JostFont.width(query,16)+1,y+14,1,20,alpha(0xff000000|pink));
        ctx.disableScissor();
        GuiDraw.rect(ctx,x+16,y+43,width-32,1,alpha(0xff403548));
        if(results.isEmpty())JostFont.draw(ctx,"No matching modules",x+18,listY+17,13,alpha(0xff9d8fa6));
        for(int i=first;i<Math.min(results.size(),first+visible);i++){
            var r=results.get(i);double rowY=listY+(i-first)*34;
            boolean hovered=mouseX>=x+8&&mouseX<x+width-8&&mouseY>=rowY&&mouseY<rowY+34;
            if(i==selected||hovered){GuiDraw.rect(ctx,x+8,rowY,width-16,32,alpha(hovered?0xff392c42:0xff2e2536));GuiDraw.rect(ctx,x+8,rowY,2,32,alpha(0xff000000|pink));}
            JostFont.draw(ctx,r.name,x+20,rowY+16,15,alpha(0xffe9dfea));
            String category=MenuData.CATEGORIES.get(r.category).name();
            JostFont.draw(ctx,category,x+width-20-JostFont.width(category,11),rowY+16,11,alpha(0xff000000|blue));
        }
        if(results.size()>visible){double track=rows*34,thumb=Math.max(16,track*visible/results.size());GuiDraw.rect(ctx,x+width-5,listY+(track-thumb)*first/(results.size()-visible),2,thumb,alpha(0xff000000|blue));}
    }
    public void click(double mx,double my,int button){
        if(!open||button!=0)return;
        if(mx<x||mx>x+width||my<y||my>y+cardHeight){close();return;}
        int row=(int)Math.floor((my-listY)/34);
        if(row>=0&&row<visible&&first+row<results.size()){selected=first+row;activate();}
    }
    public void scroll(double amount){if(open)first=Math.clamp(first-(int)Math.round(amount*3),0,Math.max(0,results.size()-visible));}
    private void activate(){if(!results.isEmpty()){var result=results.get(selected);close();choose.accept(result);}}
    public void key(int key,int modifiers,String clipboard){
        if(!open)return;
        boolean ctrl=(modifiers&GLFW.GLFW_MOD_CONTROL)!=0;
        if(key==GLFW.GLFW_KEY_ESCAPE){close();return;}
        if(ctrl&&key==GLFW.GLFW_KEY_A){selectAll=true;return;}
        if(ctrl&&key==GLFW.GLFW_KEY_V){insert(clipboard);return;}
        if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER){activate();return;}
        if(key==GLFW.GLFW_KEY_UP||key==GLFW.GLFW_KEY_DOWN){
            selected=Math.clamp(selected+(key==GLFW.GLFW_KEY_UP?-1:1),0,Math.max(0,results.size()-1));
            if(selected<first)first=selected;if(selected>=first+visible)first=selected-visible+1;return;
        }
        if(key==GLFW.GLFW_KEY_BACKSPACE||key==GLFW.GLFW_KEY_DELETE){
            if(selectAll)query="";else if(key==GLFW.GLFW_KEY_BACKSPACE&&!query.isEmpty())query=query.substring(0,query.length()-1);
            selectAll=false;changed();
        }
    }
    public void type(char c,int modifiers){if(open&&(modifiers&(GLFW.GLFW_MOD_CONTROL|GLFW.GLFW_MOD_ALT))==0&&c>=32&&c<127)insert(Character.toString(c));}
    private void insert(String value){
        if(selectAll)query="";selectAll=false;
        StringBuilder clean=new StringBuilder(query);
        for(char c:value.toCharArray())if(c>=32&&c<127&&clean.length()<64)clean.append(c);
        query=clean.toString();changed();
    }
}
