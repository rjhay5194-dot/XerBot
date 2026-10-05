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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopped;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.world.World;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.command.CommandSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;

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
   static final PvpBotMod.Opt WIND_JUMP_TICKS = opt(
      "windjumpticks", "wind_jump_ticks", 4.0, 1.0, 10.0, false, "ticks the bot rises after jumping before throwing the wind charge (higher launch)"
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
   static final Map<String, PvpBotMod.Pending> PENDING = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
   static final Map<String, Integer> LAST_HURT = new HashMap<>();
   static final ArrayDeque<PvpBotMod.MassJob> MASS_QUEUE = new ArrayDeque<>();
   static final ArrayList<PvpBotMod.PlacedWeb> PLACED_WEBS = new ArrayList<>();
   static CommandDispatcher<ServerCommandSource> dispatcher;
   static int globalTick = 0;
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
         STOPPED.add(name);
         BotSupport.run(src.getServer(), "player " + name + " stop");
         src.sendFeedback(() -> Text.literal(name + " stopped. It won't pick targets again until you use /pvpbot fight " + name + "."), false);
         return 1;
      }
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
         if (sb.length() > 0) {
            sb.append(", ");
         }

         sb.append(name);
         if (f != null) {
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
         StringBuilder sb = new StringBuilder(name);
         sb.append(BOTS.contains(name) ? ": PvP bot" : ": not a PvP bot");
         if (f == null) {
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
         int nx = missing.size();
         src.sendFeedback(() -> Text.literal("Rematch: respawning " + n + " bots, then a new countdown."), false);
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
      MACEFEAR;
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
