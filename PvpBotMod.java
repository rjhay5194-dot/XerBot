package dev.pvpbotcmd;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.CommandNode;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopped;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.fluid.FluidState;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry.Reference;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class PvpBotMod implements ModInitializer {
   static final Map<String, PvpBotMod.Opt> OPTS = new LinkedHashMap<>();
   static final PvpBotMod.Opt ATTACK_RANGE = opt("range", "attack_range", 3.0, 1.0, 6.0, false, "blocks: melee reach");
   static final PvpBotMod.Opt BUNNY_HOP = opt("bunnyhop", "bunny_hop", 1.0, 0.0, 1.0, true, "bunny hop while closing a gap");
   static final PvpBotMod.Opt HOP_STOP = opt("hopstop", "hop_stop_distance", 3.5, 1.0, 8.0, false, "stop hopping within this many blocks of the target");
   static final PvpBotMod.Opt REVENGE = opt("revenge", "revenge", 1.0, 0.0, 1.0, true, "whoever hits a bot becomes its target");
   static final PvpBotMod.Opt EAT_HEARTS = opt(
      "eat", "eat_below_hearts", 5.0, 0.0, 10.0, false, "run and eat once health drops to or below this many hearts (0 = never)"
   );
   static final PvpBotMod.Opt EAT_COUNT = opt("eatcount", "eat_count", 3.0, 1.0, 10.0, false, "food items eaten per retreat, minimum");
   static final PvpBotMod.Opt EAT_UNTIL_HEARTS = opt(
      "eatuntil", "eat_until_hearts", 9.0, 0.0, 20.0, false, "keep eating past eatcount until health reaches this many hearts (0 = ignore)"
   );
   static final PvpBotMod.Opt HUNGER_EAT_LEVEL = opt(
      "hungereat", "hunger_eat_level", 6.0, 0.0, 20.0, false, "run and eat if the hunger bar drops to or below this (0 = never)"
   );
   static final PvpBotMod.Opt EAT_FULL_SPEED = opt(
      "eatfullspeed", "eat_full_speed", 1.0, 0.0, 1.0, true, "ignore vanilla's eating slowdown: keep sprinting while eating"
   );
   static final PvpBotMod.Opt DURABILITY_SWAP = opt(
      "durabilityswap", "durability_swap", 1.0, 0.0, 1.0, true, "swap in a spare weapon/armor piece when the current one is nearly broken"
   );
   static final PvpBotMod.Opt DURABILITY_THRESHOLD = opt(
      "durabilitythreshold", "durability_swap_percent", 15.0, 1.0, 100.0, false, "percent durability remaining that counts as \"nearly broken\""
   );
   static final PvpBotMod.Opt FLEE_DIST = opt(
      "flee", "flee_distance", 8.0, 3.0, 30.0, false, "blocks to put between the bot and the target before it starts eating"
   );
   static final PvpBotMod.Opt CRIT_CHANCE = opt("critchance", "crit_chance", 25.0, 0.0, 100.0, false, "percent chance to go for a jump-crit when it can");
   static final PvpBotMod.Opt CRIT_RANGE = opt("critrange", "crit_range", 4.0, 1.0, 6.0, false, "blocks within which a crit can be attempted");
   static final PvpBotMod.Opt FALL_CRIT = opt("fallcrit", "fall_crit", 1.0, 0.0, 1.0, true, "always crit while already falling and in range");
   static final PvpBotMod.Opt CRIT_CHAIN_CHANCE = opt(
      "critchainchance", "crit_chain_chance", 40.0, 0.0, 100.0, false, "percent chance a crit against a vulnerable target starts a guaranteed-crit burst"
   );
   static final PvpBotMod.Opt CRIT_CHAIN_MIN = opt("critchainmin", "crit_chain_min_ticks", 20.0, 0.0, 200.0, false, "shortest a guaranteed-crit burst lasts");
   static final PvpBotMod.Opt CRIT_CHAIN_MAX = opt("critchainmax", "crit_chain_max_ticks", 60.0, 0.0, 200.0, false, "longest a guaranteed-crit burst lasts");
   static final PvpBotMod.Opt AUTO_TARGET = opt("autotarget", "auto_target", 1.0, 0.0, 1.0, true, "idle bots pick the nearest survival player");
   static final PvpBotMod.Opt FIND_RANGE = opt(
      "findrange", "find_range", 24.0, 4.0, 128.0, false, "blocks within which auto-target and focus fire look for a target"
   );
   static final PvpBotMod.Opt AIM = opt("aim", "aim_ticks", 3.0, 0.0, 20.0, false, "HeroBot look-delta ticks (lower = snappier aim)");
   static final PvpBotMod.Opt REACT_MIN = opt("reactmin", "reaction_min_ticks", 3.0, 0.0, 40.0, false, "shortest reaction delay before a swing, in ticks");
   static final PvpBotMod.Opt REACT_MAX = opt("reactmax", "reaction_max_ticks", 7.0, 0.0, 40.0, false, "longest reaction delay before a swing, in ticks");
   static final PvpBotMod.Opt PREDICT_TICKS = opt(
      "predictaim", "predictive_aim_ticks", 3.0, 0.0, 10.0, false, "ticks of target movement led when aim ticks is low (0 = no lead)"
   );
   static final PvpBotMod.Opt AIM_CONE = opt(
      "aimcone", "aim_cone_degrees", 4.0, 0.0, 20.0, false, "skip re-aiming while already within this many degrees of the target"
   );
   static final PvpBotMod.Opt AIM_SPREAD = opt(
      "aimspread", "aim_spread_chance", 40.0, 0.0, 100.0, false, "percent chance to aim somewhere on the target's body other than its head"
   );
   static final PvpBotMod.Opt OVERSHOOT_CHANCE = opt(
      "overshootchance", "aim_overshoot_chance", 30.0, 0.0, 100.0, false, "percent chance per aim update to flick past the target and correct next tick"
   );
   static final PvpBotMod.Opt OVERSHOOT_DEGREES = opt(
      "overshootdegrees", "aim_overshoot_degrees", 15.0, 1.0, 60.0, false, "how far past the target an overshoot flick swings"
   );
   static final PvpBotMod.Opt BOT_FIGHT = opt("botfight", "bots_fight_each_other", 1.0, 0.0, 1.0, true, "PvP bots fight each other (not just human players)");
   static final PvpBotMod.Opt WEB_ESCAPE = opt("webescape", "web_escape", 1.0, 0.0, 1.0, true, "use a water bucket to break out of a cobweb, if carried");
   static final PvpBotMod.Opt WEB_BREAK = opt(
      "webbreak", "web_break", 1.0, 0.0, 1.0, true, "mine out of a cobweb with the best weapon if no water bucket is carried"
   );
   static final PvpBotMod.Opt STRAFE = opt("strafe", "strafe", 1.0, 0.0, 1.0, true, "strafe left/right while close to the target");
   static final PvpBotMod.Opt STRAFE_TICKS = opt("strafeticks", "strafe_ticks", 12.0, 2.0, 40.0, false, "base ticks between strafe-side re-rolls");
   static final PvpBotMod.Opt COMBO_CHANCE = opt("combochance", "combo_chance", 50.0, 0.0, 100.0, false, "percent chance to start a combo after a hit lands");
   static final PvpBotMod.Opt COMBO_STAP_CHANCE = opt(
      "combostap", "combo_stap_weight", 30.0, 0.0, 100.0, false, "percent chance to pick s-tap when a combo starts"
   );
   static final PvpBotMod.Opt COMBO_WTAP_CHANCE = opt(
      "combowtap", "combo_wtap_weight", 30.0, 0.0, 100.0, false, "percent chance to pick w-tap when a combo starts"
   );
   static final PvpBotMod.Opt COMBO_UPPERCUT_CHANCE = opt(
      "combouppercut", "combo_uppercut_weight", 20.0, 0.0, 100.0, false, "percent chance to pick uppercut when a combo starts"
   );
   static final PvpBotMod.Opt COMBO_STRAFECOMBO_CHANCE = opt(
      "combostrafecombo", "combo_strafecombo_weight", 20.0, 0.0, 100.0, false, "percent chance to pick strafecombo when a combo starts"
   );
   static final PvpBotMod.Opt COMBO_TAP_TICKS = opt("combotapticks", "combo_tap_ticks", 2.0, 1.0, 10.0, false, "ticks an s-tap/w-tap pause lasts");
   static final PvpBotMod.Opt COMBO_UPPERCUT_RANGE = opt(
      "uppercutrange", "combo_uppercut_range", 3.5, 1.0, 8.0, false, "blocks within which an uppercut can be started"
   );
   static final PvpBotMod.Opt COMBO_UPPERCUT_WINDOW = opt(
      "uppercutwindow", "combo_uppercut_window", 6.0, 1.0, 20.0, false, "ticks the uppercut's rising hit window stays open"
   );
   static final PvpBotMod.Opt COMBO_STRAFE_SWITCH_TICKS = opt(
      "combostrafeticks", "combo_strafecombo_ticks", 6.0, 2.0, 30.0, false, "ticks between side switches during strafecombo"
   );
   static final PvpBotMod.Opt MIX_CHANCE = opt(
      "mixchance", "combo_mix_chance", 30.0, 0.0, 100.0, false, "percent chance a started combo is MixCombo instead of one fixed type"
   );
   static final PvpBotMod.Opt MIX_MAXHITS = opt("mixmaxhits", "combo_mix_max_hits", 5.0, 1.0, 20.0, false, "hits a MixCombo series runs before ending");
   static final PvpBotMod.Opt MIX_SWITCHCHANCE = opt(
      "mixswitchchance", "combo_mix_switch_chance", 60.0, 0.0, 100.0, false, "percent chance MixCombo re-rolls its type on each hit"
   );
   static final PvpBotMod.Opt COMBOHITS_MINHIT = opt(
      "combohitsmin", "combo_hits_escape_min", 8.0, 1.0, 60.0, false, "shortest run of hits taken before RandomRepeatedComboHits can trigger"
   );
   static final PvpBotMod.Opt COMBOHITS_MAXHIT = opt(
      "combohitsmax", "combo_hits_escape_max", 15.0, 1.0, 60.0, false, "longest run of hits taken before RandomRepeatedComboHits can trigger"
   );
   static final PvpBotMod.Opt COMBOHITS_ESCAPECHANCE = opt(
      "combohitsescapechance",
      "combo_hits_escape_chance",
      80.0,
      0.0,
      100.0,
      false,
      "percent chance the streak actually triggers an escape once the threshold is hit"
   );
   static final PvpBotMod.Opt COMBOHITS_WINDCHARGECHANCE = opt(
      "windchargechance", "combo_hits_windcharge_weight", 34.0, 0.0, 100.0, false, "percent chance the RandomRepeatedComboHits escape is a wind charge launch"
   );
   static final PvpBotMod.Opt WINDCHARGE_HEAL_CHANCE = opt(
      "windchargehealchance",
      "windcharge_heal_chance",
      20.0,
      0.0,
      100.0,
      false,
      "percent chance a low-health/hungry retreat uses an item escape (wind charge or ender pearl) instead of just running"
   );
   static final PvpBotMod.Opt WINDCHARGE_WAIT_TICKS = opt(
      "windchargewaitticks",
      "windcharge_wait_ticks",
      5.0,
      1.0,
      20.0,
      false,
      "ticks a wind-charge escape holds still (still drifting away, no hop) before jumping and throwing together"
   );
   static final PvpBotMod.Opt WINDCHARGE_BLOCKED_TICKS = opt(
      "windchargeblockedticks",
      "windcharge_blocked_ticks",
      10.0,
      2.0,
      60.0,
      false,
      "consecutive stuck ticks while fleeing before it tries a wind charge over the obstacle"
   );
   static final PvpBotMod.Opt COMBOHITS_WEBCHANCE = opt(
      "escapewebchance", "combo_hits_webtrap_weight", 33.0, 0.0, 100.0, false, "percent chance the RandomRepeatedComboHits escape is the panic web trap"
   );
   static final PvpBotMod.Opt COMBOHITS_RUNCHANCE = opt(
      "escaperunchance", "combo_hits_run_weight", 33.0, 0.0, 100.0, false, "percent chance the RandomRepeatedComboHits escape is just running away to eat"
   );
   static final PvpBotMod.Opt JUMP_CHANCE = opt(
      "jumpchance", "jump_chance", 15.0, 0.0, 100.0, false, "percent chance, every quarter second, that a bot close to its target jumps"
   );
   static final PvpBotMod.Opt JUMP_RANGE = opt("jumprange", "jump_range", 5.0, 1.0, 12.0, false, "blocks within which bots randomly jump on their target");
   static final PvpBotMod.Opt JUMP_RESET = opt(
      "jumpreset", "jump_reset_chance", 20.0, 0.0, 100.0, false, "percent chance to jump right after taking a hit, to shake off the knockback"
   );
   static final PvpBotMod.Opt FLINCH_CHANCE = opt(
      "flinchchance", "flinch_chance", 35.0, 0.0, 100.0, false, "percent chance to hesitate a moment before swinging back after taking a hit"
   );
   static final PvpBotMod.Opt FLINCH_TICKS_MIN = opt("flinchticksmin", "flinch_ticks_min", 4.0, 0.0, 40.0, false, "shortest a flinch delay lasts");
   static final PvpBotMod.Opt FLINCH_TICKS_MAX = opt("flinchticksmax", "flinch_ticks_max", 10.0, 0.0, 40.0, false, "longest a flinch delay lasts");
   static final PvpBotMod.Opt SHIELD_CHANCE = opt(
      "shield",
      "shield_chance",
      45.0,
      0.0,
      100.0,
      false,
      "percent chance to raise a shield when the target is about to swing and in range (needs a shield in the inventory)"
   );
   static final PvpBotMod.Opt SHIELD_RANGE = opt(
      "shieldrange", "shield_range", 1.5, 0.5, 6.0, false, "blocks within which the bot will consider raising a shield (it never shields while comboing)"
   );
   static final PvpBotMod.Opt SHIELD_TICKS = opt(
      "shieldticks",
      "shield_ticks",
      10.0,
      2.0,
      100.0,
      false,
      "shortest a shield hold lasts (a shield needs about 5 ticks to start blocking, so keep this well above that)"
   );
   static final PvpBotMod.Opt SHIELD_TICKS_MAX = opt(
      "shieldticksmax", "shield_ticks_max", 50.0, 2.0, 150.0, false, "longest a shield hold lasts; each hold is randomized between shieldticks and this"
   );
   static final PvpBotMod.Opt POSTSWING_SHIELD_CHANCE = opt(
      "postswingshield",
      "postswing_shield_chance",
      80.0,
      0.0,
      100.0,
      false,
      "percent chance the bot raises its shield right after landing a hit (only when it isn't running a combo)"
   );
   static final PvpBotMod.Opt POSTSWING_SHIELD_TICKS_MIN = opt(
      "postswingshieldticksmin", "postswing_shield_ticks_min", 8.0, 2.0, 100.0, false, "shortest a post-swing shield hold lasts"
   );
   static final PvpBotMod.Opt POSTSWING_SHIELD_TICKS_MAX = opt(
      "postswingshieldticksmax", "postswing_shield_ticks_max", 13.0, 2.0, 150.0, false, "longest a post-swing shield hold lasts"
   );
   static final PvpBotMod.Opt STUN_CHANCE = opt(
      "stun", "shield_stun_chance", 35.0, 0.0, 100.0, false, "percent chance to swap to an axe when the target raises a shield, to disable it"
   );
   static final PvpBotMod.Opt HIT_WEB = opt(
      "hitweb",
      "hit_web_chance",
      25.0,
      0.0,
      100.0,
      false,
      "percent chance that a landed hit is followed by really placing a cobweb at the target's feet (needs cobwebs in the inventory)"
   );
   static final PvpBotMod.Opt WEB_CRIT = opt(
      "webcrit",
      "guaranteed_web_crit",
      1.0,
      0.0,
      1.0,
      true,
      "guaranteed critical hit while the target is webbed; breaks an enclosing web open first if the target is boxed in"
   );
   static final PvpBotMod.Opt WEB_AVOID = opt(
      "webavoid", "web_avoidance", 1.0, 0.0, 1.0, true, "steer around cobwebs while closing in or fleeing (the target's own cell is never avoided)"
   );
   static final PvpBotMod.Opt PUNISH_CRIT_CHANCE = opt(
      "punishcritchance",
      "punish_crit_chance",
      50.0,
      0.0,
      100.0,
      false,
      "percent chance to guarantee a crit right back after the target lands a hit on the bot"
   );
   static final PvpBotMod.Opt MACE_AWARE = opt(
      "maceaware", "mace_awareness", 1.0, 0.0, 1.0, true, "raise the shield immediately if the target is holding a mace and airborne above the bot"
   );
   static final PvpBotMod.Opt MACE_FEAR = opt(
      "macefear", "mace_fear", 1.0, 0.0, 1.0, true, "bot gets scared (stops chasing) while the target is airborne with a mace, e.g. an elytra dive"
   );
   static final PvpBotMod.Opt MACE_FEAR_CHANCE = opt(
      "macefearchance", "mace_fear_chance", 100.0, 0.0, 100.0, false, "percent chance the bot gets scared when a mace dive starts"
   );
   static final PvpBotMod.Opt MACE_FEAR_RUN = opt(
      "macefearrun", "mace_fear_run_weight", 35.0, 0.0, 100.0, false, "percent of scared moments where the bot runs away instead of dodging in a small area"
   );
   static final PvpBotMod.Opt MACE_FEAR_RADIUS = opt(
      "macefearradius", "mace_fear_radius", 8.0, 2.0, 20.0, false, "blocks: how far the bot may wander from where it got scared while dodging"
   );
   static final PvpBotMod.Opt FALL_SHIELD_CHANCE = opt(
      "fallshieldchance", "fall_shield_chance", 60.0, 0.0, 100.0, false, "percent chance to raise the shield when the target is falling toward the bot"
   );
   static final PvpBotMod.Opt FALL_MACE = opt(
      "fallmace", "fall_mace", 1.0, 0.0, 1.0, true, "when the bot falls toward the target it may smash it with a mace"
   );
   static final PvpBotMod.Opt FALL_MACE_CHANCE = opt(
      "fallmacechance", "fall_mace_chance", 60.0, 0.0, 100.0, false, "percent chance to try the mace smash when falling (otherwise it just clutches)"
   );
   static final PvpBotMod.Opt FALL_MACE_HEIGHT = opt(
      "fallmaceheight", "fall_mace_height", 6.0, 3.0, 40.0, false, "blocks of fall left before the bot reacts (mace smash or water clutch)"
   );
   static final PvpBotMod.Opt FALL_CLUTCH = opt(
      "fallclutch", "fall_clutch", 1.0, 0.0, 1.0, true, "place a water bucket to cancel fall damage when it cannot mace the target"
   );
   static final PvpBotMod.Opt CLUTCH_HEIGHT = opt(
      "clutchheight", "clutch_height", 3.0, 1.0, 4.5, false, "blocks above the ground where the bot places the water"
   );
   static final PvpBotMod.Opt CLUTCH_SUCCESS = opt(
      "clutchsuccess", "clutch_success", 90.0, 0.0, 100.0, false, "percent chance the water clutch works"
   );
   static final PvpBotMod.Opt EAT_KNOCKBACK = opt(
      "eatknockback", "eat_knockback", 1.0, 0.0, 1.0, true, "before eating, sprint-hit the target to knock it back first"
   );
   static final PvpBotMod.Opt EAT_KNOCKBACK_TICKS = opt(
      "eatknockbackticks", "eat_knockback_ticks", 10.0, 3.0, 40.0, false, "ticks the bot tries to knock the target back before it just eats"
   );
   static final PvpBotMod.Opt MACE_SWAP = opt(
      "maceswap", "mace_swap", 1.0, 0.0, 1.0, true, "attribute swap: hit with the sword's damage and the mace's smash bonus while falling"
   );
   static final PvpBotMod.Opt MACE_SWAP_CHANCE = opt(
      "maceswapchance", "mace_swap_chance", 50.0, 0.0, 100.0, false, "percent chance to attribute swap sword to mace on a falling hit"
   );
   static final PvpBotMod.Opt SPEAR_SWAP = opt(
      "spearswap", "spear_swap", 1.0, 0.0, 1.0, true, "attribute swap: aim with a spear for long reach, then switch to the mace and hit"
   );
   static final PvpBotMod.Opt SPEAR_SWAP_CHANCE = opt(
      "spearswapchance", "spear_swap_chance", 40.0, 0.0, 100.0, false, "percent chance to use the spear swap when the target is out of normal reach"
   );
   static final PvpBotMod.Opt SPEAR_REACH = opt(
      "spearreach", "spear_reach", 4.5, 3.0, 7.0, false, "blocks of reach the spear swap can hit from"
   );
   static final PvpBotMod.Opt PATHFIND = opt(
      "pathfind", "pathfind", 1.0, 0.0, 1.0, true, "bots use A* pathfinding when blocked, stuck, or the target is at a different height"
   );
   static final PvpBotMod.Opt PATH_MODE = choiceOpt(
      "pathmode", "path_mode", 2, "who may search for a path at once: all = everyone, one = one bot at a time, half = half of the bots at a time", "all", "one", "half"
   );
   static final PvpBotMod.Opt PATH_RANGE = opt(
      "pathrange", "path_range", 24.0, 8.0, 48.0, false, "blocks around the bot the pathfinder searches"
   );
   static final PvpBotMod.Opt PATH_NODES = opt(
      "pathnodes", "path_nodes", 400.0, 50.0, 2000.0, false, "max nodes one search may expand"
   );
   static final PvpBotMod.Opt PATH_REPLAN = opt(
      "pathreplan", "path_replan_ticks", 20.0, 5.0, 100.0, false, "ticks between path searches"
   );
   static final PvpBotMod.Opt PATH_BUDGET = opt(
      "pathbudget", "path_budget", 600.0, 100.0, 5000.0, false, "max nodes ALL bots together may expand per tick"
   );
   static final PvpBotMod.Opt PATH_DROP = opt(
      "pathdrop", "path_drop", 3.0, 1.0, 6.0, false, "blocks the bot may drop while pathing (bigger drops only when the target is far below)"
   );
   static final PvpBotMod.Opt MACE_RETRY = opt(
      "maceretry", "mace_retry", 1.0, 0.0, 1.0, true, "after a mace smash that did not hit, the bot tries again until it hits"
   );
   static final PvpBotMod.Opt MACE_RETRY_MAX = opt(
      "maceretrymax", "mace_retry_max", 6.0, 1.0, 20.0, false, "max retries after a missed mace smash"
   );
   static final PvpBotMod.Opt MACE_CHAIN_GAP = opt(
      "macechaingap", "mace_chain_gap_ticks", 20.0, 5.0, 100.0, false, "ticks between chained mace smashes"
   );
   static final PvpBotMod.Opt MACE_PRESSURE = opt(
      "macepressure", "mace_pressure", 1.0, 0.0, 1.0, true, "while the target is healing (eating/drinking) the bot chains wind charge mace smashes to pressure it"
   );
   static final PvpBotMod.Opt MACE_AIM_ERROR = opt(
      "maceaimerror", "mace_aim_error", 2.0, 0.0, 20.0, false, "max degrees of aim error while diving with the mace"
   );
   static final PvpBotMod.Opt MACE_MISTAKE = opt(
      "macemistake", "mace_mistake_chance", 10.0, 0.0, 100.0, false, "percent chance a mace dive is sloppy (big aim error, likely misses)"
   );
   static final PvpBotMod.Opt WIND_JUMP_LATE_CHANCE = opt(
      "windjumplatechance", "wind_jump_late_chance", 30.0, 0.0, 100.0, false, "percent chance the jump after the wind charge is delayed more"
   );
   static final PvpBotMod.Opt WIND_JUMP_LATE_MAX = opt(
      "windjumplatemax", "wind_jump_late_max_ticks", 6.0, 1.0, 20.0, false, "max extra ticks added when the jump is delayed"
   );
   static final PvpBotMod.Opt WINDCLIMB = opt(
      "windclimb", "wind_climb", 1.0, 0.0, 1.0, true, "use a wind charge to climb when the target stands 2+ blocks higher and the bot is blocked"
   );
   static final PvpBotMod.Opt WINDCLIMB_HEIGHT = opt(
      "windclimbheight", "wind_climb_height", 2.0, 1.0, 10.0, false, "blocks: how much higher the target must be to trigger a wind charge climb"
   );
   static final PvpBotMod.Opt WINDMACE_CHANCE = opt(
      "windmacechance", "wind_mace_chance", 15.0, 0.0, 100.0, false, "percent chance, rolled about once a second, to launch up with a wind charge and mace smash the target"
   );
   static final PvpBotMod.Opt WINDMACE_HEARTS = opt(
      "windmacehearts", "wind_mace_min_hearts", 8.0, 0.0, 10.0, false, "hearts the bot needs before it tries a wind charge mace launch"
   );
   static final PvpBotMod.Opt WINDMACE_COOLDOWN = opt(
      "windmacecooldown", "wind_mace_cooldown_ticks", 200.0, 20.0, 2400.0, false, "ticks to wait between wind charge mace launches"
   );
   static final PvpBotMod.Opt WIND_JUMP_DELAY = opt(
      "windjumpdelay", "wind_jump_delay_ticks", 2.0, 0.0, 10.0, false, "ticks after throwing the wind charge before the bot jumps (charge first, then jump)"
   );
   static final PvpBotMod.Opt RECOVERY_CHANCE = opt(
      "recoverychance",
      "recovery_punish_chance",
      60.0,
      0.0,
      100.0,
      false,
      "percent chance to skip the normal reaction delay right after the target's weapon goes on cooldown"
   );
   static final PvpBotMod.Opt TOTEM_PUNISH_TICKS = opt(
      "totempunishticks", "totem_punish_ticks", 40.0, 0.0, 200.0, false, "ticks of guaranteed crits right after the target's totem of undying pops"
   );
   static final PvpBotMod.Opt PRECISION_LOCK = opt(
      "precisionlock", "precision_lock", 1.0, 0.0, 1.0, true, "aim true (no wobble/spread/overshoot) on the exact tick a swing actually fires"
   );
   static final PvpBotMod.Opt ARCHER_RUSH = opt(
      "archerrush", "archer_rush", 1.0, 0.0, 1.0, true, "tighten up and rush in if the target draws a bow or crossbow"
   );
   static final PvpBotMod.Opt WEB_LIFETIME = opt(
      "webtime", "web_lifetime_ticks", 100.0, 0.0, 1200.0, false, "ticks before a cobweb dropped by a bot disappears again (0 = never)"
   );
   static final PvpBotMod.Opt WEB_LEAD_TICKS = opt(
      "webleadticks", "web_lead_ticks", 3.0, 0.0, 10.0, false, "ticks of target movement a placed web leads by, so a moving target doesn't just walk out of it"
   );
   static final PvpBotMod.Opt WEB_ZONE_AWARE = opt(
      "webzone", "web_zone_awareness", 1.0, 0.0, 1.0, true, "flee toward a nearby existing cobweb, when there is one, to slow the chase"
   );
   static final PvpBotMod.Opt WEB_ZONE_RADIUS = opt(
      "webzoneradius", "web_zone_radius", 8.0, 2.0, 20.0, false, "blocks searched for a nearby cobweb to flee toward"
   );
   static final PvpBotMod.Opt STUCK = opt("stuck", "stuck_detection", 1.0, 0.0, 1.0, true, "hop and sidestep if barely moving while trying to close a gap");
   static final PvpBotMod.Opt TOTEM = opt(
      "totem", "totem_offhand", 1.0, 0.0, 1.0, true, "swap a totem of undying into the off-hand at very low health, and back out again after recovering"
   );
   static final PvpBotMod.Opt TOTEM_HEARTS = opt("totemhearts", "totem_switch_hearts", 2.0, 0.0, 10.0, false, "health at or below which the totem swap happens");
   static final PvpBotMod.Opt MASS_MAX = opt("massmax", "mass_spawn_max", 20.0, 1.0, 100.0, false, "most bots /pvpbot mass_spawn will queue at once");
   static final PvpBotMod.Opt KEEP_DISTANCE = opt(
      "keepdistance", "keep_distance", 2.8, 0.0, 6.0, false, "blocks the bot tries to hold from the target (0 = always close in)"
   );
   static final PvpBotMod.Opt PRESSURE_KEEP_DIST = opt(
      "pressurekeep", "pressure_keep_distance", 0.5, 0.0, 3.0, false, "tighter keep-distance used while the target is eating or fleeing"
   );
   static final PvpBotMod.Opt STRAFE_CHANCE = opt(
      "strafechance", "strafe_chance", 60.0, 0.0, 100.0, false, "percent chance to switch (or hold) strafe side on each roll"
   );
   static final PvpBotMod.Opt MISS_CHANCE = opt("miss", "miss_chance", 10.0, 0.0, 100.0, false, "percent chance a swing whiffs at air instead of landing");
   static final PvpBotMod.Opt WOBBLE = opt("wobble", "aim_wobble_degrees", 2.0, 0.0, 15.0, false, "degrees of random aim wobble");
   static final PvpBotMod.Opt VOID_AWARE = opt(
      "voidaware", "void_lava_awareness", 1.0, 0.0, 1.0, true, "steer around ledges, void and lava on the way to (and back from) the target"
   );
   static final PvpBotMod.Opt COCOON = opt("cocoon", "web_cocoon", 1.0, 0.0, 1.0, true, "web itself in and eat inside the cocoon at very low health");
   static final PvpBotMod.Opt COCOON_HEARTS = opt(
      "cocoonhearts", "cocoon_hearts", 3.0, 0.0, 10.0, false, "health at or below which the cocoon is used instead of just running"
   );
   static final PvpBotMod.Opt WEBTRAP_CHANCE = opt(
      "webtrapchance", "panic_webtrap_chance", 40.0, 0.0, 100.0, false, "percent chance to lay a wall of cobwebs behind itself before running, at panic health"
   );
   static final PvpBotMod.Opt WEBTRAP_HEARTS = opt(
      "webtraphearts", "panic_webtrap_hearts", 2.0, 0.0, 10.0, false, "health at or below which the panic web trap can trigger"
   );
   static final PvpBotMod.Opt WEBTRAP_COUNT = opt("webtrapcount", "panic_webtrap_count", 2.0, 2.0, 6.0, false, "cobwebs laid by the panic web trap");
   static final PvpBotMod.Opt RANDOMWEB_CHANCE = opt(
      "randomwebchance", "random_web_chance", 0.0, 0.0, 100.0, false, "disabled: random web placement with no hit needed (kept at 0)"
   );
   static final PvpBotMod.Opt RANDOMWEB_MINDIST = opt(
      "randomwebmindist", "random_web_min_dist", 0.5, 0.2, 3.0, false, "shortest distance for a random web placement"
   );
   static final PvpBotMod.Opt RANDOMWEB_MAXDIST = opt(
      "randomwebmaxdist", "random_web_max_dist", 1.4, 0.5, 4.0, false, "longest distance for a random web placement"
   );
   static final PvpBotMod.Opt RANDOMWEB_COOLDOWN = opt(
      "randomwebcooldown", "random_web_cooldown_ticks", 60.0, 10.0, 600.0, false, "ticks between random web placement attempts"
   );
   static final PvpBotMod.Opt POTIONS = opt(
      "potions", "splash_potions", 1.0, 0.0, 1.0, true, "bots use splash potions of healing/speed/strength/fire resistance"
   );
   static final PvpBotMod.Opt POTION_HEARTS = opt(
      "potionhearts", "potion_heal_hearts", 6.0, 0.0, 10.0, false, "health at or below which a healing splash potion is thrown"
   );
   static final PvpBotMod.Opt POTION_HEARTS2 = opt(
      "potionhearts2", "potion_heal_hearts_double", 3.0, 0.0, 10.0, false, "health at or below which two healing potions are queued instead of one"
   );
   static final PvpBotMod.Opt POTION_DELAY = opt(
      "potiondelay", "potion_delay_ticks", 200.0, 20.0, 2400.0, false, "ticks before another potion run can start after one finishes"
   );
   static final PvpBotMod.Opt LOOT = opt("loot", "loot_pickup", 1.0, 0.0, 1.0, true, "bots walk to dropped items they don't have when no fight is close");
   static final PvpBotMod.Opt LOOT_RANGE = opt("lootrange", "loot_range", 12.0, 3.0, 48.0, false, "blocks within which bots notice dropped items");
   static final PvpBotMod.Opt EXP_BOTTLE = opt(
      "expbottle",
      "exp_bottle_mending_repair",
      1.0,
      0.0,
      1.0,
      true,
      "throw an experience bottle at itself to repair mending armor when all of it is low on durability"
   );
   static final PvpBotMod.Opt EXP_BOTTLE_THRESHOLD = opt(
      "expbottlethreshold",
      "exp_bottle_durability_percent",
      15.0,
      1.0,
      100.0,
      false,
      "percent durability remaining, for every mending armor piece, that triggers this"
   );
   static final PvpBotMod.Opt FOCUS_CHANCE = opt(
      "focuschance", "focus_chance", 60.0, 0.0, 100.0, false, "percent chance a bot joins its team's focus target (0 = no focus fire)"
   );
   static final PvpBotMod.Opt FOCUS_MIN = opt("focusmin", "focus_min_ticks", 100.0, 20.0, 2400.0, false, "shortest time a team keeps one focus target");
   static final PvpBotMod.Opt FOCUS_MAX = opt("focusmax", "focus_max_ticks", 300.0, 20.0, 2400.0, false, "longest time a team keeps one focus target");
   static final PvpBotMod.Opt OPS_ONLY = opt(
      "opsonly", "ops_only_commands", 1.0, 0.0, 1.0, true, "only operators (those who can use /gamemode) may run /pvpbot"
   );
   static final PvpBotMod.Opt TAUNTS = opt("taunts", "taunts", 0.0, 0.0, 1.0, true, "bots post short chat lines when they win a kill or the match");
   static final PvpBotMod.Opt FFA_LEAVE = opt(
      "ffaleave", "ffa_bot_leave_on_death", 1.0, 0.0, 1.0, true, "an FFA match turns HeroBot's botleaveondeath on (and off again afterwards)"
   );
   static final String[] LEVEL_NAMES = new String[]{"beginner", "easy", "normal", "hard", "insane", "perfect"};
   static final List<String> DIFFICULTY_CHOICES = List.of("1", "2", "3", "4", "5", "6", "beginner", "easy", "normal", "hard", "insane", "perfect");
   static final PvpBotMod.Opt[] PRESET_OPTS = new PvpBotMod.Opt[]{
      AIM,
      REACT_MIN,
      REACT_MAX,
      CRIT_CHANCE,
      STRAFE,
      STRAFE_TICKS,
      COMBO_CHANCE,
      MIX_CHANCE,
      BUNNY_HOP,
      EAT_HEARTS,
      FALL_CRIT,
      JUMP_CHANCE,
      JUMP_RESET,
      SHIELD_CHANCE,
      STUN_CHANCE,
      STRAFE_CHANCE,
      MISS_CHANCE,
      WOBBLE,
      FOCUS_CHANCE,
      POTIONS,
      POTION_HEARTS,
      SHIELD_TICKS,
      SHIELD_RANGE
   };
   static final double[][] PRESETS = new double[][]{
      {8.0, 8.0, 16.0, 0.0, 0.0, 14.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 30.0, 6.0, 0.0, 0.0, 5.0, 10.0, 1.0},
      {5.0, 5.0, 10.0, 10.0, 1.0, 16.0, 20.0, 10.0, 0.0, 3.0, 0.0, 5.0, 5.0, 20.0, 10.0, 25.0, 20.0, 4.0, 30.0, 0.0, 5.0, 14.0, 1.2},
      {3.0, 3.0, 7.0, 25.0, 1.0, 12.0, 45.0, 25.0, 1.0, 5.0, 1.0, 15.0, 20.0, 45.0, 35.0, 55.0, 10.0, 2.0, 60.0, 1.0, 6.0, 20.0, 1.5},
      {2.0, 1.0, 4.0, 45.0, 1.0, 9.0, 65.0, 40.0, 1.0, 6.0, 1.0, 25.0, 35.0, 65.0, 60.0, 75.0, 4.0, 1.0, 80.0, 1.0, 7.0, 20.0, 1.5},
      {1.0, 0.0, 2.0, 70.0, 1.0, 6.0, 85.0, 60.0, 1.0, 7.0, 1.0, 35.0, 55.0, 85.0, 85.0, 90.0, 1.0, 0.0, 95.0, 1.0, 8.0, 18.0, 1.5},
      {0.0, 0.0, 0.0, 100.0, 1.0, 6.0, 100.0, 80.0, 1.0, 7.0, 1.0, 50.0, 100.0, 100.0, 100.0, 100.0, 0.0, 0.0, 100.0, 1.0, 8.0, 12.0, 1.5}
   };
   static String difficultyName = "normal";
   static final String[] PLAYSTYLE_NAMES = new String[]{"aggressive", "defensive", "combo"};
   static final List<String> PLAYSTYLE_CHOICES = List.of("aggressive", "defensive", "combo");
   static final PvpBotMod.Opt[] PLAYSTYLE_OPTS = new PvpBotMod.Opt[]{
      KEEP_DISTANCE, STRAFE_CHANCE, CRIT_CHAIN_CHANCE, EAT_HEARTS, POTION_HEARTS, COMBO_CHANCE, MIX_CHANCE, JUMP_CHANCE, STUN_CHANCE, COCOON_HEARTS, HIT_WEB
   };
   static final double[][] PLAYSTYLES = new double[][]{
      {1.8, 75.0, 55.0, 3.0, 4.0, 55.0, 20.0, 25.0, 45.0, 2.0, 35.0},
      {3.5, 45.0, 30.0, 7.0, 8.0, 25.0, 10.0, 8.0, 25.0, 4.0, 20.0},
      {2.2, 20.0, 70.0, 5.0, 6.0, 85.0, 55.0, 5.0, 60.0, 3.0, 30.0}
   };
   static String playstyleName = "none";
   static final double STRAFE_MAX_DIST = 6.0;
   static final double HOP_RESUME_MARGIN = 1.0;
   static final int FLEE_MAX_TICKS = 80;
   static final int EAT_TIMEOUT_TICKS = 60;
   static final int EAT_COOLDOWN_TICKS = 60;
   static final int CRIT_TIMEOUT_TICKS = 25;
   static final int WEB_BREAK_TIMEOUT_TICKS = 80;
   static final double POTION_SAFE_DIST = 5.0;
   static final double POTION_HEAL_CLOSE_DIST = 2.0;
   static final double KEEP_BAND = 0.5;
   static final double LOOT_FIGHT_DIST = 10.0;
   static final int PEARL_TIMEOUT_TICKS = 40;
   static final double PEARL_TELEPORT_DIST = 3.0;
   static final String[] TAUNT_LINES = new String[]{"gg", "ez", "too slow", "nice try", "sit down", "get good"};
   static final Set<String> BOTS = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
   static final Set<String> STOPPED = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
   static final Map<String, PvpBotMod.Fight> FIGHTS = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
   static final Map<String, PvpBotMod.GotoJob> GOTOS = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
   static final Map<String, PvpBotMod.Pending> PENDING = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
   static final Map<String, Integer> LAST_HURT = new HashMap<>();
   static final ArrayDeque<PvpBotMod.MassJob> MASS_QUEUE = new ArrayDeque<>();
   static final ArrayList<PvpBotMod.PlacedWeb> PLACED_WEBS = new ArrayList<>();
   static CommandDispatcher<ServerCommandSource> dispatcher;
   static int globalTick = 0;
   static int pathBudgetLeft = 0;
   static int fightCount = 0;
   static final Map<String, PvpBotMod.LootState> LOOTING = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
   static final Map<String, PvpBotMod.Focus> FOCUS = new HashMap<>();
   static final Set<String> FFA_ALIVE = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
   static final Set<String> FFA_BOTS = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
   static final Set<String> FFA_HUMANS = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
   static final Set<String> FFA_OUT_HUMANS = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
   static final Map<String, Integer> FFA_KILLS = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
   static final Map<String, String> LAST_ATTACKER = new HashMap<>();
   static boolean ffaActive;
   static boolean ffaTeams;
   static boolean ffaAutoStart;
   static boolean ffaAutoTeams;
   static int ffaGoTick;
   static int ffaLastCount = -1;
   static String ffaOwner = "";
   static final SuggestionProvider<ServerCommandSource> BOT_NAMES = (ctx, builder) -> CommandSource.suggestMatching(BOTS, builder);
   static final String[] DEFAULT_NAMES = new String[]{
      "Ash",
      "Blaze",
      "Cinder",
      "Drake",
      "Ember",
      "Frost",
      "Glint",
      "Havoc",
      "Ivory",
      "Jinx",
      "Kilo",
      "Lynx",
      "Maverick",
      "Nova",
      "Onyx",
      "Prowl",
      "Quartz",
      "Raven",
      "Slate",
      "Talon",
      "Umbra",
      "Vex",
      "Wraith",
      "Xeno",
      "Yeti",
      "Zephyr",
      "Ashen",
      "Bramble",
      "Crux",
      "Dusk",
      "Echo",
      "Flint",
      "Grit",
      "Hollow",
      "Iron",
      "Jolt",
      "Karma",
      "Lurk",
      "Marrow",
      "Night",
      "Orbit",
      "Pyre",
      "Quill",
      "Rift",
      "Storm",
      "Thorn",
      "Ursa",
      "Vortex",
      "Warden",
      "Zed"
   };

   static PvpBotMod.Opt opt(String cmd, String key, double def, double min, double max, boolean bool, String desc) {
      PvpBotMod.Opt o = new PvpBotMod.Opt(cmd, key, def, min, max, bool, desc, null);
      OPTS.put(cmd, o);
      return o;
   }

   static PvpBotMod.Opt choiceOpt(String cmd, String key, int def, String desc, String... choices) {
      PvpBotMod.Opt o = new PvpBotMod.Opt(cmd, key, def, 0.0, choices.length - 1, false, desc, choices);
      OPTS.put(cmd, o);
      return o;
   }

   public void onInitialize() {
      loadConfig();
      CommandRegistrationCallback.EVENT.register((CommandRegistrationCallback)(d, registryAccess, environment) -> {
         dispatcher = d;
         register(d);
      });
      ServerTickEvents.END_SERVER_TICK.register(BotSupport::tick);
      ServerLifecycleEvents.SERVER_STOPPED.register((ServerStopped)server -> {
         BOTS.clear();
         STOPPED.clear();
         FIGHTS.clear();
         GOTOS.clear();
         PENDING.clear();
         LAST_HURT.clear();
         MASS_QUEUE.clear();
         PLACED_WEBS.clear();
         LOOTING.clear();
         FOCUS.clear();
         FFA_ALIVE.clear();
         FFA_OUT_HUMANS.clear();
         ffaActive = false;
         ffaAutoStart = false;
      });
   }

   static void register(CommandDispatcher<ServerCommandSource> d) {
      LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("pvpbot");
      root.requires(BotSupport::allowed);
      root.then(
         CommandManager.literal("spawn")
            .then(CommandManager.argument("name", StringArgumentType.word()).executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "name"))))
      );
      root.then(
         CommandManager.literal("adopt")
            .then(CommandManager.argument("name", StringArgumentType.word()).executes(ctx -> adopt(ctx, StringArgumentType.getString(ctx, "name"))))
      );
      root.then(
         CommandManager.literal("fight")
            .then(
               ((RequiredArgumentBuilder)CommandManager.argument("name", StringArgumentType.word())
                     .suggests(BOT_NAMES)
                     .executes(ctx -> fight(ctx, StringArgumentType.getString(ctx, "name"), null)))
                  .then(
                     CommandManager.argument("target", StringArgumentType.word())
                        .executes(ctx -> fight(ctx, StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "target")))
                  )
            )
      );
      root.then(
         CommandManager.literal("goto")
            .then(
               CommandManager.literal("stop")
                  .then(
                     CommandManager.argument("name", StringArgumentType.word())
                        .suggests(BOT_NAMES)
                        .executes(ctx -> gotoStop(ctx, StringArgumentType.getString(ctx, "name")))
                  )
            )
            .then(
               CommandManager.argument("name", StringArgumentType.word())
                  .suggests(BOT_NAMES)
                  .then(
                     CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                        .executes(ctx -> gotoBot(
                           ctx,
                           StringArgumentType.getString(ctx, "name"),
                           BlockPosArgumentType.getBlockPos(ctx, "pos")
                        ))
                  )
            )
      );
      root.then(
         CommandManager.literal("stop")
            .then(
               CommandManager.argument("name", StringArgumentType.word())
                  .suggests(BOT_NAMES)
                  .executes(ctx -> stop(ctx, StringArgumentType.getString(ctx, "name")))
            )
      );
      root.then(
         CommandManager.literal("ping")
            .then(
               CommandManager.argument("name", StringArgumentType.word())
                  .suggests(BOT_NAMES)
                  .then(
                     CommandManager.argument("ms", IntegerArgumentType.integer(0, 1000))
                        .executes(ctx -> ping(ctx, StringArgumentType.getString(ctx, "name"), IntegerArgumentType.getInteger(ctx, "ms")))
                  )
            )
      );
      root.then(
         CommandManager.literal("remove")
            .then(
               CommandManager.argument("name", StringArgumentType.word())
                  .suggests(BOT_NAMES)
                  .executes(ctx -> remove(ctx, StringArgumentType.getString(ctx, "name")))
            )
      );
      root.then(CommandManager.literal("clear").executes(PvpBotMod::clear));
      root.then(CommandManager.literal("list").executes(PvpBotMod::list));
      root.then(CommandManager.literal("options").executes(PvpBotMod::listOptions));
      root.then(CommandManager.literal("help").executes(PvpBotMod::help));
      root.then(CommandManager.literal("reload").executes(PvpBotMod::reload));
      root.then(CommandManager.literal("gui").executes(PvpBotMod::openGui));
      root.then(
         CommandManager.literal("status")
            .then(
               CommandManager.argument("name", StringArgumentType.word())
                  .suggests(BOT_NAMES)
                  .executes(ctx -> status(ctx, StringArgumentType.getString(ctx, "name")))
            )
      );
      root.then(
         CommandManager.literal("mass_spawn")
            .then(
               ((RequiredArgumentBuilder)CommandManager.argument("count", IntegerArgumentType.integer(1, 100))
                     .executes(ctx -> massSpawn(ctx, IntegerArgumentType.getInteger(ctx, "count"), null)))
                  .then(
                     CommandManager.argument("team", StringArgumentType.word())
                        .executes(ctx -> massSpawn(ctx, IntegerArgumentType.getInteger(ctx, "count"), StringArgumentType.getString(ctx, "team")))
                  )
            )
      );
      root.then(
         ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("ffa")
                     .then(
                        ((LiteralArgumentBuilder)CommandManager.literal("start").executes(ctx -> ffaStart(ctx, false)))
                           .then(CommandManager.literal("teams").executes(ctx -> ffaStart(ctx, true)))
                     ))
                  .then(CommandManager.literal("stop").executes(PvpBotMod::ffaStop)))
               .then(CommandManager.literal("restart").executes(PvpBotMod::ffaRestart)))
            .then(CommandManager.literal("stats").executes(PvpBotMod::ffaStats))
      );
      root.then(
         ((LiteralArgumentBuilder)CommandManager.literal("difficulty").executes(PvpBotMod::showDifficulty))
            .then(
               CommandManager.argument("level", StringArgumentType.word())
                  .suggests((ctx, builder) -> CommandSource.suggestMatching(DIFFICULTY_CHOICES, builder))
                  .executes(ctx -> setDifficulty(ctx, StringArgumentType.getString(ctx, "level")))
            )
      );
      root.then(
         ((LiteralArgumentBuilder)CommandManager.literal("playstyle").executes(PvpBotMod::showPlaystyle))
            .then(
               CommandManager.argument("name", StringArgumentType.word())
                  .suggests((ctx, builder) -> CommandSource.suggestMatching(PLAYSTYLE_CHOICES, builder))
                  .executes(ctx -> setPlaystyle(ctx, StringArgumentType.getString(ctx, "name")))
            )
      );

      for (PvpBotMod.Opt o : OPTS.values()) {
         LiteralArgumentBuilder<ServerCommandSource> node = (LiteralArgumentBuilder<ServerCommandSource>)CommandManager.literal(o.cmd).executes(ctx -> showOpt(ctx, o));
         if (o.choices != null) {
            node.then(
               CommandManager.argument("value", StringArgumentType.word())
                  .suggests((ctx, builder) -> CommandSource.suggestMatching(Arrays.asList(o.choices), builder))
                  .executes(ctx -> setChoice(ctx, o, StringArgumentType.getString(ctx, "value")))
            );
         } else if (o.bool) {
            node.then(
               CommandManager.argument("value", BoolArgumentType.bool()).executes(ctx -> setOpt(ctx, o, BoolArgumentType.getBool(ctx, "value") ? 1.0 : 0.0))
            );
         } else {
            node.then(
               CommandManager.argument("value", DoubleArgumentType.doubleArg(o.min, o.max))
                  .executes(ctx -> setOpt(ctx, o, DoubleArgumentType.getDouble(ctx, "value")))
            );
         }

         root.then(node);
      }

      d.register(root);
   }

   static boolean herobotPresent() {
      return dispatcher != null && dispatcher.getRoot().getChild("playerspawn") != null && dispatcher.getRoot().getChild("player") != null;
   }

   static int spawn(CommandContext<ServerCommandSource> ctx, String name) throws CommandSyntaxException {
      ServerCommandSource src = (ServerCommandSource)ctx.getSource();
      ServerPlayerEntity me = src.getPlayerOrThrow();
      MinecraftServer server = src.getServer();
      if (!herobotPresent()) {
         src.sendError(Text.literal("HeroBot is not installed: /playerspawn and /player were not found."));
         return 0;
      } else if (name.length() > 16) {
         src.sendError(Text.literal("Bot names can be at most 16 characters."));
         return 0;
      } else if (server.getPlayerManager().getPlayer(name) != null) {
         src.sendError(Text.literal("A player named " + name + " is already online."));
         return 0;
      } else if (PENDING.containsKey(name)) {
         src.sendError(Text.literal(name + " is already being spawned."));
         return 0;
      } else {
         server.getCommandManager().parseAndExecute(src, "playerspawn " + name);
         PENDING.put(name, new PvpBotMod.Pending(me.getName().getString(), false, null, null));
         return 1;
      }
   }

   static int adopt(CommandContext<ServerCommandSource> ctx, String name) throws CommandSyntaxException {
      ServerCommandSource src = (ServerCommandSource)ctx.getSource();
      ServerPlayerEntity me = src.getPlayerOrThrow();
      if (src.getServer().getPlayerManager().getPlayer(name) == null) {
         src.sendError(Text.literal("No player named " + name + " is online."));
         return 0;
      } else if (name.equalsIgnoreCase(me.getName().getString())) {
         src.sendError(Text.literal("You can't adopt yourself."));
         return 0;
      } else {
         BOTS.add(name);
         STOPPED.remove(name);
         src.sendFeedback(() -> Text.literal("Now controlling " + name + "."), false);
         return 1;
      }
   }

   static int fight(CommandContext<ServerCommandSource> ctx, String name, String target) throws CommandSyntaxException {
      ServerCommandSource src = (ServerCommandSource)ctx.getSource();
      if (!BOTS.contains(name)) {
         src.sendError(Text.literal(name + " is not a PvP bot yet. If you spawned it with /playerspawn, run /pvpbot adopt " + name + " first."));
         return 0;
      } else {
         String t = target != null ? target : src.getPlayerOrThrow().getName().getString();
         if (name.equalsIgnoreCase(t)) {
            src.sendError(Text.literal("A bot can't fight itself."));
            return 0;
         } else {
            STOPPED.remove(name);
            GOTOS.remove(name);
            PvpBotMod.Fight existing = FIGHTS.get(name);
            if (existing != null) {
               existing.target = t;
            } else {
               FIGHTS.put(name, new PvpBotMod.Fight(t));
            }

            src.sendFeedback(() -> Text.literal(name + " is now fighting " + t + "."), false);
            return 1;
         }
      }
   }

   static int stop(CommandContext<ServerCommandSource> ctx, String name) {
      ServerCommandSource src = (ServerCommandSource)ctx.getSource();
      if (!BOTS.contains(name)) {
         src.sendError(Text.literal(name + " is not a PvP bot yet. If you spawned it with /playerspawn, run /pvpbot adopt " + name + " first."));
         return 0;
      } else {
         FIGHTS.remove(name);
         GOTOS.remove(name);
         STOPPED.add(name);
         BotSupport.run(src.getServer(), "player " + name + " stop");
         src.sendFeedback(() -> Text.literal(name + " stopped. It won't pick targets again until you use /pvpbot fight " + name + "."), false);
         return 1;
      }
   }

   static int gotoBot(CommandContext<ServerCommandSource> ctx, String name, BlockPos requested) {
      ServerCommandSource src = (ServerCommandSource)ctx.getSource();
      if (!BOTS.contains(name)) {
         src.sendError(Text.literal(name + " is not a PvP bot yet. If you spawned it with /playerspawn, run /pvpbot adopt " + name + " first."));
         return 0;
      }

      ServerPlayerEntity bot = src.getServer().getPlayerManager().getPlayer(name);
      if (bot == null) {
         src.sendError(Text.literal(name + " is not online."));
         return 0;
      }

      PvpBotMod.FIGHTS.remove(name);
      STOPPED.add(name);
      GOTOS.put(name, new PvpBotMod.GotoJob(requested));
      BotSupport.run(src.getServer(), "player " + name + " stop");
      src.sendFeedback(() -> Text.literal(name + " is going to " + requested.getX() + " " + requested.getY() + " " + requested.getZ() + " using pathfinding."), false);
      return 1;
   }

   static int gotoStop(CommandContext<ServerCommandSource> ctx, String name) {
      ServerCommandSource src = (ServerCommandSource)ctx.getSource();
      if (!BOTS.contains(name)) {
         src.sendError(Text.literal(name + " is not a PvP bot yet. If you spawned it with /playerspawn, run /pvpbot adopt " + name + " first."));
         return 0;
      }

      GOTOS.remove(name);
      STOPPED.add(name);
      BotSupport.run(src.getServer(), "player " + name + " stop");
      src.sendFeedback(() -> Text.literal(name + " stopped its goto path."), false);
      return 1;
   }

   static int gotoFinish(MinecraftServer server, String name, PvpBotMod.GotoJob g, ServerPlayerEntity bot) {
      STOPPED.add(name);
      BotSupport.run(server, "player " + name + " stop");
      bot.sendMessage(Text.literal("Goto reached " + g.target.getX() + " " + g.target.getY() + " " + g.target.getZ() + "."));
      return 1;
   }

   static int ping(CommandContext<ServerCommandSource> ctx, String name, int ms) {
      ServerCommandSource src = (ServerCommandSource)ctx.getSource();
      if (!BOTS.contains(name)) {
         src.sendError(Text.literal(name + " is not a PvP bot yet. If you spawned it with /playerspawn, run /pvpbot adopt " + name + " first."));
         return 0;
      } else {
         BotSupport.run(src.getServer(), "player " + name + " ping " + ms);
         src.sendFeedback(() -> Text.literal(name + " ping set to " + ms + " ms."), false);
         return 1;
      }
   }

   static int remove(CommandContext<ServerCommandSource> ctx, String name) {
      ServerCommandSource src = (ServerCommandSource)ctx.getSource();
      if (!BOTS.contains(name)) {
         src.sendError(Text.literal(name + " is not a PvP bot yet. If you spawned it with /playerspawn, run /pvpbot adopt " + name + " first."));
         return 0;
      } else {
         removeBot(src.getServer(), name);
         src.sendFeedback(() -> Text.literal("Removed " + name + "."), false);
         return 1;
      }
   }

   static int clear(CommandContext<ServerCommandSource> ctx) {
      ServerCommandSource src = (ServerCommandSource)ctx.getSource();

      for (String name : new TreeSet<>(BOTS)) {
         removeBot(src.getServer(), name);
      }

      src.sendFeedback(() -> Text.literal("Removed all PvP bots."), false);
      return 1;
   }

   static int list(CommandContext<ServerCommandSource> ctx) {
      StringBuilder sb = new StringBuilder();

      for (String name : BOTS) {
         PvpBotMod.Fight f = FIGHTS.get(name);
         PvpBotMod.GotoJob g = GOTOS.get(name);
         if (sb.length() > 0) {
            sb.append(", ");
         }

         sb.append(name);
         if (g != null) {
            sb.append(" -> goto ").append(g.target.getX()).append(" ").append(g.target.getY()).append(" ").append(g.target.getZ());
         } else if (f != null) {
            sb.append(" -> ").append(f.target);
         }
      }

      String text = BOTS.isEmpty() ? "No PvP bots." : "PvP bots: " + sb;
      ((ServerCommandSource)ctx.getSource()).sendFeedback(() -> Text.literal(text), false);
      return BOTS.size();
   }

   static int listOptions(CommandContext<ServerCommandSource> ctx) {
      showDifficulty(ctx);

      for (PvpBotMod.Opt o : OPTS.values()) {
         ((ServerCommandSource)ctx.getSource()).sendFeedback(() -> Text.literal("/pvpbot " + o.cmd + " = " + o.show() + "     (" + o.desc + ")"), false);
      }

      return OPTS.size();
   }

   static int help(CommandContext<ServerCommandSource> ctx) {
      String[] lines = new String[]{
         "/pvpbot spawn <name>     |    mass_spawn <count> [team]     |    adopt <name>",
         "/pvpbot fight <name> [target]    |    stop <name>    |   remove <name>    |    clear    |    list",
         "/pvpbot status <name>    |    ping <name> <ms>    |    reload    |    options    |    gui",
         "/pvpbot ffa start [teams]    |    ffa stop    |    ffa restart    |    ffa stats",
         "/pvpbot difficulty <1-6 or beginner/easy/normal/hard/insane/perfect> (perfect is brutal)",
         "Teams use vanilla /team (/team add red, /team join red Bot1). Bots leave creative players alone.",
         "/pvpbot goto <bot> <x> <y> <z> sends a bot to a block position using the A* pathfinder. Coordinates support normal Minecraft absolute or relative syntax; y is the bot's feet block. Use /pvpbot goto stop <bot> to cancel.",
         "Every setting is /pvpbot <setting> [value]. Run /pvpbot options to see them all, or /pvpbot gui for a menu."
      };

      for (String line : lines) {
         ((ServerCommandSource)ctx.getSource()).sendFeedback(() -> Text.literal(line), false);
      }

      return 1;
   }

   static int reload(CommandContext<ServerCommandSource> ctx) {
      loadConfig();
      ((ServerCommandSource)ctx.getSource()).sendFeedback(() -> Text.literal("Reloaded config/pvpbotcmd.properties."), true);
      return 1;
   }

   static int openGui(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
      ServerPlayerEntity me = ((ServerCommandSource)ctx.getSource()).getPlayerOrThrow();
      Gui.open(me);
      return 1;
   }

   static int status(CommandContext<ServerCommandSource> ctx, String name) {
      ServerCommandSource src = (ServerCommandSource)ctx.getSource();
      ServerPlayerEntity bot = src.getServer().getPlayerManager().getPlayer(name);
      if (bot == null) {
         src.sendError(Text.literal(name + " is not online."));
         return 0;
      } else {
         PvpBotMod.Fight f = FIGHTS.get(name);
         PvpBotMod.GotoJob g = GOTOS.get(name);
         StringBuilder sb = new StringBuilder(name);
         sb.append(BOTS.contains(name) ? ": PvP bot" : ": not a PvP bot");
         if (g != null) {
            sb.append(", going to ").append(g.target.getX()).append(" ").append(g.target.getY()).append(" ").append(g.target.getZ());
         } else if (f == null) {
            sb.append(STOPPED.contains(name) ? ", stopped" : ", idle");
         } else {
            sb.append(", ").append(f.phase).append(" -> ").append(f.target);
            if (f.paused) {
               sb.append(", tapping");
            }

            if (f.hopping) {
               sb.append(", hopping");
            }

            if (f.crit) {
               sb.append(", going for a crit");
            }

            if (f.blocking) {
               sb.append(", blocking");
            }

            if (f.axeMode) {
               sb.append(", axe out");
            }

            if (f.webStage != 0) {
               sb.append(", escaping a cobweb");
            }
         }

         sb.append(
            String.format(
               Locale.ROOT,
               ", health %.1f, hunger %d, holding %s",
               bot.getHealth() + bot.getAbsorptionAmount(),
               bot.getHungerManager().getFoodLevel(),
               bot.getMainHandStack().getName().getString()
            )
         );
         String text = sb.toString();
         src.sendFeedback(() -> Text.literal(text), false);
         return 1;
      }
   }

   static int massSpawn(CommandContext<ServerCommandSource> ctx, int count, String team) throws CommandSyntaxException {
      ServerCommandSource src = (ServerCommandSource)ctx.getSource();
      ServerPlayerEntity me = src.getPlayerOrThrow();
      MinecraftServer server = src.getServer();
      if (!herobotPresent()) {
         src.sendError(Text.literal("HeroBot is not installed: /playerspawn and /player were not found."));
         return 0;
      } else if (count > MASS_MAX.asInt()) {
         src.sendError(Text.literal("At most " + MASS_MAX.asInt() + " bots at once. Change it with /pvpbot massmax <number>."));
         return 0;
      } else {
         Set<String> used = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

         for (PvpBotMod.MassJob job : MASS_QUEUE) {
            used.add(job.name);
         }

         List<String> names = pickRandomNames(server, count, used);
         if (names.isEmpty()) {
            src.sendError(Text.literal("No usable names found (or generated) in " + namesPath().getFileName() + "."));
            return 0;
         } else {
            queueMassJobs(server, me, names, team);
            int total = names.size();
            String shown = String.join(", ", names.subList(0, Math.min(3, names.size()))) + (names.size() > 3 ? ", ..." : "");
            src.sendFeedback(() -> Text.literal("Spawning " + total + " bots: " + shown + (team != null ? " on team " + team : "")), false);
            return total;
         }
      }
   }

   static List<String> pickRandomNames(MinecraftServer server, int count, Set<String> used) {
      List<String> pool = new ArrayList<>(loadNamePool());
      Collections.shuffle(pool, (Random)ThreadLocalRandom.current());
      List<String> picked = new ArrayList<>();

      for (String name : pool) {
         if (picked.size() >= count) {
            break;
         }

         if (isNameFree(server, used, name)) {
            used.add(name);
            picked.add(name);
         }
      }

      int guard = 0;

      while (picked.size() < count && !pool.isEmpty() && guard++ < count * 20) {
         String base = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));

         for (int n = 2; n < 100; n++) {
            String candidate = base + n;
            if (candidate.length() <= 16 && isNameFree(server, used, candidate)) {
               used.add(candidate);
               picked.add(candidate);
               break;
            }
         }
      }

      return picked;
   }

   static boolean isNameFree(MinecraftServer server, Set<String> used, String name) {
      return name.length() <= 16 && !used.contains(name) && !PENDING.containsKey(name) && server.getPlayerManager().getPlayer(name) == null;
   }

   static List<String> loadNamePool() {
      Path path = namesPath();
      if (!Files.exists(path)) {
         try (Writer out = Files.newBufferedWriter(path)) {
            out.write("# PvPBot mass_spawn draws names from this file, one per line, in random order.\n");
            out.write("# Edit it however you like; blank lines and lines starting with # are ignored.\n");

            for (String n : DEFAULT_NAMES) {
               out.write(n);
               out.write(10);
            }
         } catch (IOException var8) {
         }

         return new ArrayList<>(Arrays.asList(DEFAULT_NAMES));
      } else {
         List<String> names = new ArrayList<>();

         try {
            for (String line : Files.readAllLines(path)) {
               String n = line.trim();
               if (!n.isEmpty() && !n.startsWith("#") && n.length() <= 16) {
                  names.add(n);
               }
            }
         } catch (IOException var9) {
         }

         return (List<String>)(names.isEmpty() ? new ArrayList<>(Arrays.asList(DEFAULT_NAMES)) : names);
      }
   }

   static Path namesPath() {
      return FabricLoader.getInstance().getConfigDir().resolve("pvpbotcmd_names.txt");
   }

   static void queueMassJobs(MinecraftServer server, ServerPlayerEntity owner, List<String> names, String team) {
      Vec3d base = owner.getEntityPos();
      double radius = Math.max(3.0, Math.min(12.0, names.size() * 0.7));
      String ownerName = owner.getName().getString();
      if (team != null) {
         BotSupport.run(server, "team add " + team);
      }

      for (int i = 0; i < names.size(); i++) {
         double angle = (Math.PI * 2) * i / names.size();
         Vec3d pos = new Vec3d(base.x + Math.cos(angle) * radius, base.y, base.z + Math.sin(angle) * radius);
         MASS_QUEUE.add(new PvpBotMod.MassJob(ownerName, names.get(i), team, pos));
      }
   }

   static int ffaStart(CommandContext<ServerCommandSource> ctx, boolean teams) throws CommandSyntaxException {
      ServerCommandSource src = (ServerCommandSource)ctx.getSource();
      ServerPlayerEntity me = src.getPlayerOrThrow();
      String err = BotSupport.startFfa(src.getServer(), me.getName().getString(), teams);
      if (err != null) {
         src.sendError(Text.literal(err));
         return 0;
      } else {
         return 1;
      }
   }

   static int ffaStop(CommandContext<ServerCommandSource> ctx) {
      if (!ffaActive) {
         ((ServerCommandSource)ctx.getSource()).sendError(Text.literal("No FFA match is running."));
         return 0;
      } else {
         BotSupport.endFfa(((ServerCommandSource)ctx.getSource()).getServer(), false);
         return 1;
      }
   }

   static int ffaRestart(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
      ServerCommandSource src = (ServerCommandSource)ctx.getSource();
      ServerPlayerEntity me = src.getPlayerOrThrow();
      MinecraftServer server = src.getServer();
      ffaOwner = me.getName().getString();
      boolean teams = ffaTeams;
      if (ffaActive) {
         BotSupport.endFfa(server, false);
      }

      List<String> missing = new ArrayList<>();

      for (String n : FFA_BOTS) {
         if (server.getPlayerManager().getPlayer(n) == null && !PENDING.containsKey(n)) {
            missing.add(n);
         }
      }

      if (missing.isEmpty()) {
         String err = BotSupport.startFfa(server, ffaOwner, teams);
         if (err != null) {
            src.sendError(Text.literal(err));
            return 0;
         } else {
            return 1;
         }
      } else {
         queueMassJobs(server, me, missing, null);
         ffaAutoStart = true;
         ffaAutoTeams = teams;
         int respawnCount = missing.size();
         src.sendFeedback(() -> Text.literal("Rematch: respawning " + respawnCount + " bots, then a new countdown."), false);
         return 1;
      }
   }

   static int ffaStats(CommandContext<ServerCommandSource> ctx) {
      String text = (ffaActive ? "Match running, " + FFA_ALIVE.size() + " left. " : "No match running. ") + BotSupport.leaderboard();
      ((ServerCommandSource)ctx.getSource()).sendFeedback(() -> Text.literal(text), false);
      return 1;
   }

   static boolean isPresetOpt(PvpBotMod.Opt o) {
      for (PvpBotMod.Opt p : PRESET_OPTS) {
         if (p == o) {
            return true;
         }
      }

      return false;
   }

   static int showDifficulty(CommandContext<ServerCommandSource> ctx) {
      String text = "Difficulty: " + difficultyName;

      for (int i = 0; i < LEVEL_NAMES.length; i++) {
         if (LEVEL_NAMES[i].equals(difficultyName)) {
            text = text + " (level " + (i + 1) + " of 6)";
         }
      }

      if (difficultyName.equals("custom")) {
         text = text + " (a setting was changed after choosing a preset)";
      }

      String shown = text;
      ((ServerCommandSource)ctx.getSource()).sendFeedback(() -> Text.literal(shown), false);
      return 1;
   }

   static int setDifficulty(CommandContext<ServerCommandSource> ctx, String raw) {
      String r = raw.trim().toLowerCase(Locale.ROOT);
      int level = -1;

      for (int i = 0; i < LEVEL_NAMES.length; i++) {
         if (LEVEL_NAMES[i].equals(r) || Integer.toString(i + 1).equals(r)) {
            level = i;
            break;
         }
      }

      if (level < 0) {
         ((ServerCommandSource)ctx.getSource()).sendError(Text.literal("Unknown difficulty. Use 1-6 or beginner, easy, normal, hard, insane, perfect."));
         return 0;
      } else {
         setDifficultyLevel(level);
         String msg = "Difficulty set to " + LEVEL_NAMES[level] + " (level " + (level + 1) + " of 6).";
         ((ServerCommandSource)ctx.getSource()).sendFeedback(() -> Text.literal(msg), true);
         return 1;
      }
   }

   static void setDifficultyLevel(int level) {
      for (int i = 0; i < PRESET_OPTS.length; i++) {
         PvpBotMod.Opt o = PRESET_OPTS[i];
         o.value = Math.max(o.min, Math.min(o.max, PRESETS[level][i]));
      }

      difficultyName = LEVEL_NAMES[level];
      saveConfig();
   }

   static boolean isPlaystyleOpt(PvpBotMod.Opt o) {
      for (PvpBotMod.Opt p : PLAYSTYLE_OPTS) {
         if (p == o) {
            return true;
         }
      }

      return false;
   }

   static int showPlaystyle(CommandContext<ServerCommandSource> ctx) {
      String text = "Playstyle: " + playstyleName;
      if (playstyleName.equals("custom")) {
         text = text + " (a setting was changed after choosing one)";
      }

      String shown = text;
      ((ServerCommandSource)ctx.getSource()).sendFeedback(() -> Text.literal(shown), false);
      return 1;
   }

   static int setPlaystyle(CommandContext<ServerCommandSource> ctx, String raw) {
      String r = raw.trim().toLowerCase(Locale.ROOT);
      int idx = -1;

      for (int i = 0; i < PLAYSTYLE_NAMES.length; i++) {
         if (PLAYSTYLE_NAMES[i].equals(r)) {
            idx = i;
            break;
         }
      }

      if (idx < 0) {
         ((ServerCommandSource)ctx.getSource()).sendError(Text.literal("Unknown playstyle. Use aggressive, defensive, or combo."));
         return 0;
      } else {
         setPlaystyleIndex(idx);
         String msg = "Playstyle set to " + PLAYSTYLE_NAMES[idx] + ".";
         ((ServerCommandSource)ctx.getSource()).sendFeedback(() -> Text.literal(msg), true);
         return 1;
      }
   }

   static void setPlaystyleIndex(int idx) {
      for (int i = 0; i < PLAYSTYLE_OPTS.length; i++) {
         PvpBotMod.Opt o = PLAYSTYLE_OPTS[i];
         o.value = Math.max(o.min, Math.min(o.max, PLAYSTYLES[idx][i]));
      }

      playstyleName = PLAYSTYLE_NAMES[idx];
      saveConfig();
   }

   static int showOpt(CommandContext<ServerCommandSource> ctx, PvpBotMod.Opt o) {
      ((ServerCommandSource)ctx.getSource()).sendFeedback(() -> Text.literal(o.key + " = " + o.show() + "   (" + o.desc + ")"), false);
      return 1;
   }

   static int choiceIndex(PvpBotMod.Opt o, String raw) {
      String r = raw.trim().toLowerCase(Locale.ROOT);

      for (int i = 0; i < o.choices.length; i++) {
         if (o.choices[i].equals(r) || Integer.toString(i).equals(r)) {
            return i;
         }
      }

      return -1;
   }

   static int setChoice(CommandContext<ServerCommandSource> ctx, PvpBotMod.Opt o, String raw) {
      int idx = choiceIndex(o, raw);
      if (idx < 0) {
         ((ServerCommandSource)ctx.getSource()).sendError(Text.literal("Unknown value. Use one of: " + String.join(", ", o.choices)));
         return 0;
      } else {
         return setOpt(ctx, o, idx);
      }
   }

   static int setOpt(CommandContext<ServerCommandSource> ctx, PvpBotMod.Opt o, double v) {
      setOptValue(o, v);
      ((ServerCommandSource)ctx.getSource()).sendFeedback(() -> Text.literal(o.key + " set to " + o.show()), true);
      return 1;
   }

   static void setOptValue(PvpBotMod.Opt o, double v) {
      o.value = Math.max(o.min, Math.min(o.max, v));
      if (isPresetOpt(o)) {
         difficultyName = "custom";
      }

      if (isPlaystyleOpt(o)) {
         playstyleName = "custom";
      }

      saveConfig();
   }

   static void removeBot(MinecraftServer server, String name) {
      FIGHTS.remove(name);
      GOTOS.remove(name);
      BOTS.remove(name);
      STOPPED.remove(name);
      LAST_HURT.remove(name);
      LOOTING.remove(name);
      BotSupport.run(server, "player " + name + " disconnect");
   }

   static Path configPath() {
      return FabricLoader.getInstance().getConfigDir().resolve("pvpbotcmd.properties");
   }

   static void loadConfig() {
      Path path = configPath();
      if (!Files.exists(path)) {
         saveConfig();
      } else {
         Properties props = new Properties();

         try (Reader in = Files.newBufferedReader(path)) {
            props.load(in);
         } catch (IOException var11) {
            return;
         }

         difficultyName = props.getProperty("difficulty", "custom");
         playstyleName = props.getProperty("playstyle", "none");

         for (PvpBotMod.Opt o : OPTS.values()) {
            String raw = props.getProperty(o.key);
            if (raw != null) {
               try {
                  double v;
                  if (o.choices != null) {
                     int idx = choiceIndex(o, raw);
                     if (idx < 0) {
                        continue;
                     }

                     v = idx;
                  } else {
                     v = o.bool ? (Boolean.parseBoolean(raw.trim()) ? 1 : 0) : Double.parseDouble(raw.trim());
                  }

                  o.value = Math.max(o.min, Math.min(o.max, v));
               } catch (NumberFormatException var10) {
               }
            }
         }
      }
   }

   static void saveConfig() {
      Properties props = new Properties();
      props.setProperty("difficulty", difficultyName);
      props.setProperty("playstyle", playstyleName);
      String comment = "PvPBot Commands config. Change values in game with /pvpbot <setting> <value>, or /pvpbot gui.";

      for (PvpBotMod.Opt o : OPTS.values()) {
         props.setProperty(o.key, o.choices != null ? o.show() : (o.bool ? Boolean.toString(o.on()) : Double.toString(o.value)));
      }

      try (Writer out = Files.newBufferedWriter(configPath())) {
         props.store(out, comment);
      } catch (IOException var7) {
      }
   }

   static final class Fight {
      String target;
      ServerPlayerEntity lastBot;
      PvpBotMod.Phase phase = PvpBotMod.Phase.FIGHT;
      boolean started;
      boolean hopping;
      boolean paused;
      boolean crit;
      int weaponSlot = -1;
      int pauseUntil;
      int attackTick = -1;
      int critStart;
      int phaseStart;
      int useStart;
      int eaten;
      int nextEatTick;
      int eatSlot;
      int eatStackCount;
      Item eatItem;
      int reactAt = -1;
      int sprintHoldUntil;
      int sidestepUntil;
      int lastHitStamp = Integer.MIN_VALUE;
      double lastHp = -1.0;
      Vec3d lastPos;
      boolean axeMode;
      boolean targetBlocking;
      boolean blocking;
      int blockUntil;
      int botHurtStamp = Integer.MIN_VALUE;
      int placeStage;
      int placeStart;
      int placeCooldown;
      BlockPos placeCell;
      boolean placeRandom;
      boolean placeRetried;
      int nextRandomWebTick;
      int strafeDir;
      int nextStrafeTick;
      int webStage;
      int webStart;
      int nextWebTick;
      int webSlot;
      BlockPos webPos;
      int keepMode = 1;
      boolean edgeHold;
      int fleeFor;
      int fleeStuckTicks;
      double fleeLastX = Double.NaN;
      double fleeLastZ = Double.NaN;
      int thrownMask;
      int potionStage;
      int potionStart;
      int potionKind;
      int nextPotionTick;
      int cocoonStage;
      int cocoonStart;
      int cocoonRetries;
      BlockPos cocoonCheckCell;
      boolean inCocoon;
      boolean webWasEating;
      int critChainUntil;
      int comboActive;
      int comboType = -1;
      boolean comboMixed;
      int comboMixHitsLeft;
      boolean comboUppercutRising;
      int comboUppercutStart;
      int comboHitsStreak;
      int comboHitsThreshold = -1;
      int webtrapStage;
      int webtrapStart;
      int webtrapPlaced;
      int windStage;
      int windStart;
      int pearlStage;
      int pearlStart;
      Vec3d pearlFrom;
      int flinchUntil;
      boolean overshotLast;
      int breakStage;
      int breakStart;
      BlockPos breakPos;
      boolean punishCrit;
      double lastTargetScale = 1.0;
      boolean targetHadTotem;
      int healsRemaining;
      int nextDurabilityCheck;
      int nextExpBottleTick;
      int nextFearTick;
      int fearMode;
      int fearStart;
      int fearGone;
      int fearMove;
      double fearAnchorX;
      double fearAnchorZ;
      int nextFearStrafeTick;
      int fallRoll;
      double climbX = Double.NaN;
      double climbZ = Double.NaN;
      int climbStuck;
      int nextWindClimbTick;
      int nextWindMaceTick;
      int launchPurpose;
      int launchStage;
      int launchStart;
      int launchWindSlot;
      int launchMaceSlot;
      boolean launchMoved;
      int ekStart;
      boolean ekHit;
      int ekHitTick;
      int fallMode;
      int fallBucketSlot = -1;
      int fallMaceSlot = -1;
      boolean fallClutchOk;
      boolean fallPlaced;
      BlockPos fallWaterPos;
      int fallLandTick;
      int swapRoll;
      int spearRoll;
      int spearStage;
      int spearStart;
      int spearMaceSlot = -1;
      boolean chainRetry;
      int chainCount;
      int chainNextTick;
      int chainExpire;
      double diveErrYaw;
      double diveErrPitch;
      int launchJumpDelay = 2;
      boolean lastHit;
      boolean pathActive;
      List<BlockPos> path;
      int pathIdx;
      int pathPlanTick;
      int nextPathTick;
      int pathStuck;
      int pathJumpTick;
      boolean pathMoving;
      double pathLastX = Double.NaN;
      double pathLastZ = Double.NaN;
      int order;

      Fight(String target) {
         this.target = target;
      }

      void reset() {
         this.phase = PvpBotMod.Phase.FIGHT;
         this.started = false;
         this.hopping = false;
         this.paused = false;
         this.crit = false;
         this.weaponSlot = -1;
         this.attackTick = -1;
         this.eaten = 0;
         this.reactAt = -1;
         this.strafeDir = 0;
         this.webStage = 0;
         this.axeMode = false;
         this.targetBlocking = false;
         this.blocking = false;
         this.placeStage = 0;
         this.placeRandom = false;
         this.placeRetried = false;
         this.lastHp = -1.0;
         this.lastPos = null;
         this.keepMode = 1;
         this.edgeHold = false;
         this.fleeFor = 0;
         this.fleeStuckTicks = 0;
         this.fleeLastX = Double.NaN;
         this.fleeLastZ = Double.NaN;
         this.thrownMask = 0;
         this.potionStage = 0;
         this.healsRemaining = 0;
         this.cocoonStage = 0;
         this.cocoonRetries = 0;
         this.inCocoon = false;
         this.webWasEating = false;
         this.critChainUntil = 0;
         this.comboActive = 0;
         this.comboType = -1;
         this.comboMixed = false;
         this.comboMixHitsLeft = 0;
         this.comboUppercutRising = false;
         this.comboHitsStreak = 0;
         this.comboHitsThreshold = -1;
         this.webtrapStage = 0;
         this.windStage = 0;
         this.pearlStage = 0;
         this.pearlFrom = null;
         this.flinchUntil = 0;
         this.overshotLast = false;
         this.breakStage = 0;
         this.breakPos = null;
         this.punishCrit = false;
         this.fearMode = 0;
         this.fearMove = 0;
         this.fallRoll = 0;
         this.climbStuck = 0;
         this.launchStage = 0;
         this.launchMoved = false;
         this.ekHit = false;
         this.fallMode = 0;
         this.fallPlaced = false;
         this.fallWaterPos = null;
         this.fallLandTick = 0;
         this.swapRoll = 0;
         this.spearRoll = 0;
         this.spearStage = 0;
         this.pathActive = false;
         this.path = null;
         this.pathMoving = false;
         this.pathStuck = 0;
         this.pathIdx = 0;
         this.pathPlanTick = 0;
         this.nextPathTick = 0;
         this.pathJumpTick = -100;
         this.pathLastX = Double.NaN;
         this.pathLastZ = Double.NaN;
      }
   }

   static final class GotoJob {
      final BlockPos target;
      BlockPos goal;
      List<BlockPos> path;
      int pathIdx;
      int pathPlanTick;
      int nextPathTick;
      int pathStuck;
      int pathJumpTick = -100;
      boolean pathMoving;
      double lastX = Double.NaN;
      double lastZ = Double.NaN;

      GotoJob(BlockPos target) {
         this.target = target.toImmutable();
      }
   }

   static final class Focus {
      final String target;
      final int expire;

      Focus(String target, int expire) {
         this.target = target;
         this.expire = expire;
      }
   }

   static final class LootState {
      final int entityId;
      final int start;

      LootState(int entityId, int start) {
         this.entityId = entityId;
         this.start = start;
      }
   }

   static final class MassJob {
      final String owner;
      final String name;
      final String team;
      final Vec3d pos;

      MassJob(String owner, String name, String team, Vec3d pos) {
         this.owner = owner;
         this.name = name;
         this.team = team;
         this.pos = pos;
      }
   }

   static final class Opt {
      final String cmd;
      final String key;
      final double min;
      final double max;
      final boolean bool;
      final String desc;
      final String[] choices;
      double value;

      Opt(String cmd, String key, double def, double min, double max, boolean bool, String desc, String[] choices) {
         this.cmd = cmd;
         this.key = key;
         this.min = min;
         this.max = max;
         this.bool = bool;
         this.desc = desc;
         this.choices = choices;
         this.value = def;
      }

      boolean on() {
         return this.value >= 0.5;
      }

      int asInt() {
         return (int)Math.round(this.value);
      }

      String show() {
         if (this.choices != null) {
            return this.choices[(int)Math.round(this.value)];
         } else if (this.bool) {
            return this.on() ? "on" : "off";
         } else {
            return this.value == Math.rint(this.value) ? Long.toString(Math.round(this.value)) : Double.toString(this.value);
         }
      }
   }

   static final class Pending {
      final String owner;
      final boolean quiet;
      final String team;
      final Vec3d tpPos;
      int waited;

      Pending(String owner, boolean quiet, String team, Vec3d tpPos) {
         this.owner = owner;
         this.quiet = quiet;
         this.team = team;
         this.tpPos = tpPos;
      }
   }

   static enum Phase {
      FIGHT,
      FLEE,
      EAT,
      COCOON,
      POTION,
      WEBTRAP,
      WINDESCAPE,
      PEARLESCAPE,
      WINDLAUNCH,
      MACEFEAR,
      EATKB,
      FALL;
   }

   static final class PlacedWeb {
      final World level;
      final BlockPos pos;
      final int expireTick;

      PlacedWeb(World level, BlockPos pos, int expireTick) {
         this.level = level;
         this.pos = pos;
         this.expireTick = expireTick;
      }
   }
}

