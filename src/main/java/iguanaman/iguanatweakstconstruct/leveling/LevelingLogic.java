package iguanaman.iguanatweakstconstruct.leveling;

import iguanaman.iguanatweakstconstruct.old.modifiers.*;
import iguanaman.iguanatweakstconstruct.reference.Config;
import iguanaman.iguanatweakstconstruct.reference.Reference;
import iguanaman.iguanatweakstconstruct.util.HarvestLevels;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import tconstruct.items.tools.*;
import tconstruct.library.TConstructRegistry;
import tconstruct.library.modifier.ItemModifier;
import tconstruct.library.tools.HarvestTool;
import tconstruct.library.tools.Weapon;
import tconstruct.modifiers.tools.ModAntiSpider;
import tconstruct.modifiers.tools.ModInteger;
import tconstruct.modifiers.tools.ModReinforced;
import tconstruct.modifiers.tools.ModSmite;

/**
 * Utility class that takes care of all the Tool XP related things.
 * Basically how leveling works:
 *  - You get XP for doing stuff with the tool
 *  - On levelup, you gain additional modifiers (according to configuration)
 *  - On levelup, you gain random bonus modifiers (according to configuration)
 *  - If pick-boosting is enabled, all the xp you gain also fills a secondary xp-bar, the mining-boost-xp
 *  - When your mining-boost-xp is full, your mining level is increased by 1. Only works once per pick.
 */
public abstract class LevelingLogic {
    public static final String TAG_EXP = "ToolEXP";
    public static final String TAG_LEVEL = "ToolLevel";
    public static final String TAG_BOOST_EXP = "HeadEXP"; // HeadEXP for downwards compatibility
    public static final String TAG_IS_BOOSTED = "HarvestLevelModified";

    public static final int MAX_LEVEL = 6;

    public static int getLevel(NBTTagCompound tags) { return tags.getInteger(TAG_LEVEL); }
    public static int getHarvestLevel(NBTTagCompound tags) { return tags.hasKey("HarvestLevel") ? tags.getInteger("HarvestLevel") : -1; }
    public static long getXp(NBTTagCompound tags) { return tags.getLong(TAG_EXP); }
    public static long getBoostXp(NBTTagCompound tags) { return tags.getLong(TAG_BOOST_EXP); }
    public static boolean hasLevel(NBTTagCompound tags) { return tags.hasKey(TAG_LEVEL); }
    public static boolean hasXp(NBTTagCompound tags) { return tags.hasKey(TAG_EXP); }
    public static boolean hasBoostXp(NBTTagCompound tags) { return tags.hasKey(TAG_BOOST_EXP); }
    public static boolean isBoosted(NBTTagCompound tags) { return tags.getBoolean(TAG_IS_BOOSTED); }
    public static boolean isMaxLevel(NBTTagCompound tags) { return getLevel(tags) >= MAX_LEVEL; }

    /**
    * can only be boosted if:
    * - tool was created while pick boosting was active
    * - tool hasn't been boosted yet
    * - tool doesn't have max mining level already
    */
    public static boolean canBoostMiningLevel(NBTTagCompound tags)
    {
        return tags.hasKey(TAG_IS_BOOSTED) && !isBoosted(tags) && getHarvestLevel(tags) < HarvestLevels.max;
    }

    /**
     * Add the leveling specific NBT.
     * @param tag The tag that should recieve the data. Usually InfiTool Tag.
     */
    public static void addLevelingTags(NBTTagCompound tag)
    {
        // we start with level 1
        tag.setInteger(TAG_LEVEL, 1);
        // and no xp :(
        tag.setLong(TAG_EXP, 0);

        // mining level boost
        int hlvl = tag.getInteger("HarvestLevel");
        // only tools with >stone level can be boosted
        if(Config.levelingPickaxeBoost && hlvl > 0) {
            tag.setLong(TAG_BOOST_EXP, 0);
            tag.setBoolean(TAG_IS_BOOSTED, false);

            // reduce harvestlevel by 1 if pickaxe boosting is required
            if(Config.pickaxeBoostRequired) {
                    tag.setInteger("HarvestLevel", hlvl - 1);
            }
        }
    }

