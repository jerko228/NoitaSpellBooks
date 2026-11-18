package ua.spellbooks.noita;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class NoitaSpellBooks extends JavaPlugin implements Listener {

    private NamespacedKey spellKey;
    private SpellExecutor executor;

    @Override
    public void onEnable() {
        this.spellKey = new NamespacedKey(this, "noita_spell_data");
        this.executor = new SpellExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("NoitaSpellBooks enabled! 30 spell tokens loaded.");
    }

    @EventHandler
    public void onBookSign(PlayerEditBookEvent event) {
        if (!event.isSigning()) return;

        Player player = event.getPlayer();
        BookMeta meta = event.getNewBookMeta();
        String fullText = String.join(" ", meta.getPages());

        SpellStats stats = SpellStats.compile(fullText);
        if (stats.tokens.isEmpty()) {
            return; // Normal signed book if no magic tokens found
        }

        if (player.getLevel() < stats.xpCost) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Недостатньо досвіду для створення заклинання! Потрібно: "
                    + ChatColor.GOLD + stats.xpCost + " рівнів" + ChatColor.RED + " (у вас: " + player.getLevel() + ").");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // Deduct XP levels
        player.setLevel(player.getLevel() - stats.xpCost);

        // Build Lore & Visuals (Option 2: Lore summary upon signing)
        String title = meta.hasTitle() ? meta.getTitle() : "Магічний Фоліант";
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "✦ " + title + " ✦");

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "--------------------------------");
        lore.add(ChatColor.GRAY + "Формула: " + ChatColor.AQUA + stats.tokens.stream()
                .map(Enum::name)
                .collect(Collectors.joining(" ")));
        lore.add("");
        lore.add(ChatColor.YELLOW + "Характеристики заклинання:");
        lore.add(ChatColor.GRAY + " • Шкода: " + ChatColor.RED + String.format("%.1f", stats.damage));
        lore.add(ChatColor.GRAY + " • Швидкість: " + ChatColor.GREEN + String.format("%.2f", stats.speed));
        lore.add(ChatColor.GRAY + " • Дальність: " + ChatColor.BLUE + String.format("%.0f", stats.range) + " бл.");
        lore.add(ChatColor.GRAY + " • Радіус дії: " + ChatColor.GOLD + String.format("%.1f", stats.radius) + " бл.");
        lore.add(ChatColor.GRAY + " • Снарядів: " + ChatColor.WHITE + stats.count + " шт.");
        lore.add(ChatColor.GRAY + " • Перезарядка: " + ChatColor.YELLOW + String.format("%.1f", stats.cooldownTicks / 20.0) + " с.");
        lore.add("");
        lore.add(ChatColor.DARK_PURPLE + "Вартість створення: " + ChatColor.GOLD + stats.xpCost + " рівнів");
        lore.add(ChatColor.GREEN + "Статус: Активне заклинання (ПКМ)");
        lore.add(ChatColor.DARK_GRAY + "--------------------------------");

        meta.setLore(lore);
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        // Save raw formula in PDC so it cannot be spoofed by anvil renaming
        meta.getPersistentDataContainer().set(spellKey, PersistentDataType.STRING, fullText);

        event.setNewBookMeta(meta);
        player.sendMessage(ChatColor.GREEN + "Заклинання успішно створено! Списано " + ChatColor.GOLD + stats.xpCost + " рівнів досвіду.");
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
    }

    @EventHandler
    public void onSpellCast(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.WRITTEN_BOOK || !item.hasItemMeta()) return;

        BookMeta meta = (BookMeta) item.getItemMeta();
        String rawSpell = meta.getPersistentDataContainer().get(spellKey, PersistentDataType.STRING);
        if (rawSpell == null) return;

        // Cancel opening the book UI when casting
        event.setCancelled(true);

        SpellStats stats = SpellStats.compile(rawSpell);
        executor.cast(event.getPlayer(), stats);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("spellwords")) {
            sender.sendMessage(ChatColor.GOLD + "=== Доступні 30 слів заклинань (NoitaSpellBooks) ===");
            sender.sendMessage(ChatColor.AQUA + "[Structure]: " + ChatColor.WHITE + "Bolt, Arrow, Triple, Burst, Orb, Boomerang, Touch, Aura, Bounce, Mine");
            sender.sendMessage(ChatColor.RED + "[Effect]: " + ChatColor.WHITE + "Fire, Frost, Lightning, Boom, Poison, Heal, Dig, Light, Vampire, Push");
            sender.sendMessage(ChatColor.GREEN + "[Modifier]: " + ChatColor.WHITE + "Fast, Slow, Power, Giant, Tiny, Far, Aim, Blast, Cheap, Wet");
            return true;
        }
        return false;
    }
}
