package com.fabrictpa;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

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
        Path dir = server.getWorldPath(LevelResource.ROOT).resolve(FabricTpaMod.MOD_ID);
        Path filePath = dir.resolve(FILE_NAME);
        HomeStorage storage = new HomeStorage(server, filePath);

        try {
            Files.createDirectories(dir);
            if (Files.exists(filePath) && Files.size(filePath) > 0) {
                CompoundTag nbt = NbtIo.readCompressed(filePath, NbtAccounter.unlimitedHeap());
                if (nbt != null) {
                    storage.home = SavedLocation.fromNbt(nbt.getCompound("home").orElse(null));
                }
            }
        } catch (Exception exception) {
            FabricTpaMod.LOGGER.error("Failed to load shared home from {}", filePath, exception);
        }

        return storage;
    }

    public Optional<SavedLocation> getHome() {
        return Optional.ofNullable(this.home);
    }

    public void setHome(ServerPlayer player) {
        this.home = SavedLocation.fromPlayer(player);
        this.save();
    }

    public void save() {
        CompoundTag root = new CompoundTag();
        if (this.home != null) {
            root.put("home", this.home.toNbt());
        }

        try {
            Files.createDirectories(this.filePath.getParent());
            NbtIo.writeCompressed(root, this.filePath);
        } catch (IOException exception) {
            FabricTpaMod.LOGGER.error("Failed to save shared home to {}", this.filePath, exception);
        }
    }

    public ServerLevel getWorld(SavedLocation location) {
        ServerLevel world = this.server.getLevel(location.dimension());
        if (world == null) {
            throw new IllegalStateException("Home dimension is missing: " + location.dimension().identifier());
        }
        return world;
    }

    public record SavedLocation(ResourceKey<Level> dimension, Vec3 pos, float yaw, float pitch) {
        public static SavedLocation fromPlayer(ServerPlayer player) {
            return new SavedLocation(player.level().dimension(), player.position(), player.getYRot(), player.getXRot());
        }

        public static @Nullable SavedLocation fromNbt(@Nullable CompoundTag nbt) {
            if (nbt == null) {
                return null;
            }

            Identifier identifier = Identifier.tryParse(nbt.getString("dimension").orElse("minecraft:overworld"));
            if (identifier == null) {
                identifier = Identifier.tryParse("minecraft:overworld");
            }

            return new SavedLocation(
                ResourceKey.create(Registries.DIMENSION, identifier),
                new Vec3(
                    nbt.getDouble("x").orElse(0.0),
                    nbt.getDouble("y").orElse(0.0),
                    nbt.getDouble("z").orElse(0.0)
                ),
                nbt.getFloat("yaw").orElse(0.0F),
                nbt.getFloat("pitch").orElse(0.0F)
            );
        }

        public CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
            nbt.putString("dimension", this.dimension.identifier().toString());
            nbt.putDouble("x", this.pos.x);
            nbt.putDouble("y", this.pos.y);
            nbt.putDouble("z", this.pos.z);
            nbt.putFloat("yaw", this.yaw);
            nbt.putFloat("pitch", this.pitch);
            return nbt;
        }
    }
}
