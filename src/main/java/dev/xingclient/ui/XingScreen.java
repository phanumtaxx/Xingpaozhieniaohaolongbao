package dev.xingclient.ui;

import dev.xingclient.*;
import java.util.*;
import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Native pixel-space reproduction of the approved HTML, independent of game GUI scale. */
public final class XingScreen extends Screen {
    private static final int TEXT=0xffc5c3c6, DARK=0xff292333;
    private final XingClient app;
    private final Blossoms blossoms=new Blossoms();
    private final MascotMotion mascot=new MascotMotion();
    private long mascotFrame;
    private boolean[] mascotPixels;
    private int mascotImageWidth,mascotImageHeight;
    private boolean mascotMaskLoaded;
    private final List<Hit> hits=new ArrayList<>();
    private final List<Panel> panels=new ArrayList<>();
    private final Set<String> expanded=new HashSet<>();
    private Screen parent;
    private double w,h,factor,workspaceX,workspaceWidth,panelWidth,mx,my;
    private final ModuleSearch search=new ModuleSearch(this::openSearchResult);
    private final PlayerList playerList;
    private String binding,pendingReveal;
    private Panel dragging;
    private double dragX,dragY;
    private Hit sliding;
    private String toast="";
    private long toastUntil;
    private record Hit(double x,double y,double w,double h,Runnable left,Runnable right,DoubleConsumer slide) {
        boolean contains(double mx,double my){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    }
    private static final class Panel {
        final int index;double x,y,bodyHeight,scroll,maxScroll;boolean collapsed;
        Panel(int index){this.index=index;}
    }
    public XingScreen(XingClient app){super(Text.literal("Xingpaozhieniaohaolongbao Client"));this.app=app;playerList=new PlayerList(app,this);for(int i=0;i<6;i++)panels.add(new Panel(i));}
    public PlayerList playerList(){return playerList;}
    public void parent(Screen parent){this.parent=parent;}
    public boolean searchOpen(){return search.isOpen();}
    public int searchResultCount(){return search.resultCount();}
    public boolean settingsExpanded(String key){return expanded.contains(key);}
    public String searchQuery(){return search.query();}
    public double renderFactor(){return factor;}
    public MascotMotion mascotMotion(){return mascot;}
    @Override protected void init(){fit();}
    @Override public boolean shouldPause(){return false;}
    @Override public void renderBackground(DrawContext ctx,int x,int y,float delta){}
    @Override public void close(){client.setScreen(parent);}
    @Override public void removed(){app.store.flush();blossoms.pause();mascot.release();mascotFrame=0;dragging=null;sliding=null;binding=null;search.reset();playerList.reset();}
    private void fit(){
        double pixels=client.getWindow().getFramebufferWidth(),pixelHeight=client.getWindow().getFramebufferHeight();
        double fit=Math.min(1,pixels/1000.0);
        factor=fit/client.getWindow().getScaleFactor();w=pixels/fit;h=pixelHeight/fit;
        mascot.layout(w,h);
        workspaceWidth=Math.min(1240,w-(w<=1000?24:40));workspaceX=(w-workspaceWidth)/2;
        panelWidth=w<=1000?154:Math.floor((workspaceWidth-40)/6);
        for(var p:panels){var pos=app.settings.positions.get(MenuData.CATEGORIES.get(p.index).name());p.x=pos==null?p.index*(panelWidth+8):Math.clamp(pos.x,0,Math.max(0,workspaceWidth-panelWidth));p.y=pos==null?0:Math.clamp(pos.y,0,Math.max(0,h-90));}
    }
    private static boolean inside(double mx,double my,double x,double y,double width,double height){return mx>=x&&mx<x+width&&my>=y&&my<y+height;}
    private boolean hover(double x,double y,double width,double height){return inside(mx,my,x,y,width,height);}
    private void hit(double x,double y,double width,double height,Runnable left){hit(x,y,width,height,left,null);}
    private void hit(double x,double y,double width,double height,Runnable left,Runnable right){hits.add(new Hit(x,y,width,height,left,right,null));}
    private void text(DrawContext ctx,String value,double x,double centerY,int size,int color){JostFont.draw(ctx,value,x,centerY,size,color);}
    private void changed(){app.changed();}
    private void notify(String value){toast=value;toastUntil=System.currentTimeMillis()+1500;}
    private void toggle(String key){var entry=app.settings.modules.get(key);entry.enabled=!entry.enabled;changed();}
    private void expand(String key){if(!expanded.add(key))expanded.remove(key);}
    private void openSearchResult(ModuleSearch.Result result){
        String key=result.key();expanded.add(key);pendingReveal=key;
        var panel=panels.stream().filter(p->p.index==result.category()).findFirst().orElseThrow();
        panel.collapsed=false;
        // A category dragged to the bottom must have room to reveal its settings.
        if(h-(52+panel.y)<240){
            panel.y=Math.max(0,h-292);
            app.settings.positions.put(MenuData.CATEGORIES.get(panel.index).name(),new ClientSettings.Position(panel.x,panel.y));changed();
        }
        panels.remove(panel);panels.add(panel);
    }

