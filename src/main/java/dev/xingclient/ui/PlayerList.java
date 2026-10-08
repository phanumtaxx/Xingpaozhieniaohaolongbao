package dev.xingclient.ui;

import dev.xingclient.*;
import java.util.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.glfw.GLFW;

/** Compact loaded-player browser and live details, scoped to the ClickGUI. */
public final class PlayerList {
    private final XingClient app;
    private final XingScreen parent;
    private boolean open;
    private double progress,x,y,width,height;
    private long frame,refresh,lastClick;
    private UUID selected,lastClicked;
    private String selectedName="";
    private int firstRow,visibleRows=8,columns=3;
    private Object world;
    private List<PlayerEntity> players=List.of();
    private final List<Button> buttons=new ArrayList<>();
    private record Button(double x,double y,double w,double h,Runnable action){boolean hit(double px,double py){return px>=x&&px<x+w&&py>=y&&py<y+h;}}
    public PlayerList(XingClient app,XingScreen parent){this.app=app;this.parent=parent;}
    public boolean isOpen(){return open;}
    public boolean blocksInput(){return open||progress>.001;}
    public int count(){refresh();return players.size();}
    public UUID selected(){return selected;}
    public int firstRow(){return firstRow;}
    public void open(){open=true;selected=null;firstRow=0;lastClicked=null;world=MinecraftClient.getInstance().world;refresh=0;frame=System.nanoTime();refresh();}
    public void inspect(PlayerEntity player){open();selected=player.getUuid();selectedName=player.getGameProfile().getName();}
    public void reset(){open=false;progress=0;frame=0;selected=null;players=List.of();buttons.clear();}
    public void close(){open=false;lastClicked=null;}
    private void refresh(){
        var mc=MinecraftClient.getInstance();long now=System.currentTimeMillis();
        if(world!=mc.world){world=mc.world;selected=null;firstRow=0;refresh=0;}
        if(now-refresh<200)return;refresh=now;
        double radius=mc.options.getViewDistance().getValue()*16.0;
        players=mc.world==null||mc.player==null?List.of():mc.world.getPlayers().stream()
            .filter(p->p!=mc.player&&!p.isRemoved()&&p.isAlive()&&p.squaredDistanceTo(mc.player)<=radius*radius)
            .sorted(Comparator.<PlayerEntity>comparingDouble(p->p.squaredDistanceTo(mc.player)).thenComparing(PlayerEntity::getUuid)).map(p->(PlayerEntity)p).toList();
    }
    private int alpha(int color){return ((int)((color>>>24)*progress)<<24)|(color&0xffffff);}
    private void text(DrawContext ctx,String text,double x,double y,int size,int color){JostFont.draw(ctx,text,x,y,size,alpha(color));}
    private String distance(PlayerEntity p){var mc=MinecraftClient.getInstance();return mc.player==null?"":String.format(Locale.ROOT,"%.1f m",p.distanceTo(mc.player));}
    public void draw(DrawContext ctx,double sw,double sh,int pink,int blue,double mx,double my,float delta){
        long now=System.nanoTime();double dt=frame==0?0:Math.min(.05,(now-frame)/1e9);frame=now;
        progress=Math.clamp(progress+(open?1:-1)*dt/.18,0,1);buttons.clear();if(progress==0)return;
        refresh();double ease=1-Math.pow(1-progress,3);
        width=Math.min(660,sw-48);height=Math.min(410,sh-90);x=(sw-width)/2;y=(sh-height)/2-16*(1-ease);
        GuiDraw.rect(ctx,0,0,sw,sh,(int)(185*ease)<<24);
        GuiDraw.rect(ctx,x,y,width,height,alpha(0xff18151e));GuiDraw.border(ctx,x,y,width,height,alpha(0xff554557));
        GuiDraw.rect(ctx,x,y,width/2,2,alpha(0xff000000|pink));GuiDraw.rect(ctx,x+width/2,y,width/2,2,alpha(0xff000000|blue));
        text(ctx,"Player List",x+16,y+23,16,0xffeee5f1);
        button(ctx,"x",x+width-36,y+9,24,26,true,this::close,mx,my,pink);
        if(selected!=null)button(ctx,"Back",x+width-100,y+9,56,26,true,()->{selected=null;lastClicked=null;},mx,my,blue);
        GuiDraw.rect(ctx,x+12,y+44,width-24,1,alpha(0xff403548));
        if(selected==null)drawList(ctx,mx,my,pink,blue);else drawDetails(ctx,mx,my,pink,blue,delta);
    }
    private void drawList(DrawContext ctx,double mx,double my,int pink,int blue){
        columns=width>=590?3:2;visibleRows=Math.max(1,(int)((height-64)/40));
        int totalRows=(players.size()+columns-1)/columns;firstRow=Math.clamp(firstRow,0,Math.max(0,totalRows-visibleRows));
        double cardW=(width-32-(columns-1)*6)/columns;
        if(players.isEmpty())text(ctx,MinecraftClient.getInstance().world==null?"Join a world to see nearby players":"No nearby players",x+18,y+76,14,0xffa79bab);
        for(int i=firstRow*columns;i<Math.min(players.size(),(firstRow+visibleRows)*columns);i++){
            var p=players.get(i);int cell=i-firstRow*columns;double bx=x+16+(cell%columns)*(cardW+6),by=y+54+(cell/columns)*40;
            boolean hover=mx>=bx&&mx<bx+cardW&&my>=by&&my<by+35;
            GuiDraw.rect(ctx,bx,by,cardW,35,alpha(hover?0xff3a2d43:0xff25202d));
            GuiDraw.rect(ctx,bx,by,2,35,alpha(0xff000000|(hover?pink:blue)));
            text(ctx,p.getGameProfile().getName(),bx+9,by+11,13,0xffeadfeb);text(ctx,distance(p),bx+9,by+26,10,0xff000000|blue);
            buttons.add(new Button(bx,by,cardW,35,()->{
                long now=System.currentTimeMillis();if(p.getUuid().equals(lastClicked)&&now-lastClick<=400){selected=p.getUuid();selectedName=p.getGameProfile().getName();lastClicked=null;}else{lastClicked=p.getUuid();lastClick=now;}
            }));
        }
        if(totalRows>visibleRows){double track=visibleRows*40,thumb=Math.max(16,track*visibleRows/totalRows);GuiDraw.rect(ctx,x+width-6,y+54+(track-thumb)*firstRow/(totalRows-visibleRows),2,thumb,alpha(0xff000000|blue));}
    }
    private void drawDetails(DrawContext ctx,double mx,double my,int pink,int blue,float delta){
        PlayerEntity player=players.stream().filter(p->p.getUuid().equals(selected)).findFirst().orElse(null);
        double previewW=width*.4,previewY=y+57,previewH=height-72;
        text(ctx,selectedName,x+previewW+24,y+70,16,0xffeee5f1);
        text(ctx,player==null?"No longer in range":distance(player),x+previewW+24,y+91,12,0xff000000|blue);
        if(player!=null&&progress>.95)PlayerPreview.draw(ctx,player,x+12,previewY,previewW-12,previewH,delta);
        else if(player==null)text(ctx,"Player unavailable",x+26,y+height/2,13,0xff9c8ba5);
        double bx=x+previewW+24,bw=Math.min(220,width-previewW-44),by=y+116;
        for(String action:List.of("Follow","Kill","Target","Mace (Once)","Mace (Loop)","Spectate")){
            boolean target=action.equals("Target"),spectate=action.equals("Spectate"),enabled=player!=null&&(target||spectate);
            var saved=HazelTarget.get(app);String label=target&&saved!=null&&saved.uuid.equals(selected.toString())?"Target saved":action;
            button(ctx,label,bx,by,bw,30,enabled,()->{if(target)HazelTarget.save(app,player);else if(spectate)MinecraftClient.getInstance().setScreen(new PlayerSpectateScreen(parent,player));},mx,my,target?pink:blue);by+=36;
        }
        text(ctx,"Combat actions await autopilot",bx,by+5,11,0xff8e7f98);
    }
    private void button(DrawContext ctx,String label,double bx,double by,double bw,double bh,boolean enabled,Runnable action,double mx,double my,int accent){
        boolean hover=enabled&&mx>=bx&&mx<bx+bw&&my>=by&&my<by+bh;
        GuiDraw.rect(ctx,bx,by,bw,bh,alpha(hover?0xff42314a:enabled?0xff2d2535:0xff201c25));
        text(ctx,label,bx+9,by+bh/2,13,enabled?0xff000000|accent:0xff716779);
        buttons.add(new Button(bx,by,bw,bh,enabled?action:()->{}));
    }
    public void click(double mx,double my,int button){
        if(!open||button!=0)return;
        for(var b:List.copyOf(buttons))if(b.hit(mx,my)){b.action.run();return;}
        if(mx<x||mx>x+width||my<y||my>y+height)close();
    }
    public void scroll(double amount){if(open&&selected==null){firstRow=Math.max(0,firstRow-(int)Math.round(amount*2));lastClicked=null;}}
    public void key(int key){if(!open)return;if(key==GLFW.GLFW_KEY_ESCAPE){if(selected!=null)selected=null;else close();}else if(key==GLFW.GLFW_KEY_P)close();}
}