    /**
     * Updates the tool information with the given tool and boost xp. This SETS the xp!
     * @param player     Required for awesome *ding* sound
     * @param toolXP     Value the tool XP shall be set to. -1 for no change.
     * @param boostXP    Value the mining-boost XP shall be set to. -1 for no change.
     */
	public static void updateXP(ItemStack tool, EntityPlayer player, long toolXP, long boostXP)
	{
		NBTTagCompound tags = tool.getTagCompound().getCompoundTag("InfiTool");
		if (!hasLevel(tags)) return;

		int level = getLevel(tags);

		boolean leveled = false;
		boolean pickLeveled = false;

        // Update Tool XP
		if (toolXP >= 0 && hasXp(tags) && level > 0 && level < MAX_LEVEL)
		{
            // set new xp value
			tags.setLong(TAG_EXP, toolXP);

			// check for levelup
			if (toolXP >= getRequiredXp(tool, tags))
			{
				levelUpTool(tool, player);
				leveled = true;
			}
		}

        // handle mining boost XP
        if(Config.levelingPickaxeBoost) {
            // we can only if we have a proper material (>stone) and are not max mining level already
            if (canBoostMiningLevel(tags)) {
                tags.setLong(TAG_BOOST_EXP, boostXP);

                // check for mining boost levelup!
                if (boostXP >= getRequiredBoostXp(tool)) {
                    levelUpMiningLevel(tool, player, leveled);

                    pickLeveled = true;
                }
            }
        }


        // if we got a levelup, play a sound!
		if ((leveled || pickLeveled) && !player.worldObj.isRemote)
            player.worldObj.playSoundAtEntity(player, Reference.RESOURCE + ":chime", 0.9f, 1.0f);
	}

	public static void addXP(ItemStack tool, EntityPlayer player, long xp)
	{
		if (player.capabilities.isCreativeMode) return;

		NBTTagCompound tags = tool.getTagCompound().getCompoundTag("InfiTool");

        // only if we have a level or xp
        if(!hasLevel(tags) || !hasXp(tags))
            return;

        // tool EXP
		Long toolXp = -1L;
        if(hasXp(tags))
            toolXp = getXp(tags) + xp;

        // mininglevel boost EXP
        Long boostXp = -1L;
        if(hasBoostXp(tags))
            boostXp = getBoostXp(tags) + xp;


        // update the tool information
		updateXP(tool, player, toolXp, boostXp);
	}

	public static int getRequiredBoostXp(ItemStack tool)
	{
		return getRequiredXp(tool, null, true);
	}

	public static int getRequiredXp(ItemStack tool, NBTTagCompound tags)
	{
		return getRequiredXp(tool, tags, false);
	}

	protected static int getRequiredXp(ItemStack tool, NBTTagCompound tags, boolean miningBoost)
	{
		if (tags == null) tags = tool.getTagCompound().getCompoundTag("InfiTool");

		float base = 400;

		if (tool.getItem() instanceof Weapon || tool.getItem() instanceof Shortbow)
		{
			if (tool.getItem() instanceof Scythe) base *= 1.5f;
			base *= Config.xpRequiredWeaponsPercentage / 100f;
		}
		else
		{
			int miningSpeed = tags.getInteger("MiningSpeed");
			int divider = 1;
			if (tags.hasKey("MiningSpeed2"))
			{
				miningSpeed += tags.getInteger("MiningSpeed2");
				divider += 1;
			}
			if (tags.hasKey("MiningSpeedHandle"))
			{
				miningSpeed += tags.getInteger("MiningSpeedHandle");
				divider += 1;
			}
			if (tags.hasKey("MiningSpeedExtra"))
			{
				miningSpeed += tags.getInteger("MiningSpeedExtra");
				divider += 1;
			}

			base = 100f;
			base += (float)miningSpeed / (float)divider / 2f;

			if (tool.getItem() instanceof Hatchet) base /= 2f;
			else if (tool.getItem() instanceof Shovel) base *= 2f;
			else if (tool.getItem() instanceof Mattock) base *= 2.5f;
			else if (tool.getItem() instanceof LumberAxe) base *= 3f;
			else if (tool.getItem() instanceof Hammer) base *= 6f;
			else if (tool.getItem() instanceof Excavator) base *= 9f;

			base *= Config.xpRequiredToolsPercentage / 100f;
		}

		if (miningBoost)
		{
			int harvestLevelCopper = HarvestLevels._2_copper;
			int harvestLevel = TConstructRegistry.getMaterial(tags.getInteger("Head")).harvestLevel();
			if (harvestLevel >= harvestLevelCopper) base *= Math.pow(Config.xpPerLevelMultiplier, harvestLevel - harvestLevelCopper);
			base *= Config.levelingPickaxeBoostXpPercentage / 100f;
		}
		else
		{
			int level = tags.getInteger("ToolLevel");
			if (level >= 1) base *= Math.pow(Config.xpPerLevelMultiplier, level - 1);
		}

		return Math.round(base);
	}