    @Override public void render(DrawContext ctx,int mouseX,int mouseY,float delta){
        fit();mx=mouseX/factor;my=mouseY/factor;hits.clear();
        long now=System.nanoTime();
        if(app.settings.mascot&&client.isWindowFocused()){
            if(mascotFrame!=0)mascot.advance(Math.min((now-mascotFrame)/1_000_000_000.0,.1));
            mascotFrame=now;
        }else{mascot.release();mascotFrame=0;}
        if(client.world==null)ctx.fill(0,0,width,height,0xff000000);
        else {
            // Blur the already-rendered world before drawing any client UI or blossoms.
            ctx.draw();
            client.gameRenderer.renderBlur();
        }
        ctx.getMatrices().push();ctx.getMatrices().scale((float)factor,(float)factor,1);
        boolean lifted=mascot.held()||mascot.airborne();
        if(!lifted)renderMascot(ctx);
        String brand="Xingpaozhieniaohaolongbao ";
        text(ctx,brand,6,15,12,0xff000000|app.settings.pink);text(ctx,"Client",6+JostFont.width(brand,12),15,12,0xff000000|app.settings.blue);
        ctx.enableScissor(0,32,(int)w,(int)h);
        for(var p:panels)renderPanel(ctx,p);
        ctx.disableScissor();
        if(lifted)renderMascot(ctx);
        if(app.settings.blossoms&&client.isWindowFocused())blossoms.draw(ctx,w,h);else blossoms.pause();
        if(System.currentTimeMillis()<toastUntil){double tw=JostFont.width(toast,11)+24;GuiDraw.rect(ctx,(w-tw)/2,h-42,tw,25,0xff18131f);GuiDraw.border(ctx,(w-tw)/2,h-42,tw,25,0xff594551);JostFont.center(ctx,toast,w/2,h-30,11,0xff000000|app.settings.pink);}
        search.draw(ctx,w,h,app.settings.pink,app.settings.blue,mx,my);
        playerList.draw(ctx,w,h,app.settings.pink,app.settings.blue,mx,my,delta);
        ctx.getMatrices().pop();
    }
    private void renderMascot(DrawContext ctx){
        if(!app.settings.mascot)return;
        GuiDraw.texture(ctx,GuiDraw.texture("mascot"),(float)mascot.x(),(float)mascot.y(),(float)mascot.width(),(float)mascot.height(),0xffffffff);
    }
    private boolean grabMascot(int button){
        if(button!=0||!app.settings.mascot||!mascot.contains(mx,my))return false;
        if(!mascotMaskLoaded){
            mascotMaskLoaded=true;
            try(var stream=client.getResourceManager().getResource(GuiDraw.texture("mascot")).orElseThrow().getInputStream();
                var image=net.minecraft.client.texture.NativeImage.read(stream)){
                mascotImageWidth=image.getWidth();mascotImageHeight=image.getHeight();mascotPixels=new boolean[mascotImageWidth*mascotImageHeight];
                for(int y=0;y<mascotImageHeight;y++)for(int x=0;x<mascotImageWidth;x++)mascotPixels[y*mascotImageWidth+x]=(image.getColorArgb(x,y)>>>24)>24;
            }catch(Exception e){org.slf4j.LoggerFactory.getLogger("xingclient").warn("Could not load mascot click mask",e);}
        }
        if(mascotPixels!=null){
            int x=Math.clamp((int)((mx-mascot.x())/mascot.width()*mascotImageWidth),0,mascotImageWidth-1);
            int y=Math.clamp((int)((my-mascot.y())/mascot.height()*mascotImageHeight),0,mascotImageHeight-1);
            if(!mascotPixels[y*mascotImageWidth+x])return false;
        }
        mascot.grab(mx,my);return true;
    }
    private void renderPanel(DrawContext ctx,Panel p){
        var category=MenuData.CATEGORIES.get(p.index);double x=workspaceX+p.x,y=52+p.y;
        int color=app.settings.accent(p.index);int fontSize=w<=1000?15:16;
        List<String> names=category.modules();
        double content=names.isEmpty()?42:2;for(String name:names)content+=26+(expanded.contains(MenuData.key(category,name))?134:0);
        p.bodyHeight=p.collapsed?0:Math.max(0,Math.min(content,Math.min(h-102,h-y-30)));
        p.maxScroll=Math.max(0,content-p.bodyHeight);p.scroll=Math.clamp(p.scroll,0,p.maxScroll);
        if(pendingReveal!=null&&pendingReveal.startsWith(category.name()+".")){
            double offset=1;
            for(String name:names){
                String key=MenuData.key(category,name);
                if(key.equals(pendingReveal)){
                    if(offset<p.scroll)p.scroll=offset;
                    if(offset+160>p.scroll+p.bodyHeight)p.scroll=offset+160-p.bodyHeight;
                    p.scroll=Math.clamp(p.scroll,0,p.maxScroll);pendingReveal=null;break;
                }
                offset+=26+(expanded.contains(key)?134:0);
            }
        }
        int bg=(app.settings.opacity*255/100)<<24|0x1e1b21;
        GuiDraw.rect(ctx,x,y,panelWidth,28+p.bodyHeight,bg);GuiDraw.border(ctx,x,y,panelWidth,28+p.bodyHeight,color);
        GuiDraw.rect(ctx,x,y,panelWidth,27,color);JostFont.center(ctx,category.name(),x+panelWidth/2,y+14,w<=1000?14:15,0xff25202b);
        if(hover(x,y,panelWidth,27))text(ctx,p.collapsed?"+":"-",x+panelWidth-14,y+14,13,0xff352c45);
        hit(x,y,panelWidth-23,27,()->{dragging=p;dragX=mx-x;dragY=my-y;},()->p.collapsed=!p.collapsed);
        hit(x+panelWidth-23,y,23,27,()->p.collapsed=!p.collapsed,()->p.collapsed=!p.collapsed);
        if(p.collapsed||p.bodyHeight==0)return;
        double top=y+28,bottom=top+p.bodyHeight;
        ctx.enableScissor((int)x+1,(int)top,(int)(x+panelWidth-1),(int)bottom);
        double row=top+1-p.scroll;
        if(names.isEmpty())text(ctx,"no matching modules",x+6,row+20,12,0xff9d8aa7);
        for(String name:names){
            String key=MenuData.key(category,name);var entry=app.settings.modules.get(key);
            if(row+26>top&&row<bottom){
                if(entry.enabled)GuiDraw.rect(ctx,x+1,row,panelWidth-2,26,color);else if(hover(x+1,row,panelWidth-2,26))GuiDraw.rect(ctx,x+1,row,panelWidth-2,26,0x0dffffff);
                text(ctx,name,x+6,row+13,fontSize,entry.enabled?DARK:TEXT);
                if(hover(x+1,row,panelWidth-2,26)||expanded.contains(key))text(ctx,expanded.contains(key)?"v":">",x+panelWidth-16,row+13,13,entry.enabled?0xff41334d:0xff938799);
                double clippedY=Math.max(row,top),clippedH=Math.min(row+26,bottom)-clippedY;
                hit(x+1,clippedY,panelWidth-24,clippedH,()->toggle(key),()->expand(key));
                hit(x+panelWidth-23,clippedY,22,clippedH,()->expand(key),()->expand(key));
            }
            row+=26;
            if(expanded.contains(key)){
                int firstHit=hits.size();
                renderSettings(ctx,key,x+1,row,panelWidth-2,color);
                // Clipped settings must not retain clickable regions outside their panel.
                for(int i=firstHit;i<hits.size();i++){var a=hits.get(i);double cy=Math.max(a.y,top),ch=Math.max(0,Math.min(a.y+a.h,bottom)-cy);hits.set(i,new Hit(a.x,cy,a.w,ch,a.left,a.right,a.slide));}
                row+=134;
            }
        }
        if(p.maxScroll>0){double thumb=Math.max(18,p.bodyHeight*p.bodyHeight/content);GuiDraw.rect(ctx,x+panelWidth-3,top+(p.bodyHeight-thumb)*p.scroll/p.maxScroll,2,thumb,color);}
        ctx.disableScissor();
    }
    private void renderSettings(DrawContext ctx,String key,double x,double y,double width,int color){
        var e=app.settings.modules.get(key);GuiDraw.rect(ctx,x,y,width,134,0xff111015);GuiDraw.rect(ctx,x,y+133,width,1,0x1affffff);
        text(ctx,"mode",x+8,y+20,11,0xffaaa0b2);
        GuiDraw.rect(ctx,x+width-87,y+9,79,23,0xff24212b);GuiDraw.border(ctx,x+width-87,y+9,79,23,0xff42394f);
        text(ctx,new String[]{"normal","strict","custom"}[e.mode],x+width-81,y+20,11,0xffd3c3dd);text(ctx,"v",x+width-20,y+20,10,0xffd3c3dd);
        hit(x+width-87,y+9,79,23,()->{e.mode=(e.mode+1)%3;changed();});
        text(ctx,"intensity",x+8,y+48,11,0xffaaa0b2);text(ctx,e.intensity+"%",x+width-35,y+48,11,color);
        slider(ctx,x+8,y+65,width-16,e.intensity/100.0,color,v->{e.intensity=(int)Math.round(v*100);changed();});
        checkbox(ctx,"show in hud",x+8,y+89,width-16,e.visible,color,()->{e.visible=!e.visible;changed();});
        text(ctx,"keybind",x+8,y+115,11,0xffaaa0b2);
        String label=key.equals(binding)?"press key":bindName(e);double bw=Math.max(40,JostFont.width(label,10)+12);
        GuiDraw.rect(ctx,x+width-8-bw,y+104,bw,23,0xff28232f);GuiDraw.border(ctx,x+width-8-bw,y+104,bw,23,0xff443c52);
        text(ctx,label,x+width-2-bw,y+115,10,color);
        hit(x+width-8-bw,y+104,bw,23,()->{binding=key;notify("Press a key or mouse button - Esc cancels - Delete clears");});
    }
    private static String bindName(ClientSettings.Entry e){
        if(e.key<0)return "none";if(e.mouse)return "mouse"+(e.key+1);
        String name=GLFW.glfwGetKeyName(e.key,0);return name==null?switch(e.key){case GLFW.GLFW_KEY_SPACE->"space";case GLFW.GLFW_KEY_LEFT_SHIFT->"lshift";case GLFW.GLFW_KEY_LEFT_CONTROL->"lctrl";case GLFW.GLFW_KEY_LEFT_ALT->"lalt";case GLFW.GLFW_KEY_TAB->"tab";default->"key "+e.key;}:name.toLowerCase(Locale.ROOT);
    }
    private void slider(DrawContext ctx,double x,double y,double width,double value,int color,DoubleConsumer update){
        GuiDraw.rect(ctx,x,y-1,width,3,0xff4d4553);GuiDraw.rect(ctx,x,y-1,width*value,3,color);
        GuiDraw.rect(ctx,x+value*width-3,y-5,6,11,color);
        hits.add(new Hit(x,y-9,width,18,null,null,update));
    }
    private void checkbox(DrawContext ctx,String label,double x,double y,double width,boolean checked,int color,Runnable action){
        text(ctx,label,x,y,12,0xffc1b3c8);double bx=x+width-12;
        GuiDraw.rect(ctx,bx,y-6,12,12,checked?color:0xff29232f);GuiDraw.border(ctx,bx,y-6,12,12,checked?color:0xff776782);
        if(checked){GuiDraw.rect(ctx,bx+3,y-1,2,4,DARK);GuiDraw.rect(ctx,bx+5,y-3,2,4,DARK);GuiDraw.rect(ctx,bx+7,y-5,2,4,DARK);}
        hit(x,y-11,width,22,action);
    }
    @Override public boolean mouseClicked(double mouseX,double mouseY,int button){
        mx=mouseX/factor;my=mouseY/factor;
        if(playerList.blocksInput()){playerList.click(mx,my,button);return true;}
        if(search.blocksInput()){search.click(mx,my,button);return true;}
        if(binding!=null){var e=app.settings.modules.get(binding);e.mouse=true;e.key=button;binding=null;changed();return true;}
        if((mascot.held()||mascot.airborne())&&grabMascot(button))return true;
        for(int i=hits.size()-1;i>=0;i--){var hit=hits.get(i);if(!hit.contains(mx,my))continue;
            if(button==0&&hit.slide!=null){sliding=hit;hit.slide.accept(Math.clamp((mx-hit.x)/hit.w,0,1));return true;}
            Runnable action=button==1?hit.right:button==0?hit.left:null;
            if(action!=null){action.run();if(dragging!=null){panels.remove(dragging);panels.add(dragging);}return true;}
            if(button==0||button==1)return true;
        }
        for(var panel:panels)if(inside(mx,my,workspaceX+panel.x,52+panel.y,panelWidth,28+panel.bodyHeight))return true;
        grabMascot(button);return true;
    }
    @Override public boolean mouseDragged(double mouseX,double mouseY,int button,double dx,double dy){
        mx=mouseX/factor;my=mouseY/factor;
        if(search.blocksInput()||playerList.blocksInput())return true;
        if(mascot.held()&&button==0){mascot.drag(mx,my);return true;}
        if(sliding!=null){sliding.slide.accept(Math.clamp((mx-sliding.x)/sliding.w,0,1));return true;}
        if(dragging!=null){double x=Math.clamp(mx-dragX-workspaceX,0,Math.max(0,workspaceWidth-panelWidth));double y=Math.clamp(my-dragY-52,0,Math.max(0,h-90));app.settings.positions.put(MenuData.CATEGORIES.get(dragging.index).name(),new ClientSettings.Position(x,y));changed();return true;}
        return false;
    }
    @Override public boolean mouseReleased(double x,double y,int button){if(button==0){mascot.drag(x/factor,y/factor);mascot.release();dragging=null;sliding=null;}return true;}
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double horizontal,double vertical){
        mx=mouseX/factor;my=mouseY/factor;
        if(playerList.blocksInput()){playerList.scroll(vertical);return true;}
        if(search.blocksInput()){search.scroll(vertical);return true;}
        for(int i=panels.size()-1;i>=0;i--){var p=panels.get(i);if(inside(mx,my,workspaceX+p.x,52+p.y,panelWidth,28+p.bodyHeight)){p.scroll=Math.clamp(p.scroll-vertical*45,0,p.maxScroll);return true;}}
        return false;
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_RIGHT_SHIFT)return true;
        if(playerList.blocksInput()){playerList.key(key);return true;}
        if(search.blocksInput()){
            String clipboard=(modifiers&GLFW.GLFW_MOD_CONTROL)!=0&&key==GLFW.GLFW_KEY_V?client.keyboard.getClipboard():"";
            search.key(key,modifiers,clipboard);return true;
        }
        if((modifiers&GLFW.GLFW_MOD_CONTROL)!=0&&key==GLFW.GLFW_KEY_F){
            binding=null;dragging=null;sliding=null;mascot.release();search.open();return true;
        }
        if(binding!=null){if(key!=GLFW.GLFW_KEY_ESCAPE){var e=app.settings.modules.get(binding);e.key=key==GLFW.GLFW_KEY_BACKSPACE||key==GLFW.GLFW_KEY_DELETE?-1:key;e.mouse=false;changed();}binding=null;return true;}
        if(key==GLFW.GLFW_KEY_P&&modifiers==0){dragging=null;sliding=null;mascot.release();playerList.open();return true;}
        if(key==GLFW.GLFW_KEY_ESCAPE){close();return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean charTyped(char chr,int modifiers){
        if(playerList.blocksInput())return true;
        if(search.blocksInput()){search.type(chr,modifiers);return true;}
        return false;
    }
}