final class BotSupport {
   /** World the player is in (works on every 1.21.11 Yarn build). */
   static net.minecraft.server.world.ServerWorld sw(ServerPlayerEntity p) {
      return (net.minecraft.server.world.ServerWorld)p.getEntityWorld();
   }

   static void run(MinecraftServer server, String command) {
      server.getCommandManager().parseAndExecute(server.getCommandSource().withSilent(), command);
   }

   static boolean badFood(ItemStack stack) {
      return stack.isOf(Items.ROTTEN_FLESH)
         || stack.isOf(Items.SPIDER_EYE)
         || stack.isOf(Items.POISONOUS_POTATO)
         || stack.isOf(Items.PUFFERFISH)
         || stack.isOf(Items.CHORUS_FRUIT)
         || stack.isOf(Items.CHICKEN);
   }

   static int findFoodIndex(ServerPlayerEntity bot) {
      boolean goldenOnly = bot.getHungerManager().getFoodLevel() >= 20;
      PlayerInventory inv = bot.getInventory();
      int any = -1;

      for (int i = 0; i < 36; i++) {
         ItemStack stack = inv.getStack(i);
         if (!stack.isEmpty() && stack.contains(DataComponentTypes.FOOD) && !badFood(stack)) {
            if (stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) {
               return i;
            }

            if (!goldenOnly && any < 0) {
               any = i;
            }
         }
      }

      return any;
   }

