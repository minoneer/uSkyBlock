package us.talabrek.ultimateskyblock.event;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import us.talabrek.ultimateskyblock.player.UltimateHolder;
import us.talabrek.ultimateskyblock.uSkyBlock;

public class MenuEvents implements Listener {
    private final uSkyBlock plugin;

    public MenuEvents(uSkyBlock plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void guiClick(final InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof UltimateHolder holder) {
            if (holder.getMenuType() == UltimateHolder.MenuType.CONFIG) {
                plugin.getConfigMenu().onClick(event);
            } else {
                plugin.getMenu().onClick(event);
            }
        }
    }
}
