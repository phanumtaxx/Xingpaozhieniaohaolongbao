package dev.xingclient.module;

import dev.xingclient.XingClient;
import dev.xingclient.nativebridge.XingNativeBridge;
import dev.xingclient.render.web.WebOverlay;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

/** Supplies Minecraft snapshots to native Nametags and the HTML renderer. Author: uint32. */
public final class NativeNametagsModule extends Module {
    private final Map<String,Vec3d> positions=new HashMap<>();
    private final Map<String,WebOverlay.Registration> anchors=new HashMap<>();
    private final HashSet<UUID> replaced=new HashSet<>();
    private WebOverlay.Registration backdrop;
    private record Equipment(String name,String icon,int count) {}
    private record Tag(String id,String name,double health,String healthColor,List<Equipment> equipment) {}
    private record Tags(List<Tag> players,double fontSize,String backgroundColor,double backgroundAlpha,double paddingX,double paddingY,double cornerRadius) {}
    public NativeNametagsModule() {super("nametags","Nametags",ModuleCategory.VISUALS);}
    @Override protected void onEnable() {
        XingNativeBridge.loadLibrary();
        var app=XingClient.INSTANCE;
        app.webModule.setEnabled(true);
        app.webOverlay.installFont("Xing Inter","InterVariable.woff2");
        app.webOverlay.installAsset("Inter-LICENSE.txt");
        app.webOverlay.installExtension("nametags","nametags.js","nametags.css");
        backdrop=app.webOverlay.requestBackdrop(id(),()->app.settings.nametags.blur);
        app.settings.modules.get(app.webModule.id()).enabled=true;
    }
    @Override protected void onDisable() {
        for(var anchor:anchors.values()) anchor.close();
        if(backdrop!=null) {backdrop.close();backdrop=null;}
        anchors.clear();positions.clear();replaced.clear();
        XingClient.INSTANCE.webOverlay.removeChannel("nametags");
    }
    public boolean replaces(UUID player) {
        return isEnabled()&&XingClient.INSTANCE.webModule.isEnabled()&&replaced.contains(player);
    }
    public void capture(float delta) {
        if(!isEnabled()) return;
        var app=XingClient.INSTANCE;
        var client=MinecraftClient.getInstance();
        var settings=app.settings.nametags;
        settings.normalize();
        var players=client.world==null?List.<net.minecraft.client.network.AbstractClientPlayerEntity>of():client.world.getPlayers();
        double[] input=new double[players.size()*8],output=new double[players.size()*4];
        int[] flags=new int[players.size()];
        for(int i=0;i<players.size();i++) {
            var player=players.get(i);int at=i*8;
            input[at]=player.lastRenderX;input[at+1]=player.lastRenderY;input[at+2]=player.lastRenderZ;
            input[at+3]=player.getX();input[at+4]=player.getY();input[at+5]=player.getZ();
            input[at+6]=player.getHeight();input[at+7]=client.gameRenderer.getCamera().getPos().squaredDistanceTo(player.getPos());
            flags[i]=(player==client.player?1:0)|(player.isSpectator()?2:0)|(player.isInvisible()?4:0);
        }
        plan(input,flags,output,settings.range,settings.offset,delta,settings.invisible);
        var keep=new HashSet<String>();var tags=new ArrayList<Tag>();
        replaced.clear();
        for(int i=0;i<players.size();i++) {
            if(output[i*4+3]==0) continue;
            var player=players.get(i);String id="nametag:"+player.getUuid();
            positions.put(id,new Vec3d(output[i*4],output[i*4+1],output[i*4+2]));
            if(!anchors.containsKey(id)) anchors.put(id,app.webOverlay.track(id,()->positions.get(id)));
            var equipment=new ArrayList<Equipment>();
            for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}) {
                addEquipment(equipment,player.getEquippedStack(slot));
            }
            addEquipment(equipment,player.getMainHandStack());addEquipment(equipment,player.getOffHandStack());
            float health=player.getHealth()+player.getAbsorptionAmount();
            String color=String.format("#%06x",healthColor(health,player.getMaxHealth()));
            keep.add(id);replaced.add(player.getUuid());
            tags.add(new Tag(id,player.getName().getString(),health,color,equipment));
        }
        anchors.entrySet().removeIf(entry->{if(keep.contains(entry.getKey()))return false;entry.getValue().close();positions.remove(entry.getKey());return true;});
        app.webOverlay.publish("nametags",new Tags(tags,settings.fontSize,String.format("#%06x",settings.backgroundColor),settings.backgroundAlpha,settings.paddingX,settings.paddingY,settings.cornerRadius));
    }
    private static void addEquipment(List<Equipment> equipment,ItemStack stack) {
        if(!stack.isEmpty()) equipment.add(new Equipment(stack.getName().getString(),
            XingClient.INSTANCE.webOverlay.itemIcon(stack),stack.getCount()));
    }
    private static native int healthColor(float health,float maximum);
    private static native void plan(double[] players,int[] flags,double[] output,double range,double offset,double delta,boolean invisible);
}
