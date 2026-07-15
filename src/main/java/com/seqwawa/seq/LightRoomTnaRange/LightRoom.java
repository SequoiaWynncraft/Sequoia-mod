package com.seqwawa.seq.LightRoomTnaRange;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static com.seqwawa.seq.halcyon.HalcyonRingRenderer.renderRingWall;

public final class LightRoom {
    private static final Vec3 LightPos = new Vec3(-12743, 70, 8380);
    private static final List<AbstractClientPlayer> possibleLightHolders = new ArrayList<>();
    private static int playerUnderLight = 0;
    private static AbstractClientPlayer LightHolder = null;
    private static boolean prepRoom = false;
    private static boolean inRoom = false;
    private static final int radius = 7;
    private static boolean wasPrep = false;

    private LightRoom() {}

    public static void init(){
        ClientTickEvents.END_CLIENT_TICK.register(LightRoom::Tick);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(LightRoom::Render);
    }

    public static void Tick(Minecraft client){
        if(client.player == null) return;
        if(client.level == null) return;

        String scoreboard = client.level.getScoreboard().toString();
        prepRoom = scoreboard.contains("Gather the Light!");
        inRoom = scoreboard.contains("Find and kill");

        if(prepRoom && !wasPrep){
            LightHolder = null;
            possibleLightHolders.clear();
            playerUnderLight = 0;
        }
        wasPrep = prepRoom;

        if(!prepRoom)return;

        client.level.players().forEach(player -> {
            if(player.isSpectator())return;
            if(player.position().distanceTo(LightPos) <= 1.5){
                if(!possibleLightHolders.contains(player)){
                    possibleLightHolders.add(player);
                }
                playerUnderLight++;
            }
            else{
                if(possibleLightHolders.contains(player)){
                    possibleLightHolders.remove(player);
                }
            }
            if(playerUnderLight >= 60 && possibleLightHolders.size() == 1){
                LightHolder = possibleLightHolders.getFirst();
            }
        });
    }
    public static void Render(WorldRenderContext context) {
        if(!inRoom) return;
        if(LightHolder == null) return;
        float tickDelta = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 center = LightHolder.getPosition(tickDelta);
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        PoseStack.Pose pose = context.matrices().last();
        VertexConsumer vertices = context.consumers().getBuffer(RenderTypes.debugQuads());

        renderRingWall(vertices, pose, center, camera, radius);
    }
}
