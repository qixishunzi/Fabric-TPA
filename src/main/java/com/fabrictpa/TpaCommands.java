package com.fabrictpa;

import java.util.Set;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.PosArgument;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class TpaCommands {
    private static final SimpleCommandExceptionType HOME_NOT_SET = new SimpleCommandExceptionType(Text.literal("家尚未设置。"));
    private static final SimpleCommandExceptionType PLAYER_ONLY = new SimpleCommandExceptionType(Text.literal("该命令只能由玩家执行。"));
    private static final SimpleCommandExceptionType HOME_WORLD_MISSING = new SimpleCommandExceptionType(Text.literal("家的目标维度不存在。"));

    private TpaCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("tpa")
            .then(CommandManager.literal("home")
                .executes(TpaCommands::teleportToHome))
            .then(CommandManager.literal("set")
                .requires(TpaCommands::isAdmin)
                .then(CommandManager.literal("home")
                    .executes(TpaCommands::setHome)))
            .then(CommandManager.argument("destination", EntityArgumentType.player())
                .requires(TpaCommands::isPlayer)
                .executes(TpaCommands::teleportSelfToEntity))
            .then(CommandManager.argument("location", Vec3ArgumentType.vec3())
                .requires(TpaCommands::isPlayer)
                .executes(TpaCommands::teleportSelfToLocation)));
    }

    private static boolean isPlayer(ServerCommandSource source) {
        return source.getEntity() instanceof ServerPlayerEntity;
    }

    private static boolean isAdmin(ServerCommandSource source) {
        return source.getPermissions().hasPermission(new Permission.Level(PermissionLevel.OWNERS));
    }

    private static int setHome(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = getPlayer(context);
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Vec3d pos = player.getEntityPos();
        FabricTpaMod.getHomeStorage(context.getSource().getServer()).setHome(player);
        context.getSource().sendFeedback(
            () -> Text.literal("已将家设置在坐标 ")
                .formatted(Formatting.YELLOW)
                .append(Text.literal(formatWithDimension(world, pos)).formatted(Formatting.GOLD))
                .append(Text.literal("。").formatted(Formatting.YELLOW)),
            false
        );
        FabricTpaMod.LOGGER.info("{} set home at {} in {}.", player.getName().getString(), formatCoordinates(pos), formatDimensionEnglish(world.getRegistryKey()));
        return 1;
    }

    private static int teleportToHome(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = getPlayer(context);
        HomeStorage storage = FabricTpaMod.getHomeStorage(context.getSource().getServer());
        HomeStorage.SavedLocation home = storage.getHome().orElseThrow(HOME_NOT_SET::create);

        ServerWorld world;
        try {
            world = storage.getWorld(home);
        } catch (IllegalStateException exception) {
            throw HOME_WORLD_MISSING.create();
        }

        teleportEntity(player, world, home.pos(), home.yaw(), home.pitch());
        broadcast(
            context,
            coloredMessage(player.getName().getString(), "家", formatWithDimension(world, home.pos())),
            String.format("%s teleported home at %s in %s.", player.getName().getString(), formatCoordinates(home.pos()), formatDimensionEnglish(world.getRegistryKey()))
        );
        return 1;
    }

    private static int teleportSelfToEntity(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = getPlayer(context);
        ServerPlayerEntity destination = EntityArgumentType.getPlayer(context, "destination");
        teleportToEntity(player, destination);
        broadcast(
            context,
            coloredMessage(player.getName().getString(), destination.getName().getString(), formatWithDimension((ServerWorld) destination.getEntityWorld(), destination.getEntityPos())),
            String.format(
                "%s teleported to %s at %s in %s.",
                player.getName().getString(),
                destination.getName().getString(),
                formatCoordinates(destination.getEntityPos()),
                formatDimensionEnglish(((ServerWorld) destination.getEntityWorld()).getRegistryKey())
            )
        );
        return 1;
    }

    private static int teleportSelfToLocation(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = getPlayer(context);
        Vec3d location = getLocation(context, "location");
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        teleportEntity(player, world, location, player.getYaw(), player.getPitch());
        broadcast(
            context,
            coloredCoordinateMessage(player.getName().getString(), formatWithDimension(world, location)),
            String.format("%s teleported to %s in %s.", player.getName().getString(), formatCoordinates(location), formatDimensionEnglish(world.getRegistryKey()))
        );
        return 1;
    }

    private static ServerPlayerEntity getPlayer(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        try {
            return context.getSource().getPlayerOrThrow();
        } catch (CommandSyntaxException exception) {
            throw PLAYER_ONLY.create();
        }
    }

    private static Vec3d getLocation(CommandContext<ServerCommandSource> context, String name) throws CommandSyntaxException {
        return Vec3ArgumentType.getVec3(context, name);
    }

    private static void teleportToEntity(Entity target, Entity destination) {
        teleportEntity(target, (ServerWorld) destination.getEntityWorld(), destination.getEntityPos(), destination.getYaw(), destination.getPitch());
    }

    private static void teleportEntity(Entity entity, ServerWorld world, Vec3d pos, float yaw, float pitch) {
        entity.teleport(world, pos.x, pos.y, pos.z, Set.<PositionFlag>of(), yaw, pitch, false);
    }

    private static void broadcast(CommandContext<ServerCommandSource> context, Text message, String consoleMessage) {
        for (ServerPlayerEntity targetPlayer : context.getSource().getServer().getPlayerManager().getPlayerList()) {
            targetPlayer.sendMessage(message, false);
        }
        FabricTpaMod.LOGGER.info(consoleMessage);
    }

    private static Text coloredMessage(String playerName, String targetName, String location) {
        MutableText message = Text.empty();
        message.append(Text.literal(playerName).formatted(Formatting.AQUA));
        message.append(Text.literal(" 传送到了 ").formatted(Formatting.YELLOW));
        message.append(Text.literal(targetName).formatted(Formatting.GREEN));
        message.append(Text.literal("，坐标 ").formatted(Formatting.YELLOW));
        message.append(Text.literal(location).formatted(Formatting.GOLD));
        message.append(Text.literal("。").formatted(Formatting.YELLOW));
        return message;
    }

    private static Text coloredCoordinateMessage(String playerName, String location) {
        MutableText message = Text.empty();
        message.append(Text.literal(playerName).formatted(Formatting.AQUA));
        message.append(Text.literal(" 传送到了坐标 ").formatted(Formatting.YELLOW));
        message.append(Text.literal(location).formatted(Formatting.GOLD));
        message.append(Text.literal("。").formatted(Formatting.YELLOW));
        return message;
    }

    private static String formatWithDimension(ServerWorld world, Vec3d pos) {
        return formatCoordinates(pos) + "，维度 " + formatDimension(world.getRegistryKey());
    }

    private static String formatCoordinates(Vec3d pos) {
        BlockPos blockPos = BlockPos.ofFloored(pos);
        return "(" + blockPos.getX() + ", " + blockPos.getY() + ", " + blockPos.getZ() + ")";
    }

    private static String formatDimension(RegistryKey<World> dimension) {
        var id = dimension.getValue();
        if (World.OVERWORLD.getValue().equals(id)) {
            return "主世界";
        }
        if (World.NETHER.getValue().equals(id)) {
            return "下界";
        }
        if (World.END.getValue().equals(id)) {
            return "末地";
        }
        return id.toString();
    }

    private static String formatDimensionEnglish(RegistryKey<World> dimension) {
        var id = dimension.getValue();
        if (World.OVERWORLD.getValue().equals(id)) {
            return "overworld";
        }
        if (World.NETHER.getValue().equals(id)) {
            return "nether";
        }
        if (World.END.getValue().equals(id)) {
            return "the_end";
        }
        return id.toString();
    }
}