   static int weaponScore(ItemStack stack) {
      if (stack.isEmpty()) {
         return 0;
      } else if (stack.isOf(Items.NETHERITE_SWORD)) {
         return 7;
      } else if (stack.isOf(Items.DIAMOND_SWORD)) {
         return 6;
      } else if (stack.isOf(Items.IRON_SWORD)) {
         return 5;
      } else if (stack.isOf(Items.STONE_SWORD)) {
         return 4;
      } else if (stack.isIn(ItemTags.SWORDS)) {
         return 3;
      } else {
         return stack.isIn(ItemTags.AXES) ? 2 : 0;
      }
   }

   static int findWeaponSlot(ServerPlayerEntity bot) {
      PlayerInventory inv = bot.getInventory();
      int best = -1;
      int bestScore = 0;

      for (int i = 0; i < 9; i++) {
         int score = weaponScore(inv.getStack(i));
         if (score > bestScore) {
            bestScore = score;
            best = i;
         }
      }

      return best;
   }

   static int toHotbar(ServerPlayerEntity bot, int idx, int avoidSlot) {
      if (idx < 9) {
         return idx;
      } else {
         PlayerInventory inv = bot.getInventory();
         int target = -1;

         for (int i = 0; i < 9; i++) {
            if (i != avoidSlot && inv.getStack(i).isEmpty()) {
               target = i;
               break;
            }
         }

         if (target < 0) {
            for (int ix = 0; ix < 9; ix++) {
               if (ix != avoidSlot) {
                  target = ix;
                  break;
               }
            }
         }

         if (target < 0) {
            return -1;
         } else {
            ItemStack moving = inv.getStack(idx);
            ItemStack displaced = inv.getStack(target);
            inv.setStack(idx, displaced);
            inv.setStack(target, moving);
            return target;
         }
      }
   }

   static EquipmentSlot armorSlotFor(ItemStack stack) {
      if (stack.isIn(ItemTags.HEAD_ARMOR)) {
         return EquipmentSlot.HEAD;
      } else if (stack.isIn(ItemTags.CHEST_ARMOR)) {
         return EquipmentSlot.CHEST;
      } else if (stack.isIn(ItemTags.LEG_ARMOR)) {
         return EquipmentSlot.LEGS;
      } else {
         return stack.isIn(ItemTags.FOOT_ARMOR) ? EquipmentSlot.FEET : null;
      }
   }

   static void equipArmor(ServerPlayerEntity bot) {
      PlayerInventory inv = bot.getInventory();

      for (int i = 0; i < 36; i++) {
         ItemStack stack = inv.getStack(i);
         if (!stack.isEmpty()) {
            EquipmentSlot slot = armorSlotFor(stack);
            if (slot != null && bot.getEquippedStack(slot).isEmpty()) {
               bot.equipStack(slot, stack.copy());
               inv.setStack(i, ItemStack.EMPTY);
            }
         }
      }

      if (bot.getEquippedStack(EquipmentSlot.OFFHAND).isEmpty()) {
         int idx = PvpBotMod.TOTEM.on() ? findItemIndex(bot, Items.TOTEM_OF_UNDYING) : -1;
         if (idx < 0) {
            idx = findItemIndex(bot, Items.SHIELD);
         }

         if (idx >= 0) {
            bot.equipStack(EquipmentSlot.OFFHAND, inv.getStack(idx).copy());
            inv.setStack(idx, ItemStack.EMPTY);
         }
      }
   }

   static void maybeSwitchTotem(ServerPlayerEntity bot) {
      if (PvpBotMod.TOTEM.on() && !(PvpBotMod.TOTEM_HEARTS.value <= 0.0)) {
         double hp = bot.getHealth() + bot.getAbsorptionAmount();
         if (!(hp > PvpBotMod.TOTEM_HEARTS.value * 2.0)) {
            ItemStack off = bot.getEquippedStack(EquipmentSlot.OFFHAND);
            if (!off.isOf(Items.TOTEM_OF_UNDYING)) {
               PlayerInventory inv = bot.getInventory();
               int idx = findItemIndex(bot, Items.TOTEM_OF_UNDYING);
               if (idx >= 0) {
                  ItemStack totem = inv.getStack(idx);
                  inv.setStack(idx, off.isEmpty() ? ItemStack.EMPTY : off.copy());
                  bot.equipStack(EquipmentSlot.OFFHAND, totem.copy());
               }
            }
         }
      }
   }

   static void maybeSwitchTotemBack(ServerPlayerEntity bot) {
      if (PvpBotMod.TOTEM.on() && !(PvpBotMod.TOTEM_HEARTS.value <= 0.0)) {
         double hp = bot.getHealth() + bot.getAbsorptionAmount();
         if (!(hp < PvpBotMod.TOTEM_HEARTS.value * 2.0 + 4.0)) {
            ItemStack off = bot.getEquippedStack(EquipmentSlot.OFFHAND);
            if (off.isOf(Items.TOTEM_OF_UNDYING)) {
               PlayerInventory inv = bot.getInventory();
               int idx = findItemIndex(bot, Items.SHIELD);
               if (idx >= 0) {
                  ItemStack shield = inv.getStack(idx);
                  inv.setStack(idx, off.copy());
                  bot.equipStack(EquipmentSlot.OFFHAND, shield.copy());
               }
            }
         }
      }
   }

   static int findAxeSlot(ServerPlayerEntity bot) {
      PlayerInventory inv = bot.getInventory();

      for (int i = 0; i < 9; i++) {
         if (inv.getStack(i).isIn(ItemTags.AXES)) {
            return i;
         }
      }

      return -1;
   }

   static String fmt(double v) {
      return String.format(Locale.ROOT, "%.2f", v);
   }

   static void startWebPlace(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target) {
      if (f.placeStage == 0
         && PvpBotMod.globalTick >= f.placeCooldown
         && !(PvpBotMod.HIT_WEB.value <= 0.0)
         && !(ThreadLocalRandom.current().nextDouble() * 100.0 >= PvpBotMod.HIT_WEB.value)) {
         int idx = findItemIndex(bot, Items.COBWEB);
         if (idx >= 0 && webAimPoint(bot, target, f) != null) {
            int slot = toHotbar(bot, idx, findWeaponSlot(bot));
            if (slot >= 0) {
               run(server, "player " + name + " move");
               run(server, "player " + name + " hotbar " + (slot + 1));
               f.strafeDir = 0;
               f.placeStage = 1;
               f.placeRandom = false;
               f.placeRetried = false;
               f.placeStart = PvpBotMod.globalTick;
            }
         }
      }
   }

   static void startRandomWebPlace(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target) {
      if (f.placeStage == 0) {
         int idx = findItemIndex(bot, Items.COBWEB);
         if (idx >= 0 && randomWebAimPoint(bot, target, f) != null) {
            int slot = toHotbar(bot, idx, findWeaponSlot(bot));
            if (slot >= 0) {
               run(server, "player " + name + " move");
               run(server, "player " + name + " hotbar " + (slot + 1));
               f.strafeDir = 0;
               f.placeStage = 1;
               f.placeRandom = true;
               f.placeRetried = false;
               f.placeStart = PvpBotMod.globalTick;
            }
         }
      }
   }

   static Vec3d webAimPoint(ServerPlayerEntity bot, ServerPlayerEntity target, PvpBotMod.Fight f) {
      World level = BotSupport.sw(target);
      int floorY = target.getBlockPos().getY();
      double lead = PvpBotMod.WEB_LEAD_TICKS.value * 0.4;
      Vec3d vel = target.getVelocity();
      double tx = target.getX() + (lead > 0.0 ? vel.x * lead : 0.0);
      double tz = target.getZ() + (lead > 0.0 ? vel.z * lead : 0.0);
      double dx = bot.getX() - tx;
      double dz = bot.getZ() - tz;
      double len = Math.sqrt(dx * dx + dz * dz);
      if (len < 0.05) {
         dx = 1.0;
         dz = 0.0;
         len = 1.0;
      }

      double[] fracs = new double[]{0.0, 0.3, 0.5, -0.3};

      for (double frac : fracs) {
         double px = tx + dx / len * frac;
         double pz = tz + dz / len * frac;
         BlockPos cell = BlockPos.ofFloored(px, floorY, pz);
         if (level.getBlockState(cell).isAir() && !level.getBlockState(cell.down()).isAir()) {
            f.placeCell = cell;
            return new Vec3d(px, floorY, pz);
         }
      }

      return null;
   }

   static Vec3d randomWebAimPoint(ServerPlayerEntity bot, ServerPlayerEntity target, PvpBotMod.Fight f) {
      World level = BotSupport.sw(target);
      int floorY = target.getBlockPos().getY();
      double dx = bot.getX() - target.getX();
      double dz = bot.getZ() - target.getZ();
      double len = Math.sqrt(dx * dx + dz * dz);
      if (len < 0.05) {
         dx = 1.0;
         dz = 0.0;
         len = 1.0;
      }

      double lo = Math.min(PvpBotMod.RANDOMWEB_MINDIST.value, PvpBotMod.RANDOMWEB_MAXDIST.value);
      double hi = Math.max(PvpBotMod.RANDOMWEB_MINDIST.value, PvpBotMod.RANDOMWEB_MAXDIST.value);
      double frac = lo + ThreadLocalRandom.current().nextDouble() * (hi - lo);
      if (ThreadLocalRandom.current().nextBoolean()) {
         frac = -frac;
      }

      double px = target.getX() + dx / len * frac;
      double pz = target.getZ() + dz / len * frac;
      BlockPos cell = BlockPos.ofFloored(px, floorY, pz);
      if (level.getBlockState(cell).isAir() && !level.getBlockState(cell.down()).isAir()) {
         f.placeCell = cell;
         return new Vec3d(px, floorY, pz);
      } else {
         return null;
      }
   }

   static boolean webPlaceStep(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target) {
      if (f.placeStage == 0) {
         return false;
      } else {
         int elapsed = PvpBotMod.globalTick - f.placeStart;
         if (f.placeStage == 1) {
            if (elapsed >= 3) {
               Vec3d aim = f.placeRandom ? randomWebAimPoint(bot, target, f) : webAimPoint(bot, target, f);
               if (aim == null || bot.squaredDistanceTo(aim.x, aim.y, aim.z) > 16.0) {
                  endWebPlace(server, name, f);
                  return false;
               }

               run(server, "player " + name + " look at " + fmt(aim.x) + " " + fmt(aim.y) + " " + fmt(aim.z));
               f.placeStage = 2;
               f.placeStart = PvpBotMod.globalTick;
            }
         } else if (f.placeStage == 2) {
            if (elapsed >= 1) {
               Vec3d aim = f.placeRandom ? randomWebAimPoint(bot, target, f) : webAimPoint(bot, target, f);
               if (aim != null && bot.squaredDistanceTo(aim.x, aim.y, aim.z) <= 16.0) {
                  run(server, "player " + name + " look at " + fmt(aim.x) + " " + fmt(aim.y) + " " + fmt(aim.z));
               }

               useOnce(server, name, f);
               f.placeStage = 3;
               f.placeStart = PvpBotMod.globalTick;
            }
         } else if (elapsed >= 2) {
            boolean placed = f.placeCell != null && BotSupport.sw(bot).getBlockState(f.placeCell).isOf(Blocks.COBWEB);
            if (!placed && !f.placeRetried && findItemIndex(bot, Items.COBWEB) >= 0) {
               Vec3d aim = f.placeRandom ? randomWebAimPoint(bot, target, f) : webAimPoint(bot, target, f);
               if (aim != null && bot.squaredDistanceTo(aim.x, aim.y, aim.z) <= 16.0) {
                  f.placeRetried = true;
                  f.placeStage = 2;
                  f.placeStart = PvpBotMod.globalTick - 1;
                  return true;
               }
            }

            if (placed) {
               if (PvpBotMod.WEB_LIFETIME.asInt() > 0) {
                  PvpBotMod.PLACED_WEBS.add(new PvpBotMod.PlacedWeb(BotSupport.sw(bot), f.placeCell, PvpBotMod.globalTick + PvpBotMod.WEB_LIFETIME.asInt()));
               }

               if (PvpBotMod.CRIT_CHAIN_CHANCE.value > 0.0) {
                  int lo = Math.min(PvpBotMod.CRIT_CHAIN_MIN.asInt(), PvpBotMod.CRIT_CHAIN_MAX.asInt());
                  int hi = Math.max(PvpBotMod.CRIT_CHAIN_MIN.asInt(), PvpBotMod.CRIT_CHAIN_MAX.asInt());
                  f.critChainUntil = PvpBotMod.globalTick + lo + ThreadLocalRandom.current().nextInt(hi - lo + 1);
               }
            }

            endWebPlace(server, name, f);
            return false;
         }

         return true;
      }
   }

   static void endWebPlace(MinecraftServer server, String name, PvpBotMod.Fight f) {
      f.placeStage = 0;
      f.placeRandom = false;
      f.placeRetried = false;
      f.placeCooldown = PvpBotMod.globalTick + 30;
      f.weaponSlot = -1;
      run(server, "player " + name + " move forward");
      run(server, "player " + name + " sprint");
   }

   static String lookCmd(String name, String target) {
      int t = PvpBotMod.AIM.asInt();
      return "player " + name + " look upon " + target + " eyes" + (t > 0 ? " delta " + t : "");
   }

   static void swing(PvpBotMod.Fight f) {
      ServerPlayerEntity b = f == null ? null : f.lastBot;
      if (b != null) {
         b.swingHand(Hand.MAIN_HAND);
      }
   }

   static void useOnce(MinecraftServer server, String name, PvpBotMod.Fight f) {
      run(server, "player " + name + " use once");
      swing(f);
   }

