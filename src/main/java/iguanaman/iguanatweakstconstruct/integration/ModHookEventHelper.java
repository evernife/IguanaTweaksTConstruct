package iguanaman.iguanatweakstconstruct.integration;

import com.gamerforea.eventhelper.util.EventUtils;
import cpw.mods.fml.common.Loader;
import iguanaman.iguanatweakstconstruct.util.Log;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.world.BlockEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ModHookEventHelper {

    public static boolean initialized = false;

    public static void initialize() {
        initialized = Loader.isModLoaded(getModID());
        if(initialized) {
            Log.info("EventHelper has been enabled on ArsMagica!");
        }
    }

    public static String getModID() {
        return "EventHelper";
    }

    public static boolean cantBreak(@Nonnull EntityPlayer player, double x, double y, double z){
        if (initialized){
            return EventUtils.cantBreak(player,x,y,z);
        }
        return false;
    }

    public static boolean cantAttack(@Nonnull Entity attacker, @Nonnull Entity victim){
        if (initialized){
            return EventUtils.cantDamage(attacker,victim);
        }
        return false;
    }
}
