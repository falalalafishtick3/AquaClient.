
package com.aqua.client.render;

import com.aqua.client.AquaClient;
import com.aqua.client.config.HighlightSettings;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Set;

public final class BlockHighlighter {

    private static final Set<BlockPos> STORAGE = new LinkedHashSet<>();
    private static final Set<BlockPos> SPAWNERS = new LinkedHashSet<>();

    private static int scanDelay;

    private BlockHighlighter() {}

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(BlockHighlighter::render);
    }

    public static void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            STORAGE.clear();
            SPAWNERS.clear();
            scanDelay = 0;
            return;
        }

        if (++scanDelay < 10) return;
        scanDelay = 0;

        STORAGE.clear();
        SPAWNERS.clear();

        int storageRange = AquaClient.CONFIG.storage.enabled
                ? clampRange(AquaClient.CONFIG.storage.range) : 0;

        int spawnerRange = AquaClient.CONFIG.spawner.enabled
                ? clampRange(AquaClient.CONFIG.spawner.range) : 0;

        int radius = Math.max(storageRange, spawnerRange);
        if (radius == 0) return;

        BlockPos origin = client.player.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        int minY = Math.max(client.level.getMinY(), origin.getY() - 24);
        int maxY = Math.min(client.level.getMaxY() - 1, origin.getY() + 24);

        for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
            int dx = x - origin.getX();

            for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
                int dz = z - origin.getZ();
                int distanceSq = dx * dx + dz * dz;

                boolean checkStorage = storageRange > 0
                        && distanceSq <= storageRange * storageRange;

                boolean checkSpawner = spawnerRange > 0
                        && distanceSq <= spawnerRange * spawnerRange;

                if (!checkStorage && !checkSpawner) continue;

                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);

                    if (!client.level.hasChunkAt(cursor)) continue;

                    Block block = client.level.getBlockState(cursor).getBlock();
                    BlockPos pos = cursor.immutable();

                    if (checkStorage && isStorage(block)) {
                        STORAGE.add(pos);
                    }

                    if (checkSpawner && block == Blocks.SPAWNER) {
                        SPAWNERS.add(pos);
                    }
                }
            }
        }
    }

    private static int clampRange(int range) {
        return Math.max(1, Math.min(64, range));
    }

    private static boolean isStorage(Block block) {
        return block == Blocks.CHEST
                || block == Blocks.TRAPPED_CHEST
                || block == Blocks.ENDER_CHEST
                || block == Blocks.DROPPER
                || block == Blocks.DISPENSER
                || block == Blocks.BARREL
                || block instanceof ShulkerBoxBlock;
    }

    private static void render(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();

        if (client.level == null || client.player == null) return;

        PoseStack matrices = context.matrices();
        MultiBufferSource consumers = context.consumers();

        if (matrices == null || consumers == null) return;

        Vec3 camera = client.gameRenderer.getMainCamera().position();

        VertexConsumer lines = consumers.getBuffer(RenderTypes.LINES);

        renderSet(matrices, lines, STORAGE, AquaClient.CONFIG.storage, camera);
        renderSet(matrices, lines, SPAWNERS, AquaClient.CONFIG.spawner, camera);
    }

    private static void renderSet(
            PoseStack matrices,
            VertexConsumer lines,
            Set<BlockPos> blocks,
            HighlightSettings settings,
            Vec3 camera
    ) {
        if (!settings.enabled || blocks.isEmpty()) return;

        float red = ((settings.color >> 16) & 255) / 255.0f;
        float green = ((settings.color >> 8) & 255) / 255.0f;
        float blue = (settings.color & 255) / 255.0f;
        float alpha = Math.max(0.0f,
                Math.min(1.0f, settings.outlineOpacity));

        PoseStack.Pose pose = matrices.last();

        for (BlockPos pos : blocks) {
            AABB box = new AABB(pos).move(
                    -camera.x,
                    -camera.y,
                    -camera.z
            ).inflate(0.002);

            float x1 = (float) box.minX;
            float y1 = (float) box.minY;
            float z1 = (float) box.minZ;
            float x2 = (float) box.maxX;
            float y2 = (float) box.maxY;
            float z2 = (float) box.maxZ;

            // Bottom
            drawLine(lines, pose, x1,y1,z1, x2,y1,z1, red,green,blue,alpha);
            drawLine(lines, pose, x2,y1,z1, x2,y1,z2, red,green,blue,alpha);
            drawLine(lines, pose, x2,y1,z2, x1,y1,z2, red,green,blue,alpha);
            drawLine(lines, pose, x1,y1,z2, x1,y1,z1, red,green,blue,alpha);

            // Top
            drawLine(lines, pose, x1,y2,z1, x2,y2,z1, red,green,blue,alpha);
            drawLine(lines, pose, x2,y2,z1, x2,y2,z2, red,green,blue,alpha);
            drawLine(lines, pose, x2,y2,z2, x1,y2,z2, red,green,blue,alpha);
            drawLine(lines, pose, x1,y2,z2, x1,y2,z1, red,green,blue,alpha);

            // Vertical edges
            drawLine(lines, pose, x1,y1,z1, x1,y2,z1, red,green,blue,alpha);
            drawLine(lines, pose, x2,y1,z1, x2,y2,z1, red,green,blue,alpha);
            drawLine(lines, pose, x2,y1,z2, x2,y2,z2, red,green,blue,alpha);
            drawLine(lines, pose, x1,y1,z2, x1,y2,z2, red,green,blue,alpha);
        }
    }

    private static void drawLine(
            VertexConsumer lines,
            PoseStack.Pose pose,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float red, float green, float blue, float alpha
    ) {
        lines.addVertex(pose, x1, y1, z1)
                .setColor(red, green, blue, alpha)
                .setNormal(pose, 0, 1, 0);

        lines.addVertex(pose, x2, y2, z2)
                .setColor(red, green, blue, alpha)
                .setNormal(pose, 0, 1, 0);
    }

    public static int getStorageCount() {
        return STORAGE.size();
    }

    public static int getSpawnerCount() {
        return SPAWNERS.size();
    }
}
