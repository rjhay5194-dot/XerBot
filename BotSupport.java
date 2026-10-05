package dev.pvpbotcmd;

import com.mojang.brigadier.tree.CommandNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.world.World;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.Registry;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.registry.entry.RegistryEntry.Reference;
import net.minecraft.server.MinecraftServer;

final class BotSupport {
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
      World level = target.getServerWorld();
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
      World level = target.getServerWorld();
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

               run(server, "player " + name + " use once");
               f.placeStage = 3;
               f.placeStart = PvpBotMod.globalTick;
            }
         } else if (elapsed >= 2) {
            boolean placed = f.placeCell != null && bot.getServerWorld().getBlockState(f.placeCell).isOf(Blocks.COBWEB);
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
                  PvpBotMod.PLACED_WEBS.add(new PvpBotMod.PlacedWeb(bot.getServerWorld(), f.placeCell, PvpBotMod.globalTick + PvpBotMod.WEB_LIFETIME.asInt()));
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
      World level = target.getServerWorld();
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
         if (bot.getServerWorld().getBlockState(p).isOf(Blocks.COBWEB)) {
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
               run(server, "player " + name + " use once");
               f.webStage = 2;
               f.webStart = PvpBotMod.globalTick;
            }
         } else if (f.webStage == 2) {
            int elapsed = PvpBotMod.globalTick - f.webStart;
            if (elapsed >= 2 && !inCobweb(bot) || elapsed >= 14) {
               run(server, "player " + name + " use once");
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
         boolean gone = f.breakPos == null || !bot.getServerWorld().getBlockState(f.breakPos).isOf(Blocks.COBWEB);
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
                     FluidState fluid = bot.getServerWorld().getFluidState(p);
                     if (fluid.isIn(FluidTags.WATER) && fluid.isStill()) {
                        bot.getServerWorld().setBlockState(p, Blocks.AIR.getDefaultState(), 3);
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
      World level = bot.getServerWorld();
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
      return t != null && t != bot && t.isAlive() && !t.isSpectator() && !t.isCreative() && bot.getServerWorld() == t.getServerWorld() && !allied(bot, t);
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
      if (!PvpBotMod.BOTS.isEmpty()) {
         if (PvpBotMod.globalTick % 10 == 0) {
            tickArmor(server);
         }

         if (!ffaCounting()) {
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
            Registry<Enchantment> enchantments = bot.getServerWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT);
            Reference<Enchantment> mending = enchantments.getOrThrow(Enchantments.MENDING);
            return EnchantmentHelper.getLevel(mending, stack) > 0;
         } catch (Exception var4) {
            return false;
         }
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
               if ((!PvpBotMod.BOTS.contains(attackerName) || botFight()) && !attacker.isCreative() && !allied(bot, attacker)) {
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
               Entity item = bot.getServerWorld().getEntityById(loot.entityId);
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

               for (ItemEntity candidate : bot.getServerWorld().getNonSpectatingEntities(ItemEntity.class, bot.getBoundingBox().expand(PvpBotMod.LOOT_RANGE.value))) {
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
