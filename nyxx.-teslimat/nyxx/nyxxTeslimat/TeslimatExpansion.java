package nyxx.nyxxTeslimat;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TeslimatExpansion extends PlaceholderExpansion {

    private final NyxxTeslimat plugin;

    // Ana sınıfa bağlanmak için constructor
    public TeslimatExpansion(NyxxTeslimat plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "teslimat"; // Bu, %teslimat_...% ön ekini belirler
    }

    @Override
    public @NotNull String getAuthor() {
        return "Nyxx";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || !player.isOnline()) return "";
        Player p = player.getPlayer();

        // Örnek: %teslimat_sure_gorevadi% yazıldığında çalışır
        if (params.startsWith("sure_")) {
            String gorevAnahtari = params.replace("sure_", "");
            long kalanMs = plugin.getRemaining(p, gorevAnahtari);

            if (kalanMs <= 0) return "HAZIR";
            return (kalanMs / 1000) / 60 + " dk";
        }

        return null;
    }
}
