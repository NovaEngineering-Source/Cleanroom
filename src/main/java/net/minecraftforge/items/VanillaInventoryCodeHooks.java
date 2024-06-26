/*
 * Minecraft Forge
 * Copyright (c) 2016-2020.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.items;

import catserver.server.inventory.CatInventoryUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDropper;
import net.minecraft.block.BlockHopper;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.IHopper;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityDispenser;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;

import catserver.server.inventory.CatCustomInventory;
import org.bukkit.inventory.InventoryHolder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class VanillaInventoryCodeHooks
{
    /**
     * Copied from TileEntityHopper#captureDroppedItems and added capability support
     * @return Null if we did nothing {no IItemHandler}, True if we moved an item, False if we moved no items
     */
    @Nullable
    public static Boolean extractHook(IHopper dest)
    {
        Pair<IItemHandler, Object> itemHandlerResult = getItemHandler(dest, EnumFacing.UP);
        if (itemHandlerResult == null)
            return null;

        if (itemHandlerResult.getValue() instanceof IInventory) return null; // CatServer - handle in vanilla

        IItemHandler handler = itemHandlerResult.getKey();

        for (int i = 0; i < handler.getSlots(); i++)
        {
            ItemStack extractItem = handler.extractItem(i, 1, true);
            if (!extractItem.isEmpty())
            {
                for (int j = 0; j < dest.getSizeInventory(); j++)
                {
                    ItemStack destStack = dest.getStackInSlot(j);
                    if (dest.isItemValidForSlot(j, extractItem) && (destStack.isEmpty() || destStack.getCount() < destStack.getMaxStackSize() && destStack.getCount() < dest.getInventoryStackLimit() && ItemHandlerHelper.canItemStacksStack(extractItem, destStack)))
                    {
                        extractItem = handler.extractItem(i, 1, false);
                        if (destStack.isEmpty())
                            dest.setInventorySlotContents(j, extractItem);
                        else
                        {
                            destStack.grow(1);
                            dest.setInventorySlotContents(j, destStack);
                        }
                        dest.markDirty();
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Copied from BlockDropper#dispense and added capability support
     */
    public static boolean dropperInsertHook(World world, BlockPos pos, TileEntityDispenser dropper, int slot, @Nonnull ItemStack stack)
    {
        EnumFacing enumfacing = world.getBlockState(pos).getValue(BlockDropper.FACING);
        BlockPos blockpos = pos.offset(enumfacing);
        Pair<IItemHandler, Object> destinationResult = getItemHandler(world, (double) blockpos.getX(), (double) blockpos.getY(), (double) blockpos.getZ(), enumfacing.getOpposite());
        if (destinationResult == null)
        {
            return true;
        }
        else
        {
            IItemHandler itemHandler = destinationResult.getKey();
            Object destination = destinationResult.getValue();
            // CatServer start
            ItemStack originNMSStack = stack.copy().splitStack(1);
            ItemStack resultNMSStack = originNMSStack;

            InventoryMoveItemEvent event = null;

            CraftItemStack oitemstack = CraftItemStack.asCraftMirror(originNMSStack);

            InventoryHolder owner = CatInventoryUtils.getOwner((TileEntity) destination);
            Inventory destinationInventory = owner != null ? owner.getInventory() : CatCustomInventory.getInventoryFromForge(itemHandler);

            if (destinationInventory != null) {
                event = new InventoryMoveItemEvent(CatInventoryUtils.getBukkitInventory(dropper), oitemstack, destinationInventory, true);
                world.getServer().getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    return false;
                }

                if (event.isCallSetItem) resultNMSStack = CraftItemStack.asNMSCopy(event.getRawItem());
            }

            ItemStack remainder = putStackInInventoryAllSlots(dropper, destination, itemHandler, resultNMSStack);
            if ((event == null || !event.isCallSetItem || ItemStack.areItemStacksEqual(resultNMSStack, originNMSStack)) && remainder.isEmpty())
            {
                remainder = stack.copy();
                remainder.shrink(1);
            }
            else
            {
                remainder = stack.copy();
            }
            // CatServer end
            dropper.setInventorySlotContents(slot, remainder);
            return false;
        }
    }

    /**
     * Copied from TileEntityHopper#transferItemsOut and added capability support
     */
    public static boolean insertHook(TileEntityHopper hopper)
    {
        EnumFacing hopperFacing = BlockHopper.getFacing(hopper.getBlockMetadata());
        Pair<IItemHandler, Object> destinationResult = getItemHandler(hopper, hopperFacing);
        if (destinationResult == null)
        {
            return false;
        }
        else
        {
            IItemHandler itemHandler = destinationResult.getKey();
            Object destination = destinationResult.getValue();
            if (isFull(itemHandler))
            {
                return false;
            }
            else
            {
                boolean foundItem = false; // CatServer
                for (int i = 0; i < hopper.getSizeInventory(); ++i)
                {
                    if (!hopper.getStackInSlot(i).isEmpty())
                    {
                        foundItem = true;
                        ItemStack originalSlotContents = hopper.getStackInSlot(i).copy();
                        // CatServer start - Optimized of call event when pushing items into other inventories
                        ItemStack originNMSStack = hopper.decrStackSize(i, hopper.world.spigotConfig.hopperAmount); // CatServer
                        ItemStack resultNMSStack = originNMSStack;

                        InventoryMoveItemEvent event = null;
                        if (!TileEntityHopper.skipHopperEvents) {
                            CraftItemStack remainder = CraftItemStack.asCraftMirror(originNMSStack);

                            InventoryHolder owner = CatInventoryUtils.getOwner((TileEntity) destination);
                            Inventory destinationInventory = owner != null ? owner.getInventory() : CatCustomInventory.getInventoryFromForge(itemHandler);

                            if (destinationInventory != null) {
                                event = new InventoryMoveItemEvent(CatInventoryUtils.getBukkitInventory(hopper), remainder, destinationInventory, true);
                                hopper.getWorld().getServer().getPluginManager().callEvent(event); //CatServer

                                if (event.isCancelled()) {
                                    return true;
                                }

                                if (event.isCallSetItem) resultNMSStack = CraftItemStack.asNMSCopy(event.getRawItem());
                            }
                        }

                        int origCount = resultNMSStack.getCount();
                        ItemStack itemstack1 = putStackInInventoryAllSlots(hopper, destination, itemHandler, resultNMSStack);

                        if (itemstack1.isEmpty())
                        {
                            if (event == null || !event.isCallSetItem || ItemStack.areItemStacksEqual(resultNMSStack, originNMSStack)) {
                                ((TileEntity) destination).markDirty();
                            } else {
                                hopper.setInventorySlotContents(i, originalSlotContents);
                            }
                            return true;
                        }
                        originalSlotContents.shrink(origCount - itemstack1.getCount());
                        // CatServer end
                        hopper.setInventorySlotContents(i, originalSlotContents);
                    }
                }
                if (foundItem) hopper.setTransferCooldown(hopper.getWorld().spigotConfig.hopperTransfer); // CatServer - Inventory was full - cooldown
                return false;
            }
        }
    }

    private static ItemStack putStackInInventoryAllSlots(TileEntity source, Object destination, IItemHandler destInventory, ItemStack stack)
    {
        for (int slot = 0; slot < destInventory.getSlots() && !stack.isEmpty(); slot++)
        {
            stack = insertStack(source, destination, destInventory, stack, slot);
        }
        return stack;
    }

    /**
     * Copied from TileEntityHopper#insertStack and added capability support
     */
    private static ItemStack insertStack(TileEntity source, Object destination, IItemHandler destInventory, ItemStack stack, int slot)
    {
        ItemStack itemstack = destInventory.getStackInSlot(slot);

        if (destInventory.insertItem(slot, stack, true).isEmpty())
        {
            boolean insertedItem = false;
            boolean inventoryWasEmpty = isEmpty(destInventory);

            if (itemstack.isEmpty())
            {
                destInventory.insertItem(slot, stack, false);
                stack = ItemStack.EMPTY;
                insertedItem = true;
            }
            else if (ItemHandlerHelper.canItemStacksStack(itemstack, stack))
            {
                int originalSize = stack.getCount();
                stack = destInventory.insertItem(slot, stack, false);
                insertedItem = originalSize < stack.getCount();
            }

            if (insertedItem)
            {
                if (inventoryWasEmpty && destination instanceof TileEntityHopper)
                {
                    TileEntityHopper destinationHopper = (TileEntityHopper)destination;

                    if (!destinationHopper.mayTransfer())
                    {
                        int k = 0;

                        if (source instanceof TileEntityHopper)
                        {
                            if (destinationHopper.getLastUpdateTime() >= ((TileEntityHopper) source).getLastUpdateTime())
                            {
                                k = 1;
                            }
                        }

                        destinationHopper.setTransferCooldown(8 - k);
                    }
                }
            }
        }

        return stack;
    }

    @Nullable
    private static Pair<IItemHandler, Object> getItemHandler(IHopper hopper, EnumFacing hopperFacing)
    {
        double x = hopper.getXPos() + (double) hopperFacing.getXOffset();
        double y = hopper.getYPos() + (double) hopperFacing.getYOffset();
        double z = hopper.getZPos() + (double) hopperFacing.getZOffset();
        return getItemHandler(hopper.getWorld(), x, y, z, hopperFacing.getOpposite());
    }

    private static boolean isFull(IItemHandler itemHandler)
    {
        for (int slot = 0; slot < itemHandler.getSlots(); slot++)
        {
            ItemStack stackInSlot = itemHandler.getStackInSlot(slot);
            if (stackInSlot.isEmpty() || stackInSlot.getCount() != stackInSlot.getMaxStackSize())
            {
                return false;
            }
        }
        return true;
    }

    private static boolean isEmpty(IItemHandler itemHandler)
    {
        for (int slot = 0; slot < itemHandler.getSlots(); slot++)
        {
            ItemStack stackInSlot = itemHandler.getStackInSlot(slot);
            if (stackInSlot.getCount() > 0)
            {
                return false;
            }
        }
        return true;
    }

    @Nullable
    public static Pair<IItemHandler, Object> getItemHandler(World worldIn, double x, double y, double z, final EnumFacing side)
    {
        Pair<IItemHandler, Object> destination = null;
        int i = MathHelper.floor(x);
        int j = MathHelper.floor(y);
        int k = MathHelper.floor(z);
        BlockPos blockpos = new BlockPos(i, j, k);
        net.minecraft.block.state.IBlockState state = worldIn.getBlockState(blockpos);
        Block block = state.getBlock();

        if (block.hasTileEntity(state))
        {
            TileEntity tileentity = worldIn.getTileEntity(blockpos);
            if (tileentity != null)
            {
                if (tileentity.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side))
                {
                    IItemHandler capability = tileentity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side);
                    destination = ImmutablePair.<IItemHandler, Object>of(capability, tileentity);
                }
            }
        }

        return destination;
    }
}
