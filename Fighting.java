package dev.pvpbotcmd;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.MinecraftServer;

final class Fighting {
   static void tickFights(MinecraftServer server) {
      Iterator<Entry<String, PvpBotMod.Fight>> it = PvpBotMod.FIGHTS.entrySet().iterator();

      while (it.hasNext()) {
         Entry<String, PvpBotMod.Fight> e = it.next();
         String name = e.getKey();
         PvpBotMod.Fight f = e.getValue();
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
               }
            }
         }
      }
   }

   static void doAttack(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity target) {
      if (PvpBotMod.PRECISION_LOCK.on()) {
         BotSupport.run(server, BotSupport.lookCmd(name, target.getName().getString()));
      }

      if (PvpBotMod.MISS_CHANCE.value > 0.0 && ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.MISS_CHANCE.value) {
         BotSupport.run(server, "player " + name + " swing");
      } else {
         ServerPlayerEntity self = f.lastBot;
         if (self != null && BotSupport.reachDistance(self, target) <= PvpBotMod.ATTACK_RANGE.value && self.canSee(target)) {
            self.attack(target);
         }

         BotSupport.run(server, "player " + name + " swing");
      }

      f.attackTick = PvpBotMod.globalTick;
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
               if (!startItemEscape(server, name, f, bot, target)) {
                  startFlee(server, name, f, 0);
               }
            } else if (!BotSupport.webPlaceStep(server, name, f, bot, target)) {
               if (!BotSupport.breakoutStep(server, name, f, bot, target)) {
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

                        if (PvpBotMod.STRAFE.on() && !f.hopping && !f.crit && dist <= 6.0) {
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
            BotSupport.run(server, "player " + name + " use once");
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
      if (!PvpBotMod.MACE_FEAR.on() || PvpBotMod.globalTick < f.nextFearTick || dist > 45.0 || !targetMaceThreat(bot, target)) {
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
                  BotSupport.run(server, "player " + name + " move");
                  BotSupport.run(server, "player " + name + " unsprint");
                  f.fearMove = 2;
                  f.strafeDir = 0;
                  f.nextFearStrafeTick = PvpBotMod.globalTick;
               }

               if (PvpBotMod.globalTick >= f.nextFearStrafeTick) {
                  f.strafeDir = f.strafeDir == 0 ? (ThreadLocalRandom.current().nextBoolean() ? 1 : -1) : -f.strafeDir;
                  BotSupport.run(server, "player " + name + " move " + (f.strafeDir > 0 ? "left" : "right"));
                  int base = Math.max(3, PvpBotMod.STRAFE_TICKS.asInt());
                  f.nextFearStrafeTick = PvpBotMod.globalTick + base + ThreadLocalRandom.current().nextInt(base / 2 + 1);
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

   static boolean windMaceStep(MinecraftServer server, String name, PvpBotMod.Fight f, ServerPlayerEntity bot, ServerPlayerEntity target, double dist) {
      if (PvpBotMod.WINDMACE_CHANCE.value <= 0.0 || PvpBotMod.globalTick % 20 != 0 || PvpBotMod.globalTick < f.nextWindMaceTick) {
         return false;
      } else if (!bot.isOnGround() || dist < 3.0 || dist > 18.0) {
         return false;
      } else if (bot.getHealth() + bot.getAbsorptionAmount() < PvpBotMod.WINDMACE_HEARTS.value * 2.0) {
         return false;
      } else if (!(ThreadLocalRandom.current().nextDouble() * 100.0 < PvpBotMod.WINDMACE_CHANCE.value)) {
         return false;
      } else if (BotSupport.findItemIndex(bot, Items.WIND_CHARGE) < 0 || BotSupport.findItemIndex(bot, Items.MACE) < 0) {
         return false;
      } else {
         f.nextWindMaceTick = PvpBotMod.globalTick + Math.max(20, PvpBotMod.WINDMACE_COOLDOWN.asInt());
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
         BotSupport.run(server, "player " + name + " jump once");
         f.phase = PvpBotMod.Phase.WINDLAUNCH;
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
         // jump first, then throw the wind charge while airborne for extra height
         if (elapsed >= Math.max(1, PvpBotMod.WIND_JUMP_TICKS.asInt()) && !bot.isOnGround()) {
            BotSupport.run(server, "player " + name + " use once");
            f.launchStage = 2;
            f.launchStart = PvpBotMod.globalTick;
            f.launchMoved = false;
         } else if (elapsed > 14) {
            endWindLaunch(server, name, f);
         }
      } else if (f.launchStage == 2) {
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
            if (!f.launchMoved && elapsed >= 3) {
               BotSupport.run(server, "player " + name + " hotbar " + (f.launchMaceSlot + 1));
               f.launchMoved = true;
            }

            if (f.launchMoved && (bot.getVelocity().y <= 0.05 || elapsed > 30)) {
               BotSupport.run(server, "player " + name + " move forward");
               BotSupport.run(server, "player " + name + " sprint");
               f.launchStage = 3;
               f.launchStart = PvpBotMod.globalTick;
            } else if (elapsed > 40) {
               endWindLaunch(server, name, f);
            }
         }
      } else if (f.launchStage == 3) {
         BotSupport.run(server, BotSupport.aimCmd(name, bot, target, f));
         double reach = BotSupport.reachDistance(bot, target);
         boolean falling = !bot.isOnGround() && bot.getVelocity().y < 0.0;
         if (falling && reach <= PvpBotMod.ATTACK_RANGE.value) {
            doAttack(server, name, f, target);
            bot.fallDistance = 0.0;
            endWindLaunch(server, name, f);
         } else if (elapsed >= 4 && bot.isOnGround() || elapsed > 120) {
            endWindLaunch(server, name, f);
         }
      }
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
            BotSupport.run(server, "player " + name + " use once");
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
            BotSupport.run(server, "player " + name + " use once");
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
            BotSupport.run(server, "player " + name + " use once");
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
            BotSupport.run(server, "player " + name + " use once");
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
            BotSupport.run(server, "player " + name + " use once");
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
