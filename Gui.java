package dev.pvpbotcmd;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.DataComponentTypes;

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
      cat("Eating", "eat", "eatcount", "eatuntil", "hungereat", "eatfullspeed", "flee", "revenge");
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
      icon(Items.GOLDEN_APPLE, "eat", "eatcount", "eatuntil", "hungereat", "flee");
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