   /** true when a ray from the bot's eye along its real look direction hits the target's box within max blocks. */
   static boolean rayReach(ServerPlayerEntity bot, ServerPlayerEntity target, double max) {
      Box bb = target.getBoundingBox();
      double yaw = Math.toRadians(bot.getYaw());
      double pitch = Math.toRadians(bot.getPitch());
      double dx = -Math.sin(yaw) * Math.cos(pitch);
      double dy = -Math.sin(pitch);
      double dz = Math.cos(yaw) * Math.cos(pitch);
      double ox = bot.getX();
      double oy = bot.getEyeY();
      double oz = bot.getZ();
      double tmin = 0.0;
      double tmax = max;
      double[] o = {ox, oy, oz};
      double[] d = {dx, dy, dz};
      double[] lo = {bb.minX, bb.minY, bb.minZ};
      double[] hi = {bb.maxX, bb.maxY, bb.maxZ};
      for (int i = 0; i < 3; i++) {
         if (Math.abs(d[i]) < 1.0E-9) {
            if (o[i] < lo[i] || o[i] > hi[i]) {
               return false;
            }
         } else {
            double t1 = (lo[i] - o[i]) / d[i];
            double t2 = (hi[i] - o[i]) / d[i];
            if (t1 > t2) {
               double t = t1;
               t1 = t2;
               t2 = t;
            }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) {
               return false;
            }
         }
      }
      return true;
   }

   static double reachDistance(ServerPlayerEntity bot, ServerPlayerEntity target) {
      Box box = target.getBoundingBox();
      double ex = bot.getX();
      double ey = bot.getEyeY();
      double ez = bot.getZ();
      double dx = Math.max(Math.max(box.minX - ex, 0.0), ex - box.maxX);
      double dy = Math.max(Math.max(box.minY - ey, 0.0), ey - box.maxY);
      double dz = Math.max(Math.max(box.minZ - ez, 0.0), ez - box.maxZ);
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   static BlockPos findCobweb(LivingEntity who) {
      Box box = who.getBoundingBox();
      BlockPos min = BlockPos.ofFloored(box.minX + 1.0E-7, box.minY + 1.0E-7, box.minZ + 1.0E-7);
      BlockPos max = BlockPos.ofFloored(box.maxX - 1.0E-7, box.maxY - 1.0E-7, box.maxZ - 1.0E-7);

      for (BlockPos p : BlockPos.iterate(min, max)) {
         if (who.getEntityWorld().getBlockState(p).isOf(Blocks.COBWEB)) {
            return p.toImmutable();
         }
      }

      return null;
   }

   static boolean inCobweb(LivingEntity who) {
      return findCobweb(who) != null;
   }

   static BlockPos targetBoxedInWeb(ServerPlayerEntity target) {
      World level = BotSupport.sw(target);
      BlockPos feet = target.getBlockPos();
      if (!level.getBlockState(feet).isOf(Blocks.COBWEB)) {
         return null;
      } else if (level.getBlockState(feet.up()).isOf(Blocks.COBWEB)) {
         return feet.up();
      } else {
         int sides = 0;
         BlockPos pick = null;

         for (BlockPos n : new BlockPos[]{feet.north(), feet.south(), feet.east(), feet.west()}) {
            if (level.getBlockState(n).isOf(Blocks.COBWEB)) {
               sides++;
               if (pick == null) {
                  pick = n.toImmutable();
               }
            }
         }

         return sides >= 2 ? pick : null;
      }
   }

   static BlockPos nearbyWeb(ServerPlayerEntity bot, double radius) {
      BlockPos center = bot.getBlockPos();
      int r = (int)Math.ceil(radius);
      double bestDistSq = radius * radius;
      BlockPos best = null;

      for (BlockPos p : BlockPos.iterate(center.add(-r, -2, -r), center.add(r, 2, r))) {
         if (BotSupport.sw(bot).getBlockState(p).isOf(Blocks.COBWEB)) {
            double d = p.getSquaredDistanceFromCenter(bot.getX(), bot.getY(), bot.getZ());
            if (d < bestDistSq) {
               bestDistSq = d;
               best = p.toImmutable();
            }
         }
      }

      return best;
   }

   static boolean targetVulnerable(ServerPlayerEntity bot, ServerPlayerEntity target) {
      return target.isUsingItem() || target.isSprinting() && movingAway(bot, target);
   }

   static int findItemIndex(ServerPlayerEntity bot, Item item) {
      PlayerInventory inv = bot.getInventory();

      for (int i = 0; i < 36; i++) {
         if (inv.getStack(i).isOf(item)) {
            return i;
         }
      }

      return -1;
   }

   static boolean webEscape(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot) {
      if (f.webStage == 0) {
         if (!f.inCocoon && (PvpBotMod.WEB_ESCAPE.on() || PvpBotMod.WEB_BREAK.on()) && PvpBotMod.globalTick >= f.nextWebTick) {
            BlockPos web = findCobweb(bot);
            if (web == null) {
               return false;
            } else {
               int idx = PvpBotMod.WEB_ESCAPE.on() ? findItemIndex(bot, Items.WATER_BUCKET) : -1;
               int slot = idx < 0 ? -1 : toHotbar(bot, idx, findWeaponSlot(bot));
               if (slot < 0 && !PvpBotMod.WEB_BREAK.on()) {
                  f.nextWebTick = PvpBotMod.globalTick + 100;
                  return false;
               } else {
                  String aim = "player "
                     + name
                     + " look at "
                     + fmt(web.getX() + 0.5)
                     + " "
                     + fmt(web.getY() + 0.5)
                     + " "
                     + fmt(web.getZ() + 0.5);
                  run(server, "player " + name + " stop");
                  if (slot >= 0) {
                     run(server, "player " + name + " hotbar " + (slot + 1));
                     run(server, aim);
                     f.webSlot = slot;
                     f.webStage = 1;
                  } else {
                     int w = findWeaponSlot(bot);
                     if (w >= 0) {
                        run(server, "player " + name + " hotbar " + (w + 1));
                     }

                     run(server, aim);
                     run(server, "player " + name + " attack continuous");
                     f.webStage = 10;
                  }

                  f.webPos = web;
                  f.webStart = PvpBotMod.globalTick;
                  boolean wasEating = f.phase == PvpBotMod.Phase.EAT;
                  f.webWasEating = wasEating;
                  f.phase = PvpBotMod.Phase.FIGHT;
                  if (!wasEating) {
                     f.eaten = 0;
                     f.nextEatTick = Math.max(f.nextEatTick, PvpBotMod.globalTick + 40);
                  }

                  f.started = false;
                  f.hopping = false;
                  f.paused = false;
                  f.crit = false;
                  f.blocking = false;
                  f.axeMode = false;
                  f.placeStage = 0;
                  f.strafeDir = 0;
                  f.attackTick = -1;
                  return true;
               }
            }
         } else {
            return false;
         }
      } else {
         if (f.webStage == 1) {
            if (PvpBotMod.globalTick - f.webStart >= 1) {
               useOnce(server, name, f);
               f.webStage = 2;
               f.webStart = PvpBotMod.globalTick;
            }
         } else if (f.webStage == 2) {
            int elapsed = PvpBotMod.globalTick - f.webStart;
            if (elapsed >= 2 && !inCobweb(bot) || elapsed >= 14) {
               useOnce(server, name, f);
               f.webStage = 3;
               f.webStart = PvpBotMod.globalTick;
            }
         } else if (f.webStage == 3) {
            if (PvpBotMod.globalTick - f.webStart >= 3) {
               cleanupWater(bot, f);
               finishWeb(server, name, f, bot, 4);
            }
         } else if (f.webStage == 10) {
            boolean timedOut = PvpBotMod.globalTick - f.webStart > 80;
            if (!inCobweb(bot) || timedOut) {
               run(server, "player " + name + " stop");
               finishWeb(server, name, f, bot, timedOut ? 60 : 4);
            }
         }

         return true;
      }
   }

   static void finishWeb(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, int cooldownTicks) {
      f.webStage = 0;
      f.nextWebTick = PvpBotMod.globalTick + cooldownTicks;
      f.started = false;
      f.weaponSlot = -1;
      if (f.webWasEating) {
         f.webWasEating = false;
         if (findFoodIndex(bot) >= 0) {
            Fighting.startEat(server, name, f, bot, f.inCocoon);
         }
      }
   }

   static boolean breakoutStep(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target) {
      if (f.breakStage == 0) {
         if (!PvpBotMod.WEB_CRIT.on()) {
            return false;
         } else {
            BlockPos web = targetBoxedInWeb(target);
            if (web == null) {
               return false;
            } else {
               int w = findWeaponSlot(bot);
               if (w >= 0 && w != f.weaponSlot) {
                  run(server, "player " + name + " hotbar " + (w + 1));
                  f.weaponSlot = w;
               }

               run(
                  server,
                  "player " + name + " look at " + fmt(web.getX() + 0.5) + " " + fmt(web.getY() + 0.5) + " " + fmt(web.getZ() + 0.5)
               );
               run(server, "player " + name + " attack continuous");
               f.breakPos = web;
               f.breakStage = 1;
               f.breakStart = PvpBotMod.globalTick;
               return true;
            }
         }
      } else {
         boolean gone = f.breakPos == null || !BotSupport.sw(bot).getBlockState(f.breakPos).isOf(Blocks.COBWEB);
         boolean timedOut = PvpBotMod.globalTick - f.breakStart > 80;
         if (!gone && !timedOut) {
            return true;
         } else {
            run(server, "player " + name + " stop");
            f.breakStage = 0;
            f.breakPos = null;
            return false;
         }
      }
   }

   static void cleanupWater(ServerPlayerEntity bot, PvpBotMod.Fight f) {
      if (f.webPos != null && f.webSlot >= 0 && f.webSlot < 36) {
         PlayerInventory inv = bot.getInventory();
         if (inv.getStack(f.webSlot).isOf(Items.BUCKET)) {
            boolean removed = false;

            for (int dx = -1; dx <= 1; dx++) {
               for (int dz = -1; dz <= 1; dz++) {
                  for (int dy = -1; dy <= 2; dy++) {
                     BlockPos p = f.webPos.add(dx, dy, dz);
                     FluidState fluid = BotSupport.sw(bot).getFluidState(p);
                     if (fluid.isIn(FluidTags.WATER) && fluid.isStill()) {
                        BotSupport.sw(bot).setBlockState(p, Blocks.AIR.getDefaultState(), 3);
                        removed = true;
                     }
                  }
               }
            }

            if (removed) {
               inv.setStack(f.webSlot, new ItemStack(Items.WATER_BUCKET));
            }
         }
      }
   }

   static boolean allowed(ServerCommandSource src) {
      if (PvpBotMod.OPS_ONLY.on() && PvpBotMod.dispatcher != null) {
         CommandNode<ServerCommandSource> gamemode = PvpBotMod.dispatcher.getRoot().getChild("gamemode");
         return gamemode == null || gamemode.getRequirement().test(src);
      } else {
         return true;
      }
   }

   static String teamName(ServerPlayerEntity p) {
      return p.getScoreboardTeam() == null ? null : p.getScoreboardTeam().getName();
   }

   static boolean allied(ServerPlayerEntity a, ServerPlayerEntity b) {
      return (!PvpBotMod.ffaActive || PvpBotMod.ffaTeams) && a.isTeammate(b);
   }

   static boolean botFight() {
      return PvpBotMod.BOT_FIGHT.on() || PvpBotMod.ffaActive;
   }

   static boolean ffaCounting() {
      return PvpBotMod.ffaActive && PvpBotMod.globalTick < PvpBotMod.ffaGoTick;
   }

   static void broadcast(MinecraftServer server, String text) {
      server.getPlayerManager().broadcast(Text.literal(text), false);
   }

   static int countItem(ServerPlayerEntity bot, Item item) {
      PlayerInventory inv = bot.getInventory();
      int n = 0;

      for (int i = 0; i < 36; i++) {
         ItemStack st = inv.getStack(i);
         if (st.isOf(item)) {
            n += st.getCount();
         }
      }

      return n;
   }

   static String aimCmd(String name, ServerPlayerEntity bot, ServerPlayerEntity target, PvpBotMod.Fight f) {
      double w = PvpBotMod.WOBBLE.value;
      double lead = PvpBotMod.PREDICT_TICKS.value;
      boolean leading = lead > 0.0 && PvpBotMod.AIM.asInt() <= 2;
      boolean spread = PvpBotMod.AIM_SPREAD.value > 0.0 && ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.AIM_SPREAD.value;
      boolean overshootRoll = !f.overshotLast
         && PvpBotMod.OVERSHOOT_CHANCE.value > 0.0
         && ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.OVERSHOOT_CHANCE.value;
      if (w <= 0.0 && !leading && !spread && !overshootRoll && !f.overshotLast) {
         return lookCmd(name, target.getName().getString());
      } else {
         double tx = target.getX();
         double tz = target.getZ();
         if (leading) {
            Vec3d vel = target.getVelocity();
            tx += vel.x * lead;
            tz += vel.z * lead;
         }

         double dx = tx - bot.getX();
         double dz = tz - bot.getZ();
         double targetY = spread
            ? target.getY() + ThreadLocalRandom.current().nextDouble() * Math.max(0.1, target.getHeight() - 0.2)
            : target.getEyeY();
         double dy = targetY - bot.getEyeY();
         double yaw = Math.toDegrees(Math.atan2(-dx, dz));
         double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
         if (w > 0.0) {
            yaw += (ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0) * w;
            pitch += (ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0) * w * 0.5;
         }

         if (f.overshotLast) {
            f.overshotLast = false;
         } else if (overshootRoll) {
            double diff = wrapDegrees(yaw - bot.getYaw());
            yaw += Math.copySign(PvpBotMod.OVERSHOOT_DEGREES.value, diff == 0.0 ? 1.0 : diff);
            f.overshotLast = true;
         }

         return "player " + name + " look " + String.format(Locale.ROOT, "%.1f %.1f", yaw, pitch);
      }
   }

   static double directYaw(ServerPlayerEntity bot, ServerPlayerEntity target) {
      double dx = target.getX() - bot.getX();
      double dz = target.getZ() - bot.getZ();
      return Math.toDegrees(Math.atan2(-dx, dz));
   }

   static double wrapDegrees(double deg) {
      deg %= 360.0;
      if (deg >= 180.0) {
         deg -= 360.0;
      } else if (deg < -180.0) {
         deg += 360.0;
      }

      return deg;
   }

   static boolean movingAway(ServerPlayerEntity bot, ServerPlayerEntity target) {
      double vx = target.getVelocity().x;
      double vz = target.getVelocity().z;
      if (vx * vx + vz * vz < 0.001) {
         return false;
      } else {
         double dx = target.getX() - bot.getX();
         double dz = target.getZ() - bot.getZ();
         return vx * dx + vz * dz > 0.0;
      }
   }

   static boolean hazardAt(World level, double x, double y, double z) {
      BlockPos feet = BlockPos.ofFloored(x, y, z);
      if (!level.getFluidState(feet).isIn(FluidTags.LAVA) && !level.getFluidState(feet.up()).isIn(FluidTags.LAVA)) {
         for (int i = 1; i <= 4; i++) {
            BlockPos below = feet.down(i);
            if (level.getFluidState(below).isIn(FluidTags.LAVA)) {
               return true;
            }

            if (!level.getBlockState(below).isAir()) {
               return false;
            }
         }

         return true;
      } else {
         return true;
      }
   }

   static boolean webAheadOf(World level, double x, double y, double z, BlockPos targetCell) {
      BlockPos p = BlockPos.ofFloored(x, y, z);
      return !p.equals(targetCell) && level.getBlockState(p).isOf(Blocks.COBWEB);
   }

   static int effectKind(StatusEffectInstance effect) {
      String id = effect.getTranslationKey();
      if (id.endsWith(".instant_health")) {
         return 1;
      } else if (id.endsWith(".speed")) {
         return 2;
      } else if (id.endsWith(".strength")) {
         return 3;
      } else {
         return id.endsWith(".fire_resistance") ? 4 : 0;
      }
   }

   static int potionKind(ItemStack stack) {
      if (!stack.isOf(Items.SPLASH_POTION)) {
         return 0;
      } else {
         PotionContentsComponent contents = (PotionContentsComponent)stack.get(DataComponentTypes.POTION_CONTENTS);
         if (contents == null) {
            return 0;
         } else {
            for (StatusEffectInstance effect : contents.getEffects()) {
               int kind = effectKind(effect);
               if (kind != 0) {
                  return kind;
               }
            }

            return 0;
         }
      }
   }

   static boolean hasEffectKind(ServerPlayerEntity bot, int kind) {
      for (StatusEffectInstance effect : bot.getStatusEffects()) {
         if (effectKind(effect) == kind) {
            return true;
         }
      }

      return false;
   }

   static int findPotionIndex(ServerPlayerEntity bot, int kind) {
      PlayerInventory inv = bot.getInventory();

      for (int i = 0; i < 36; i++) {
         if (potionKind(inv.getStack(i)) == kind) {
            return i;
         }
      }

      return -1;
   }

   static int wantedPotion(ServerPlayerEntity bot, PvpBotMod.Fight f, double dist, double hp) {
      if ((f.thrownMask & 1) == 0 && PvpBotMod.POTION_HEARTS.value > 0.0 && hp <= PvpBotMod.POTION_HEARTS.value * 2.0 && findPotionIndex(bot, 1) >= 0) {
         return 1;
      } else {
         if (dist >= 6.0) {
            if (!hasEffectKind(bot, 2) && findPotionIndex(bot, 2) >= 0) {
               return 2;
            }

            if (!hasEffectKind(bot, 3) && findPotionIndex(bot, 3) >= 0) {
               return 3;
            }

            if (!hasEffectKind(bot, 4) && findPotionIndex(bot, 4) >= 0) {
               return 4;
            }
         }

         return 0;
      }
   }

   static boolean cocoonReady(ServerPlayerEntity bot, PvpBotMod.Fight f, double hp) {
      return PvpBotMod.COCOON.on()
         && PvpBotMod.COCOON_HEARTS.value > 0.0
         && hp <= PvpBotMod.COCOON_HEARTS.value * 2.0
         && PvpBotMod.globalTick >= f.nextEatTick
         && countItem(bot, Items.COBWEB) >= 2
         && findFoodIndex(bot) >= 0;
   }

   static boolean cocoonGroundOk(ServerPlayerEntity bot) {
      World level = BotSupport.sw(bot);
      BlockPos feet = bot.getBlockPos();
      return !level.getBlockState(feet.down()).isAir()
         && level.getBlockState(feet).isAir()
         && level.getBlockState(feet.up()).isAir();
   }

   static boolean hasArmorFor(ServerPlayerEntity bot, EquipmentSlot slot) {
      PlayerInventory inv = bot.getInventory();

      for (int i = 0; i < 36; i++) {
         if (armorSlotFor(inv.getStack(i)) == slot) {
            return true;
         }
      }

      return false;
   }

   static boolean hasPotionKind(ServerPlayerEntity bot, int kind) {
      return findPotionIndex(bot, kind) >= 0;
   }

   static boolean wantsItem(ServerPlayerEntity bot, ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else {
         EquipmentSlot slot = armorSlotFor(stack);
         if (slot != null) {
            return bot.getEquippedStack(slot).isEmpty() && !hasArmorFor(bot, slot);
         } else {
            int kind = potionKind(stack);
            if (kind != 0) {
               return !hasPotionKind(bot, kind);
            } else {
               Item item = stack.getItem();
               return findItemIndex(bot, item) < 0 && !bot.getEquippedStack(EquipmentSlot.OFFHAND).isOf(item)
                  ? stack.isIn(ItemTags.SWORDS)
                     || stack.isIn(ItemTags.AXES)
                     || stack.isOf(Items.SHIELD)
                     || stack.isOf(Items.TOTEM_OF_UNDYING)
                     || stack.isOf(Items.COBWEB)
                     || stack.isOf(Items.WATER_BUCKET)
                     || stack.isOf(Items.WIND_CHARGE)
                     || stack.isOf(Items.ENDER_PEARL)
                     || stack.isOf(Items.EXPERIENCE_BOTTLE)
                     || stack.contains(DataComponentTypes.FOOD) && !badFood(stack)
                  : false;
            }
         }
      }
   }

   static boolean reachable(ServerPlayerEntity bot, ServerPlayerEntity t) {
      return t != null && t != bot && t.isAlive() && !t.isSpectator() && !t.isCreative() && BotSupport.sw(bot) == BotSupport.sw(t) && !allied(bot, t);
   }

   static void tick(MinecraftServer server) {
      PvpBotMod.globalTick++;
      if (!PvpBotMod.MASS_QUEUE.isEmpty() && PvpBotMod.globalTick % 4 == 0) {
         tickMass(server);
      }

      if (!PvpBotMod.PLACED_WEBS.isEmpty() && PvpBotMod.globalTick % 10 == 0) {
         tickWebs();
      }

      if (!PvpBotMod.PENDING.isEmpty()) {
         tickPending(server);
      }

      tickFfa(server);
      PvpBotMod.pathBudgetLeft = PvpBotMod.PATH_BUDGET.asInt();
      if (!PvpBotMod.BOTS.isEmpty()) {
         if (PvpBotMod.globalTick % 10 == 0) {
            tickArmor(server);
         }

         if (!ffaCounting()) {
            PvpBotMod.tickGotos(server);

            if (PvpBotMod.REVENGE.on() && PvpBotMod.globalTick % 2 == 0) {
               tickRevenge(server);
            }

            if (PvpBotMod.FOCUS_CHANCE.value > 0.0 && PvpBotMod.globalTick % 20 == 0) {
               tickFocus(server);
            }

            if ((PvpBotMod.AUTO_TARGET.on() || PvpBotMod.ffaActive) && PvpBotMod.globalTick % 20 == 0) {
               tickAutoTarget(server);
            }

            if (PvpBotMod.LOOT.on() && PvpBotMod.globalTick % 5 == 0) {
               tickLoot(server);
            }

            if (!PvpBotMod.FIGHTS.isEmpty()) {
               Fighting.tickFights(server);
            }
         }
      }
   }

   static void tickMass(MinecraftServer server) {
      PvpBotMod.MassJob job = PvpBotMod.MASS_QUEUE.poll();
      if (job != null) {
         ServerPlayerEntity owner = server.getPlayerManager().getPlayer(job.owner);
         if (owner == null) {
            PvpBotMod.MASS_QUEUE.clear();
         } else if (server.getPlayerManager().getPlayer(job.name) == null && !PvpBotMod.PENDING.containsKey(job.name)) {
            server.getCommandManager().parseAndExecute(owner.getCommandSource().withSilent(), "playerspawn " + job.name);
            PvpBotMod.PENDING.put(job.name, new PvpBotMod.Pending(job.owner, true, job.team, job.pos));
            if (PvpBotMod.MASS_QUEUE.isEmpty()) {
               owner.sendMessage(Text.literal("Mass spawn: all bots requested. They join over the next moments."));
            }
         }
      }
   }

   static void tickWebs() {
      Iterator<PvpBotMod.PlacedWeb> it = PvpBotMod.PLACED_WEBS.iterator();

      while (it.hasNext()) {
         PvpBotMod.PlacedWeb w = it.next();
         if (PvpBotMod.globalTick >= w.expireTick) {
            if (w.level.getBlockState(w.pos).isOf(Blocks.COBWEB)) {
               w.level.setBlockState(w.pos, Blocks.AIR.getDefaultState(), 3);
            }

            it.remove();
         }
      }
   }

   static void tickPending(MinecraftServer server) {
      Iterator<Entry<String, PvpBotMod.Pending>> it = PvpBotMod.PENDING.entrySet().iterator();

      while (it.hasNext()) {
         Entry<String, PvpBotMod.Pending> e = it.next();
         String name = e.getKey();
         PvpBotMod.Pending p = e.getValue();
         ServerPlayerEntity owner = server.getPlayerManager().getPlayer(p.owner);
         if (server.getPlayerManager().getPlayer(name) != null) {
            it.remove();
            PvpBotMod.BOTS.add(name);
            PvpBotMod.STOPPED.remove(name);
            run(server, "gamemode survival " + name);
            if (p.team != null) {
               run(server, "team join " + p.team + " " + name);
            }

            if (p.tpPos != null) {
               run(server, "tp " + name + " " + fmt(p.tpPos.x) + " " + fmt(p.tpPos.y) + " " + fmt(p.tpPos.z));
            }

            if (owner != null && !p.quiet) {
               owner.sendMessage(
                  Text.literal(
                     "Spawned "
                        + name
                        + ". Use /pvpbot fight "
                        + name
                        + " to start"
                        + (PvpBotMod.AUTO_TARGET.on() ? " (or wait, it will pick a target)." : ".")
                  )
               );
            }
         } else if (++p.waited > 200) {
            it.remove();
            if (owner != null) {
               owner.sendMessage(Text.literal("Could not spawn " + name + " (timed out). Check HeroBot's message above."));
            }
         }
      }
   }

   static void tickArmor(MinecraftServer server) {
      for (String name : PvpBotMod.BOTS) {
         ServerPlayerEntity bot = server.getPlayerManager().getPlayer(name);
         if (bot != null) {
            equipArmor(bot);
            maybeSwitchTotem(bot);
            maybeSwitchTotemBack(bot);
            PvpBotMod.Fight f = PvpBotMod.FIGHTS.get(name);
            if (f != null) {
               maybeSwapDurability(bot, f);
               maybeThrowExpBottle(server, name, bot, f);
            }
         }
      }
   }

   static boolean lowDurability(ItemStack stack) {
      return lowDurabilityAt(stack, PvpBotMod.DURABILITY_THRESHOLD.value);
   }

   static boolean lowDurabilityAt(ItemStack stack, double thresholdPercent) {
      if (!stack.isEmpty() && stack.isDamageable()) {
         int max = stack.getMaxDamage();
         if (max <= 0) {
            return false;
         } else {
            int remaining = max - stack.getDamage();
            return remaining * 100.0 / max <= thresholdPercent;
         }
      } else {
         return false;
      }
   }

   static boolean hasMending(ItemStack stack, ServerPlayerEntity bot) {
      if (stack.isEmpty()) {
         return false;
      } else {
         try {
            for (var entry : stack.getEnchantments().getEnchantments()) {
               if (entry.matchesKey(Enchantments.MENDING)) {
                  return true;
               }
            }
         } catch (Exception var4) {
         }

         return false;
      }
   }

   static void maybeSwapDurability(ServerPlayerEntity bot, PvpBotMod.Fight f) {
      if (PvpBotMod.DURABILITY_SWAP.on() && PvpBotMod.globalTick >= f.nextDurabilityCheck) {
         f.nextDurabilityCheck = PvpBotMod.globalTick + 20;
         PlayerInventory inv = bot.getInventory();
         ItemStack main = bot.getMainHandStack();
         if (main.isIn(ItemTags.SWORDS) && lowDurability(main)) {
            int mainSlot = inv.getSelectedSlot();

            for (int i = 0; i < 36; i++) {
               ItemStack cand = inv.getStack(i);
               if (i != mainSlot && cand.isIn(ItemTags.SWORDS) && !lowDurability(cand)) {
                  inv.setStack(i, main.copy());
                  inv.setStack(mainSlot, cand.copy());
                  f.weaponSlot = -1;
                  break;
               }
            }
         }

         for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack worn = bot.getEquippedStack(slot);
            if (!worn.isEmpty() && lowDurability(worn)) {
               for (int ix = 0; ix < 36; ix++) {
                  ItemStack cand = inv.getStack(ix);
                  if (armorSlotFor(cand) == slot && !lowDurability(cand)) {
                     inv.setStack(ix, worn.copy());
                     bot.equipStack(slot, cand.copy());
                     break;
                  }
               }
            }
         }
      }
   }

   static void maybeThrowExpBottle(MinecraftServer server, String name, ServerPlayerEntity bot, PvpBotMod.Fight f) {
      if (PvpBotMod.EXP_BOTTLE.on() && PvpBotMod.globalTick >= f.nextExpBottleTick) {
         EquipmentSlot[] slots = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

         for (EquipmentSlot slot : slots) {
            ItemStack piece = bot.getEquippedStack(slot);
            if (piece.isEmpty() || !hasMending(piece, bot) || !lowDurabilityAt(piece, PvpBotMod.EXP_BOTTLE_THRESHOLD.value)) {
               return;
            }
         }

         f.nextExpBottleTick = PvpBotMod.globalTick + 100;
         int idx = findItemIndex(bot, Items.EXPERIENCE_BOTTLE);
         if (idx >= 0) {
            int slotx = toHotbar(bot, idx, findWeaponSlot(bot));
            if (slotx >= 0) {
               run(server, "player " + name + " hotbar " + (slotx + 1));
               run(server, "player " + name + " look " + fmt(bot.getYaw()) + " 90");
               run(server, "player " + name + " use once");
               f.weaponSlot = -1;
            }
         }
      }
   }

   static void tickRevenge(MinecraftServer server) {
      for (String name : PvpBotMod.BOTS) {
         ServerPlayerEntity bot = server.getPlayerManager().getPlayer(name);
         if (bot != null && bot.getAttacker() instanceof ServerPlayerEntity attacker && attacker != bot) {
            int stamp = bot.getLastAttackedTime();
            Integer last = PvpBotMod.LAST_HURT.get(name);
            if (last == null || last != stamp) {
               PvpBotMod.LAST_HURT.put(name, stamp);
               String attackerName = attacker.getName().getString();
               if (!PvpBotMod.GOTOS.containsKey(name) && (!PvpBotMod.BOTS.contains(attackerName) || botFight()) && !attacker.isCreative() && !allied(bot, attacker)) {
                  PvpBotMod.STOPPED.remove(name);
                  PvpBotMod.Fight f = PvpBotMod.FIGHTS.get(name);
                  if (f == null) {
                     PvpBotMod.FIGHTS.put(name, new PvpBotMod.Fight(attackerName));
                  } else {
                     f.target = attackerName;
                  }
               }
            }
         }
      }
   }

   static void tickAutoTarget(MinecraftServer server) {
      for (String name : PvpBotMod.BOTS) {
         if (!PvpBotMod.STOPPED.contains(name)) {
            ServerPlayerEntity bot = server.getPlayerManager().getPlayer(name);
            if (bot != null) {
               PvpBotMod.Fight f = PvpBotMod.FIGHTS.get(name);
               if (f == null || !reachable(bot, server.getPlayerManager().getPlayer(f.target))) {
                  ServerPlayerEntity best = null;
                  double bestDist = PvpBotMod.FIND_RANGE.value;

                  for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                     if (p != bot && (botFight() || !PvpBotMod.BOTS.contains(p.getName().getString())) && !p.isCreative() && reachable(bot, p)) {
                        double d = bot.distanceTo(p);
                        if (d <= bestDist) {
                           bestDist = d;
                           best = p;
                        }
                     }
                  }

                  if (best != null) {
                     String targetName = best.getName().getString();
                     if (f == null) {
                        PvpBotMod.FIGHTS.put(name, new PvpBotMod.Fight(targetName));
                     } else {
                        f.target = targetName;
                     }
                  }
               }
            }
         }
      }
   }

   static void tickFocus(MinecraftServer server) {
      Map<String, List<ServerPlayerEntity>> teams = new HashMap<>();

      for (String name : PvpBotMod.BOTS) {
         if (!PvpBotMod.STOPPED.contains(name)) {
            ServerPlayerEntity bot = server.getPlayerManager().getPlayer(name);
            if (bot != null) {
               String team = teamName(bot);
               if (team != null) {
                  teams.computeIfAbsent(team, k -> new ArrayList<>()).add(bot);
               }
            }
         }
      }

      for (Entry<String, List<ServerPlayerEntity>> entry : teams.entrySet()) {
         List<ServerPlayerEntity> members = entry.getValue();
         PvpBotMod.Focus focus = PvpBotMod.FOCUS.get(entry.getKey());
         boolean fresh = false;
         if (focus == null || PvpBotMod.globalTick >= focus.expire || !reachable(members.get(0), server.getPlayerManager().getPlayer(focus.target))) {
            ServerPlayerEntity pick = pickFocusTarget(server, members);
            if (pick == null) {
               PvpBotMod.FOCUS.remove(entry.getKey());
               continue;
            }

            int lo = Math.min(PvpBotMod.FOCUS_MIN.asInt(), PvpBotMod.FOCUS_MAX.asInt());
            int hi = Math.max(PvpBotMod.FOCUS_MIN.asInt(), PvpBotMod.FOCUS_MAX.asInt());
            focus = new PvpBotMod.Focus(pick.getName().getString(), PvpBotMod.globalTick + lo + ThreadLocalRandom.current().nextInt(hi - lo + 1));
            PvpBotMod.FOCUS.put(entry.getKey(), focus);
            fresh = true;
         }

         ServerPlayerEntity focusTarget = server.getPlayerManager().getPlayer(focus.target);

         for (ServerPlayerEntity member : members) {
            String memberName = member.getName().getString();
            PvpBotMod.Fight f = PvpBotMod.FIGHTS.get(memberName);
            boolean idle = f == null || !reachable(member, server.getPlayerManager().getPlayer(f.target));
            if ((fresh || idle) && reachable(member, focusTarget) && !(ThreadLocalRandom.current().nextDouble() * 100.0 >= PvpBotMod.FOCUS_CHANCE.value)) {
               if (f == null) {
                  PvpBotMod.FIGHTS.put(memberName, new PvpBotMod.Fight(focus.target));
               } else {
                  f.target = focus.target;
               }
            }
         }
      }
   }

   static ServerPlayerEntity pickFocusTarget(MinecraftServer server, List<ServerPlayerEntity> members) {
      List<ServerPlayerEntity> candidates = new ArrayList<>();

      for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
         if (botFight() || !PvpBotMod.BOTS.contains(p.getName().getString())) {
            boolean fightable = true;
            boolean near = false;

            for (ServerPlayerEntity m : members) {
               if (p == m || !reachable(m, p)) {
                  fightable = false;
                  break;
               }

               if (m.distanceTo(p) <= PvpBotMod.FIND_RANGE.value) {
                  near = true;
               }
            }

            if (fightable && near) {
               candidates.add(p);
            }
         }
      }

      return candidates.isEmpty() ? null : candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
   }

   static void tickLoot(MinecraftServer server) {
      for (String name : PvpBotMod.BOTS) {
         ServerPlayerEntity bot = server.getPlayerManager().getPlayer(name);
         if (bot == null) {
            PvpBotMod.LOOTING.remove(name);
         } else {
            PvpBotMod.Fight f = PvpBotMod.FIGHTS.get(name);
            boolean fightClose = false;
            if (f != null) {
               if (f.phase != PvpBotMod.Phase.FIGHT || f.webStage != 0) {
                  continue;
               }

               ServerPlayerEntity t = server.getPlayerManager().getPlayer(f.target);
               fightClose = reachable(bot, t) && bot.distanceTo(t) <= 10.0;
            }

            PvpBotMod.LootState loot = PvpBotMod.LOOTING.get(name);
            if (loot != null) {
               Entity item = BotSupport.sw(bot).getEntityById(loot.entityId);
               if (fightClose || !(item instanceof ItemEntity) || !item.isAlive() || PvpBotMod.globalTick - loot.start > 240 || bot.distanceTo(item) < 0.8
                  )
                {
                  PvpBotMod.LOOTING.remove(name);
                  run(server, "player " + name + " stop");
                  if (f != null) {
                     f.reset();
                  }
               } else if (PvpBotMod.globalTick % 10 == 0) {
                  run(server, "player " + name + " look at " + fmt(item.getX()) + " " + fmt(item.getY()) + " " + fmt(item.getZ()));
               }
            } else if (!fightClose && bot.getInventory().getEmptySlot() >= 0) {
               ItemEntity best = null;
               double bestDist = PvpBotMod.LOOT_RANGE.value;

               for (ItemEntity candidate : BotSupport.sw(bot).getNonSpectatingEntities(ItemEntity.class, bot.getBoundingBox().expand(PvpBotMod.LOOT_RANGE.value))) {
                  double d = bot.distanceTo(candidate);
                  if (d < bestDist && wantsItem(bot, candidate.getStack())) {
                     bestDist = d;
                     best = candidate;
                  }
               }

               if (best != null) {
                  PvpBotMod.LOOTING.put(name, new PvpBotMod.LootState(best.getId(), PvpBotMod.globalTick));
                  if (f != null) {
                     f.reset();
                  }

                  run(server, "player " + name + " stop");
                  run(server, "player " + name + " autojump true");
                  run(server, "player " + name + " look at " + fmt(best.getX()) + " " + fmt(best.getY()) + " " + fmt(best.getZ()));
                  run(server, "player " + name + " move forward");
                  run(server, "player " + name + " sprint");
               }
            }
         }
      }
   }

   static String leaderboard() {
      if (PvpBotMod.FFA_KILLS.isEmpty()) {
         return "Kills: none yet";
      } else {
         List<Entry<String, Integer>> list = new ArrayList<>(PvpBotMod.FFA_KILLS.entrySet());
         list.sort((x, y) -> Integer.compare(y.getValue(), x.getValue()));
         StringBuilder sb = new StringBuilder("Kills: ");

         for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
               sb.append(" | ");
            }

            sb.append(list.get(i).getKey()).append(' ').append(list.get(i).getValue());
         }

         return sb.toString();
      }
   }

   static String commonTeam(MinecraftServer server, Set<String> names) {
      String team = null;

      for (String n : names) {
         ServerPlayerEntity p = server.getPlayerManager().getPlayer(n);
         String t = p == null ? null : teamName(p);
         if (t == null) {
            return null;
         }

         if (team == null) {
            team = t;
         } else if (!team.equals(t)) {
            return null;
         }
      }

      return team;
   }

   static String startFfa(MinecraftServer server, String ownerName, boolean teams) {
      if (PvpBotMod.ffaActive) {
         return "A match is already running. Use /pvpbot ffa stop first.";
      } else {
         PvpBotMod.FFA_ALIVE.clear();
         PvpBotMod.FFA_BOTS.clear();
         PvpBotMod.FFA_HUMANS.clear();
         PvpBotMod.FFA_OUT_HUMANS.clear();
         PvpBotMod.FFA_KILLS.clear();
         PvpBotMod.LAST_ATTACKER.clear();

         for (String name : PvpBotMod.BOTS) {
            ServerPlayerEntity bot = server.getPlayerManager().getPlayer(name);
            if (bot != null && bot.isAlive() && !bot.isCreative()) {
               PvpBotMod.FFA_ALIVE.add(name);
               PvpBotMod.FFA_BOTS.add(name);
            }
         }

         for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            String n = p.getName().getString();
            if (!PvpBotMod.BOTS.contains(n) && !p.isCreative() && !p.isSpectator() && p.isAlive()) {
               PvpBotMod.FFA_ALIVE.add(n);
               PvpBotMod.FFA_HUMANS.add(n);
            }
         }

         if (PvpBotMod.FFA_ALIVE.size() < 2) {
            PvpBotMod.FFA_ALIVE.clear();
            return "A match needs at least 2 fighters: PvP bots or players in survival mode.";
         } else {
            PvpBotMod.ffaActive = true;
            PvpBotMod.ffaTeams = teams;
            PvpBotMod.ffaOwner = ownerName;
            PvpBotMod.ffaGoTick = PvpBotMod.globalTick + 100;
            PvpBotMod.ffaLastCount = -1;
            if (PvpBotMod.FFA_LEAVE.on()) {
               run(server, "herobot botleaveondeath true");
            }

            for (String n : PvpBotMod.FFA_BOTS) {
               PvpBotMod.STOPPED.remove(n);
               PvpBotMod.FIGHTS.remove(n);
               PvpBotMod.GOTOS.remove(n);
               PvpBotMod.LOOTING.remove(n);
               run(server, "player " + n + " stop");
            }

            broadcast(server, "FFA match" + (teams ? " (teams)" : "") + ": " + PvpBotMod.FFA_ALIVE.size() + " fighters. Starting in 5 seconds...");
            return null;
         }
      }
   }

   static void endFfa(MinecraftServer server, boolean natural) {
      if (PvpBotMod.ffaActive) {
         PvpBotMod.ffaActive = false;
         if (PvpBotMod.FFA_LEAVE.on()) {
            run(server, "herobot botleaveondeath false");
         }

         for (String n : PvpBotMod.FFA_OUT_HUMANS) {
            run(server, "gamemode survival " + n);
         }

         String winner = null;
         if (natural) {
            if (PvpBotMod.FFA_ALIVE.size() == 1) {
               winner = PvpBotMod.FFA_ALIVE.iterator().next();
            } else if (PvpBotMod.ffaTeams) {
               String team = commonTeam(server, PvpBotMod.FFA_ALIVE);
               if (team != null) {
                  winner = "team " + team;
               }
            }

            broadcast(server, winner != null ? "FFA over! Winner: " + winner : "FFA over! Nobody is left standing.");
         } else {
            broadcast(server, "FFA match stopped.");
         }

         broadcast(server, leaderboard());
         if (PvpBotMod.TAUNTS.on() && winner != null && PvpBotMod.FFA_BOTS.contains(winner)) {
            broadcast(server, "<" + winner + "> gg");
         }

         for (String n : PvpBotMod.FFA_BOTS) {
            if (server.getPlayerManager().getPlayer(n) != null) {
               PvpBotMod.STOPPED.add(n);
               PvpBotMod.FIGHTS.remove(n);
               PvpBotMod.GOTOS.remove(n);
               run(server, "player " + n + " stop");
            }
         }
      }
   }

   static void ffaEliminate(MinecraftServer server, String name, ServerPlayerEntity player) {
      if (PvpBotMod.FFA_ALIVE.remove(name)) {
         String killer = PvpBotMod.LAST_ATTACKER.get(name);
         boolean credited = killer != null && !killer.equalsIgnoreCase(name) && (PvpBotMod.FFA_BOTS.contains(killer) || PvpBotMod.FFA_HUMANS.contains(killer));
         if (credited) {
            PvpBotMod.FFA_KILLS.merge(killer, 1, Integer::sum);
         }

         if (PvpBotMod.FFA_HUMANS.contains(name)) {
            PvpBotMod.FFA_OUT_HUMANS.add(name);
         } else if (player != null) {
            run(server, "player " + name + " disconnect");
         }

         broadcast(server, name + " was eliminated" + (credited ? " by " + killer : "") + " (" + PvpBotMod.FFA_ALIVE.size() + " left)");
         if (credited && PvpBotMod.TAUNTS.on() && PvpBotMod.FFA_BOTS.contains(killer)) {
            broadcast(server, "<" + killer + "> " + PvpBotMod.TAUNT_LINES[ThreadLocalRandom.current().nextInt(PvpBotMod.TAUNT_LINES.length)]);
         }
      }
   }

   static void tickFfa(MinecraftServer server) {
      if (PvpBotMod.ffaAutoStart && PvpBotMod.MASS_QUEUE.isEmpty() && PvpBotMod.PENDING.isEmpty()) {
         PvpBotMod.ffaAutoStart = false;
         String err = startFfa(server, PvpBotMod.ffaOwner, PvpBotMod.ffaAutoTeams);
         if (err != null) {
            broadcast(server, err);
         }
      } else if (PvpBotMod.ffaActive) {
         if (PvpBotMod.globalTick < PvpBotMod.ffaGoTick) {
            int left = (PvpBotMod.ffaGoTick - PvpBotMod.globalTick + 19) / 20;
            if (left != PvpBotMod.ffaLastCount) {
               PvpBotMod.ffaLastCount = left;
               broadcast(server, "FFA starts in " + left + "...");
            }
         } else {
            if (PvpBotMod.ffaLastCount != 0) {
               PvpBotMod.ffaLastCount = 0;
               broadcast(server, "FIGHT!");
            }

            if (PvpBotMod.globalTick % 5 == 0) {
               for (String name : new ArrayList<>(PvpBotMod.FFA_ALIVE)) {
                  ServerPlayerEntity p = server.getPlayerManager().getPlayer(name);
                  if (p != null) {
                     LivingEntity hurtBy = p.getAttacker();
                     if (hurtBy instanceof ServerPlayerEntity) {
                        PvpBotMod.LAST_ATTACKER.put(name, ((ServerPlayerEntity)hurtBy).getName().getString());
                     }
                  }
               }

               for (String namex : new ArrayList<>(PvpBotMod.FFA_ALIVE)) {
                  ServerPlayerEntity p = server.getPlayerManager().getPlayer(namex);
                  if (p == null || !p.isAlive()) {
                     ffaEliminate(server, namex, p);
                  }
               }

               if (PvpBotMod.globalTick % 20 == 0) {
                  for (String n : PvpBotMod.FFA_OUT_HUMANS) {
                     ServerPlayerEntity p = server.getPlayerManager().getPlayer(n);
                     if (p != null && p.isAlive() && !p.isSpectator()) {
                        run(server, "gamemode spectator " + n);
                     }
                  }
               }

               if (PvpBotMod.FFA_ALIVE.size() <= 1 || PvpBotMod.ffaTeams && commonTeam(server, PvpBotMod.FFA_ALIVE) != null) {
                  endFfa(server, true);
               }
            }
         }
      }
   }
}