    /**
     * Applies all the logic for increasing the tool level. This is only specific to the *tool* level, and has no relation to the mining-level-boost!
     */
	public static void levelUpTool(ItemStack stack, EntityPlayer player)
	{
		NBTTagCompound tags = stack.getTagCompound().getCompoundTag("InfiTool");
		World world = player.worldObj;

        // *ding* levelup!
		int level = getLevel(tags);
        level++;
		tags.setInteger(TAG_LEVEL, level);

		boolean isTool = stack.getItem() instanceof HarvestTool;

        // reset tool xp to 0, since we're at a new level now
        tags.setLong(TAG_EXP, 0L);

        // tell the player how awesome he is
        if (!world.isRemote)
        {
            String message = "";
            switch (level)
            {
                case 2: message = "\u00a73You begin to feel comfortable handling the " + stack.getDisplayName(); break;
                case 3: message = "\u00a73You are now accustomed to the weight of the " + stack.getDisplayName(); break;
                case 4: message = "\u00a73You have become adept at handling the " + stack.getDisplayName(); break;
                case 5: message = "\u00a73You are now an expert at using the " + stack.getDisplayName() + "\u00a73!"; break;
                case 6: message = "\u00a73You have mastered the " + stack.getDisplayName() + "\u00a73!"; break;
            }

            if (!message.equalsIgnoreCase(""))
            {
                player.addChatMessage(new ChatComponentText(message));
            }
        }

        int currentModifiers = tags.getInteger("Modifiers");

        // Add Modifier for leveling up?
        if(Config.toolLevelingExtraModifiers)
        {
            int modifiersToAdd = 0;
            // check if we are supposed to add a modifier at this levelup
            for(int lvl : Config.toolModifiersAtLevels)
                if(level == lvl)
                    modifiersToAdd++;
                    // yes, no break. this means if a level is in the list multiple times, you get multiple modifiers

            if(modifiersToAdd > 0)
            {
                currentModifiers += modifiersToAdd;
                tags.setInteger("Modifiers", currentModifiers);

                // fancy message on clientside
                if(!world.isRemote) {
                    // todo: solve "modifier(s)" more beautiful because localization
                    if(world.rand.nextInt(10) < modifiersToAdd)
                        player.addChatMessage(new ChatComponentText("\u00a79More Bling for your Thing (+" + modifiersToAdd + " modifier" + (modifiersToAdd>1 ? "s" : "") + ")."));
                    else
                        player.addChatMessage(new ChatComponentText("\u00a79You notice room for improvement (+" + modifiersToAdd + " modifier" + (modifiersToAdd>1 ? "s" : "") + ")."));
                }
            }
        }


        // Add random bonuses on leveling up?
		if (Config.toolLevelingRandomBonuses)
		{
            RandomBonusses.tryModifying(player, stack);
		}
	}

	public static void levelUpMiningLevel(ItemStack stack, EntityPlayer player, boolean leveled)
	{
		NBTTagCompound tags = stack.getTagCompound().getCompoundTag("InfiTool");

        // we only apply that once
        if(isBoosted(tags))
            return;

        // reset miningboost xp to 0
        if(hasBoostXp(tags))
            tags.setLong(TAG_BOOST_EXP, 0L);

        // fancy message
		if (player != null) {
            if(!player.worldObj.isRemote) {
                if (!leveled)
                    player.addChatMessage(new ChatComponentText("\u00a73Suddenly, a flash of light shines from the tip of your " + stack.getDisplayName() + "\u00a73 (+1 mining level)"));
                else
                    player.addChatMessage(new ChatComponentText("\u00a79Suddenly, a flash of light shines from the tip of the pickaxe (+1 mining level)"));
            }
        }

		tags.setBoolean(TAG_IS_BOOSTED, true);
        // increase harvest level by 1
		tags.setInteger("HarvestLevel", tags.getInteger("HarvestLevel") + 1);
	}

