package local.dualwield;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class CombatPackets {
    public static final int ACTION = 4;
    public static final int PRIMARY = 5;
    public static final int SWING = 0;
    public static final int GUARD = 1;
    public static final int LOWER_GUARD = 2;
    public static final int FOLLOW_UP = 3;
    public static final int SHORT_ART_DRAW = 6;

    static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Press.TYPE, Press.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Status.TYPE, Status.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OffhandSwing.TYPE, OffhandSwing.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Press.TYPE, (payload, context) -> {
            if (payload.action() == PRIMARY) WakizashiCombat.attack(context.player());
            else DualWieldCombat.press(context.player(), payload.action());
        });
    }

    public static void swingOffhand(ServerPlayer player) {
        animateOffhand(player, SWING);
    }

    public static void drawShortArt(ServerPlayer player) {
        sendSwing(player, new OffhandSwing(player.getId(), SHORT_ART_DRAW));
    }

    static void animateOffhand(ServerPlayer player, int animation) {
        var payload = new OffhandSwing(player.getId(), animation);
        sendSwing(player, payload);
        for (var watcher : PlayerLookup.tracking(player)) {
            if (watcher != player) sendSwing(watcher, payload);
        }
    }

    private static void sendSwing(ServerPlayer player, OffhandSwing payload) {
        if (ServerPlayNetworking.canSend(player, OffhandSwing.TYPE)) ServerPlayNetworking.send(player, payload);
    }

    static void status(ServerPlayer player, boolean active, float yaw) {
        if (ServerPlayNetworking.canSend(player, Status.TYPE)) {
            ServerPlayNetworking.send(player, new Status(active, yaw));
        }
    }

    public record Press(int action) implements CustomPacketPayload {
        public static final Type<Press> TYPE = new Type<>(Identifier.fromNamespaceAndPath(DualWield.ID, "press"));
        static final StreamCodec<RegistryFriendlyByteBuf, Press> CODEC =
            ByteBufCodecs.VAR_INT.map(Press::new, Press::action).cast();

        @Override public Type<Press> type() { return TYPE; }
    }

    public record Status(boolean active, float yaw) implements CustomPacketPayload {
        public static final Type<Status> TYPE = new Type<>(Identifier.fromNamespaceAndPath(DualWield.ID, "status"));
        static final StreamCodec<RegistryFriendlyByteBuf, Status> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, Status::active,
            ByteBufCodecs.FLOAT, Status::yaw, Status::new);

        @Override public Type<Status> type() { return TYPE; }
    }

    public record OffhandSwing(int entityId, int animation) implements CustomPacketPayload {
        public static final Type<OffhandSwing> TYPE = new Type<>(Identifier.fromNamespaceAndPath(DualWield.ID, "offhand_swing"));
        static final StreamCodec<RegistryFriendlyByteBuf, OffhandSwing> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, OffhandSwing::entityId,
                ByteBufCodecs.VAR_INT, OffhandSwing::animation, OffhandSwing::new);

        @Override public Type<OffhandSwing> type() { return TYPE; }
    }
}