final class Fighting {
   static void tickFights(MinecraftServer server) {
      Iterator<Entry<String, PvpBotMod.Fight>> it = PvpBotMod.FIGHTS.entrySet().iterator();
      PvpBotMod.fightCount = PvpBotMod.FIGHTS.size();
      int orderIdx = 0;

      while (it.hasNext()) {
         Entry<String, PvpBotMod.Fight> e = it.next();
         String name = e.getKey();
         PvpBotMod.Fight f = e.getValue();
         f.order = orderIdx++;
         ServerPlayerEntity bot = server.getPlayerManager().getPlayer(name);
         if (bot == null) {
            it.remove();
            PvpBotMod.BOTS.remove(name);
            PvpBotMod.LOOTING.remove(name);
         } else if (!PvpBotMod.LOOTING.containsKey(name)) {
            if (bot != f.lastBot) {
               f.lastBot = bot;
               f.reset();
            }

            ServerPlayerEntity target = server.getPlayerManager().getPlayer(f.target);
            if (!BotSupport.reachable(bot, target)) {
               if (f.started || f.hopping || f.phase != PvpBotMod.Phase.FIGHT) {
                  BotSupport.run(server, "player " + name + " stop");
                  f.reset();
               }
            } else if (f.phase == PvpBotMod.Phase.COCOON || !BotSupport.webEscape(server, name, f, bot)) {
               double dist = bot.distanceTo(target);
               if (bot.isOnGround()) {
                  f.swapRoll = 0;
                  f.spearRoll = 0;
               }

               if (f.phase != PvpBotMod.Phase.FALL) {
                  fallTrigger(server, name, f, bot, target, dist);
               }

               switch (f.phase) {
                  case FIGHT:
                     fightPhase(server, name, f, bot, target, dist);
                     break;
                  case FLEE:
                     fleePhase(server, name, f, bot, target, dist);
                     break;
                  case EAT:
                     eatPhase(server, name, f, bot, target, dist);
                     break;
                  case COCOON:
                     cocoonPhase(server, name, f, bot, target, dist);
                     break;
                  case POTION:
                     potionPhase(server, name, f, bot, target, dist);
                     break;
                  case WEBTRAP:
                     webtrapPhase(server, name, f, bot, target, dist);
                     break;
                  case WINDESCAPE:
                     windEscapePhase(server, name, f, bot, target, dist);
                     break;
                  case PEARLESCAPE:
                     pearlEscapePhase(server, name, f, bot, target, dist);
                     break;
                  case WINDLAUNCH:
                     windLaunchPhase(server, name, f, bot, target, dist);
                     break;
                  case MACEFEAR:
                     maceFearPhase(server, name, f, bot, target, dist);
                     break;
                  case EATKB:
                     eatKnockbackPhase(server, name, f, bot, target, dist);
                     break;
                  case FALL:
                     fallPhase(server, name, f, bot, target, dist);
               }
            }
         }
      }
   }

   static boolean doAttack(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity target) {
      if (PvpBotMod.PRECISION_LOCK.on()) {
         BotSupport.run(server, BotSupport.lookCmd(name, target.getName().getString()));
      }

      double before = target.getHealth() + target.getAbsorptionAmount();
      if (PvpBotMod.MISS_CHANCE.value > 0.0 && ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.MISS_CHANCE.value) {
         BotSupport.swing(f);
      } else {
         ServerPlayerEntity self = f.lastBot;
         if (self != null && BotSupport.rayReach(self, target, PvpBotMod.ATTACK_RANGE.value) && self.canSee(target)) {
            boolean swapped = attributeSwap(server, name, f, self);
            self.attack(target);
            if (swapped) {
               f.weaponSlot = -1;
            }
         }

         BotSupport.swing(f);
      }

      f.attackTick = PvpBotMod.globalTick;
      f.lastHit = !target.isAlive() || target.getHealth() + target.getAbsorptionAmount() < before - 0.01;
      return f.lastHit;
   }

   static void axisCmd(MinecraftServer server, String name, PvpBotMod.Fight f) {
      if (f.keepMode > 0) {
         BotSupport.run(server, "player " + name + " move forward");
      } else if (f.keepMode < 0) {
         BotSupport.run(server, "player " + name + " move backward");
      }
   }

   static void resumeMove(MinecraftServer server, String name, PvpBotMod.Fight f) {
      if (!f.edgeHold) {
         axisCmd(server, name, f);
         if (f.keepMode > 0) {
            BotSupport.run(server, "player " + name + " sprint");
         }
      }
   }

   static void stopStrafe(MinecraftServer server, String name, PvpBotMod.Fight f) {
      BotSupport.run(server, "player " + name + " move");
      axisCmd(server, name, f);
      f.strafeDir = 0;
   }

   static void maybeAim(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target) {
      Vec3d tv = target.getVelocity();
      boolean targetMoving = tv.x * tv.x + tv.z * tv.z > 0.01;
      int interval = targetMoving ? 1 : 2;
      if (PvpBotMod.globalTick % interval == 0) {
         double cone = PvpBotMod.AIM_CONE.value;
         if (cone > 0.0 && !targetMoving) {
            double diff = Math.abs(BotSupport.wrapDegrees(BotSupport.directYaw(bot, target) - bot.getYaw()));
            if (diff < cone) {
               return;
            }
         }

         BotSupport.run(server, BotSupport.aimCmd(name, bot, target, f));
      }
   }

