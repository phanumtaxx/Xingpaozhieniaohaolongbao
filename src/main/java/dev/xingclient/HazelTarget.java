package dev.xingclient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

/** Stored selection only. No combat controller is connected to it. */
public final class HazelTarget {
    public static String scope(MinecraftClient mc){
        if(mc.getCurrentServerEntry()!=null)return "server:"+mc.getCurrentServerEntry().address.strip().toLowerCase(java.util.Locale.ROOT);
        return "singleplayer:"+(mc.getServer()==null?"":mc.getServer().getSaveProperties().getLevelName());
    }
    public static void save(XingClient app,PlayerEntity player){
        app.settings.targets.put(scope(MinecraftClient.getInstance()),new ClientSettings.SavedTarget(player.getUuidAsString(),player.getGameProfile().getName()));app.changed();
    }
    public static ClientSettings.SavedTarget get(XingClient app){return app.settings.targets.get(scope(MinecraftClient.getInstance()));}
    public static boolean command(String command){
        if(!command.equals("hazel")&&!command.startsWith("hazel "))return false;
        var mc=MinecraftClient.getInstance();var app=XingClient.INSTANCE;if(app==null)return false;
        var target=get(app);
        mc.inGameHud.getChatHud().addMessage(Text.literal(target==null?"Hazel: No saved target. Open the ClickGUI, press P, and choose Target on a player.":"Hazel: Saved target is "+target.name+". Autopilot is not available yet."));
        return true;
    }
    private HazelTarget(){}
}
