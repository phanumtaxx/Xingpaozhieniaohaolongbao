package dev.xingclient.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.player.PlayerEntity;
import org.joml.Quaternionf;

/** Live pose/skin/equipment, rendered without terrain or modifying the player entity. */
final class PlayerPreview {
    static void draw(DrawContext ctx,PlayerEntity player,double x,double y,double w,double h,float delta){
        var dispatcher=MinecraftClient.getInstance().getEntityRenderDispatcher();
        var state=dispatcher.getRenderer(player).getAndUpdateRenderState(player,delta);
        if(!(state instanceof LivingEntityRenderState living))return;
        float originalYaw=living.bodyYaw;var label=state.displayName;var hitbox=state.hitbox;
        var rotation=new Quaternionf(dispatcher.getRotation());
        float scale=(float)Math.min(w*(player.isGliding()?.25:.38),h*.43)/player.getScale();
        ctx.enableScissor((int)x,(int)y,(int)(x+w),(int)(y+h));ctx.getMatrices().push();
        try{
            living.bodyYaw=180;state.displayName=null;state.hitbox=null;
            ctx.getMatrices().translate(x+w/2,y+h/2+player.getHeight()*scale/2,50);
            ctx.getMatrices().scale(scale,scale,-scale);
            ctx.getMatrices().multiply(new Quaternionf().rotationZ((float)Math.PI).rotateX(-.12f));
            ctx.draw();DiffuseLighting.enableGuiShaderLighting();
            dispatcher.setRotation(new Quaternionf().rotationY((float)Math.PI));dispatcher.setRenderShadows(false);
            ctx.draw(vertices->dispatcher.render(state,0,0,0,ctx.getMatrices(),vertices,15728880));ctx.draw();
        }finally{
            living.bodyYaw=originalYaw;state.displayName=label;state.hitbox=hitbox;
            dispatcher.setRotation(rotation);dispatcher.setRenderShadows(true);
            ctx.getMatrices().pop();ctx.disableScissor();DiffuseLighting.enableGuiDepthLighting();
        }
    }
    private PlayerPreview(){}
}