   static void fightPhase(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target, double dist) {
      double hpNow = bot.getHealth() + bot.getAbsorptionAmount();
      if (f.lastHp >= 0.0 && hpNow < f.lastHp - 0.01) {
         if (PvpBotMod.JUMP_RESET.value > 0.0 && bot.isOnGround() && ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.JUMP_RESET.value) {
            BotSupport.run(server, "player " + name + " jump once");
         }

         if (PvpBotMod.PUNISH_CRIT_CHANCE.value > 0.0 && ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.PUNISH_CRIT_CHANCE.value) {
            f.punishCrit = true;
            f.flinchUntil = PvpBotMod.globalTick;
         } else if (PvpBotMod.FLINCH_CHANCE.value > 0.0 && ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.FLINCH_CHANCE.value) {
            int lo = Math.min(PvpBotMod.FLINCH_TICKS_MIN.asInt(), PvpBotMod.FLINCH_TICKS_MAX.asInt());
            int hi = Math.max(PvpBotMod.FLINCH_TICKS_MIN.asInt(), PvpBotMod.FLINCH_TICKS_MAX.asInt());
            f.flinchUntil = PvpBotMod.globalTick + lo + ThreadLocalRandom.current().nextInt(hi - lo + 1);
         }
      }

      f.lastHp = hpNow;
      double targetScale = target.getAttackCooldownProgress(0.5F);
      if (f.lastTargetScale > 0.9
         && targetScale < 0.3
         && PvpBotMod.RECOVERY_CHANCE.value > 0.0
         && ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.RECOVERY_CHANCE.value) {
         f.reactAt = PvpBotMod.globalTick;
      }

      f.lastTargetScale = targetScale;
      boolean hasTotemNow = target.getEquippedStack(EquipmentSlot.OFFHAND).isOf(Items.TOTEM_OF_UNDYING)
         || target.getEquippedStack(EquipmentSlot.MAINHAND).isOf(Items.TOTEM_OF_UNDYING);
      if (f.targetHadTotem && !hasTotemNow && target.isAlive() && PvpBotMod.TOTEM_PUNISH_TICKS.asInt() > 0) {
         f.critChainUntil = Math.max(f.critChainUntil, PvpBotMod.globalTick + PvpBotMod.TOTEM_PUNISH_TICKS.asInt());
      }

      f.targetHadTotem = hasTotemNow;
      LivingEntity botHurtBy = bot.getAttacker();
      int botHurtStampNow = bot.getLastAttackedTime();
      if (botHurtBy == target && botHurtStampNow != f.botHurtStamp) {
         f.botHurtStamp = botHurtStampNow;
         f.comboHitsStreak++;
         if (f.comboActive != 0) {
            f.comboActive = 0;
            f.comboType = -1;
         }
      }

      if (f.comboHitsStreak > 0) {
         if (f.comboHitsThreshold < 0) {
            int lo = Math.min(PvpBotMod.COMBOHITS_MINHIT.asInt(), PvpBotMod.COMBOHITS_MAXHIT.asInt());
            int hi = Math.max(PvpBotMod.COMBOHITS_MINHIT.asInt(), PvpBotMod.COMBOHITS_MAXHIT.asInt());
            f.comboHitsThreshold = lo + ThreadLocalRandom.current().nextInt(hi - lo + 1);
         }

         if (f.comboHitsStreak >= f.comboHitsThreshold && ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.COMBOHITS_ESCAPECHANCE.value) {
            f.comboHitsStreak = 0;
            f.comboHitsThreshold = -1;
            if (startComboEscape(server, name, f, bot, target)) {
               return;
            }
         }
      }

      if (maceFearStep(server, name, f, bot, target, dist)
         || windClimbStep(server, name, f, bot, target)
         || windMaceStep(server, name, f, bot, target, dist)) {
         return;
      }

      int potionWanted = PvpBotMod.POTIONS.on() && PvpBotMod.globalTick >= f.nextPotionTick ? BotSupport.wantedPotion(bot, f, dist, hpNow) : 0;
      if (potionWanted != 0) {
         startPotionRun(server, name, f, dist, potionWanted, hpNow);
      } else if (!BotSupport.cocoonReady(bot, f, hpNow) || !startCocoon(server, name, f, bot)) {
         if (!(PvpBotMod.WEBTRAP_CHANCE.value > 0.0)
            || !(PvpBotMod.WEBTRAP_HEARTS.value > 0.0)
            || !(hpNow <= PvpBotMod.WEBTRAP_HEARTS.value * 2.0)
            || PvpBotMod.globalTick < f.nextEatTick
            || BotSupport.findFoodIndex(bot) < 0
            || BotSupport.countItem(bot, Items.COBWEB) < Math.max(2, PvpBotMod.WEBTRAP_COUNT.asInt())
            || !(ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.WEBTRAP_CHANCE.value)
            || !startWebTrap(server, name, f, bot, target)) {
            boolean hungry = PvpBotMod.HUNGER_EAT_LEVEL.value > 0.0 && bot.getHungerManager().getFoodLevel() <= PvpBotMod.HUNGER_EAT_LEVEL.asInt();
            boolean lowHealth = PvpBotMod.EAT_HEARTS.value > 0.0 && hpNow <= PvpBotMod.EAT_HEARTS.value * 2.0;
            if ((lowHealth || hungry) && PvpBotMod.globalTick >= f.nextEatTick && BotSupport.findFoodIndex(bot) >= 0) {
               if (!startEatKnockback(server, name, f, bot, target, dist) && !startItemEscape(server, name, f, bot, target)) {
                  startFlee(server, name, f, 0);
               }
            } else if (!BotSupport.webPlaceStep(server, name, f, bot, target)) {
               if (!BotSupport.breakoutStep(server, name, f, bot, target)) {
                  if (pathStep(server, name, f, bot, target, dist)) {
                     return;
                  }

                  maybeAim(server, name, f, bot, target);
                  boolean vulnerable = BotSupport.targetVulnerable(bot, target);
                  boolean archer = PvpBotMod.ARCHER_RUSH.on()
                     && (target.getMainHandStack().isOf(Items.BOW) || target.getMainHandStack().isOf(Items.CROSSBOW));
                  if (vulnerable && PvpBotMod.FALL_CRIT.on() && bot.isOnGround() && !f.hopping && !f.crit && dist <= PvpBotMod.JUMP_RANGE.value) {
                     BotSupport.run(server, "player " + name + " jump once");
                  }

                  double hx = target.getX() - bot.getX();
                  double hz = target.getZ() - bot.getZ();
                  double hl = Math.sqrt(hx * hx + hz * hz);
                  boolean edgeAhead = false;
                  boolean edgeBehind = false;
                  if (PvpBotMod.VOID_AWARE.on() && hl > 0.3) {
                     double ux = hx / hl;
                     double uz = hz / hl;
                     edgeAhead = BotSupport.hazardAt(BotSupport.sw(bot), bot.getX() + ux * 1.4, bot.getY(), bot.getZ() + uz * 1.4);
                     edgeBehind = BotSupport.hazardAt(BotSupport.sw(bot), bot.getX() - ux * 1.4, bot.getY(), bot.getZ() - uz * 1.4);
                  }

                  if (PvpBotMod.WEB_AVOID.on() && hl > 0.3 && f.strafeDir == 0 && !f.hopping && !f.edgeHold) {
                     double ux = hx / hl;
                     double uz = hz / hl;
                     if (BotSupport.webAheadOf(
                        BotSupport.sw(bot), bot.getX() + ux * 1.2, bot.getY(), bot.getZ() + uz * 1.2, target.getBlockPos()
                     )) {
                        f.strafeDir = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
                        BotSupport.run(server, "player " + name + " move " + (f.strafeDir > 0 ? "left" : "right"));
                        axisCmd(server, name, f);
                        f.sidestepUntil = PvpBotMod.globalTick + 10;
                        f.nextStrafeTick = PvpBotMod.globalTick + 12;
                     }
                  }

                  if (!f.started && !f.paused) {
                     BotSupport.run(server, "player " + name + " autojump true");
                     BotSupport.run(server, "player " + name + " sprint");
                     BotSupport.run(server, "player " + name + " move forward");
                     f.keepMode = 1;
                     f.edgeHold = false;
                     f.started = true;
                  }

                  if (f.edgeHold) {
                     if (!edgeAhead) {
                        f.edgeHold = false;
                        f.started = false;
                     }
                  } else if (edgeAhead && f.keepMode >= 0 && dist > 1.5 && !f.paused) {
                     f.edgeHold = true;
                     f.keepMode = 0;
                     f.strafeDir = 0;
                     BotSupport.run(server, "player " + name + " move");
                     if (f.hopping) {
                        BotSupport.run(server, "player " + name + " jump");
                        f.hopping = false;
                     }
                  }

                  boolean targetBlocks = target.isBlocking();
                  if (targetBlocks && !f.targetBlocking) {
                     f.targetBlocking = true;
                     if (PvpBotMod.STUN_CHANCE.value > 0.0
                        && BotSupport.findAxeSlot(bot) >= 0
                        && ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.STUN_CHANCE.value) {
                        f.axeMode = true;
                        f.weaponSlot = -1;
                     }
                  } else if (!targetBlocks && f.targetBlocking) {
                     f.targetBlocking = false;
                     if (f.axeMode) {
                        f.axeMode = false;
                        f.weaponSlot = -1;
                     }
                  }

                  if (f.weaponSlot < 0 || PvpBotMod.globalTick % 10 == 0) {
                     int w = f.axeMode ? BotSupport.findAxeSlot(bot) : BotSupport.findWeaponSlot(bot);
                     if (w < 0) {
                        w = BotSupport.findWeaponSlot(bot);
                     }

                     if (w >= 0 && w != f.weaponSlot) {
                        BotSupport.run(server, "player " + name + " hotbar " + (w + 1));
                        f.weaponSlot = w;
                     }
                  }

                  if (f.attackTick >= 0 && PvpBotMod.globalTick - f.attackTick >= 2) {
                     f.attackTick = -1;
                     if (f.comboActive != 0 && !f.edgeHold) {
                        int type = f.comboMixed
                           ? (
                              PvpBotMod.MIX_SWITCHCHANCE.value > 0.0 && ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.MIX_SWITCHCHANCE.value
                                 ? pickComboType()
                                 : f.comboType
                           )
                           : f.comboType;
                        f.comboType = type;
                        runComboStep(server, name, f, bot, target, type, edgeBehind);
                        if (f.comboMixed) {
                           f.comboMixHitsLeft--;
                           if (f.comboMixHitsLeft <= 0) {
                              f.comboActive = 0;
                              f.comboType = -1;
                           }
                        }
                     } else if (PvpBotMod.POSTSWING_SHIELD_CHANCE.value > 0.0
                        && !f.blocking
                        && bot.getEquippedStack(EquipmentSlot.OFFHAND).isOf(Items.SHIELD)
                        && ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.POSTSWING_SHIELD_CHANCE.value) {
                        BotSupport.run(server, "player " + name + " use continuous");
                        f.blocking = true;
                        int lo = Math.min(PvpBotMod.POSTSWING_SHIELD_TICKS_MIN.asInt(), PvpBotMod.POSTSWING_SHIELD_TICKS_MAX.asInt());
                        int hi = Math.max(PvpBotMod.POSTSWING_SHIELD_TICKS_MIN.asInt(), PvpBotMod.POSTSWING_SHIELD_TICKS_MAX.asInt());
                        f.blockUntil = PvpBotMod.globalTick + Math.max(2, lo + ThreadLocalRandom.current().nextInt(hi - lo + 1));
                     }
                  }

                  if (f.paused) {
                     if (PvpBotMod.globalTick >= f.pauseUntil) {
                        resumeMove(server, name, f);
                        f.paused = false;
                        f.nextStrafeTick = PvpBotMod.globalTick;
                     }
                  } else {
                     if (!f.edgeHold) {
                        if (f.hopping) {
                           if (!PvpBotMod.BUNNY_HOP.on() || dist <= PvpBotMod.HOP_STOP.value) {
                              BotSupport.run(server, "player " + name + " jump");
                              f.hopping = false;
                           }
                        } else if (PvpBotMod.BUNNY_HOP.on() && !f.crit && dist > PvpBotMod.HOP_STOP.value + 1.0) {
                           BotSupport.run(server, "player " + name + " jump continuous");
                           f.hopping = true;
                           if (f.keepMode != 1) {
                              f.keepMode = 1;
                              BotSupport.run(server, "player " + name + " move forward");
                              BotSupport.run(server, "player " + name + " sprint");
                           }
                        }

                        double effectiveKeep = !vulnerable && !archer ? PvpBotMod.KEEP_DISTANCE.value : PvpBotMod.PRESSURE_KEEP_DIST.value;
                        if (effectiveKeep > 0.0) {
                           if (!f.hopping && !f.crit) {
                              int want = dist < effectiveKeep - 0.5 ? -1 : (dist > effectiveKeep + 0.5 ? 1 : 0);
                              if (want < 0 && edgeBehind) {
                                 want = 0;
                              }

                              if (want != f.keepMode) {
                                 f.keepMode = want;
                                 if (want < 0) {
                                    BotSupport.run(server, "player " + name + " move backward");
                                 } else if (want > 0) {
                                    BotSupport.run(server, "player " + name + " move forward");
                                    BotSupport.run(server, "player " + name + " sprint");
                                 } else {
                                    BotSupport.run(server, "player " + name + " move");
                                    f.strafeDir = 0;
                                    f.nextStrafeTick = PvpBotMod.globalTick;
                                 }
                              }
                           }
                        } else if (f.keepMode != 1) {
                           f.keepMode = 1;
                           BotSupport.run(server, "player " + name + " move forward");
                           BotSupport.run(server, "player " + name + " sprint");
                        }

                        if (PvpBotMod.STRAFE.on() && !f.hopping && !f.crit && dist <= 6.0 && !targetMaceThreat(bot, target)) {
                           if (PvpBotMod.globalTick >= f.nextStrafeTick) {
                              if (ThreadLocalRandom.current().nextDouble() * 100.0 < (archer ? 100.0 : PvpBotMod.STRAFE_CHANCE.value)) {
                                 f.strafeDir = f.strafeDir == 0 ? (ThreadLocalRandom.current().nextBoolean() ? 1 : -1) : -f.strafeDir;
                                 BotSupport.run(server, "player " + name + " move " + (f.strafeDir > 0 ? "left" : "right"));
                                 axisCmd(server, name, f);
                              } else if (f.strafeDir != 0) {
                                 stopStrafe(server, name, f);
                              }

                              int base = Math.max(2, PvpBotMod.STRAFE_TICKS.asInt());
                              f.nextStrafeTick = PvpBotMod.globalTick + base + ThreadLocalRandom.current().nextInt(base / 2 + 1);
                           }
                        } else if (f.strafeDir != 0 && PvpBotMod.globalTick >= f.sidestepUntil) {
                           stopStrafe(server, name, f);
                        }

                        if (PvpBotMod.STUCK.on() && PvpBotMod.globalTick % 20 == 0) {
                           Vec3d here = bot.getEntityPos();
                           if (f.lastPos != null && f.lastPos.distanceTo(here) < 0.2 && !f.blocking && dist > PvpBotMod.ATTACK_RANGE.value + 1.0) {
                              BotSupport.run(server, "player " + name + " jump once");
                              f.strafeDir = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
                              BotSupport.run(server, "player " + name + " move " + (f.strafeDir > 0 ? "left" : "right"));
                              axisCmd(server, name, f);
                              f.sidestepUntil = PvpBotMod.globalTick + 10;
                              f.nextStrafeTick = PvpBotMod.globalTick + 12;
                           }

                           f.lastPos = here;
                        }

                        if (PvpBotMod.JUMP_CHANCE.value > 0.0
                           && !f.hopping
                           && !f.crit
                           && dist <= PvpBotMod.JUMP_RANGE.value
                           && PvpBotMod.globalTick % 5 == 0
                           && bot.isOnGround()
                           && ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.JUMP_CHANCE.value) {
                           BotSupport.run(server, "player " + name + " jump once");
                        }
                     }

                     if (!f.crit && !f.edgeHold && f.keepMode > 0 && PvpBotMod.globalTick >= f.sprintHoldUntil && !bot.isSprinting()) {
                        BotSupport.run(server, "player " + name + " sprint");
                     }

                     double reach = BotSupport.reachDistance(bot, target);
                     boolean ready = bot.getAttackCooldownProgress(0.5F) >= 1.0F && PvpBotMod.globalTick >= f.flinchUntil;
                     if (f.comboUppercutRising) {
                        if (!bot.isOnGround() && bot.getVelocity().y > 0.0 && reach <= PvpBotMod.ATTACK_RANGE.value && ready) {
                           doAttack(server, name, f, target);
                           f.comboUppercutRising = false;
                           f.sprintHoldUntil = PvpBotMod.globalTick + 3;
                           bot.fallDistance = 0.0;
                           return;
                        }

                        if (PvpBotMod.globalTick - f.comboUppercutStart > PvpBotMod.COMBO_UPPERCUT_WINDOW.asInt()
                           || bot.isOnGround() && bot.getVelocity().y <= 0.0) {
                           f.comboUppercutRising = false;
                           bot.fallDistance = 0.0;
                        }
                     }

                     LivingEntity lastHitter = target.getAttacker();
                     int hitStamp = target.getLastAttackedTime();
                     if (lastHitter == bot && hitStamp != f.lastHitStamp) {
                        f.lastHitStamp = hitStamp;
                        f.comboHitsStreak = 0;
                        f.comboHitsThreshold = -1;
                        BotSupport.startWebPlace(server, name, f, bot, target);
                        if (f.comboActive == 0
                           && PvpBotMod.COMBO_CHANCE.value > 0.0
                           && ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.COMBO_CHANCE.value) {
                           startCombo(f);
                        }
                     }

                     boolean maceThreat = fallShieldWanted(f, bot, target)
                           && !f.blocking
                           && bot.getEquippedStack(EquipmentSlot.OFFHAND).isOf(Items.SHIELD)
                        || PvpBotMod.MACE_AWARE.on()
                        && !f.blocking
                        && (
                           target.getMainHandStack().isOf(Items.MACE)
                              || target.getEquippedStack(EquipmentSlot.OFFHAND).isOf(Items.MACE)
                        )
                        && !target.isOnGround()
                        && target.getY() > bot.getY() + 1.0
                        && bot.getEquippedStack(EquipmentSlot.OFFHAND).isOf(Items.SHIELD);
                     if (maceThreat) {
                        BotSupport.run(server, "player " + name + " use continuous");
                        f.blocking = true;
                        f.blockUntil = PvpBotMod.globalTick + Math.max(2, PvpBotMod.SHIELD_TICKS_MAX.asInt());
                     } else {
                        if (f.blocking) {
                           if (PvpBotMod.globalTick < f.blockUntil) {
                              return;
                           }

                           BotSupport.run(server, "player " + name + " use");
                           f.blocking = false;
                        } else if (PvpBotMod.SHIELD_CHANCE.value > 0.0
                           && f.critChainUntil <= PvpBotMod.globalTick
                           && !ready
                           && dist <= PvpBotMod.SHIELD_RANGE.value
                           && f.comboActive == 0
                           && bot.getEquippedStack(EquipmentSlot.OFFHAND).isOf(Items.SHIELD)
                           && ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.SHIELD_CHANCE.value) {
                           BotSupport.run(server, "player " + name + " use continuous");
                           f.blocking = true;
                           int lo = Math.min(PvpBotMod.SHIELD_TICKS.asInt(), PvpBotMod.SHIELD_TICKS_MAX.asInt());
                           int hi = Math.max(PvpBotMod.SHIELD_TICKS.asInt(), PvpBotMod.SHIELD_TICKS_MAX.asInt());
                           f.blockUntil = PvpBotMod.globalTick + Math.max(2, lo + ThreadLocalRandom.current().nextInt(hi - lo + 1));
                           return;
                        }

                        if (f.axeMode && bot.getMainHandStack().isIn(ItemTags.AXES)) {
                           if (reach <= PvpBotMod.ATTACK_RANGE.value) {
                              doAttack(server, name, f, target);
                              f.axeMode = false;
                              f.weaponSlot = -1;
                              f.reactAt = -1;
                           }
                        } else if (f.crit) {
                           boolean falling = !bot.isOnGround() && bot.getVelocity().y < 0.0;
                           if (falling && reach <= PvpBotMod.ATTACK_RANGE.value && ready) {
                              doAttack(server, name, f, target);
                              f.crit = false;
                              f.sprintHoldUntil = PvpBotMod.globalTick + 3;
                           } else if (PvpBotMod.globalTick - f.critStart > 25) {
                              f.crit = false;
                              BotSupport.run(server, "player " + name + " sprint");
                           }
                        } else if (PvpBotMod.FALL_CRIT.on()
                           && ready
                           && reach <= PvpBotMod.ATTACK_RANGE.value
                           && !bot.isOnGround()
                           && bot.getVelocity().y < 0.0) {
                           BotSupport.run(server, "player " + name + " unsprint");
                           doAttack(server, name, f, target);
                           f.reactAt = -1;
                           f.sprintHoldUntil = PvpBotMod.globalTick + 3;
                        } else {
                           boolean guaranteedWebCrit = PvpBotMod.WEB_CRIT.on() && BotSupport.inCobweb(target);
                           boolean critPossible = PvpBotMod.CRIT_CHANCE.value > 0.0 && dist <= PvpBotMod.CRIT_RANGE.value;
                           if (ready && (reach <= PvpBotMod.ATTACK_RANGE.value || critPossible || guaranteedWebCrit || f.punishCrit)) {
                              if (f.reactAt < 0) {
                                 int lo = Math.min(PvpBotMod.REACT_MIN.asInt(), PvpBotMod.REACT_MAX.asInt());
                                 int hi = Math.max(PvpBotMod.REACT_MIN.asInt(), PvpBotMod.REACT_MAX.asInt());
                                 f.reactAt = PvpBotMod.globalTick + lo + ThreadLocalRandom.current().nextInt(hi - lo + 1);
                              }

                              if (PvpBotMod.globalTick >= f.reactAt) {
                                 f.reactAt = -1;
                                 boolean chaining = f.critChainUntil > PvpBotMod.globalTick;
                                 boolean tryCrit;
                                 if (f.punishCrit) {
                                    tryCrit = bot.isOnGround() && !f.hopping;
                                    f.punishCrit = false;
                                 } else if (guaranteedWebCrit) {
                                    tryCrit = bot.isOnGround() && !f.hopping;
                                 } else if (chaining) {
                                    tryCrit = critPossible && bot.isOnGround() && !f.hopping;
                                 } else {
                                    tryCrit = critPossible
                                       && bot.isOnGround()
                                       && !f.hopping
                                       && ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.CRIT_CHANCE.value;
                                    boolean critVulnerable = tryCrit && vulnerable;
                                    if (critVulnerable
                                       && PvpBotMod.CRIT_CHAIN_CHANCE.value > 0.0
                                       && ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.CRIT_CHAIN_CHANCE.value) {
                                       int lo = Math.min(PvpBotMod.CRIT_CHAIN_MIN.asInt(), PvpBotMod.CRIT_CHAIN_MAX.asInt());
                                       int hi = Math.max(PvpBotMod.CRIT_CHAIN_MIN.asInt(), PvpBotMod.CRIT_CHAIN_MAX.asInt());
                                       f.critChainUntil = PvpBotMod.globalTick + lo + ThreadLocalRandom.current().nextInt(hi - lo + 1);
                                    }
                                 }

                                 if (tryCrit) {
                                    BotSupport.run(server, "player " + name + " unsprint");
                                    BotSupport.run(server, "player " + name + " jump once");
                                    f.crit = true;
                                    f.critStart = PvpBotMod.globalTick;
                                 } else if (reach <= PvpBotMod.ATTACK_RANGE.value) {
                                    doAttack(server, name, f, target);
                                 }
                              }
                           } else {
                              f.reactAt = -1;
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   static int pickComboType() {
      if (PvpBotMod.COMBO_STAP_CHANCE.value > 0.0 && ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.COMBO_STAP_CHANCE.value) {
         return 0;
      } else if (PvpBotMod.COMBO_WTAP_CHANCE.value > 0.0 && ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.COMBO_WTAP_CHANCE.value) {
         return 1;
      } else if (PvpBotMod.COMBO_UPPERCUT_CHANCE.value > 0.0 && ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.COMBO_UPPERCUT_CHANCE.value) {
         return 2;
      } else {
         return PvpBotMod.COMBO_STRAFECOMBO_CHANCE.value > 0.0 && ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.COMBO_STRAFECOMBO_CHANCE.value
            ? 3
            : 0;
      }
   }

   static void startCombo(PvpBotMod.Fight f) {
      boolean mixed = PvpBotMod.MIX_CHANCE.value > 0.0 && ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.MIX_CHANCE.value;
      f.comboActive = 1;
      f.comboMixed = mixed;
      f.comboMixHitsLeft = mixed ? Math.max(1, PvpBotMod.MIX_MAXHITS.asInt()) : Integer.MAX_VALUE;
      f.comboType = pickComboType();
   }

   static void runComboStep(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target, int type, boolean edgeBehind) {
      if (f.hopping) {
         BotSupport.run(server, "player " + name + " jump");
         f.hopping = false;
      }

      switch (type) {
         case 0:
            if (!edgeBehind) {
               BotSupport.run(server, "player " + name + " move backward");
            } else {
               BotSupport.run(server, "player " + name + " move");
               f.strafeDir = 0;
            }

            f.paused = true;
            f.pauseUntil = PvpBotMod.globalTick + Math.max(1, PvpBotMod.COMBO_TAP_TICKS.asInt());
            break;
         case 1:
            BotSupport.run(server, "player " + name + " move");
            f.strafeDir = 0;
            f.paused = true;
            f.pauseUntil = PvpBotMod.globalTick + Math.max(1, PvpBotMod.COMBO_TAP_TICKS.asInt());
            break;
         case 2:
            double dx = target.getX() - bot.getX();
            double dz = target.getZ() - bot.getZ();
            double d2 = Math.sqrt(dx * dx + dz * dz);
            if (bot.isOnGround() && d2 <= PvpBotMod.COMBO_UPPERCUT_RANGE.value) {
               BotSupport.run(server, "player " + name + " sprint");
               BotSupport.run(server, "player " + name + " move forward");
               BotSupport.run(server, "player " + name + " jump once");
               f.comboUppercutRising = true;
               f.comboUppercutStart = PvpBotMod.globalTick;
            }
            break;
         case 3:
            f.strafeDir = f.strafeDir == 0 ? (ThreadLocalRandom.current().nextBoolean() ? 1 : -1) : -f.strafeDir;
            BotSupport.run(server, "player " + name + " move " + (f.strafeDir > 0 ? "left" : "right"));
            axisCmd(server, name, f);
            f.paused = true;
            f.pauseUntil = PvpBotMod.globalTick + Math.max(1, PvpBotMod.COMBO_STRAFE_SWITCH_TICKS.asInt());
      }
   }

   static boolean startComboEscape(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target) {
      boolean hasWind = BotSupport.findItemIndex(bot, Items.WIND_CHARGE) >= 0;
      boolean hasPearl = BotSupport.findItemIndex(bot, Items.ENDER_PEARL) >= 0;
      boolean hasWeb = BotSupport.countItem(bot, Items.COBWEB) >= Math.max(2, PvpBotMod.WEBTRAP_COUNT.asInt());
      if (PvpBotMod.COMBOHITS_WINDCHARGECHANCE.value > 0.0 && ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.COMBOHITS_WINDCHARGECHANCE.value) {
         if (hasWind && startWindEscape(server, name, f, bot, target)) {
            return true;
         }

         if (hasPearl && startPearlEscape(server, name, f, bot, target)) {
            return true;
         }
      }

      if (hasWeb
         && PvpBotMod.COMBOHITS_WEBCHANCE.value > 0.0
         && ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.COMBOHITS_WEBCHANCE.value
         && startWebTrap(server, name, f, bot, target)) {
         return true;
      } else if (PvpBotMod.COMBOHITS_RUNCHANCE.value > 0.0 && ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.COMBOHITS_RUNCHANCE.value) {
         startFlee(server, name, f, 0);
         return true;
      } else {
         startFlee(server, name, f, 0);
         return true;
      }
   }

   static boolean startItemEscape(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target) {
      if (!(PvpBotMod.WINDCHARGE_HEAL_CHANCE.value <= 0.0) && !(ThreadLocalRandom.current().nextDouble() * 100.0 >= PvpBotMod.WINDCHARGE_HEAL_CHANCE.value)) {
         boolean hasWind = BotSupport.findItemIndex(bot, Items.WIND_CHARGE) >= 0;
         boolean hasPearl = BotSupport.findItemIndex(bot, Items.ENDER_PEARL) >= 0;
         if (hasWind && hasPearl) {
            boolean windFirst = ThreadLocalRandom.current().nextBoolean();
            return windFirst
               ? startWindEscape(server, name, f, bot, target) || startPearlEscape(server, name, f, bot, target)
               : startPearlEscape(server, name, f, bot, target) || startWindEscape(server, name, f, bot, target);
         } else if (hasWind) {
            return startWindEscape(server, name, f, bot, target);
         } else {
            return hasPearl ? startPearlEscape(server, name, f, bot, target) : false;
         }
      } else {
         return false;
      }
   }

   static boolean startWebTrap(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target) {
      int idx = BotSupport.findItemIndex(bot, Items.COBWEB);
      int slot = idx < 0 ? -1 : BotSupport.toHotbar(bot, idx, BotSupport.findWeaponSlot(bot));
      if (slot < 0) {
         return false;
      } else {
         BotSupport.run(server, "player " + name + " stop");
         BotSupport.run(server, "player " + name + " hotbar " + (slot + 1));
         double dx = bot.getX() - target.getX();
         double dz = bot.getZ() - target.getZ();
         double yaw = Math.toDegrees(Math.atan2(-dx, dz));
         BotSupport.run(server, "player " + name + " look " + String.format(Locale.ROOT, "%.1f", yaw) + " 90");
         f.phase = PvpBotMod.Phase.WEBTRAP;
         f.webtrapStage = 1;
         f.webtrapStart = PvpBotMod.globalTick;
         f.webtrapPlaced = 0;
         f.strafeDir = 0;
         f.started = false;
         f.hopping = false;
         f.paused = false;
         f.crit = false;
         f.blocking = false;
         f.axeMode = false;
         f.placeStage = 0;
         f.attackTick = -1;
         f.edgeHold = false;
         return true;
      }
   }

   static void webtrapPhase(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target, double dist) {
      int elapsed = PvpBotMod.globalTick - f.webtrapStart;
      int need = Math.max(2, PvpBotMod.WEBTRAP_COUNT.asInt());
      if (f.webtrapStage == 1) {
         if (elapsed >= 1) {
            BotSupport.useOnce(server, name, f);
            f.webtrapStage = 2;
            f.webtrapStart = PvpBotMod.globalTick;
         }
      } else if (f.webtrapStage == 2) {
         if (elapsed >= 2) {
            f.webtrapPlaced++;
            if (f.webtrapPlaced >= need || BotSupport.findItemIndex(bot, Items.COBWEB) < 0) {
               startFlee(server, name, f, 0);
               return;
            }

            double dx = bot.getX() - target.getX();
            double dz = bot.getZ() - target.getZ();
            double yaw = Math.toDegrees(Math.atan2(-dx, dz));
            BotSupport.run(server, "player " + name + " look " + String.format(Locale.ROOT, "%.1f", yaw) + " 0");
            BotSupport.run(server, "player " + name + " move backward");
            f.webtrapStage = 3;
            f.webtrapStart = PvpBotMod.globalTick;
         }
      } else if (f.webtrapStage == 3 && elapsed >= 4) {
         BotSupport.run(server, "player " + name + " move");
         double dx = bot.getX() - target.getX();
         double dz = bot.getZ() - target.getZ();
         double yaw = Math.toDegrees(Math.atan2(-dx, dz));
         BotSupport.run(server, "player " + name + " look " + String.format(Locale.ROOT, "%.1f", yaw) + " 90");
         f.webtrapStage = 1;
         f.webtrapStart = PvpBotMod.globalTick;
      }
   }

   // ---------------------------------------------------------------------
   // Mace fear, falling-target shield, wind charge climb, wind charge mace launch
   // ---------------------------------------------------------------------

   static boolean targetMaceThreat(ServerPlayerEntity bot, ServerPlayerEntity target) {
      if (target.isOnGround()) {
         return false;
      }

      boolean mace = target.getMainHandStack().isOf(Items.MACE) || target.getEquippedStack(EquipmentSlot.OFFHAND).isOf(Items.MACE);

      for (int i = 0; i < 9 && !mace; i++) {
         mace = target.getInventory().getStack(i).isOf(Items.MACE);
      }

      if (!mace) {
         return false;
      }

      return target.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA) || target.getY() > bot.getY() + 3.0 || target.fallDistance > 1.5;
   }

   static boolean fallShieldWanted(PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target) {
      if (target.isOnGround()) {
         f.fallRoll = 0;
         return false;
      } else if (PvpBotMod.FALL_SHIELD_CHANCE.value <= 0.0) {
         return false;
      } else {
         double hx = target.getX() - bot.getX();
         double hz = target.getZ() - bot.getZ();
         boolean falling = target.getVelocity().y < -0.35 && target.getY() > bot.getY() + 1.5 && Math.sqrt(hx * hx + hz * hz) <= 7.0;
         if (!falling) {
            return false;
         } else {
            if (f.fallRoll == 0) {
               f.fallRoll = ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.FALL_SHIELD_CHANCE.value ? 2 : 1;
            }

            return f.fallRoll == 2;
         }
      }
   }

   static boolean maceFearStep(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target, double dist) {
      if (!PvpBotMod.MACE_FEAR.on() || PvpBotMod.globalTick < f.nextFearTick || dist > 6.0 || !targetMaceThreat(bot, target)) {
         return false;
      } else if (!(ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.MACE_FEAR_CHANCE.value)) {
         f.nextFearTick = PvpBotMod.globalTick + 40;
         return false;
      } else {
         BotSupport.run(server, "player " + name + " stop");
         f.phase = PvpBotMod.Phase.MACEFEAR;
         f.fearMode = ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.MACE_FEAR_RUN.value ? 2 : 1;
         f.fearStart = PvpBotMod.globalTick;
         f.fearGone = 0;
         f.fearMove = 0;
         f.fearAnchorX = bot.getX();
         f.fearAnchorZ = bot.getZ();
         f.nextFearStrafeTick = PvpBotMod.globalTick;
         f.started = false;
         f.hopping = false;
         f.paused = false;
         f.crit = false;
         f.axeMode = false;
         f.placeStage = 0;
         f.strafeDir = 0;
         f.attackTick = -1;
         f.edgeHold = false;
         f.comboActive = 0;
         f.comboUppercutRising = false;
         f.keepMode = 0;
         return true;
      }
   }

   static void endMaceFear(MinecraftServer server, String name, PvpBotMod.Fight f) {
      BotSupport.run(server, "player " + name + " stop");
      f.reset();
      f.nextFearTick = PvpBotMod.globalTick + 30;
   }

   static void maceFearPhase(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target, double dist) {
      f.fearGone = targetMaceThreat(bot, target) ? 0 : f.fearGone + 1;
      if (f.blocking && PvpBotMod.globalTick >= f.blockUntil) {
         BotSupport.run(server, "player " + name + " use");
         f.blocking = false;
      }

      if (target.isOnGround() || f.fearGone >= 8 || PvpBotMod.globalTick - f.fearStart > 600 || dist > 70.0) {
         endMaceFear(server, name, f);
      } else {
         if (!f.blocking && bot.getEquippedStack(EquipmentSlot.OFFHAND).isOf(Items.SHIELD) && fallShieldWanted(f, bot, target)) {
            BotSupport.run(server, "player " + name + " use continuous");
            f.blocking = true;
            f.blockUntil = PvpBotMod.globalTick + 12;
         }

         if (f.fearMode == 2) {
            if (PvpBotMod.globalTick % 2 == 0) {
               BotSupport.run(server, "player " + name + " look " + String.format(Locale.ROOT, "%.1f", BotSupport.directYaw(bot, target) + 180.0) + " 0");
            }

            if (f.fearMove != 3) {
               BotSupport.run(server, "player " + name + " move forward");
               BotSupport.run(server, "player " + name + " sprint");
               f.fearMove = 3;
            }
         } else {
            double adx = f.fearAnchorX - bot.getX();
            double adz = f.fearAnchorZ - bot.getZ();
            if (Math.sqrt(adx * adx + adz * adz) > PvpBotMod.MACE_FEAR_RADIUS.value) {
               if (PvpBotMod.globalTick % 2 == 0) {
                  BotSupport.run(server, "player " + name + " look " + String.format(Locale.ROOT, "%.1f", Math.toDegrees(Math.atan2(-adx, adz))) + " 0");
               }

               if (f.fearMove != 1) {
                  BotSupport.run(server, "player " + name + " move");
                  BotSupport.run(server, "player " + name + " move forward");
                  f.fearMove = 1;
                  f.strafeDir = 0;
               }
            } else {
               if (PvpBotMod.globalTick % 2 == 0) {
                  BotSupport.run(server, BotSupport.aimCmd(name, bot, target, f));
               }

               if (f.fearMove != 2) {
                  // hold still facing the diver, no strafing
                  BotSupport.run(server, "player " + name + " move");
                  BotSupport.run(server, "player " + name + " unsprint");
                  f.fearMove = 2;
                  f.strafeDir = 0;
               }
            }
         }
      }
   }

   static boolean windClimbStep(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target) {
      if (PvpBotMod.WINDCLIMB.on() && bot.isOnGround() && target.isOnGround()) {
         if (PvpBotMod.globalTick % 5 == 0) {
            double mx = bot.getX() - f.climbX;
            double mz = bot.getZ() - f.climbZ;
            f.climbStuck = !Double.isNaN(f.climbX) && mx * mx + mz * mz < 0.04 ? f.climbStuck + 5 : 0;
            f.climbX = bot.getX();
            f.climbZ = bot.getZ();
         }

         if (PvpBotMod.globalTick < f.nextWindClimbTick) {
            return false;
         } else {
            double up = target.getY() - bot.getY();
            if (!(up < PvpBotMod.WINDCLIMB_HEIGHT.value) && !(up > 24.0)) {
               double hx = target.getX() - bot.getX();
               double hz = target.getZ() - bot.getZ();
               double hl = Math.sqrt(hx * hx + hz * hz);
               if (hl > 14.0) {
                  return false;
               } else if (hl > 3.5 && f.climbStuck < 10) {
                  return false;
               } else if (BotSupport.findItemIndex(bot, Items.WIND_CHARGE) < 0) {
                  return false;
               } else {
                  f.nextWindClimbTick = PvpBotMod.globalTick + 80;
                  return startWindLaunch(server, name, f, bot, target, 1);
               }
            } else {
               return false;
            }
         }
      } else {
         f.climbStuck = 0;
         return false;
      }
   }

   static boolean targetHealing(ServerPlayerEntity target) {
      if (!target.isUsingItem()) {
         return false;
      }

      ItemStack a = target.getActiveItem();
      return !a.isEmpty()
         && !a.isOf(Items.BOW)
         && !a.isOf(Items.CROSSBOW)
         && !a.isOf(Items.SHIELD)
         && !a.isOf(Items.TRIDENT)
         && !a.isOf(Items.SPYGLASS)
         && !isSpear(a);
   }

   static void rollDiveAim(PvpBotMod.Fight f) {
      double e = PvpBotMod.MACE_AIM_ERROR.value;
      f.diveErrYaw = (ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0) * e;
      f.diveErrPitch = (ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0) * e * 0.7;
      if (chance(PvpBotMod.MACE_MISTAKE.value)) {
         double sign = ThreadLocalRandom.current().nextBoolean() ? 1.0 : -1.0;
         f.diveErrYaw += sign * (8.0 + ThreadLocalRandom.current().nextDouble() * 8.0);
      }
   }

   /** aim at the target's body with this dive's aim error (the error is what makes a smash miss). */
   static String maceAimCmd(String name, ServerPlayerEntity bot, ServerPlayerEntity target, PvpBotMod.Fight f) {
      double dx = target.getX() - bot.getX();
      double dz = target.getZ() - bot.getZ();
      double hd = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
      double dy = target.getY() + 0.9 - bot.getEyeY();
      double yaw = BotSupport.directYaw(bot, target) + f.diveErrYaw;
      double pitch = -Math.toDegrees(Math.atan2(dy, hd)) + f.diveErrPitch;
      return "player " + name + " look " + String.format(Locale.ROOT, "%.1f", yaw) + " " + String.format(Locale.ROOT, "%.1f", Math.max(-90.0, Math.min(90.0, pitch)));
   }

   /** called after every mace smash attempt: a miss schedules a retry, a hit ends the retry chain. */
   static void smashResult(PvpBotMod.Fight f, boolean hit) {
      f.chainNextTick = PvpBotMod.globalTick + Math.max(5, PvpBotMod.MACE_CHAIN_GAP.asInt());
      if (hit) {
         f.chainRetry = false;
         f.chainCount = 0;
      } else if (PvpBotMod.MACE_RETRY.on() && f.chainCount < PvpBotMod.MACE_RETRY_MAX.asInt()) {
         f.chainRetry = true;
         f.chainCount++;
         f.chainExpire = PvpBotMod.globalTick + 200;
      } else {
         f.chainRetry = false;
         f.chainCount = 0;
      }
   }

   static boolean windMaceStep(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target, double dist) {
      if (f.chainRetry && PvpBotMod.globalTick > f.chainExpire) {
         f.chainRetry = false;
         f.chainCount = 0;
      }

      boolean forced = (f.chainRetry || PvpBotMod.MACE_PRESSURE.on() && targetHealing(target)) && PvpBotMod.globalTick >= f.chainNextTick;
      if (!forced && (PvpBotMod.WINDMACE_CHANCE.value <= 0.0 || PvpBotMod.globalTick % 20 != 0 || PvpBotMod.globalTick < f.nextWindMaceTick)) {
         return false;
      } else if (!bot.isOnGround() || dist < (forced ? 1.5 : 3.0) || dist > 18.0) {
         return false;
      } else if (bot.getHealth() + bot.getAbsorptionAmount() < PvpBotMod.WINDMACE_HEARTS.value * 2.0) {
         return false;
      } else if (!forced && !(ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.WINDMACE_CHANCE.value)) {
         return false;
      } else if (BotSupport.findItemIndex(bot, Items.WIND_CHARGE) < 0 || BotSupport.findItemIndex(bot, Items.MACE) < 0) {
         return false;
      } else {
         f.nextWindMaceTick = PvpBotMod.globalTick + Math.max(20, PvpBotMod.WINDMACE_COOLDOWN.asInt());
         f.chainNextTick = PvpBotMod.globalTick + Math.max(5, PvpBotMod.MACE_CHAIN_GAP.asInt());
         return startWindLaunch(server, name, f, bot, target, 2);
      }
   }

   /** purpose 1 = climb to the target, purpose 2 = launch high and mace smash the target. */
   static boolean startWindLaunch(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target, int purpose) {
      int widx = BotSupport.findItemIndex(bot, Items.WIND_CHARGE);
      int wslot = widx < 0 ? -1 : BotSupport.toHotbar(bot, widx, BotSupport.findWeaponSlot(bot));
      if (wslot < 0) {
         return false;
      } else {
         int mslot = -1;
         if (purpose == 2) {
            int midx = BotSupport.findItemIndex(bot, Items.MACE);
            mslot = midx < 0 ? -1 : BotSupport.toHotbar(bot, midx, wslot);
            if (mslot < 0) {
               return false;
            }
         }

         BotSupport.run(server, "player " + name + " stop");
         BotSupport.run(server, "player " + name + " hotbar " + (wslot + 1));
         BotSupport.run(
            server, "player " + name + " look " + String.format(Locale.ROOT, "%.1f", BotSupport.directYaw(bot, target)) + (purpose == 2 ? " 90" : " 80")
         );
         f.phase = PvpBotMod.Phase.WINDLAUNCH;
         f.swapRoll = 0;
         f.spearRoll = 0;
         f.spearStage = 0;
         f.launchPurpose = purpose;
         f.launchStage = 1;
         f.launchStart = PvpBotMod.globalTick;
         f.launchWindSlot = wslot;
         f.launchMaceSlot = mslot;
         f.launchMoved = false;
         f.started = false;
         f.hopping = false;
         f.paused = false;
         f.crit = false;
         f.blocking = false;
         f.axeMode = false;
         f.placeStage = 0;
         f.strafeDir = 0;
         f.attackTick = -1;
         f.edgeHold = false;
         f.comboActive = 0;
         f.comboUppercutRising = false;
         return true;
      }
   }

   static void endWindLaunch(MinecraftServer server, String name, PvpBotMod.Fight f) {
      BotSupport.run(server, "player " + name + " stop");
      f.reset();
   }

   static void windLaunchPhase(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target, double dist) {
      int elapsed = PvpBotMod.globalTick - f.launchStart;
      if (f.launchPurpose == 1) {
         bot.fallDistance = 0.0;
      }

      if (f.launchStage == 1) {
         // wind charge first (looking down), then jump
         if (elapsed >= 2) {
            BotSupport.useOnce(server, name, f);
            f.launchStage = 2;
            f.launchStart = PvpBotMod.globalTick;
            f.launchJumpDelay = Math.max(0, PvpBotMod.WIND_JUMP_DELAY.asInt());
            if (chance(PvpBotMod.WIND_JUMP_LATE_CHANCE.value)) {
               f.launchJumpDelay += 2 + ThreadLocalRandom.current().nextInt(Math.max(1, PvpBotMod.WIND_JUMP_LATE_MAX.asInt()));
            }
         } else if (elapsed > 14) {
            endWindLaunch(server, name, f);
         }
      } else if (f.launchStage == 2) {
         if (elapsed >= f.launchJumpDelay) {
            BotSupport.run(server, "player " + name + " jump once");
            f.launchStage = 3;
            f.launchStart = PvpBotMod.globalTick;
            f.launchMoved = false;
         }
      } else if (f.launchStage == 3) {
         if (f.launchPurpose == 1) {
            if (!f.launchMoved && elapsed >= 2) {
               BotSupport.run(server, "player " + name + " look " + String.format(Locale.ROOT, "%.1f", BotSupport.directYaw(bot, target)) + " 0");
               BotSupport.run(server, "player " + name + " move forward");
               BotSupport.run(server, "player " + name + " sprint");
               f.launchMoved = true;
            }

            if (f.launchMoved && PvpBotMod.globalTick % 2 == 0) {
               BotSupport.run(server, "player " + name + " look " + String.format(Locale.ROOT, "%.1f", BotSupport.directYaw(bot, target)) + " 0");
            }

            if (elapsed >= 6 && bot.isOnGround() || elapsed > 70) {
               endWindLaunch(server, name, f);
            }
         } else {
            // switch to the mace, then dive straight at the target: aim + forward, no strafing
            if (elapsed >= 3) {
               BotSupport.run(server, "player " + name + " hotbar " + (f.launchMaceSlot + 1));
               BotSupport.run(server, "player " + name + " move");
               BotSupport.run(server, "player " + name + " move forward");
               BotSupport.run(server, "player " + name + " sprint");
               f.strafeDir = 0;
               rollDiveAim(f);
               f.launchStage = 4;
               f.launchStart = PvpBotMod.globalTick;
            } else if (elapsed > 40) {
               endWindLaunch(server, name, f);
            }
         }
      } else if (f.launchStage == 4) {
         f.strafeDir = 0;
         BotSupport.run(server, maceAimCmd(name, bot, target, f));
         if (elapsed % 4 == 0) {
            BotSupport.run(server, "player " + name + " move forward");
            BotSupport.run(server, "player " + name + " sprint");
         }

         int sp = spearSwapStep(server, name, f, bot, target);
         if (sp == 2) {
            bot.fallDistance = 0.0;
            smashResult(f, f.lastHit);
            endWindLaunch(server, name, f);
            return;
         }

         if (sp == 1) {
            return;
         }

         boolean falling = !bot.isOnGround() && bot.getVelocity().y < 0.0;
         if (falling && BotSupport.rayReach(bot, target, PvpBotMod.ATTACK_RANGE.value)) {
            boolean hit = doAttack(server, name, f, target);
            bot.fallDistance = 0.0;
            smashResult(f, hit);
            endWindLaunch(server, name, f);
         } else if (elapsed >= 4 && bot.isOnGround() || elapsed > 120) {
            smashResult(f, false);
            endWindLaunch(server, name, f);
         }
      }
   }

   // ---------------------------------------------------------------------
   // Falling: mace smash / water clutch, attribute swaps, golden apple knockback
   // ---------------------------------------------------------------------

   static boolean chance(double percent) {
      return percent > 0.0 && ThreadLocalRandom.current().nextDouble() * 100.0 < percent;
   }

   /** returns {drop, type, x, y, z}; type 0 = solid, 1 = water, 2 = lava, -1 = nothing within 64 blocks. x,y,z is the ground block. */
   static double[] groundInfo(ServerPlayerEntity bot) {
      net.minecraft.server.world.ServerWorld w = BotSupport.sw(bot);
      BlockPos base = bot.getBlockPos();

      for (int i = 0; i < 64; i++) {
         BlockPos p = base.down(i);
         int type = -1;
         if (w.getFluidState(p).isIn(FluidTags.LAVA)) {
            type = 2;
         } else if (w.getFluidState(p).isIn(FluidTags.WATER)) {
            type = 1;
         } else if (!w.getBlockState(p).isAir()) {
            type = 0;
         }

         if (type >= 0) {
            return new double[]{bot.getY() - (p.getY() + 1), type, p.getX(), p.getY(), p.getZ()};
         }
      }

      return new double[]{0.0, -1.0, 0.0, 0.0, 0.0};
   }

   static boolean inNether(ServerPlayerEntity bot) {
      return BotSupport.sw(bot).getRegistryKey().getValue().getPath().equals("the_nether");
   }

   static boolean smashFeasible(ServerPlayerEntity bot, ServerPlayerEntity target, double drop) {
      if (target.getY() > bot.getY() + 0.5) {
         return false;
      }

      double hx = target.getX() - bot.getX();
      double hz = target.getZ() - bot.getZ();
      double hd = Math.sqrt(hx * hx + hz * hz);
      double v = Math.max(0.1, -bot.getVelocity().y);
      double d = drop;
      int t = 0;

      while (d > 0.0 && t < 200) {
         d -= v;
         v = Math.min(3.92, (v + 0.08) * 0.98);
         t++;
      }

      Vec3d vel = bot.getVelocity();
      double hs = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
      double gain = hs * Math.min(t, 8) * 0.8 + 0.013 * t * t;
      return hd - PvpBotMod.ATTACK_RANGE.value <= gain + 0.5;
   }

   static boolean fallTrigger(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target, double dist) {
      if (f.phase == PvpBotMod.Phase.WINDLAUNCH || f.phase == PvpBotMod.Phase.FALL) {
         return false;
      } else if (!PvpBotMod.FALL_MACE.on() && !PvpBotMod.FALL_CLUTCH.on()) {
         return false;
      } else if (bot.isOnGround() || bot.getVelocity().y > -0.6 || bot.fallDistance < 3.0) {
         return false;
      } else if (bot.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
         return false;
      } else {
         double[] g = groundInfo(bot);
         if (g[1] != 0.0 && g[1] != 2.0 || g[0] < PvpBotMod.FALL_MACE_HEIGHT.value) {
            return false;
         }

         BotSupport.run(server, "player " + name + " stop");
         f.phase = PvpBotMod.Phase.FALL;
         f.fallMode = 0;
         f.fallPlaced = false;
         f.fallWaterPos = null;
         f.fallLandTick = 0;
         f.swapRoll = 0;
         f.spearRoll = 0;
         f.spearStage = 0;
         f.started = false;
         f.hopping = false;
         f.paused = false;
         f.crit = false;
         f.blocking = false;
         f.axeMode = false;
         f.placeStage = 0;
         f.strafeDir = 0;
         f.attackTick = -1;
         f.edgeHold = false;
         f.comboActive = 0;
         f.comboUppercutRising = false;
         f.keepMode = 0;
         return true;
      }
   }

   static void endFall(MinecraftServer server, String name, PvpBotMod.Fight f) {
      BotSupport.run(server, "player " + name + " stop");
      f.reset();
   }

   static boolean hasBucket(ServerPlayerEntity bot) {
      return BotSupport.findItemIndex(bot, Items.WATER_BUCKET) >= 0;
   }

   /** switches into clutch mode; returns false when the bot cannot clutch. */
   static boolean enterClutch(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot) {
      if (!PvpBotMod.FALL_CLUTCH.on() || inNether(bot)) {
         return false;
      }

      int idx = BotSupport.findItemIndex(bot, Items.WATER_BUCKET);
      int slot = idx < 0 ? -1 : BotSupport.toHotbar(bot, idx, BotSupport.findWeaponSlot(bot));
      if (slot < 0) {
         return false;
      }

      BotSupport.run(server, "player " + name + " move");
      BotSupport.run(server, "player " + name + " hotbar " + (slot + 1));
      f.fallBucketSlot = slot;
      f.fallMode = 2;
      f.fallPlaced = false;
      f.fallClutchOk = chance(PvpBotMod.CLUTCH_SUCCESS.value);
      return true;
   }

   static void fallPhase(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target, double dist) {
      boolean landed = bot.isOnGround() || bot.isTouchingWater();
      if (landed && f.fallMode == 1) {
         smashResult(f, false);
      }

      if (landed) {
         if (f.fallPlaced && f.fallWaterPos != null) {
            // pick the water back up after landing
            if (f.fallLandTick == 0) {
               f.fallLandTick = PvpBotMod.globalTick;
               BotSupport.run(server, "player " + name + " look " + String.format(Locale.ROOT, "%.1f", bot.getYaw()) + " 90");
               BotSupport.useOnce(server, name, f);
            } else if (PvpBotMod.globalTick - f.fallLandTick >= 2) {
               pickUpClutchWater(bot, f);
               endFall(server, name, f);
            }

            return;
         }

         endFall(server, name, f);
         return;
      }

      if (PvpBotMod.globalTick - f.fallLandTick > 600 && f.fallLandTick != 0) {
         endFall(server, name, f);
         return;
      }

      double[] g = groundInfo(bot);
      double drop = g[0];
      if (f.fallMode == 0) {
         int midx = BotSupport.findItemIndex(bot, Items.MACE);
         boolean smash = PvpBotMod.FALL_MACE.on() && midx >= 0 && chance(PvpBotMod.FALL_MACE_CHANCE.value) && smashFeasible(bot, target, drop);
         if (smash) {
            int wslot = BotSupport.findWeaponSlot(bot);
            boolean holdSword = PvpBotMod.MACE_SWAP.on() && wslot >= 0 && chance(PvpBotMod.MACE_SWAP_CHANCE.value);
            int mslot = BotSupport.toHotbar(bot, midx, holdSword ? wslot : -1);
            if (mslot < 0) {
               smash = false;
            } else {
               f.fallMaceSlot = mslot;
               f.swapRoll = holdSword ? 1 : 2;
               int hold = holdSword ? BotSupport.findWeaponSlot(bot) : mslot;
               BotSupport.run(server, "player " + name + " hotbar " + (hold + 1));
               BotSupport.run(server, "player " + name + " move");
               BotSupport.run(server, "player " + name + " move forward");
               BotSupport.run(server, "player " + name + " sprint");
               f.fallMode = 1;
               rollDiveAim(f);
            }
         }

         if (!smash && !enterClutch(server, name, f, bot)) {
            endFall(server, name, f);
         }

         return;
      }

      if (f.fallMode == 1) {
         f.strafeDir = 0;
         BotSupport.run(server, maceAimCmd(name, bot, target, f));
         if (PvpBotMod.globalTick % 4 == 0) {
            BotSupport.run(server, "player " + name + " move forward");
            BotSupport.run(server, "player " + name + " sprint");
         }

         int sp = spearSwapStep(server, name, f, bot, target);
         if (sp == 2) {
            bot.fallDistance = 0.0;
            smashResult(f, f.lastHit);
            endFall(server, name, f);
            return;
         }

         if (sp == 1) {
            return;
         }

         boolean falling = bot.getVelocity().y < 0.0;
         if (falling && bot.fallDistance > 1.5 && BotSupport.rayReach(bot, target, PvpBotMod.ATTACK_RANGE.value)) {
            boolean hit = doAttack(server, name, f, target);
            bot.fallDistance = 0.0;
            smashResult(f, hit);
            endFall(server, name, f);
            return;
         }

         double reach = BotSupport.reachDistance(bot, target);
         if (drop <= PvpBotMod.CLUTCH_HEIGHT.value + 3.5 && reach > PvpBotMod.ATTACK_RANGE.value + 1.5) {
            enterClutch(server, name, f, bot);
         }

         return;
      }

      if (f.fallMode == 2) {
         BotSupport.run(server, "player " + name + " look " + String.format(Locale.ROOT, "%.1f", bot.getYaw()) + " 90");
         if (!f.fallPlaced && (drop <= PvpBotMod.CLUTCH_HEIGHT.value || drop + bot.getVelocity().y <= 0.3)) {
            f.fallPlaced = true;
            if (f.fallClutchOk && g[1] != 1.0) {
               BlockPos cell = new BlockPos((int)g[2], (int)g[3] + 1, (int)g[4]);
               net.minecraft.server.world.ServerWorld w = BotSupport.sw(bot);
               if (w.getBlockState(cell).isAir()) {
                  w.setBlockState(cell, Blocks.WATER.getDefaultState(), 3);
                  bot.getInventory().setStack(f.fallBucketSlot, new ItemStack(Items.BUCKET));
                  f.fallWaterPos = cell;
                  bot.fallDistance = 0.0;
                  BotSupport.swing(f);
               }
            }
         }
      }
   }

   static void pickUpClutchWater(ServerPlayerEntity bot, PvpBotMod.Fight f) {
      if (f.fallWaterPos == null || f.fallBucketSlot < 0) {
         return;
      }

      PlayerInventory inv = bot.getInventory();
      if (!inv.getStack(f.fallBucketSlot).isOf(Items.BUCKET)) {
         return;
      }

      net.minecraft.server.world.ServerWorld w = BotSupport.sw(bot);
      boolean removed = false;

      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            for (int dy = -1; dy <= 2; dy++) {
               BlockPos p = f.fallWaterPos.add(dx, dy, dz);
               FluidState fluid = w.getFluidState(p);
               if (fluid.isIn(FluidTags.WATER) && fluid.isStill()) {
                  w.setBlockState(p, Blocks.AIR.getDefaultState(), 3);
                  removed = true;
               }
            }
         }
      }

      if (removed) {
         inv.setStack(f.fallBucketSlot, new ItemStack(Items.WATER_BUCKET));
      }
   }

   static int hotbarSlotOf(ServerPlayerEntity bot, Item item) {
      PlayerInventory inv = bot.getInventory();

      for (int i = 0; i < 9; i++) {
         if (inv.getStack(i).isOf(item)) {
            return i;
         }
      }

      return -1;
   }

   static boolean isSpear(ItemStack stack) {
      return !stack.isEmpty() && stack.getName().getString().toLowerCase(Locale.ROOT).contains("spear");
   }

   static int hotbarSpear(ServerPlayerEntity bot) {
      PlayerInventory inv = bot.getInventory();

      for (int i = 0; i < 9; i++) {
         if (isSpear(inv.getStack(i))) {
            return i;
         }
      }

      return -1;
   }

   static boolean fallingHit(ServerPlayerEntity bot) {
      return !bot.isOnGround() && bot.fallDistance > 1.5 && bot.getVelocity().y < 0.0;
   }

   /**
    * Attribute swap: the sword's damage is still applied for this tick, but the mace's smash bonus reads the held item.
    * So we switch to the mace right before the hit and the hit gets both. Returns true if it swapped.
    */
   static boolean attributeSwap(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity self) {
      if (!PvpBotMod.MACE_SWAP.on() || !fallingHit(self) || BotSupport.weaponScore(self.getMainHandStack()) <= 0) {
         return false;
      }

      if (f.swapRoll == 0) {
         f.swapRoll = chance(PvpBotMod.MACE_SWAP_CHANCE.value) ? 1 : 2;
      }

      if (f.swapRoll != 1) {
         return false;
      }

      int mslot = hotbarSlotOf(self, Items.MACE);
      if (mslot < 0) {
         return false;
      }

      BotSupport.run(server, "player " + name + " hotbar " + (mslot + 1));
      return true;
   }

   /** 0 = not used, 1 = holding the spear / waiting, 2 = switched to the mace and hit. */
   static int spearSwapStep(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target) {
      if (!PvpBotMod.SPEAR_SWAP.on()) {
         return 0;
      }

      if (f.spearStage == 0) {
         if (f.spearRoll == 2 || !fallingHit(bot)) {
            return 0;
         }

         double reach = BotSupport.reachDistance(bot, target);
         if (reach <= PvpBotMod.ATTACK_RANGE.value || reach > PvpBotMod.SPEAR_REACH.value + 0.5) {
            return 0;
         }

         if (f.spearRoll == 0) {
            f.spearRoll = chance(PvpBotMod.SPEAR_SWAP_CHANCE.value) ? 1 : 2;
            if (f.spearRoll == 2) {
               return 0;
            }
         }

         int sslot = hotbarSpear(bot);
         int mslot = hotbarSlotOf(bot, Items.MACE);
         if (sslot < 0 || mslot < 0) {
            f.spearRoll = 2;
            return 0;
         }

         BotSupport.run(server, "player " + name + " hotbar " + (sslot + 1));
         f.spearMaceSlot = mslot;
         f.spearStage = 1;
         f.spearStart = PvpBotMod.globalTick;
         return 1;
      }

      BotSupport.run(server, BotSupport.aimCmd(name, bot, target, f));
      if (PvpBotMod.globalTick - f.spearStart < 1) {
         return 1;
      }

      if (fallingHit(bot) && BotSupport.rayReach(bot, target, PvpBotMod.SPEAR_REACH.value) && bot.canSee(target)) {
         BotSupport.run(server, "player " + name + " hotbar " + (f.spearMaceSlot + 1));
         double beforeHp = target.getHealth() + target.getAbsorptionAmount();
         bot.attack(target);
         f.lastHit = !target.isAlive() || target.getHealth() + target.getAbsorptionAmount() < beforeHp - 0.01;
         BotSupport.swing(f);
         f.spearStage = 0;
         f.spearRoll = 2;
         f.attackTick = PvpBotMod.globalTick;
         return 2;
      }

      if (!fallingHit(bot) || PvpBotMod.globalTick - f.spearStart > 20) {
         BotSupport.run(server, "player " + name + " hotbar " + (f.spearMaceSlot + 1));
         f.spearStage = 0;
         f.spearRoll = 2;
      }

      return 1;
   }

   static boolean startEatKnockback(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target, double dist) {
      if (!PvpBotMod.EAT_KNOCKBACK.on() || !bot.isOnGround() || BotSupport.reachDistance(bot, target) > PvpBotMod.ATTACK_RANGE.value + 2.0) {
         return false;
      }

      int w = BotSupport.findWeaponSlot(bot);
      if (w < 0) {
         return false;
      }

      BotSupport.run(server, "player " + name + " stop");
      BotSupport.run(server, "player " + name + " hotbar " + (w + 1));
      BotSupport.run(server, "player " + name + " move forward");
      BotSupport.run(server, "player " + name + " sprint");
      f.phase = PvpBotMod.Phase.EATKB;
      f.ekStart = PvpBotMod.globalTick;
      f.ekHit = false;
      f.ekHitTick = 0;
      f.started = false;
      f.hopping = false;
      f.paused = false;
      f.crit = false;
      f.blocking = false;
      f.axeMode = false;
      f.placeStage = 0;
      f.strafeDir = 0;
      f.attackTick = -1;
      f.edgeHold = false;
      f.comboActive = 0;
      f.comboUppercutRising = false;
      f.keepMode = 0;
      return true;
   }

   static void eatKnockbackPhase(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target, double dist) {
      int elapsed = PvpBotMod.globalTick - f.ekStart;
      if (!f.ekHit) {
         BotSupport.run(server, BotSupport.aimCmd(name, bot, target, f));
         if (!bot.isSprinting()) {
            BotSupport.run(server, "player " + name + " move forward");
            BotSupport.run(server, "player " + name + " sprint");
         }

         if (bot.isSprinting()
            && bot.getAttackCooldownProgress(0.5F) >= 0.9F
            && BotSupport.rayReach(bot, target, PvpBotMod.ATTACK_RANGE.value)) {
            doAttack(server, name, f, target);
            f.ekHit = true;
            f.ekHitTick = PvpBotMod.globalTick;
         }
      }

      boolean pushed = f.ekHit && (BotSupport.reachDistance(bot, target) >= 3.5 || PvpBotMod.globalTick - f.ekHitTick >= 5);
      if (pushed || elapsed >= Math.max(3, PvpBotMod.EAT_KNOCKBACK_TICKS.asInt())) {
         if (!startItemEscape(server, name, f, bot, target)) {
            startFlee(server, name, f, 0);
         }
      }
   }

   // ---------------------------------------------------------------------
   // Pathfinding (A*), used only when the straight line is not enough
   // ---------------------------------------------------------------------

   static final class PfNode {
      final int x;
      final int y;
      final int z;
      double g;
      double f;
      PfNode parent;
      boolean closed;

      PfNode(int x, int y, int z) {
         this.x = x;
         this.y = y;
         this.z = z;
      }
   }

   static long pfKey(int x, int y, int z) {
      return ((long)(x & 0x3FFFFFF) << 38) | ((long)(z & 0x3FFFFFF) << 12) | (long)((y + 2048) & 0xFFF);
   }

   static boolean pfFree(World w, int x, int y, int z) {
      BlockPos p = new BlockPos(x, y, z);
      BlockState s = w.getBlockState(p);
      if (!s.getCollisionShape(w, p).isEmpty()) {
         return false;
      } else if (w.getFluidState(p).isIn(FluidTags.LAVA)) {
         return false;
      } else {
         return !s.isOf(Blocks.FIRE)
            && !s.isOf(Blocks.SOUL_FIRE)
            && !s.isOf(Blocks.COBWEB)
            && !s.isOf(Blocks.CACTUS)
            && !s.isOf(Blocks.SWEET_BERRY_BUSH)
            && !s.isOf(Blocks.POWDER_SNOW);
      }
   }

   /** two free cells (feet + head), and the head is not under water. */
   static boolean pfClear(World w, int x, int y, int z) {
      return pfFree(w, x, y, z) && pfFree(w, x, y + 1, z) && !w.getFluidState(new BlockPos(x, y + 1, z)).isIn(FluidTags.WATER);
   }

   static boolean pfFloor(World w, int x, int y, int z) {
      BlockPos p = new BlockPos(x, y, z);
      BlockState s = w.getBlockState(p);
      if (s.isOf(Blocks.MAGMA_BLOCK) || s.isOf(Blocks.CACTUS) || w.getFluidState(p).isIn(FluidTags.LAVA)) {
         return false;
      } else {
         return !s.getCollisionShape(w, p).isEmpty();
      }
   }

   static double pfHeuristic(int x, int y, int z, int gx, int gy, int gz) {
      double dx = Math.abs(x - gx);
      double dz = Math.abs(z - gz);
      double dy = Math.abs(y - gy);
      return Math.max(dx, dz) + 0.414 * Math.min(dx, dz) + 0.7 * dy;
   }

   static void pfOffer(
      java.util.HashMap<Long, PfNode> map, java.util.PriorityQueue<PfNode> open, PfNode cur, int nx, int ny, int nz, double cost, int gx, int gy, int gz
   ) {
      long key = pfKey(nx, ny, nz);
      PfNode n = map.get(key);
      double g = cur.g + cost;
      if (n == null) {
         n = new PfNode(nx, ny, nz);
         n.g = g;
         n.f = g + pfHeuristic(nx, ny, nz, gx, gy, gz);
         n.parent = cur;
         map.put(key, n);
         open.add(n);
      } else if (!n.closed && g < n.g) {
         PfNode copy = new PfNode(nx, ny, nz);
         copy.g = g;
         copy.f = g + pfHeuristic(nx, ny, nz, gx, gy, gz);
         copy.parent = cur;
         n.closed = true;
         map.put(key, copy);
         open.add(copy);
      }
   }

   /** returns the cells to walk through (start excluded), or null if there is no progress. used[0] gets the nodes expanded. */
   static List<BlockPos> pathSearch(World w, BlockPos start, BlockPos goal, int maxNodes, int maxDrop, int range, int[] used) {
      java.util.HashMap<Long, PfNode> map = new java.util.HashMap<>();
      java.util.PriorityQueue<PfNode> open = new java.util.PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
      int gx = goal.getX();
      int gy = goal.getY();
      int gz = goal.getZ();
      PfNode s = new PfNode(start.getX(), start.getY(), start.getZ());
      s.f = pfHeuristic(s.x, s.y, s.z, gx, gy, gz);
      map.put(pfKey(s.x, s.y, s.z), s);
      open.add(s);
      PfNode best = s;
      double bestH = s.f;
      int expanded = 0;
      int[] dxs = new int[]{1, -1, 0, 0, 1, 1, -1, -1};
      int[] dzs = new int[]{0, 0, 1, -1, 1, -1, 1, -1};

      while (!open.isEmpty() && expanded < maxNodes) {
         PfNode cur = open.poll();
         if (cur.closed) {
            continue;
         }

         cur.closed = true;
         expanded++;
         if (Math.abs(cur.x - gx) <= 1 && Math.abs(cur.z - gz) <= 1 && Math.abs(cur.y - gy) <= 2) {
            best = cur;
            used[1] = 1;
            break;
         }

         double h = pfHeuristic(cur.x, cur.y, cur.z, gx, gy, gz);
         if (h < bestH) {
            bestH = h;
            best = cur;
         }

         for (int i = 0; i < 8; i++) {
            int dx = dxs[i];
            int dz = dzs[i];
            int nx = cur.x + dx;
            int nz = cur.z + dz;
            if (Math.abs(nx - start.getX()) > range || Math.abs(nz - start.getZ()) > range) {
               continue;
            }

            boolean diag = dx != 0 && dz != 0;
            double base = diag ? 1.414 : 1.0;
            if (diag && (!pfClear(w, cur.x + dx, cur.y, cur.z) || !pfClear(w, cur.x, cur.y, cur.z + dz))) {
               continue;
            }

            boolean feetFree = pfClear(w, nx, cur.y, nz);
            if (feetFree && pfFloor(w, nx, cur.y - 1, nz)) {
               boolean wet = w.getFluidState(new BlockPos(nx, cur.y, nz)).isIn(FluidTags.WATER);
               pfOffer(map, open, cur, nx, cur.y, nz, base + (wet ? 1.0 : 0.0), gx, gy, gz);
            } else if (!diag && !feetFree && pfClear(w, nx, cur.y + 1, nz) && pfClear(w, cur.x, cur.y + 1, cur.z) && pfFree(w, cur.x, cur.y + 2, cur.z) && pfFloor(w, nx, cur.y, nz)) {
               pfOffer(map, open, cur, nx, cur.y + 1, nz, base + 1.0, gx, gy, gz);
            } else if (!diag && feetFree && !pfFloor(w, nx, cur.y - 1, nz)) {
               for (int d = 1; d <= maxDrop; d++) {
                  if (!pfFree(w, nx, cur.y - d, nz)) {
                     break;
                  }

                  if (pfFloor(w, nx, cur.y - d - 1, nz)) {
                     if (pfClear(w, nx, cur.y - d, nz)) {
                        pfOffer(map, open, cur, nx, cur.y - d, nz, base + 0.5 * d + (d > 3 ? 2.0 : 0.0), gx, gy, gz);
                     }

                     break;
                  }
               }
            }
         }
      }

      used[0] = expanded;
      if (best == s) {
         return null;
      }

      java.util.ArrayList<BlockPos> out = new java.util.ArrayList<>();

      for (PfNode n = best; n != null && n != s; n = n.parent) {
         out.add(new BlockPos(n.x, n.y, n.z));
      }

      Collections.reverse(out);
      return out;
   }

   /** who may search for a path right now, from the pathmode switch (all / one / half). */
   static boolean pathMayPlan(PvpBotMod.Fight f) {
      int mode = PvpBotMod.PATH_MODE.asInt();
      int n = Math.max(1, PvpBotMod.fightCount);
      if (mode == 0) {
         return true;
      } else if (mode == 1) {
         return (PvpBotMod.globalTick / 4) % n == f.order % n;
      } else {
         int rp = Math.max(5, PvpBotMod.PATH_REPLAN.asInt());
         return (PvpBotMod.globalTick / rp) % 2 == f.order % 2;
      }
   }

   static boolean planPath(PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target) {
      int cap = Math.min(PvpBotMod.PATH_NODES.asInt(), PvpBotMod.pathBudgetLeft);
      if (cap < 60) {
         return false;
      }

      World w = BotSupport.sw(bot);
      BlockPos start = bot.getBlockPos();
      BlockPos goal = target.getBlockPos();
      if (!target.isOnGround()) {
         double[] g = groundInfo(target);
         if (g[1] == 0.0 && g[0] < 32.0) {
            goal = new BlockPos((int)g[2], (int)g[3] + 1, (int)g[4]);
         }
      }

      int maxDrop = PvpBotMod.PATH_DROP.asInt();
      if (goal.getY() <= start.getY() - 4) {
         // the target is far below (a cave hole): allow a big drop if the bot can survive or clutch it
         int need = start.getY() - goal.getY() + 2;
         double hp = bot.getHealth() + bot.getAbsorptionAmount();
         boolean safe = hasBucket(bot) || BotSupport.findItemIndex(bot, Items.MACE) >= 0 || hp > Math.max(0, need - 3) + 6.0;
         if (safe) {
            maxDrop = Math.min(32, need);
         }
      }

      // PATH_RANGE is a minimum search radius, not a hard maximum distance
      // to the target. Long bridges/staircases can require a wider search.
      int horizontal = Math.max(Math.abs(goal.getX() - start.getX()), Math.abs(goal.getZ() - start.getZ()));
      int searchRange = Math.max((int)Math.round(PvpBotMod.PATH_RANGE.value), horizontal + 6);
      searchRange = Math.min(searchRange, 128);

      int[] used = new int[2];
      List<BlockPos> path = pathSearch(w, start, goal, cap, maxDrop, searchRange, used);

      PvpBotMod.pathBudgetLeft -= Math.max(used[0], 1);
      f.pathPlanTick = PvpBotMod.globalTick;
      if (path == null || path.isEmpty()) {
         return false;
      }

      f.path = path;
      f.pathIdx = 0;
      f.pathStuck = 0;
      return true;
   }

   static BlockPos resolveGotoGoal(World w, BlockPos requested) {
      int rx = requested.getX();
      int ry = requested.getY();
      int rz = requested.getZ();
      BlockPos best = null;
      double bestScore = Double.POSITIVE_INFINITY;

      for (int dy = -1; dy <= 2; dy++) {
         for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
               int x = rx + dx;
               int y = ry + dy;
               int z = rz + dz;
               if (!pfClear(w, x, y, z) || !pfFloor(w, x, y - 1, z)) {
                  continue;
               }

               double horizontal = Math.sqrt((double)dx * dx + (double)dz * dz);
               double score = horizontal + Math.abs(dy) * 1.5;
               if (score < bestScore) {
                  bestScore = score;
                  best = new BlockPos(x, y, z);
               }
            }
         }
      }

      return best;
   }

   static boolean planGoto(PvpBotMod.GotoJob g, ServerPlayerEntity bot) {
      int cap = Math.min(PvpBotMod.PATH_NODES.asInt(), PvpBotMod.pathBudgetLeft);
      if (cap < 60) {
         return false;
      }

      World w = BotSupport.sw(bot);
      BlockPos start = bot.getBlockPos();
      BlockPos resolved = resolveGotoGoal(w, g.target);
      if (resolved == null) {
         g.goal = null;
         g.path = null;
         return false;
      }

      g.goal = resolved;
      int horizontal = Math.max(Math.abs(resolved.getX() - start.getX()), Math.abs(resolved.getZ() - start.getZ()));
      int searchRange = Math.max((int)Math.round(PvpBotMod.PATH_RANGE.value), horizontal + 6);
      searchRange = Math.min(searchRange, 128);

      int[] used = new int[2];
      List<BlockPos> path = pathSearch(w, start, resolved, cap, PvpBotMod.PATH_DROP.asInt(), searchRange, used);
      PvpBotMod.pathBudgetLeft -= Math.max(used[0], 1);
      g.pathPlanTick = PvpBotMod.globalTick;

      if (path == null || path.isEmpty()) {
         g.path = null;
         return false;
      }

      g.path = path;
      g.pathIdx = 0;
      g.pathStuck = 0;
      g.pathMoving = false;
      return true;
   }

   static boolean gotoReached(PvpBotMod.GotoJob g, ServerPlayerEntity bot) {
      BlockPos p = g.goal != null ? g.goal : g.target;
      double dx = p.getX() + 0.5 - bot.getX();
      double dz = p.getZ() + 0.5 - bot.getZ();
      return dx * dx + dz * dz <= 0.64 && Math.abs(bot.getY() - p.getY()) < 1.25;
   }

   static boolean gotoPathStep(MinecraftServer server, String name, PvpBotMod.GotoJob g, ServerPlayerEntity bot) {
      if (gotoReached(g, bot)) {
         return true;
      }

      if (g.path == null || g.pathIdx >= g.path.size()) {
         return false;
      }

      BlockPos wp = g.path.get(g.pathIdx);
      for (int k = 0; k < 3; k++) {
         double ddx0 = wp.getX() + 0.5 - bot.getX();
         double ddz0 = wp.getZ() + 0.5 - bot.getZ();
         if (Math.sqrt(ddx0 * ddx0 + ddz0 * ddz0) < 0.7 && Math.abs(bot.getY() - wp.getY()) < 1.5 && g.pathIdx < g.path.size() - 1) {
            g.pathIdx++;
            wp = g.path.get(g.pathIdx);
         } else {
            break;
         }
      }

      double ddx = wp.getX() + 0.5 - bot.getX();
      double ddz = wp.getZ() + 0.5 - bot.getZ();
      double dh = Math.sqrt(ddx * ddx + ddz * ddz);
      if (dh < 0.7 && Math.abs(bot.getY() - wp.getY()) < 1.5 && g.pathIdx >= g.path.size() - 1) {
         g.pathIdx = g.path.size();
         return gotoReached(g, bot);
      }

      double yaw = Math.toDegrees(Math.atan2(-ddx, ddz));
      BotSupport.run(server, "player " + name + " look " + String.format(Locale.ROOT, "%.1f", yaw) + " 0");

      if (!g.pathMoving || PvpBotMod.globalTick % 20 == 0) {
         BotSupport.run(server, "player " + name + " move forward");
         g.pathMoving = true;
      }

      if (wp.getY() > bot.getBlockY() && bot.isOnGround() && dh < 1.55 && PvpBotMod.globalTick - g.pathJumpTick >= 8) {
         BotSupport.run(server, "player " + name + " jump once");
         g.pathJumpTick = PvpBotMod.globalTick;
      }

      return true;
   }

   static void tickGotos(MinecraftServer server) {
      Iterator<Entry<String, PvpBotMod.GotoJob>> it = PvpBotMod.GOTOS.entrySet().iterator();
      while (it.hasNext()) {
         Entry<String, PvpBotMod.GotoJob> e = it.next();
         String name = e.getKey();
         PvpBotMod.GotoJob g = e.getValue();
         ServerPlayerEntity bot = server.getPlayerManager().getPlayer(name);

         if (bot == null || !PvpBotMod.BOTS.contains(name)) {
            it.remove();
            continue;
         }

         if (PvpBotMod.FIGHTS.containsKey(name)) {
            it.remove();
            continue;
         }

         if (PvpBotMod.globalTick % 5 == 0) {
            double mx = bot.getX() - g.lastX;
            double mz = bot.getZ() - g.lastZ;
            if (!Double.isNaN(g.lastX) && g.path != null && !gotoReached(g, bot) && mx * mx + mz * mz < 0.0036) {
               g.pathStuck += 5;
            } else {
               g.pathStuck = 0;
            }
            g.lastX = bot.getX();
            g.lastZ = bot.getZ();
         }

         if (gotoReached(g, bot)) {
            gotoFinish(server, name, g, bot);
            it.remove();
            continue;
         }

         int now = PvpBotMod.globalTick;
         boolean needPlan = g.path == null || g.pathIdx >= g.path.size() || now >= g.nextPathTick || g.pathStuck >= 15;
         if (needPlan && PvpBotMod.pathBudgetLeft >= 60) {
            boolean ok = planGoto(g, bot);
            if (ok) {
               g.nextPathTick = now + Math.max(5, PvpBotMod.PATH_REPLAN.asInt());
            } else {
               g.nextPathTick = now + 10;
               g.pathStuck = 0;
            }
         }

         if (g.pathStuck >= 15) {
            g.path = null;
            g.pathIdx = 0;
            g.pathMoving = false;
            g.pathStuck = 0;
         }

         boolean finished = gotoPathStep(server, name, g, bot);
         if (finished && gotoReached(g, bot)) {
            gotoFinish(server, name, g, bot);
            it.remove();
         }
      }
   }

   static void pathStop(PvpBotMod.Fight f) {
      f.pathActive = false;
      f.path = null;
      f.pathMoving = false;
      f.started = false;
      f.hopping = false;
      f.keepMode = 0;
   }

   /** returns true while the pathfinder is steering the bot this tick (the normal chase logic is skipped). */
   static boolean pathStep(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target, double dist) {
      if (!PvpBotMod.PATHFIND.on()) {
         if (f.pathActive) {
            pathStop(f);
         }

         return false;
      }

      int now = PvpBotMod.globalTick;
      if (now % 5 == 0) {
         double mx = bot.getX() - f.pathLastX;
         double mz = bot.getZ() - f.pathLastZ;
         boolean trying = f.pathActive || f.keepMode > 0;
         f.pathStuck = trying && !Double.isNaN(f.pathLastX) && mx * mx + mz * mz < 0.0036 && dist > PvpBotMod.ATTACK_RANGE.value + 1.5
            ? f.pathStuck + 5
            : 0;
         f.pathLastX = bot.getX();
         f.pathLastZ = bot.getZ();
      }

      // Do not stop pathfinding just because the raw distance is small.
      // A wall/fence can leave the target inside melee distance while still
      // making the target unreachable. Only leave path mode when the target
      // is actually visible and attack-reachable.
      boolean attackReachable = dist <= PvpBotMod.ATTACK_RANGE.value + 1.0
         && bot.canSee(target)
         && BotSupport.rayReach(bot, target, PvpBotMod.ATTACK_RANGE.value);
      if (attackReachable) {
         if (f.pathActive) {
            pathStop(f);
         }

         return false;
      }

      double dy = target.getY() - bot.getY();
      if (!f.pathActive) {
         if (now % 5 != 0 || now < f.nextPathTick) {
            return false;
         }

         boolean needed = f.pathStuck >= 10 || Math.abs(dy) >= 2.0 || !bot.canSee(target);
         if (!needed || !pathMayPlan(f)) {
            return false;
         }

         if (!planPath(f, bot, target)) {
            f.nextPathTick = now + 20;
            return false;
         }

         f.pathActive = true;
         f.pathMoving = false;
         f.strafeDir = 0;
         f.hopping = false;
         f.comboActive = 0;
         f.paused = false;
         f.crit = false;
         BotSupport.run(server, "player " + name + " move");
      } else {
         boolean replan = now - f.pathPlanTick >= Math.max(5, PvpBotMod.PATH_REPLAN.asInt()) || f.pathStuck >= 15 || f.path == null || f.pathIdx >= f.path.size();
         if (replan && pathMayPlan(f) && PvpBotMod.pathBudgetLeft >= 60) {
            boolean ok = planPath(f, bot, target);
            if (!ok) {
               pathStop(f);
               f.nextPathTick = now + 20;
               return false;
            }

            if (f.pathStuck == 0) {
               BotSupport.run(server, "player " + name + " jump once");
            }
         }

         if (now % 5 == 0 && bot.canSee(target) && Math.abs(dy) < 1.6 && f.pathStuck < 10) {
            pathStop(f);
            f.nextPathTick = now + 10;
            return false;
         }
      }

      if (f.path == null || f.pathIdx >= f.path.size()) {
         if (!f.pathActive || now - f.pathPlanTick > 100) {
            pathStop(f);
            return false;
         }

         return true;
      }

      BlockPos wp = f.path.get(f.pathIdx);
      for (int k = 0; k < 3; k++) {
         double ddx0 = wp.getX() + 0.5 - bot.getX();
         double ddz0 = wp.getZ() + 0.5 - bot.getZ();
         if (Math.sqrt(ddx0 * ddx0 + ddz0 * ddz0) < 0.7 && Math.abs(bot.getY() - wp.getY()) < 1.5 && f.pathIdx < f.path.size() - 1) {
            f.pathIdx++;
            wp = f.path.get(f.pathIdx);
         } else {
            break;
         }
      }

      double ddx = wp.getX() + 0.5 - bot.getX();
      double ddz = wp.getZ() + 0.5 - bot.getZ();
      double dh = Math.sqrt(ddx * ddx + ddz * ddz);
      if (dh < 0.7 && Math.abs(bot.getY() - wp.getY()) < 1.5 && f.pathIdx >= f.path.size() - 1) {
         f.pathIdx = f.path.size();
         return true;
      }

      // Turn every tick while pathing so narrow routes get continuous
      // lateral correction instead of overshooting at sprint speed.
      double yaw = Math.toDegrees(Math.atan2(-ddx, ddz));
      BotSupport.run(server, "player " + name + " look " + String.format(Locale.ROOT, "%.1f", yaw) + " 0");

      if (!f.pathMoving || now % 20 == 0) {
         // Walk rather than sprint while following a narrow path.
         BotSupport.run(server, "player " + name + " move forward");
         f.pathMoving = true;
         f.keepMode = 1;
      }

      if (wp.getY() > bot.getBlockY() && bot.isOnGround() && dh < 1.55 && now - f.pathJumpTick >= 8) {
         BotSupport.run(server, "player " + name + " jump once");
         f.pathJumpTick = now;
      }

      return true;
   }

   static boolean startWindEscape(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target) {
      int idx = BotSupport.findItemIndex(bot, Items.WIND_CHARGE);
      int slot = idx < 0 ? -1 : BotSupport.toHotbar(bot, idx, BotSupport.findWeaponSlot(bot));
      if (slot < 0) {
         return false;
      } else {
         BotSupport.run(server, "player " + name + " stop");
         BotSupport.run(server, "player " + name + " hotbar " + (slot + 1));
         double dx = bot.getX() - target.getX();
         double dz = bot.getZ() - target.getZ();
         double yaw = Math.toDegrees(Math.atan2(-dx, dz));
         BotSupport.run(server, "player " + name + " look " + String.format(Locale.ROOT, "%.1f", yaw) + " 65");
         BotSupport.run(server, "player " + name + " move forward");
         f.phase = PvpBotMod.Phase.WINDESCAPE;
         f.windStage = 0;
         f.windStart = PvpBotMod.globalTick;
         f.started = false;
         f.hopping = false;
         f.paused = false;
         f.crit = false;
         f.blocking = false;
         f.axeMode = false;
         f.placeStage = 0;
         f.strafeDir = 0;
         f.attackTick = -1;
         f.edgeHold = false;
         return true;
      }
   }

   static void windEscapePhase(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target, double dist) {
      int elapsed = PvpBotMod.globalTick - f.windStart;
      bot.fallDistance = 0.0;
      if (f.windStage == 0) {
         if (elapsed >= Math.max(1, PvpBotMod.WINDCHARGE_WAIT_TICKS.asInt())) {
            BotSupport.run(server, "player " + name + " jump once");
            BotSupport.useOnce(server, name, f);
            f.windStage = 1;
            f.windStart = PvpBotMod.globalTick;
         }
      } else if (f.windStage == 1 && (!bot.isOnGround() || elapsed > 20)) {
         startEat(server, name, f, bot, false);
      }
   }

   static boolean startPearlEscape(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target) {
      int idx = BotSupport.findItemIndex(bot, Items.ENDER_PEARL);
      int slot = idx < 0 ? -1 : BotSupport.toHotbar(bot, idx, BotSupport.findWeaponSlot(bot));
      if (slot < 0) {
         return false;
      } else {
         BotSupport.run(server, "player " + name + " stop");
         BotSupport.run(server, "player " + name + " hotbar " + (slot + 1));
         double dx = bot.getX() - target.getX();
         double dz = bot.getZ() - target.getZ();
         double yaw = Math.toDegrees(Math.atan2(-dx, dz));
         BotSupport.run(server, "player " + name + " look " + String.format(Locale.ROOT, "%.1f", yaw) + " -10");
         BotSupport.run(server, "player " + name + " move forward");
         f.phase = PvpBotMod.Phase.PEARLESCAPE;
         f.pearlStage = 1;
         f.pearlStart = PvpBotMod.globalTick;
         f.pearlFrom = bot.getEntityPos();
         f.started = false;
         f.hopping = false;
         f.paused = false;
         f.crit = false;
         f.blocking = false;
         f.axeMode = false;
         f.placeStage = 0;
         f.strafeDir = 0;
         f.attackTick = -1;
         f.edgeHold = false;
         return true;
      }
   }

   static void pearlEscapePhase(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target, double dist) {
      int elapsed = PvpBotMod.globalTick - f.pearlStart;
      bot.fallDistance = 0.0;
      if (f.pearlStage == 1) {
         if (elapsed >= 1) {
            BotSupport.useOnce(server, name, f);
            f.pearlStage = 2;
            f.pearlStart = PvpBotMod.globalTick;
         }
      } else if (f.pearlStage == 2) {
         boolean teleported = f.pearlFrom != null && bot.getEntityPos().distanceTo(f.pearlFrom) > 3.0;
         if (teleported || elapsed > 40) {
            BotSupport.run(server, "player " + name + " move forward");
            startEat(server, name, f, bot, false);
         }
      }
   }

   static void startFlee(MinecraftServer server, String name, PvpBotMod.Fight f, int purpose) {
      BotSupport.run(server, "player " + name + " stop");
      BotSupport.run(server, "player " + name + " autojump true");
      BotSupport.run(server, "player " + name + " sprint");
      BotSupport.run(server, "player " + name + " move forward");
      BotSupport.run(server, "player " + name + " jump continuous");
      f.phase = PvpBotMod.Phase.FLEE;
      f.fleeFor = purpose;
      f.blocking = false;
      f.axeMode = false;
      f.placeStage = 0;
      f.phaseStart = PvpBotMod.globalTick;
      f.strafeDir = 0;
      f.started = false;
      f.hopping = true;
      f.paused = false;
      f.crit = false;
      f.attackTick = -1;
      f.edgeHold = false;
      f.fleeStuckTicks = 0;
      f.fleeLastX = Double.NaN;
      f.fleeLastZ = Double.NaN;
   }

   static void fleePhase(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target, double dist) {
      double hp = bot.getHealth() + bot.getAbsorptionAmount();
      if (f.fleeFor != 0 || !BotSupport.cocoonReady(bot, f, hp) || !startCocoon(server, name, f, bot)) {
         if (!Double.isNaN(f.fleeLastX)) {
            double sdx = bot.getX() - f.fleeLastX;
            double sdz = bot.getZ() - f.fleeLastZ;
            if (sdx * sdx + sdz * sdz < 9.0E-4) {
               f.fleeStuckTicks++;
            } else {
               f.fleeStuckTicks = 0;
            }
         }

         f.fleeLastX = bot.getX();
         f.fleeLastZ = bot.getZ();
         if (f.fleeStuckTicks < Math.max(2, PvpBotMod.WINDCHARGE_BLOCKED_TICKS.asInt())
            || BotSupport.findItemIndex(bot, Items.WIND_CHARGE) < 0
            || !startWindEscape(server, name, f, bot, target)) {
            if (PvpBotMod.globalTick % 2 == 0) {
               BlockPos web = PvpBotMod.WEB_ZONE_AWARE.on() && f.fleeFor == 0 ? BotSupport.nearbyWeb(bot, PvpBotMod.WEB_ZONE_RADIUS.value) : null;
               double baseYaw;
               if (web != null) {
                  double dx = web.getX() + 0.5 - bot.getX();
                  double dz = web.getZ() + 0.5 - bot.getZ();
                  baseYaw = Math.toDegrees(Math.atan2(-dx, dz));
               } else {
                  double dx = bot.getX() - target.getX();
                  double dz = bot.getZ() - target.getZ();
                  baseYaw = Math.toDegrees(Math.atan2(-dx, dz));
               }

               double yaw = baseYaw;
               if (PvpBotMod.VOID_AWARE.on() || PvpBotMod.WEB_AVOID.on()) {
                  double[] turns = new double[]{0.0, 50.0, -50.0, 100.0, -100.0};
                  BlockPos targetCell = target.getBlockPos();

                  for (double turn : turns) {
                     double a = Math.toRadians(baseYaw + turn);
                     double px = bot.getX() - Math.sin(a) * 1.6;
                     double pz = bot.getZ() + Math.cos(a) * 1.6;
                     boolean bad = PvpBotMod.VOID_AWARE.on() && BotSupport.hazardAt(BotSupport.sw(bot), px, bot.getY(), pz)
                        || PvpBotMod.WEB_AVOID.on() && BotSupport.webAheadOf(BotSupport.sw(bot), px, bot.getY(), pz, targetCell);
                     if (!bad) {
                        yaw = baseYaw + turn;
                        break;
                     }
                  }
               }

               BotSupport.run(server, "player " + name + " look " + String.format(Locale.ROOT, "%.1f", yaw) + " 0");
            }

            if (!bot.isSprinting()) {
               BotSupport.run(server, "player " + name + " sprint");
            }

            if (dist >= PvpBotMod.FLEE_DIST.value || PvpBotMod.globalTick - f.phaseStart > 80) {
               if (f.fleeFor == 1) {
                  enterPotionPhase(server, name, f);
               } else {
                  startEat(server, name, f, bot, false);
               }
            }
         }
      }
   }

   static void startEat(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, boolean stayPut) {
      BotSupport.run(server, "player " + name + " stop");
      f.hopping = false;
      int idx = BotSupport.findFoodIndex(bot);
      int slot = idx < 0 ? -1 : BotSupport.toHotbar(bot, idx, BotSupport.findWeaponSlot(bot));
      if (slot < 0) {
         endRetreat(server, name, f);
      } else {
         ItemStack food = bot.getInventory().getStack(slot);
         f.eatSlot = slot;
         f.eatItem = food.getItem();
         f.eatStackCount = food.getCount();
         BotSupport.run(server, "player " + name + " hotbar " + (slot + 1));
         BotSupport.run(server, "player " + name + " look " + String.format(Locale.ROOT, "%.1f", bot.getYaw()) + " -20");
         BotSupport.run(server, "player " + name + " use continuous");
         if (!stayPut) {
            BotSupport.run(server, "player " + name + " move forward");
            BotSupport.run(server, "player " + name + " jump continuous");
         }

         f.phase = PvpBotMod.Phase.EAT;
         f.useStart = PvpBotMod.globalTick;
      }
   }

   static void eatPhase(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target, double dist) {
      if (PvpBotMod.EAT_FULL_SPEED.on() && !bot.isSprinting() && PvpBotMod.globalTick % 2 == 0) {
         BotSupport.run(server, "player " + name + " sprint");
      }

      int elapsed = PvpBotMod.globalTick - f.useStart;
      ItemStack now = bot.getInventory().getStack(f.eatSlot);
      boolean consumed = now.isEmpty() || !now.isOf(f.eatItem) || now.getCount() < f.eatStackCount;
      boolean gaveUp = elapsed > 60 || elapsed >= 5 && !bot.isUsingItem();
      if (consumed || gaveUp) {
         BotSupport.run(server, "player " + name + " use");
         f.eaten++;
         double hpNow = bot.getHealth() + bot.getAbsorptionAmount();
         boolean healthy = PvpBotMod.EAT_UNTIL_HEARTS.value <= 0.0 || hpNow >= PvpBotMod.EAT_UNTIL_HEARTS.value * 2.0;
         if ((f.eaten < PvpBotMod.EAT_COUNT.asInt() || !healthy) && BotSupport.findFoodIndex(bot) >= 0) {
            startEat(server, name, f, bot, f.inCocoon);
         } else {
            endRetreat(server, name, f);
         }
      }
   }

   static void endRetreat(MinecraftServer server, String name, PvpBotMod.Fight f) {
      BotSupport.run(server, "player " + name + " stop");
      f.reset();
      f.nextEatTick = PvpBotMod.globalTick + 60;
   }

   static boolean startCocoon(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot) {
      int idx = BotSupport.findItemIndex(bot, Items.COBWEB);
      int slot = idx < 0 ? -1 : BotSupport.toHotbar(bot, idx, BotSupport.findWeaponSlot(bot));
      if (slot >= 0 && BotSupport.cocoonGroundOk(bot)) {
         BotSupport.run(server, "player " + name + " stop");
         BotSupport.run(server, "player " + name + " hotbar " + (slot + 1));
         f.phase = PvpBotMod.Phase.COCOON;
         f.cocoonStage = 1;
         f.cocoonStart = PvpBotMod.globalTick;
         f.cocoonRetries = 0;
         f.cocoonCheckCell = null;
         f.eaten = 0;
         f.inCocoon = false;
         f.started = false;
         f.hopping = false;
         f.paused = false;
         f.crit = false;
         f.blocking = false;
         f.axeMode = false;
         f.placeStage = 0;
         f.strafeDir = 0;
         f.attackTick = -1;
         f.edgeHold = false;
         return true;
      } else {
         return false;
      }
   }

   static boolean cocoonSettled(ServerPlayerEntity bot) {
      Vec3d v = bot.getVelocity();
      return bot.isOnGround() && v.x * v.x + v.z * v.z < 0.0025;
   }

   static void cocoonPhase(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target, double dist) {
      int elapsed = PvpBotMod.globalTick - f.cocoonStart;
      if (f.cocoonStage == 1) {
         if (!cocoonSettled(bot)) {
            f.cocoonStart = PvpBotMod.globalTick;
            return;
         }

         if (elapsed >= 1) {
            if (BotSupport.findItemIndex(bot, Items.COBWEB) < 0) {
               startFlee(server, name, f, 0);
               return;
            }

            f.cocoonCheckCell = bot.getBlockPos();
            BotSupport.run(server, "player " + name + " look " + String.format(Locale.ROOT, "%.1f", bot.getYaw()) + " 90");
            BotSupport.useOnce(server, name, f);
            f.cocoonStage = 2;
            f.cocoonStart = PvpBotMod.globalTick;
         }
      } else if (f.cocoonStage == 2) {
         if (elapsed >= 2) {
            boolean placed = f.cocoonCheckCell != null && BotSupport.sw(bot).getBlockState(f.cocoonCheckCell).isOf(Blocks.COBWEB);
            if (!placed) {
               f.cocoonRetries++;
               if (f.cocoonRetries > 3) {
                  startFlee(server, name, f, 0);
                  return;
               }

               f.cocoonStage = 1;
               f.cocoonStart = PvpBotMod.globalTick;
               return;
            }

            if (!cocoonSettled(bot)) {
               f.cocoonStart = PvpBotMod.globalTick;
               return;
            }

            if (BotSupport.findItemIndex(bot, Items.COBWEB) < 0) {
               f.inCocoon = true;
               startEat(server, name, f, bot, true);
               return;
            }

            f.cocoonCheckCell = bot.getBlockPos().up();
            BotSupport.run(server, "player " + name + " look " + String.format(Locale.ROOT, "%.1f", bot.getYaw()) + " 90");
            BotSupport.useOnce(server, name, f);
            f.cocoonStage = 3;
            f.cocoonStart = PvpBotMod.globalTick;
         }
      } else if (f.cocoonStage == 3 && elapsed >= 2) {
         boolean placedx = f.cocoonCheckCell != null && BotSupport.sw(bot).getBlockState(f.cocoonCheckCell).isOf(Blocks.COBWEB);
         if (!placedx && f.cocoonRetries <= 3 && BotSupport.findItemIndex(bot, Items.COBWEB) >= 0) {
            f.cocoonRetries++;
            f.cocoonStage = 2;
            f.cocoonStart = PvpBotMod.globalTick;
            return;
         }

         f.inCocoon = true;
         startEat(server, name, f, bot, true);
      }
   }

   static void startPotionRun(MinecraftServer server, String name, PvpBotMod.Fight f, double dist, int kind, double hp) {
      f.thrownMask = 0;
      f.healsRemaining = kind == 1 && PvpBotMod.POTION_HEARTS2.value > 0.0 && hp <= PvpBotMod.POTION_HEARTS2.value * 2.0 ? 2 : 0;
      boolean closeHeal = kind == 1 && dist <= 2.0;
      if (!(dist >= PvpBotMod.FLEE_DIST.value) && !closeHeal) {
         startFlee(server, name, f, 1);
      } else {
         enterPotionPhase(server, name, f);
      }
   }

   static void enterPotionPhase(MinecraftServer server, String name, PvpBotMod.Fight f) {
      BotSupport.run(server, "player " + name + " stop");
      f.phase = PvpBotMod.Phase.POTION;
      f.potionStage = 0;
      f.potionStart = PvpBotMod.globalTick;
      f.started = false;
      f.hopping = false;
      f.paused = false;
      f.crit = false;
      f.blocking = false;
      f.axeMode = false;
      f.placeStage = 0;
      f.strafeDir = 0;
      f.attackTick = -1;
      f.edgeHold = false;
   }

   static void potionPhase(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target, double dist) {
      if (f.potionStage == 0) {
         double hp = bot.getHealth() + bot.getAbsorptionAmount();
         int kind = f.healsRemaining > 0 && BotSupport.findPotionIndex(bot, 1) >= 0 ? 1 : BotSupport.wantedPotion(bot, f, dist, hp);
         boolean closeHeal = kind == 1 && dist <= 2.0;
         if (dist < 5.0 && !closeHeal) {
            f.nextPotionTick = PvpBotMod.globalTick + PvpBotMod.POTION_DELAY.asInt();
            endRetreat(server, name, f);
            f.nextEatTick = PvpBotMod.globalTick;
            return;
         }

         if (kind == 0) {
            f.nextPotionTick = PvpBotMod.globalTick + PvpBotMod.POTION_DELAY.asInt();
            endRetreat(server, name, f);
            return;
         }

         int idx = BotSupport.findPotionIndex(bot, kind);
         int slot = idx < 0 ? -1 : BotSupport.toHotbar(bot, idx, BotSupport.findWeaponSlot(bot));
         if (slot < 0) {
            f.thrownMask |= 1 << kind - 1;
            return;
         }

         BotSupport.run(server, "player " + name + " hotbar " + (slot + 1));
         BotSupport.run(server, "player " + name + " look " + String.format(Locale.ROOT, "%.1f", bot.getYaw()) + " 90");
         f.potionKind = kind;
         f.potionStage = 1;
         f.potionStart = PvpBotMod.globalTick;
      } else if (f.potionStage == 1) {
         if (PvpBotMod.globalTick - f.potionStart >= 1) {
            BotSupport.useOnce(server, name, f);
            if (f.potionKind == 1 && f.healsRemaining > 0) {
               f.healsRemaining--;
            } else {
               f.thrownMask = f.thrownMask | 1 << f.potionKind - 1;
            }

            f.potionStage = 2;
            f.potionStart = PvpBotMod.globalTick;
         }
      } else if (PvpBotMod.globalTick - f.potionStart >= 3) {
         f.potionStage = 0;
      }
   }
}

final class Gui {
   private static final int ROWS = 6;
   private static final int SIZE = 54;
   private static final int BODY_SIZE = 45;
   private static final int PREV_SLOT = 45;
   private static final int PAGE_LABEL_SLOT = 49;
   private static final int NEXT_SLOT = 53;
   private static final int DIFFICULTY_ROW = 9;
   private static final int PLAYSTYLE_ROW = 27;
   private static final Map<String, String> CATEGORY = new LinkedHashMap<>();
   private static final List<String> CATEGORY_ORDER = new ArrayList<>();
   private static final Map<String, Item> ICON = new LinkedHashMap<>();

   private Gui() {
   }

   private static void cat(String category, String... cmds) {
      if (!CATEGORY_ORDER.contains(category)) {
         CATEGORY_ORDER.add(category);
      }

      for (String cmd : cmds) {
         CATEGORY.put(cmd, category);
      }
   }

   private static void icon(Item item, String... cmds) {
      for (String cmd : cmds) {
         ICON.put(cmd, item);
      }
   }

   private static List<String> pageNames() {
      List<String> pages = new ArrayList<>();
      pages.add("Difficulty & Playstyle");
      pages.addAll(CATEGORY_ORDER);
      return pages;
   }

   private static List<String> cmdsFor(String category) {
      List<String> list = new ArrayList<>();

      for (Entry<String, String> e : CATEGORY.entrySet()) {
         if (e.getValue().equals(category)) {
            list.add(e.getKey());
         }
      }

      return list;
   }

   static void open(ServerPlayerEntity player) {
      player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inv, p) -> new Gui.GuiMenu(syncId), Text.literal("PvpBot Settings")));
   }

   static {
      cat(
         "Combat",
         "range",
         "autotarget",
         "findrange",
         "aim",
         "reactmin",
         "reactmax",
         "predictaim",
         "aimcone",
         "critchance",
         "critrange",
         "fallcrit",
         "critchainchance",
         "critchainmin",
         "critchainmax",
         "jumpchance",
         "jumprange",
         "jumpreset",
         "keepdistance",
         "pressurekeep",
         "strafe",
         "strafeticks",
         "strafechance",
         "botfight",
         "bunnyhop",
         "hopstop",
         "punishcritchance",
         "recoverychance",
         "totempunishticks",
         "archerrush"
      );
      cat("Eating", "eat", "eatcount", "eatuntil", "hungereat", "eatfullspeed", "flee", "revenge", "eatknockback", "eatknockbackticks");
      cat(
         "Shield",
         "shield",
         "shieldrange",
         "shieldticks",
         "shieldticksmax",
         "postswingshield",
         "postswingshieldticksmin",
         "postswingshieldticksmax",
         "stun",
         "maceaware"
      );
      cat(
         "Combo",
         "combochance",
         "combostap",
         "combowtap",
         "combouppercut",
         "combostrafecombo",
         "combotapticks",
         "uppercutrange",
         "uppercutwindow",
         "combostrafeticks",
         "mixchance",
         "mixmaxhits",
         "mixswitchchance"
      );
      cat(
         "Escape",
         "combohitsmin",
         "combohitsmax",
         "combohitsescapechance",
         "windchargechance",
         "windchargehealchance",
         "windchargewaitticks",
         "windchargeblockedticks",
         "escapewebchance",
         "escaperunchance"
      );
      cat(
         "Webs",
         "hitweb",
         "webcrit",
         "webavoid",
         "webtime",
         "webleadticks",
         "webzone",
         "webzoneradius",
         "webescape",
         "webbreak",
         "stuck",
         "cocoon",
         "cocoonhearts",
         "webtrapchance",
         "webtraphearts",
         "webtrapcount",
         "randomwebchance",
         "randomwebmindist",
         "randomwebmaxdist",
         "randomwebcooldown"
      );
      cat(
         "Potions & Items",
         "potions",
         "potionhearts",
         "potionhearts2",
         "potiondelay",
         "totem",
         "totemhearts",
         "loot",
         "lootrange",
         "durabilityswap",
         "durabilitythreshold",
         "expbottle",
         "expbottlethreshold",
         "massmax"
      );
      cat(
         "Human Mistakes",
         "miss",
         "wobble",
         "flinchchance",
         "flinchticksmin",
         "flinchticksmax",
         "overshootchance",
         "overshootdegrees",
         "aimspread",
         "precisionlock"
      );
      cat("Team & FFA", "focuschance", "focusmin", "focusmax", "taunts", "ffaleave", "opsonly", "voidaware");
      cat("Wind & Mace", "windclimb", "windclimbheight", "windmacechance", "windmacehearts", "windmacecooldown", "windjumpdelay", "macefear", "macefearchance", "macefearrun", "macefearradius", "fallshieldchance");
      cat("Mace Pressure", "maceretry", "maceretrymax", "macechaingap", "macepressure", "maceaimerror", "macemistake", "windjumplatechance", "windjumplatemax");
      cat("Pathfinding", "pathfind", "pathmode", "pathrange", "pathnodes", "pathreplan", "pathbudget", "pathdrop");
      cat("Fall & Swap", "fallmace", "fallmacechance", "fallmaceheight", "fallclutch", "clutchheight", "clutchsuccess", "maceswap", "maceswapchance", "spearswap", "spearswapchance", "spearreach");
      icon(
         Items.DIAMOND_SWORD,
         "range",
         "critchance",
         "critrange",
         "critchainchance",
         "critchainmin",
         "critchainmax",
         "jumpchance",
         "jumprange",
         "jumpreset",
         "punishcritchance",
         "recoverychance",
         "totempunishticks"
      );
      icon(Items.BOW, "aim", "reactmin", "reactmax", "predictaim", "aimcone", "findrange");
      icon(Items.LEATHER_BOOTS, "keepdistance", "pressurekeep", "strafeticks", "strafechance", "hopstop");
      icon(Items.SPECTRAL_ARROW, "archerrush");
      icon(Items.GOLDEN_APPLE, "eat", "eatcount", "eatuntil", "hungereat", "flee", "eatknockback", "eatknockbackticks");
      icon(
         Items.SHIELD,
         "shield",
         "shieldrange",
         "shieldticks",
         "shieldticksmax",
         "postswingshield",
         "postswingshieldticksmin",
         "postswingshieldticksmax",
         "stun",
         "maceaware"
      );
      icon(Items.PISTON, "combostap", "combowtap", "combotapticks");
      icon(Items.FEATHER, "combouppercut", "uppercutrange", "uppercutwindow");
      icon(Items.LEATHER_BOOTS, "combostrafecombo", "combostrafeticks");
      icon(Items.GOLDEN_SWORD, "combochance", "mixchance", "mixmaxhits", "mixswitchchance");
      icon(Items.WIND_CHARGE, "windchargechance", "windchargehealchance", "windchargewaitticks", "windchargeblockedticks");
      icon(Items.ENDER_PEARL, "combohitsmin", "combohitsmax", "combohitsescapechance", "escaperunchance");
      icon(
         Items.COBWEB,
         "hitweb",
         "webtime",
         "webleadticks",
         "webzoneradius",
         "cocoonhearts",
         "webtrapchance",
         "webtraphearts",
         "webtrapcount",
         "randomwebchance",
         "randomwebmindist",
         "randomwebmaxdist",
         "randomwebcooldown",
         "escapewebchance"
      );
      icon(Items.SPLASH_POTION, "potionhearts", "potionhearts2", "potiondelay");
      icon(Items.TOTEM_OF_UNDYING, "totemhearts");
      icon(Items.EXPERIENCE_BOTTLE, "expbottlethreshold");
      icon(Items.ANVIL, "durabilitythreshold");
      icon(Items.CHEST, "lootrange");
      icon(Items.PLAYER_HEAD, "massmax");
      icon(Items.SPIDER_EYE, "miss", "wobble", "flinchchance", "flinchticksmin", "flinchticksmax", "overshootchance", "overshootdegrees", "aimspread");
      icon(Items.WHITE_BANNER, "focuschance", "focusmin", "focusmax");
      icon(Items.NAME_TAG, "taunts");
      icon(Items.WIND_CHARGE, "windclimb", "windclimbheight", "windmacechance", "windmacehearts", "windmacecooldown", "windjumpdelay");
      icon(Items.MACE, "macefear", "macefearchance", "macefearrun", "macefearradius", "fallshieldchance");
      icon(Items.MACE, "maceretry", "maceretrymax", "macechaingap", "macepressure", "maceaimerror", "macemistake", "windjumplatechance", "windjumplatemax");
      icon(Items.COMPASS, "pathfind", "pathmode", "pathrange", "pathnodes", "pathreplan", "pathbudget", "pathdrop");
      icon(Items.WATER_BUCKET, "fallmace", "fallmacechance", "fallmaceheight", "fallclutch", "clutchheight", "clutchsuccess", "maceswap", "maceswapchance", "spearswap", "spearswapchance", "spearreach");
   }

   static final class GuiMenu extends ScreenHandler {
      private final Inventory container = new SimpleInventory(54);
      private int page;

      GuiMenu(int syncId) {
         super(ScreenHandlerType.GENERIC_9X6, syncId);

         for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
               int index = row * 9 + col;
               this.addSlot(new Slot(this.container, index, 8 + col * 18, 18 + row * 18) {
                  public boolean canInsert(ItemStack stack) {
                     return false;
                  }

                  public boolean canTakeItems(PlayerEntity who) {
                     return false;
                  }
               });
            }
         }

         this.render();
      }

