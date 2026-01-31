package nyxx.nyxxTeslimat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class NyxxTeslimat extends JavaPlugin implements Listener, CommandExecutor {
   private final Map<String, Long> cooldowns = new HashMap();

   public void onEnable() {
      this.saveDefaultConfig();
      this.getCommand("teslimat").setExecutor(this);
      Bukkit.getPluginManager().registerEvents(this, this);
   }

   private String color(String s) {
      return ChatColor.translateAlternateColorCodes('&', s);
   }

   public void openMenu(Player player) {
      int size = this.getConfig().getInt("settings.menu-size");
      Inventory inv = Bukkit.createInventory((InventoryHolder)null, size, this.color(this.getConfig().getString("settings.menu-title")));
      ItemStack border = new ItemStack(Material.valueOf(this.getConfig().getString("settings.border-item")));
      ItemStack filler = new ItemStack(Material.valueOf(this.getConfig().getString("settings.filler-item")));
      ItemMeta bMeta = border.getItemMeta();
      bMeta.setDisplayName(" ");
      border.setItemMeta(bMeta);
      ItemMeta fMeta = filler.getItemMeta();
      fMeta.setDisplayName(" ");
      filler.setItemMeta(fMeta);

      for(int i = 0; i < size; ++i) {
         if (i >= 9 && i <= 44 && i % 9 != 0 && (i + 1) % 9 != 0) {
            inv.setItem(i, filler);
         } else {
            inv.setItem(i, border);
         }
      }

      if (this.getConfig().getConfigurationSection("deliveries") != null) {
         Iterator var19 = this.getConfig().getConfigurationSection("deliveries").getKeys(false).iterator();

         while(var19.hasNext()) {
            String key = (String)var19.next();
            String path = "deliveries." + key + ".";
            ItemStack item = new ItemStack(Material.valueOf(this.getConfig().getString(path + "display-item")));
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(this.color(this.getConfig().getString(path + "name")));
            long timeLeft = this.getRemaining(player, key);
            String status = timeLeft <= 0L ? this.getConfig().getString("messages.ready-status") : this.getConfig().getString("messages.wait-status").replace("{TIME}", timeLeft / 1000L / 60L + " dk");
            List<String> lore = new ArrayList();
            Iterator var17 = this.getConfig().getStringList(path + "lore").iterator();

            while(var17.hasNext()) {
               String l = (String)var17.next();
               lore.add(this.color(l.replace("{STATUS}", status)));
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(this.getConfig().getInt(path + "slot"), item);
         }
      }

      player.openInventory(inv);
   }

   private long getRemaining(Player p, String k) {
      String var10000 = p.getUniqueId().toString();
      String id = var10000 + k;
      return !this.cooldowns.containsKey(id) ? 0L : (Long)this.cooldowns.get(id) + this.getConfig().getLong("deliveries." + k + ".cooldown") * 1000L - System.currentTimeMillis();
   }

   @EventHandler
   public void onClick(InventoryClickEvent e) {
      if (e.getView().getTitle().equals(this.color(this.getConfig().getString("settings.menu-title")))) {
         e.setCancelled(true);
         if (e.getCurrentItem() != null && e.getWhoClicked() instanceof Player) {
            Player p = (Player)e.getWhoClicked();
            if (this.getConfig().getConfigurationSection("deliveries") != null) {
               Iterator var3 = this.getConfig().getConfigurationSection("deliveries").getKeys(false).iterator();

               while(var3.hasNext()) {
                  String key = (String)var3.next();
                  if (e.getSlot() == this.getConfig().getInt("deliveries." + key + ".slot")) {
                     this.handleDelivery(p, key);
                     break;
                  }
               }

            }
         }
      }
   }

   private void handleDelivery(Player p, String k) {
      if (this.getRemaining(p, k) > 0L) {
         p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0F, 1.0F);
      } else {
         String path = "deliveries." + k + ".";
         Material m = Material.valueOf(this.getConfig().getString(path + "required-material"));
         int amt = this.getConfig().getInt(path + "required-amount");
         if (p.getInventory().containsAtLeast(new ItemStack(m), amt)) {
            p.getInventory().removeItem(new ItemStack[]{new ItemStack(m, amt)});
            this.cooldowns.put(p.getUniqueId().toString() + k, System.currentTimeMillis());
            Iterator var6 = this.getConfig().getStringList(path + "reward-commands").iterator();

            while(var6.hasNext()) {
               String cmd = (String)var6.next();
               Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{PLAYER}", p.getName()));
            }

            String var10002 = this.getConfig().getString("settings.prefix");
            p.sendMessage(this.color(var10002 + this.getConfig().getString("messages.success")));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
            p.closeInventory();
         } else {
            p.sendMessage(this.color(this.getConfig().getString("settings.prefix") + this.getConfig().getString("messages.no-items").replace("{NEED}", String.valueOf(amt)).replace("{ITEM}", m.name())));
         }

      }
   }

   public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
      if (a.length > 0 && a[0].equalsIgnoreCase("reload") && s.hasPermission("nyxx.admin")) {
         this.reloadConfig();
         String var10002 = this.getConfig().getString("settings.prefix");
         s.sendMessage(this.color(var10002 + this.getConfig().getString("messages.reloaded")));
         return true;
      } else {
         if (s instanceof Player) {
            this.openMenu((Player)s);
         }

         return true;
      }
   }
}
