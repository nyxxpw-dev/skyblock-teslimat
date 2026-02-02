package nyxx.nyxxTeslimat;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

public final class NyxxTeslimat extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<String, Long> cooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getCommand("teslimat").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new TeslimatExpansion(this).register();
            getLogger().info("PlaceholderAPI basariyla baglandi!");
        }
    }

    private String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    public void openMenu(Player player) {
        int size = getConfig().getInt("settings.menu-size");

        // Karartıyı önlemek için sadece ana glif kodunu bırakıyoruz.
        String customTitle = "§f\uE001";
        Inventory inv = Bukkit.createInventory(null, size, customTitle);

        // Arka planı doldurarak siyah ekranın önüne geçiyoruz (Config'de AIR ise varsayılan cam koyar)
        String fillerType = getConfig().getString("settings.filler-item", "GRAY_STAINED_GLASS_PANE");
        if (!fillerType.equalsIgnoreCase("AIR")) {
            ItemStack filler = new ItemStack(Material.valueOf(fillerType));
            ItemMeta fMeta = filler.getItemMeta();
            if(fMeta != null) {
                fMeta.setDisplayName(" ");
                filler.setItemMeta(fMeta);
            }
            for (int i = 0; i < size; i++) {
                inv.setItem(i, filler);
            }
        }

        if (getConfig().getConfigurationSection("deliveries") != null) {
            for (String key : getConfig().getConfigurationSection("deliveries").getKeys(false)) {
                String path = "deliveries." + key + ".";
                ItemStack item = new ItemStack(Material.valueOf(getConfig().getString(path + "display-item")));
                ItemMeta meta = item.getItemMeta();
                if(meta != null) {
                    meta.setDisplayName(color(getConfig().getString(path + "name")));

                    long timeLeft = getRemaining(player, key);
                    String status = (timeLeft <= 0) ? getConfig().getString("messages.ready-status") :
                            getConfig().getString("messages.wait-status").replace("{TIME}", (timeLeft/1000)/60 + " dk");

                    List<String> lore = new ArrayList<>();
                    for (String l : getConfig().getStringList(path + "lore")) lore.add(color(l.replace("{STATUS}", status)));
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
                inv.setItem(getConfig().getInt(path + "slot"), item);
            }
        }
        player.openInventory(inv);
    }

    public void openAdminPanel(Player admin) {
        Inventory adminInv = Bukkit.createInventory(null, 27, color("&4&lAdmin Kontrol Paneli"));

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta iMeta = info.getItemMeta();
        iMeta.setDisplayName(color("&e&lTeslimat Bilgileri"));
        iMeta.setLore(Arrays.asList(color("&7Sistemde aktif &f" + cooldowns.size() + " &7kayıt var.")));
        info.setItemMeta(iMeta);
        adminInv.setItem(10, info);

        ItemStack reset = new ItemStack(Material.TNT_MINECART);
        ItemMeta rMeta = reset.getItemMeta();
        rMeta.setDisplayName(color("&c&lSüreleri Sıfırla"));
        rMeta.setLore(Arrays.asList(color("&7Tüm oyuncuların bekleme"), color("&7sürelerini anında temizler.")));
        reset.setItemMeta(rMeta);
        adminInv.setItem(13, reset);

        ItemStack reload = new ItemStack(Material.SUNFLOWER);
        ItemMeta reMeta = reload.getItemMeta();
        reMeta.setDisplayName(color("&a&lKonfigürasyonu Yenile"));
        reload.setItemMeta(reMeta);
        adminInv.setItem(16, reload);

        admin.openInventory(adminInv);
    }

    public long getRemaining(Player p, String k) {
        String id = p.getUniqueId().toString() + k;
        if (!cooldowns.containsKey(id)) return 0;
        long totalCooldown = getConfig().getLong("deliveries." + k + ".cooldown") * 1000;
        return (cooldowns.get(id) + totalCooldown) - System.currentTimeMillis();
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTitle() == null) return;
        String title = e.getView().getTitle();
        Player p = (Player) e.getWhoClicked();

        if (title.equals(color("&4&lAdmin Kontrol Paneli"))) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null) return;

            if (e.getSlot() == 13) {
                cooldowns.clear();
                p.sendMessage(color("&a[!] Tüm bekleme süreleri temizlendi."));
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.0f);
                p.closeInventory();
            } else if (e.getSlot() == 16) {
                reloadConfig();
                p.sendMessage(color("&a[!] Ayarlar yenilendi."));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                p.closeInventory();
            }
            return;
        }

        if (title.contains("\uE001")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;

            if (getConfig().getConfigurationSection("deliveries") == null) return;
            for (String key : getConfig().getConfigurationSection("deliveries").getKeys(false)) {
                if (e.getSlot() == getConfig().getInt("deliveries." + key + ".slot")) {
                    handleDelivery(p, key);
                    break;
                }
            }
        }
    }

    private void handleDelivery(Player p, String k) {
        if (getRemaining(p, k) > 0) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, 1);
            return;
        }

        String path = "deliveries." + k + ".";
        Material m = Material.valueOf(getConfig().getString(path + "required-material"));
        int amt = getConfig().getInt(path + "required-amount");

        if (p.getInventory().containsAtLeast(new ItemStack(m), amt)) {
            p.getInventory().removeItem(new ItemStack(m, amt));
            cooldowns.put(p.getUniqueId().toString() + k, System.currentTimeMillis());

            for (String cmd : getConfig().getStringList(path + "reward-commands"))
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{PLAYER}", p.getName()));

            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.2f);
            p.sendTitle(color("&a&lBAŞARILI!"), color("&fTeslimat ödülleri verildi."), 10, 40, 10);
            p.spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1.5, 0), 30, 0.5, 0.5, 0.5, 0.05);

            p.sendMessage(color(getConfig().getString("settings.prefix") + getConfig().getString("messages.success")));
            p.closeInventory();
        } else {
            p.sendMessage(color(getConfig().getString("settings.prefix") + getConfig().getString("messages.no-items")
                    .replace("{NEED}", String.valueOf(amt)).replace("{ITEM}", m.name())));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player)) return true;
        Player p = (Player) s;

        // --- /teslimat bak <oyuncu> ---
        if (a.length > 1 && a[0].equalsIgnoreCase("bak") && p.hasPermission("nyxx.admin")) {
            Player target = Bukkit.getPlayer(a[1]);
            if (target == null) {
                p.sendMessage(color("&cOyuncu bulunamadı."));
                return true;
            }
            p.sendMessage(color("&8&m-------&6&l " + target.getName() + " Bilgi &8&m-------"));
            if (getConfig().getConfigurationSection("deliveries") != null) {
                for (String key : getConfig().getConfigurationSection("deliveries").getKeys(false)) {
                    long remain = getRemaining(target, key);
                    String status = (remain <= 0) ? "&aHAZIR" : "&e" + (remain/1000)/60 + " dk";
                    p.sendMessage(color("&7- " + key.toUpperCase() + ": " + status));
                }
            }
            p.sendMessage(color("&8&m-----------------------------------"));
            return true;
        }

        if (a.length > 0 && a[0].equalsIgnoreCase("admin") && p.hasPermission("nyxx.admin")) {
            openAdminPanel(p);
            return true;
        }

        if (a.length > 0 && a[0].equalsIgnoreCase("reload") && p.hasPermission("nyxx.admin")) {
            reloadConfig();
            p.sendMessage(color(getConfig().getString("settings.prefix") + getConfig().getString("messages.reloaded")));
            return true;
        }

        openMenu(p);
        return true;
    }
}
