package com.aqua.client.render;

import com.aqua.client.AquaClient;
import com.aqua.client.config.HighlightSettings;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
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

/**
 * Client-side block scanner and world renderer for Aqua Client.
 *
 * Only scans chunks which are already loaded on the client. It never requests
 * chunks from the server and keeps storage and spawner results independent.
 */
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

        if (++scanDelay < 10) return; // rescan twice per second
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
        int radiusSq = radius * radius;

        for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
            int dx = x - origin.getX();
            for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
                int dz = z - origin.getZ();
                int horizontalSq = dx * dx + dz * dz;
                if (horizontalSq > radiusSq) continue;

                // Avoid scanning positions outside the individual feature range.
                boolean checkStorage = storageRange > 0
                        && horizontalSq <= storageRange * storageRange;
                boolean checkSpawner = spawnerRange > 0
                        && horizontalSq <= spawnerRange * spawnerRange;
                if (!checkStorage && !checkSpawner) continue;

                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    if (!client.level.hasChunkAt(cursor)) continue;

                    Block block = client.level.getBlockState(cursor).getBlock();
                    BlockPos immutable = cursor.immutable();

                    if (checkStorage && isStorage(block)) STORAGE.add(immutable);
                    if (checkSpawner && block == Blocks.SPAWNER) SPAWNERS.add(immutable);
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

        float red = ((settings.color >> 16) & 0xFF) / 255.0f;
        float green = ((settings.color >> 8) & 0xFF) / 255.0f;
        float blue = (settings.color & 0xFF) / 255.0f;
        float alpha = Math.max(0.0f, Math.min(1.0f, settings.outlineOpacity));

        for (BlockPos pos : blocks) {
            AABB box = new AABB(pos).move(-camera.x, -camera.y, -camera.z).inflate(0.002);
            LevelRenderer.renderLineBox(matrices, lines, box, red, green, blue, alpha);
        }
    }

    public static int getStorageCount() { return STORAGE.size(); }
    public static int getSpawnerCount() { return SPAWNERS.size(); }
}