      public boolean canUse(PlayerEntity player) {
         return true;
      }

      public ItemStack quickMove(PlayerEntity player, int index) {
         return ItemStack.EMPTY;
      }

      public void onSlotClick(int slotId, int button, SlotActionType clickType, PlayerEntity player) {
         if (slotId >= 0 && slotId < 54 && player instanceof ServerPlayerEntity) {
            if (slotId == 45) {
               this.page = Math.floorMod(this.page - 1, Gui.pageNames().size());
               this.render();
            } else if (slotId == 53) {
               this.page = Math.floorMod(this.page + 1, Gui.pageNames().size());
               this.render();
            } else {
               boolean rightClick = button == 1;
               boolean shift = clickType == SlotActionType.QUICK_MOVE;
               if (this.page == 0) {
                  this.handlePresetClick(slotId);
               } else {
                  this.handleSettingClick(slotId, rightClick, shift);
               }

               this.render();
            }
         }
      }

      private void handleSettingClick(int slotId, boolean rightClick, boolean shift) {
         if (slotId < 45) {
            List<String> cmds = Gui.cmdsFor(Gui.pageNames().get(this.page));
            if (slotId < cmds.size()) {
               PvpBotMod.Opt o = PvpBotMod.OPTS.get(cmds.get(slotId));
               if (o != null) {
                  if (o.bool) {
                     PvpBotMod.setOptValue(o, o.on() ? 0.0 : 1.0);
                  } else {
                     double range = o.max - o.min;
                     double smallStep = Math.max(range / 100.0, range >= 1.0 ? 1.0 : 0.05);
                     double bigStep = Math.max(smallStep * 5.0, range / 20.0);
                     double step = shift ? bigStep : smallStep;
                     PvpBotMod.setOptValue(o, o.value + (rightClick ? -step : step));
                  }
               }
            }
         }
      }

