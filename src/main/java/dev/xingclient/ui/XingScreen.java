package dev.xingclient.ui;

import dev.xingclient.*;
import dev.xingclient.module.NativeAutoCrystalModule;
import dev.xingclient.module.MaceKillModule;
import java.util.*;
import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Native pixel-space reproduction of the approved HTML, independent of game GUI scale. */
public final class XingScreen extends Screen {
    private static final int TEXT=0xffc5c3c6, DARK=0xff292333;
    private static final int MODULE_DETAILS_HEIGHT=58;
    private static final int AUTO_CRYSTAL_SETTINGS_HEIGHT=228;
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
    private final ModuleSearch search;
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
    public XingScreen(XingClient app){super(Text.literal("Xingpaozhieniaohaolongbao Client"));this.app=app;search=new ModuleSearch(app.menuData,this::openSearchResult);playerList=new PlayerList(app,this);for(int i=0;i<app.menuData.categories().size();i++)panels.add(new Panel(i));}
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
        int categoryCount=Math.max(1,app.menuData.categories().size());
        panelWidth=w<=1000?154:Math.min(200,Math.floor((workspaceWidth-8*(categoryCount-1))/categoryCount));
        for(var p:panels){var pos=app.settings.positions.get(app.menuData.categories().get(p.index).name());p.x=pos==null?p.index*(panelWidth+8):Math.clamp(pos.x,0,Math.max(0,workspaceWidth-panelWidth));p.y=pos==null?0:Math.clamp(pos.y,0,Math.max(0,h-90));}
    }
    private static boolean inside(double mx,double my,double x,double y,double width,double height){return mx>=x&&mx<x+width&&my>=y&&my<y+height;}
    private boolean hover(double x,double y,double width,double height){return inside(mx,my,x,y,width,height);}
    private void hit(double x,double y,double width,double height,Runnable left){hit(x,y,width,height,left,null);}
    private void hit(double x,double y,double width,double height,Runnable left,Runnable right){hits.add(new Hit(x,y,width,height,left,right,null));}
    private void text(DrawContext ctx,String value,double x,double centerY,int size,int color){JostFont.draw(ctx,value,x,centerY,size,color);}
    private void changed(){app.changed();}
    private void notify(String value){toast=value;toastUntil=System.currentTimeMillis()+1500;}
    private void toggle(String key){var module=app.modules.get(key);try{module.toggle();}catch(LinkageError error){notify("Native module unavailable; rebuild the xing DLL");org.slf4j.LoggerFactory.getLogger("xingclient").warn("Could not enable module "+module.name(),error);return;}app.settings.modules.get(key).enabled=module.isEnabled();changed();}
    private void expand(String key){if(!expanded.add(key))expanded.remove(key);}
    private void openSearchResult(ModuleSearch.Result result){
        String key=result.key();expanded.add(key);pendingReveal=key;
        var panel=panels.stream().filter(p->p.index==result.category()).findFirst().orElseThrow();
        panel.collapsed=false;
        // A category dragged to the bottom must have room to reveal its settings.
        if(h-(52+panel.y)<240){
            panel.y=Math.max(0,h-292);
            app.settings.positions.put(app.menuData.categories().get(panel.index).name(),new ClientSettings.Position(panel.x,panel.y));changed();
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
        var category=app.menuData.categories().get(p.index);double x=workspaceX+p.x,y=52+p.y;
        int color=app.settings.accent(p.index);int fontSize=w<=1000?15:16;
        var registeredModules=category.modules();
        double content=registeredModules.isEmpty()?42:2;
        for(var module:registeredModules)content+=26+(expanded.contains(module.id())?detailsHeight(module):0);
        p.bodyHeight=p.collapsed?0:Math.max(0,Math.min(content,Math.min(h-102,h-y-30)));
        p.maxScroll=Math.max(0,content-p.bodyHeight);p.scroll=Math.clamp(p.scroll,0,p.maxScroll);
        if(pendingReveal!=null){
            double offset=1;
            for(var module:registeredModules){
                String key=module.id();
                if(key.equals(pendingReveal)){
                    if(offset<p.scroll)p.scroll=offset;
                    if(offset+26+detailsHeight(module)>p.scroll+p.bodyHeight)p.scroll=offset+26+detailsHeight(module)-p.bodyHeight;
                    p.scroll=Math.clamp(p.scroll,0,p.maxScroll);pendingReveal=null;break;
                }
                offset+=26+(expanded.contains(key)?detailsHeight(module):0);
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
        if(registeredModules.isEmpty())text(ctx,"no registered modules",x+6,row+20,12,0xff9d8aa7);
        for(var module:registeredModules){
            String key=module.id();var entry=app.settings.modules.get(key);
            if(row+26>top&&row<bottom){
                if(entry.enabled)GuiDraw.rect(ctx,x+1,row,panelWidth-2,26,color);else if(hover(x+1,row,panelWidth-2,26))GuiDraw.rect(ctx,x+1,row,panelWidth-2,26,0x0dffffff);
                text(ctx,module.name(),x+6,row+13,fontSize,entry.enabled?DARK:TEXT);
                if(hover(x+1,row,panelWidth-2,26)||expanded.contains(key))text(ctx,expanded.contains(key)?"v":">",x+panelWidth-16,row+13,13,entry.enabled?0xff41334d:0xff938799);
                double clippedY=Math.max(row,top),clippedH=Math.min(row+26,bottom)-clippedY;
                hit(x+1,clippedY,panelWidth-24,clippedH,()->toggle(key),()->expand(key));
                hit(x+panelWidth-23,clippedY,22,clippedH,()->expand(key),()->expand(key));
            }
            row+=26;
            if(expanded.contains(key)){
                int firstHit=hits.size();
                renderSettings(ctx,module,x+1,row,panelWidth-2,color);
                // Clipped settings must not retain clickable regions outside their panel.
                for(int i=firstHit;i<hits.size();i++){var a=hits.get(i);double cy=Math.max(a.y,top),ch=Math.max(0,Math.min(a.y+a.h,bottom)-cy);hits.set(i,new Hit(a.x,cy,a.w,ch,a.left,a.right,a.slide));}
                row+=detailsHeight(module);
            }
        }
        if(p.maxScroll>0){double thumb=Math.max(18,p.bodyHeight*p.bodyHeight/content);GuiDraw.rect(ctx,x+panelWidth-3,top+(p.bodyHeight-thumb)*p.scroll/p.maxScroll,2,thumb,color);}
        ctx.disableScissor();
    }
    private void renderSettings(DrawContext ctx,dev.xingclient.module.Module module,double x,double y,double width,int color){
        String key=module.id();var entry=app.settings.modules.get(key);
        int height=detailsHeight(module);
        GuiDraw.rect(ctx,x,y,width,height,0xff111015);GuiDraw.rect(ctx,x,y+height-1,width,1,0x1affffff);
        text(ctx,module.status(),x+8,y+15,11,color);
        text(ctx,"keybind",x+8,y+42,11,0xffaaa0b2);
        String label=key.equals(binding)?"press key":bindName(entry);double bw=Math.max(40,JostFont.width(label,10)+12);
        GuiDraw.rect(ctx,x+width-8-bw,y+30,bw,23,0xff28232f);GuiDraw.border(ctx,x+width-8-bw,y+30,bw,23,0xff443c52);
        text(ctx,label,x+width-2-bw,y+41,10,color);
        hit(x+width-8-bw,y+30,bw,23,()->{binding=key;notify("Press a key or mouse button - Esc cancels - Delete clears");});
        if(module instanceof NativeAutoCrystalModule)renderAutoCrystalSettings(ctx,x,y+62,width,color);
        if(module instanceof MaceKillModule)renderMaceSettings(ctx,x,y+62,width,color);
    }
    private static int detailsHeight(dev.xingclient.module.Module module){
        return module instanceof MaceKillModule?270:module instanceof NativeAutoCrystalModule?AUTO_CRYSTAL_SETTINGS_HEIGHT:MODULE_DETAILS_HEIGHT;
    }
    private void renderMaceSettings(DrawContext ctx,double x,double y,double width,int color){
        var s=app.settings.maceKill;
        text(ctx,"Experimental / one attempt",x+8,y+7,10,0xffaaa0b2);y+=22;
        double lx=x+8,sx=x+90,sw=Math.max(20,width-126);
        renderValueSetting(ctx,"Charges",s.charges,1,8,1,x,y,lx,sx,sw,color,v->s.charges=(int)v);y+=22;
        renderValueSetting(ctx,"Shot ticks",s.chargeIntervalTicks,10,30,1,x,y,lx,sx,sw,color,v->s.chargeIntervalTicks=(int)v);y+=22;
        renderValueSetting(ctx,"Packet ticks",s.heartbeatTicks,1,20,1,x,y,lx,sx,sw,color,v->s.heartbeatTicks=(int)v);y+=22;
        renderValueSetting(ctx,"Hold height",s.holdHeight,.2,3,.1,x,y,lx,sx,sw,color,v->s.holdHeight=v);y+=22;
        renderValueSetting(ctx,"Min fall",s.minimumFall,1.6,20,.2,x,y,lx,sx,sw,color,v->s.minimumFall=v);y+=22;
        renderValueSetting(ctx,"Hold seconds",s.holdSeconds,3,30,1,x,y,lx,sx,sw,color,v->s.holdSeconds=(int)v);y+=22;
        renderValueSetting(ctx,"Attack range",s.attackRange,1,3,.1,x,y,lx,sx,sw,color,v->s.attackRange=v);y+=25;
        checkbox(ctx,"Auto attack",x+8,y+5,width-16,s.autoAttack,color,()->{s.autoAttack=!s.autoAttack;app.changed();});
    }
    private void renderAutoCrystalSettings(DrawContext ctx,double x,double y,double width,int color){
        ClientSettings.AutoCrystalSettings settings=app.settings.autoCrystal;
        double labelX=x+8,sliderX=x+82,sliderWidth=Math.max(24,width-112);
        renderValueSetting(ctx,"Min damage",settings.minimumDamage,0,20,1,x,y,labelX,sliderX,sliderWidth,color,
                value->settings.minimumDamage=value);
        y+=22;
        renderValueSetting(ctx,"Low HP damage",settings.lowHealthMinimumDamage,0,20,1,x,y,labelX,sliderX,sliderWidth,color,
                value->settings.lowHealthMinimumDamage=value);
        y+=22;
        renderValueSetting(ctx,"Low HP at",settings.lowHealthThreshold,1,20,1,x,y,labelX,sliderX,sliderWidth,color,
                value->settings.lowHealthThreshold=value);
        y+=22;
        renderValueSetting(ctx,"Max self",settings.maximumSelfDamage,0,36,1,x,y,labelX,sliderX,sliderWidth,color,
                value->settings.maximumSelfDamage=value);
        y+=22;
        renderValueSetting(ctx,"Break range",settings.breakRange,1,7,0.25,x,y,labelX,sliderX,sliderWidth,color,
                value->settings.breakRange=value);
        y+=24;
        checkbox(ctx,"Break existing",x+8,y+7,width-16,settings.breakExisting,color,()->{
            settings.breakExisting=!settings.breakExisting;app.changed();
        });
        y+=25;
        checkbox(ctx,"Same tick break/place",x+8,y+7,width-16,settings.sameTickBreakPlace,color,()->{
            settings.sameTickBreakPlace=!settings.sameTickBreakPlace;app.changed();
        });
    }
    private void renderValueSetting(DrawContext ctx,String label,double value,double minimum,double maximum,double step,
                                    double panelX,double y,double labelX,double sliderX,double sliderWidth,int color,
                                    DoubleConsumer update){
        text(ctx,label,labelX,y+8,10,0xffc1b3c8);
        String valueText=String.format(Locale.ROOT,"%.2f",value);
        text(ctx,valueText,panelX+panelWidth-8-JostFont.width(valueText,10),y+8,10,0xffaaa0b2);
        double normalized=(value-minimum)/(maximum-minimum);
        slider(ctx,sliderX,y+8,sliderWidth,Math.clamp(normalized,0,1),color,fraction->{
            double next=minimum+Math.round((minimum+fraction*(maximum-minimum)-minimum)/step)*step;
            update.accept(Math.clamp(next,minimum,maximum));app.changed();
        });
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
        if(dragging!=null){double x=Math.clamp(mx-dragX-workspaceX,0,Math.max(0,workspaceWidth-panelWidth));double y=Math.clamp(my-dragY-52,0,Math.max(0,h-90));app.settings.positions.put(app.menuData.categories().get(dragging.index).name(),new ClientSettings.Position(x,y));changed();return true;}
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
