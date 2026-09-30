package com.kaduvill.configurabledrawer.menu;

import com.kaduvill.configurabledrawer.ConfigurableDrawer;
import com.kaduvill.configurabledrawer.DrawerConfig;
import com.kaduvill.configurabledrawer.drawer.DrawerStorage;
import com.kaduvill.configurabledrawer.drawer.SideRules;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;

public final class DrawerNetwork {
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(ConfigurableDrawer.MODID);
    public static final int CAPACITY = 0, JEI_FILTER = 1, CURSOR_FILTER = 2,
            CLEAR_FILTER = 3, TAKE_STACK = 4, CYCLE_SIDE = 5,
            TOGGLE_VOID = 6, CYCLE_REDSTONE = 7, SET_THRESHOLD = 8, TOGGLE_ICON = 9,
            BULK_INSERT = 10;
    private DrawerNetwork() { }
    public static void init() {
        CHANNEL.registerMessage(ActionHandler.class, Action.class, 0, Side.SERVER);
        CHANNEL.registerMessage(StateHandler.class, State.class, 1, Side.CLIENT);
    }
    public static final class Action implements IMessage {
        public int window, action;
        public long capacity;
        public ItemStack filter = ItemStack.EMPTY;
        public Action() { }
        public Action(int window, int action, long capacity, ItemStack filter) {
            this.window = window; this.action = action; this.capacity = capacity;
            this.filter = filter.copy();
            if (!this.filter.isEmpty()) this.filter.setCount(1);
        }
        @Override public void toBytes(ByteBuf buf) {
            buf.writeInt(window); buf.writeByte(action); buf.writeLong(capacity);
            if (action == JEI_FILTER) ByteBufUtils.writeItemStack(buf, filter);
        }
        @Override public void fromBytes(ByteBuf buf) {
            window = buf.readInt(); action = buf.readUnsignedByte(); capacity = buf.readLong();
            if (action == JEI_FILTER) filter = ByteBufUtils.readItemStack(buf);
        }
    }
    public static final class ActionHandler implements IMessageHandler<Action, IMessage> {
        @Override public IMessage onMessage(Action message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!(player.openContainer instanceof ContainerDrawer)) return;
                ContainerDrawer container = (ContainerDrawer) player.openContainer;
                if (container.windowId != message.window || !container.canInteractWith(player) || !container.tile.active()) return;
                DrawerStorage storage = container.tile.storage();
                switch (message.action) {
                    case CAPACITY: storage.setCapacity(message.capacity); break;
                    case JEI_FILTER: storage.setFilter(message.filter); break;
                    case CURSOR_FILTER: storage.setFilter(player.inventory.getItemStack()); break;
                    case CLEAR_FILTER: storage.setFilter(ItemStack.EMPTY); break;
                    case TAKE_STACK: container.takeStack(player); break;
                    case BULK_INSERT:
                        if (player.inventory.getItemStack().isEmpty())
                            container.tile.insertMatchingInventory(player);
                        break;
                    case CYCLE_SIDE:
                        if (message.capacity < 0 || message.capacity >= SideRules.ENTRIES) return;
                        storage.cycleSide((int) message.capacity);break;
                    case TOGGLE_VOID: storage.toggleVoidOverflow(); break;
                    case CYCLE_REDSTONE:
                        if (message.capacity != 0 && message.capacity != 1) return;
                        storage.cycleRedstone(message.capacity == 1);
                        break;
                    case SET_THRESHOLD: storage.setThreshold(message.capacity); break;
                    case TOGGLE_ICON: storage.toggleFrontIcon(); break;
                    default: return;
                }
                container.detectAndSendChanges();
                container.send(player); // Correct the GUI if the server rejected an edit.
            });
            return null;
        }
    }
    public static final class State implements IMessage {
        public int window, sides, redstone;
        public BlockPos pos;
        public long count, capacity, maximum, threshold;
        public boolean voidOverflow, frontIcon;
        public ItemStack filter = ItemStack.EMPTY;
        public State() { }
        public State(ContainerDrawer container) {
            window = container.windowId; pos = container.tile.getPos();
            DrawerStorage storage = container.tile.storage();
            count = storage.count(); capacity = storage.capacity(); maximum = DrawerConfig.maximumCapacity;
            filter = storage.filter();
            sides = storage.sides();
            voidOverflow = storage.voidOverflow();
            frontIcon = storage.frontIcon();
            redstone = storage.redstone();
            threshold = storage.threshold();
        }
        @Override public void toBytes(ByteBuf buf) {
            buf.writeInt(window); buf.writeLong(pos.toLong()); buf.writeLong(count);
            buf.writeLong(capacity); buf.writeLong(maximum);
            ByteBufUtils.writeItemStack(buf, filter);
            buf.writeInt(sides);
            buf.writeBoolean(voidOverflow); buf.writeBoolean(frontIcon);
            buf.writeByte(redstone); buf.writeLong(threshold);
        }
        @Override public void fromBytes(ByteBuf buf) {
            window = buf.readInt(); pos = BlockPos.fromLong(buf.readLong()); count = buf.readLong();
            capacity = buf.readLong(); maximum = buf.readLong();
            filter = ByteBufUtils.readItemStack(buf); sides = buf.readInt() & SideRules.MASK;
            voidOverflow = buf.readBoolean(); frontIcon = buf.readBoolean();
            redstone = Math.min(buf.readUnsignedByte(), DrawerStorage.REDSTONE_AT_MOST);
            threshold = Math.max(0L, buf.readLong());
        }
    }
    public static final class StateHandler implements IMessageHandler<State, IMessage> {
        @Override public IMessage onMessage(State message, MessageContext context) {
            ConfigurableDrawer.PROXY.receiveState(message);
            return null;
        }
    }
}
