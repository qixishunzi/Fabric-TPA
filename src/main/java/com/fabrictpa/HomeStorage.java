package com.fabrictpa;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class HomeStorage {
    private static final String FILE_NAME = "home.dat";

    private final MinecraftServer server;
    private final Path filePath;
    private @Nullable SavedLocation home;

    private HomeStorage(MinecraftServer server, Path filePath) {
        this.server = server;
        this.filePath = filePath;
    }

    public static HomeStorage load(MinecraftServer server) {
        Path dir = server.getSavePath(WorldSavePath.ROOT).resolve(FabricTpaMod.MOD_ID);
        Path filePath = dir.resolve(FILE_NAME);
        HomeStorage storage = new HomeStorage(server, filePath);

        try {
            Files.createDirectories(dir);
            if (Files.exists(filePath) && Files.size(filePath) > 0) {
                NbtCompound nbt = NbtIo.readCompressed(filePath, NbtSizeTracker.ofUnlimitedBytes());
                if (nbt != null) {
                    nbt.getCompound("home").ifPresent(homeNbt -> storage.home = SavedLocation.fromNbt(homeNbt));
                }
            }
        } catch (Exception exception) {
            FabricTpaMod.LOGGER.error("加载公共家失败: {}", filePath, exception);
        }

        return storage;
    }

    public Optional<SavedLocation> getHome() {
        return Optional.ofNullable(this.home);
    }

    public void setHome(ServerPlayerEntity player) {
        this.home = SavedLocation.fromPlayer(player);
        this.save();
    }

    public void save() {
        NbtCompound root = new NbtCompound();
        if (this.home != null) {
            root.put("home", this.home.toNbt());
        }

        try {
            Files.createDirectories(this.filePath.getParent());
            NbtIo.writeCompressed(root, this.filePath);
        } catch (IOException exception) {
            FabricTpaMod.LOGGER.error("保存公共家失败: {}", this.filePath, exception);
        }
    }

    public ServerWorld getWorld(SavedLocation location) {
        ServerWorld world = this.server.getWorld(location.dimension());
        if (world == null) {
            throw new IllegalStateException("家的目标维度不存在: " + location.dimension().getValue());
        }
        return world;
    }

    public record SavedLocation(RegistryKey<World> dimension, Vec3d pos, float yaw, float pitch) {
        public static SavedLocation fromPlayer(ServerPlayerEntity player) {
            return new SavedLocation(player.getEntityWorld().getRegistryKey(), player.getEntityPos(), player.getYaw(), player.getPitch());
        }

        public static SavedLocation fromNbt(NbtCompound nbt) {
            Identifier identifier = Identifier.tryParse(nbt.getString("dimension").orElse("minecraft:overworld"));
            if (identifier == null) {
                identifier = Identifier.tryParse("minecraft:overworld");
            }

            return new SavedLocation(
                RegistryKey.of(RegistryKeys.WORLD, identifier),
                new Vec3d(
                    nbt.getDouble("x").orElse(0.0),
                    nbt.getDouble("y").orElse(0.0),
                    nbt.getDouble("z").orElse(0.0)
                ),
                nbt.getFloat("yaw").orElse(0.0F),
                nbt.getFloat("pitch").orElse(0.0F)
            );
        }

        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("dimension", this.dimension.getValue().toString());
            nbt.putDouble("x", this.pos.x);
            nbt.putDouble("y", this.pos.y);
            nbt.putDouble("z", this.pos.z);
            nbt.putFloat("yaw", this.yaw);
            nbt.putFloat("pitch", this.pitch);
            return nbt;
        }
    }
}