      private void handlePresetClick(int slotId) {
         if (slotId >= 9 && slotId < 9 + PvpBotMod.LEVEL_NAMES.length) {
            PvpBotMod.setDifficultyLevel(slotId - 9);
         } else if (slotId >= 27 && slotId < 27 + PvpBotMod.PLAYSTYLE_NAMES.length) {
            PvpBotMod.setPlaystyleIndex(slotId - 27);
         }
      }

      private void render() {
         for (int i = 0; i < 54; i++) {
            this.container.setStack(i, ItemStack.EMPTY);
         }

         List<String> pages = Gui.pageNames();
         String pageName = pages.get(this.page);
         this.container.setStack(49, labelItem(Items.PAPER, pageName, List.of("Page " + (this.page + 1) + " of " + pages.size())));
         this.container.setStack(45, labelItem(Items.ARROW, "<- Previous page", List.of()));
         this.container.setStack(53, labelItem(Items.ARROW, "Next page ->", List.of()));
         if (this.page == 0) {
            for (int i = 0; i < PvpBotMod.LEVEL_NAMES.length; i++) {
               boolean current = PvpBotMod.LEVEL_NAMES[i].equals(PvpBotMod.difficultyName);
               this.container
                  .setStack(
                     9 + i,
                     labelItem(
                        current ? Items.LIME_DYE : Items.RED_DYE,
                        i + 1 + ". " + PvpBotMod.LEVEL_NAMES[i],
                        List.of(current ? "Current difficulty" : "Click to select")
                     )
                  );
            }

            for (int i = 0; i < PvpBotMod.PLAYSTYLE_NAMES.length; i++) {
               boolean current = PvpBotMod.PLAYSTYLE_NAMES[i].equals(PvpBotMod.playstyleName);
               this.container
                  .setStack(
                     27 + i,
                     labelItem(
                        current ? Items.LIME_DYE : Items.RED_DYE,
                        PvpBotMod.PLAYSTYLE_NAMES[i],
                        List.of(current ? "Current playstyle" : "Click to select")
                     )
                  );
            }
         } else {
            List<String> cmds = Gui.cmdsFor(pageName);

            for (int i = 0; i < cmds.size() && i < 45; i++) {
               PvpBotMod.Opt o = PvpBotMod.OPTS.get(cmds.get(i));
               if (o != null) {
                  List<String> lore = new ArrayList<>();
                  lore.add("/pvpbot " + o.cmd);
                  lore.add(o.desc);
                  lore.add(o.bool ? "Click to toggle" : "Left: increase   Right: decrease   Shift: bigger step");
                  Item icon = o.bool ? (o.on() ? Items.LIME_DYE : Items.RED_DYE) : Gui.ICON.getOrDefault(o.cmd, Items.PAPER);
                  this.container.setStack(i, labelItem(icon, o.key + " = " + o.show(), lore));
               }
            }
         }
      }

      private static ItemStack labelItem(Item item, String name, List<String> lore) {
         ItemStack stack = new ItemStack(item);
         stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
         if (!lore.isEmpty()) {
            List<Text> lines = new ArrayList<>();

            for (String line : lore) {
               lines.add(Text.literal(line));
            }

            stack.set(DataComponentTypes.LORE, new LoreComponent(lines));
         }

         return stack;
      }
   }
}
