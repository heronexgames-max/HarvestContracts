package dev.heronex;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.java.JavaPlugin;

public final class HarvestContracts extends JavaPlugin implements Listener {
    private static final List<Material> CROPS = List.of(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS);
    private static final int GOAL = 24;

    private long day() { return java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay(); }

    private Material crop(long day) { return CROPS.get(Math.floorMod(day, CROPS.size())); }

    private String key(Player player, long day) { return "contracts." + player.getUniqueId() + "." + day; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void harvest(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        long day = day();
        Block block = event.getBlock();
        if (!(block.getBlockData() instanceof Ageable data) || data.getAge() != data.getMaximumAge()) return;
        recordWeekly(player, block.getType(), day);
        recordCommunity(player, block.getType(), week(day));
        if (block.getType() != crop(day)) return;
        String key = key(player, day);
        int progress = getConfig().getInt(key + ".progress");
        if (progress >= GOAL) return;
        getConfig().set(key + ".progress", progress + 1);
        if (progress + 1 == GOAL) {
            registerStreak(player, day);
            saveConfig();
            player.sendMessage("Daily contract complete. Use /harvest claim to collect 3 emeralds.");
        }
    }

    private String streak(Player player) {
        return "streaks." + player.getUniqueId();
    }

    private int activeStreak(String path, long today) {
        long lastDay = getConfig().getLong(path + ".last-day", Long.MIN_VALUE);
        return lastDay == today || lastDay == today - 1 ? getConfig().getInt(path + ".length") : 0;
    }

    private void registerStreak(Player player, long today) {
        String path = streak(player);
        if (getConfig().getLong(path + ".last-day", Long.MIN_VALUE) == today) return;
        int length = activeStreak(path, today) + 1;
        getConfig().set(path + ".last-day", today);
        getConfig().set(path + ".length", length);
        getConfig().set(path + ".best", Math.max(length, getConfig().getInt(path + ".best")));

        int reward = length % 7 == 0 ? 12 : length == 3 ? 5 : 0;
        if (reward > 0) {
            getConfig().set(path + ".pending", getConfig().getLong(path + ".pending") + reward);
            player.sendMessage("Harvest streak milestone: " + length + " days. Claim with /harvest streak claim.");
        }
    }

    private void streakCommand(Player player, boolean claim) {
        String path = streak(player);
        long pending = getConfig().getLong(path + ".pending");
        if (!claim || pending == 0) {
            player.sendMessage("Daily streak: " + activeStreak(path, day()) + " | Best: "
                + getConfig().getInt(path + ".best") + " | Unclaimed emeralds: " + pending);
            return;
        }
        int batch = (int) Math.min(Material.EMERALD.getMaxStackSize(), pending);
        int overflow = player.getInventory().addItem(new ItemStack(Material.EMERALD, batch))
            .values().stream().mapToInt(ItemStack::getAmount).sum();
        int collected = batch - overflow;
        if (collected > 0) {
            getConfig().set(path + ".pending", pending - collected);
            saveConfig();
        }
        player.sendMessage("Streak reward: " + collected + " collected, " + (pending - collected) + " remaining.");
    }

    private List<Material> weeklyCrops(long week) {
        int start = Math.floorMod(week, CROPS.size());
        return List.of(CROPS.get(start), CROPS.get((start + 1) % CROPS.size()),
            CROPS.get((start + 2) % CROPS.size()));
    }

    private long week(long day) {
        return Math.floorDiv(day + 3, 7);
    }

    private String weeklyKey(Player player, long week) {
        return "weekly." + player.getUniqueId() + "." + week;
    }

    private int weeklyGoal(int objective) {
        return 36 + 12 * objective;
    }

    private void recordWeekly(Player player, Material material, long day) {
        long week = week(day);
        List<Material> crops = weeklyCrops(week);
        int objective = crops.indexOf(material);
        if (objective < 0) return;
        String key = weeklyKey(player, week);
        String progressKey = key + "." + material.name();
        int count = getConfig().getInt(progressKey);
        if (count >= weeklyGoal(objective)) return;
        getConfig().set(progressKey, count + 1);
        for (int i = 0; i < crops.size(); i++) {
            if (getConfig().getInt(key + "." + crops.get(i).name()) < weeklyGoal(i)) return;
        }
        getConfig().set(key + ".reward", 18);
        saveConfig();
        player.sendMessage("Weekly contract complete. Claim 18 emeralds from /harvest board.");
    }

    private void claimWeekly(Player player) {
        var periods = getConfig().getConfigurationSection("weekly." + player.getUniqueId());
        if (periods == null) {
            player.sendMessage("No completed weekly rewards.");
            return;
        }
        String reward = periods.getKeys(false).stream()
            .map(Long::parseLong).sorted().map(period -> period + ".reward")
            .filter(path -> periods.getInt(path) > 0).findFirst().orElse(null);
        if (reward == null) {
            player.sendMessage("No completed weekly rewards.");
            return;
        }
        int amount = periods.getInt(reward);
        int overflow = player.getInventory().addItem(new ItemStack(Material.EMERALD, amount))
            .values().stream().mapToInt(ItemStack::getAmount).sum();
        periods.set(reward, overflow);
        saveConfig();
        player.sendMessage("Weekly reward: " + (amount - overflow) + " collected, " + overflow + " still available.");
    }

    private static final int COMMUNITY_PER_CROP = 256;
    private static final int COMMUNITY_MINIMUM = 32;
    private static final int COMMUNITY_REWARD = 24;

    private String campaign(long week) { return "campaigns." + week; }

    private void recordCommunity(Player player, Material material, long week) {
        List<Material> crops = weeklyCrops(week);
        if (!crops.contains(material)) return;
        String campaign = campaign(week);
        if (getConfig().getBoolean(campaign + ".complete")) return;
        String cropPath = campaign + ".crops." + material.name();
        int count = getConfig().getInt(cropPath);
        if (count >= COMMUNITY_PER_CROP) return;
        getConfig().set(cropPath, count + 1);

        String member = "campaign-players." + player.getUniqueId() + "." + week;
        int contributed = getConfig().getInt(member + ".contributed");
        if (contributed < COMMUNITY_MINIMUM) {
            getConfig().set(member + ".contributed", contributed + 1);
            if (contributed + 1 == COMMUNITY_MINIMUM) {
                getConfig().set(member + ".unclaimed", COMMUNITY_REWARD);
                player.sendMessage("You qualified for the community reward if the server completes all three goals.");
            }
        }
        if (count + 1 == COMMUNITY_PER_CROP && crops.stream().allMatch(crop ->
                getConfig().getInt(campaign + ".crops." + crop.name()) >= COMMUNITY_PER_CROP)) {
            getConfig().set(campaign + ".complete", true);
            saveConfig();
            getServer().broadcast(Component.text("Community harvest complete! Qualified players can use /harvest community."));
        }
    }

    private void claimCommunity(Player player) {
        var periods = getConfig().getConfigurationSection("campaign-players." + player.getUniqueId());
        String reward = periods == null ? null : periods.getKeys(false).stream()
            .map(Long::parseLong).sorted()
            .filter(period -> getConfig().getBoolean(campaign(period) + ".complete"))
            .map(period -> period + ".unclaimed")
            .filter(path -> periods.getInt(path) > 0).findFirst().orElse(null);
        if (reward == null) {
            player.sendMessage("No community reward ready. See /harvest board for campaign progress.");
            return;
        }
        int amount = periods.getInt(reward);
        int overflow = player.getInventory().addItem(new ItemStack(Material.EMERALD, amount))
            .values().stream().mapToInt(ItemStack::getAmount).sum();
        periods.set(reward, overflow);
        saveConfig();
        player.sendMessage("Community reward: " + (amount - overflow) + " emeralds collected, " + overflow + " still available.");
    }

    private final class Board implements InventoryHolder {
        private final Inventory inventory = getServer().createInventory(this, 36, Component.text("Harvest Contracts"));

        @Override public Inventory getInventory() { return inventory; }

        void item(int slot, Material material, String title, String detail) {
            var stack = new ItemStack(material);
            stack.editMeta(meta -> {
                meta.displayName(Component.text(title));
                meta.lore(List.of(Component.text(detail)));
            });
            inventory.setItem(slot, stack);
        }
    }

    private void openBoard(Player player) {
        long day = day();
        long week = week(day);
        Board board = new Board();
        board.item(4, Material.FARMLAND, "Daily | " + crop(day).name(),
            getConfig().getInt(key(player, day) + ".progress") + "/" + GOAL + " | Resets at midnight UTC");
        var crops = weeklyCrops(week);
        for (int i = 0; i < crops.size(); i++) {
            Material material = crops.get(i);
            board.item(11 + i * 2, Material.PAPER, "Weekly | " + material.name(),
                getConfig().getInt(weeklyKey(player, week) + "." + material.name()) + "/" + weeklyGoal(i));
        }
        String member = "campaign-players." + player.getUniqueId() + "." + week;
        for (int i = 0; i < crops.size(); i++) {
            String cropName = crops.get(i).name();
            board.item(20 + i * 2, Material.HAY_BLOCK, "Community | " + cropName,
                getConfig().getInt(campaign(week) + ".crops." + cropName) + "/" + COMMUNITY_PER_CROP);
        }
        board.item(31, Material.GOLD_INGOT, "Claim community reward",
            "Your contribution: " + getConfig().getInt(member + ".contributed") + "/" + COMMUNITY_MINIMUM);
        String streak = streak(player);
        board.item(33, Material.CLOCK, "Daily streak | " + activeStreak(streak, day),
            "Best: " + getConfig().getInt(streak + ".best") + " | Pending: " + getConfig().getLong(streak + ".pending"));
        board.item(29, Material.EMERALD, "Claim weekly reward", "Collect the oldest completed reward.");
        player.openInventory(board.inventory);
    }

    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Board)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        switch (event.getRawSlot()) {
            case 29 -> claimWeekly(player);
            case 31 -> claimCommunity(player);
            case 33 -> streakCommand(player, true);
        }
    }

    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Board) event.setCancelled(true);
    }

    @Override public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::saveConfig, 1200L, 1200L);
    }

    @Override public void onDisable() { saveConfig(); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length > 0 && args[0].equalsIgnoreCase("streak")) {
            if (args.length == 1) streakCommand(player, false);
            else if (args.length == 2 && args[1].equalsIgnoreCase("claim")) streakCommand(player, true);
            else player.sendMessage("/harvest streak [claim]");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("board")) {
            openBoard(player);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("community")) {
            claimCommunity(player);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("weekly")) {
            claimWeekly(player);
            return true;
        }
        long day = day();
        String key = key(player, day);
        int progress = getConfig().getInt(key + ".progress");
        if (args.length == 0) {
            player.sendMessage("Daily crop: " + crop(day).name() + " | " + progress + "/" + GOAL
                + (getConfig().getBoolean(key + ".claimed") ? " | Claimed" : " | /harvest claim")
                + " | Resets at midnight UTC");
            return true;
        }
        if (args.length != 1 || !args[0].equalsIgnoreCase("claim")) {
            player.sendMessage("/harvest [claim|board|weekly|community|streak]");
        } else if (getConfig().getBoolean(key + ".claimed")) {
            player.sendMessage("Today's reward is already claimed.");
        } else if (progress < GOAL) {
            player.sendMessage("Harvest " + (GOAL - progress) + " more mature " + crop(day).name() + ".");
        } else {
            int remaining = getConfig().getInt(key + ".remaining", 3);
            int overflow = player.getInventory().addItem(new ItemStack(Material.EMERALD, remaining))
                .values().stream().mapToInt(ItemStack::getAmount).sum();
            getConfig().set(key + ".remaining", overflow);
            getConfig().set(key + ".claimed", overflow == 0);
            saveConfig();
            player.sendMessage("Collected " + (remaining - overflow) + " emeralds."
                + (overflow == 0 ? "" : " Free inventory space and claim again for the rest."));
        }
        return true;
    }
}