    /*
	private static boolean tryModify(EntityPlayer player, ItemStack stack, int rnd, boolean isTool)
	{
		ItemModifier mod = null;
		Item item = stack.getItem();

		ItemStack[] nullItemStack = new ItemStack[] {};
		if (rnd < 1)
		{
			mod = new ModInteger(nullItemStack, 4, "Moss", Config.mossRepairSpeed, "\u00a72", "Auto-Repair");
			if (!player.worldObj.isRemote)
				player.addChatMessage(new ChatComponentText("\u00a79It seems to have accumulated a patch of moss (+1 repair)"));
		}
		else if (rnd < 2 && (!isTool && !(item instanceof Shortbow) || isTool && (item instanceof Pickaxe || item instanceof Hammer)))
		{
			mod = new IguanaModLapis(nullItemStack, 10, new int[]{100});
			if (((IguanaModLapis)mod).canModify(stack, nullItemStack)) {
				if (!player.worldObj.isRemote)
					player.addChatMessage(new ChatComponentText("\u00a79Perhaps holding on to it will bring you luck? (+100 luck)"));
			} else return false;
		}
		else if (rnd < 6 && (isTool || item instanceof Shortbow))
		{
			mod = new IguanaModRedstone(nullItemStack, 2, 50);
			if (((IguanaModRedstone)mod).canModify(stack, nullItemStack, true)) {
				if (!player.worldObj.isRemote)
					player.addChatMessage(new ChatComponentText("\u00a79You spin it around with a flourish (+1 haste)"));
			} else return false;
		}
		else if (rnd < 3 && !isTool && !(item instanceof Shortbow))
		{
			mod = new IguanaModAttack("Quartz", nullItemStack, 11, 30);
			if (((IguanaModAttack)mod).canModify(stack, nullItemStack, true)) {
				if (!player.worldObj.isRemote)
					player.addChatMessage(new ChatComponentText("\u00a79You take the time to sharpen the dull edges of the blade (+1 attack)"));
			} else return false;
		}
		else if (rnd < 4 && !isTool && !(item instanceof Shortbow))
		{
			mod = new ModInteger(nullItemStack, 13, "Beheading", 1, "\u00a7d", "Beheading");
			if (!player.worldObj.isRemote)
				player.addChatMessage(new ChatComponentText("\u00a79You could take someones head off with that! (+1 beheading)"));
		}
		else if (rnd < 5 && !isTool && !(item instanceof Shortbow))
		{
			mod = new IguanaModBlaze(nullItemStack, 7, new int[]{25});
			if (((IguanaModBlaze)mod).canModify(stack, nullItemStack)) {
				if (!player.worldObj.isRemote)
					player.addChatMessage(new ChatComponentText("\u00a79It starts to feels more hot to the touch (+1 fire aspect)"));
			} else return false;
		}
		else if (rnd < 6 && !isTool && !(item instanceof Shortbow))
		{
			mod = new ModInteger(nullItemStack, 8, "Necrotic", 1, "\u00a78", "Life Steal");
			if (!player.worldObj.isRemote)
				player.addChatMessage(new ChatComponentText("\u00a79It shudders with a strange energy (+1 life steal)"));
		}
		else if (rnd < 7 && !isTool && !(item instanceof Shortbow))
		{
			mod = new ModSmite("Smite", 14, nullItemStack, new int[]{ 36});
			if (!player.worldObj.isRemote)
				player.addChatMessage(new ChatComponentText("\u00a79It begins to radiate a slight glow (+1 smite)"));
		}
		else if (rnd < 8 && !isTool && !(item instanceof Shortbow))
		{
			mod = new ModAntiSpider("Anti-Spider",15, nullItemStack, new int[]{ 4});
			if (!player.worldObj.isRemote)
				player.addChatMessage(new ChatComponentText("\u00a79A strange odor emanates from the weapon (+1 bane of arthropods)"));
		}
		else if (rnd < 9 && !isTool)
		{
			mod = new IguanaModPiston(nullItemStack, 3, new int[]{10});
			if (((IguanaModPiston)mod).canModify(stack, nullItemStack)) {
				if (!player.worldObj.isRemote)
					player.addChatMessage(new ChatComponentText("\u00a79Feeling more confident, you can more easily keep your assailants at bay (+1 knockback)"));
			} else return false;
		}
		else if (rnd < 10)
		{
			mod = new ModReinforced(nullItemStack, 16, 1);
			if (!player.worldObj.isRemote)
				player.addChatMessage(new ChatComponentText("\u00a79Fixing up the wear and tear should make it last a little longer (+1 reinforced)"));
		}

		if (mod == null) return false;

		mod.addMatchingEffect(stack);
		mod.modify(nullItemStack, stack);
		return true;
	}
	*/
}