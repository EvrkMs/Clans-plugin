package com.cruiser.clans.listener;

import java.util.Locale;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.cruiser.clans.ClanPlugin;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Слушатель чата для перехвата сообщений клана
 */
public class ChatListener implements Listener {
    
    private final ClanPlugin plugin;
    
    public ChatListener(ClanPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Обработка сообщений чата (Paper API)
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        if (!plugin.getConfig().getBoolean("chat.enabled", true)) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Проверяем, находится ли игрок в режиме чата клана
        if (plugin.getChatService().isInClanChatMode(player)) {
            event.setCancelled(true);
            
            // Извлекаем текст сообщения
            String message = PlainTextComponentSerializer.plainText().serialize(event.message());
            
            // Отправляем в чат клана асинхронно
            plugin.getChatService().sendClanMessage(player, message).exceptionally(ex -> {
                plugin.getLogger().warning("Ошибка при отправке сообщения в чат клана: " + ex.getMessage());
                return false;
            });
        } else if (plugin.getConfig().getBoolean("display.show-tags-in-chat", true)) {
            // Добавляем тег клана к обычным сообщениям
            plugin.getData().findPlayerByUuid(player.getUniqueId()).thenAccept(optPlayer -> {
                if (optPlayer.isPresent() && optPlayer.get().isInClan()) {
                    var clanPlayer = optPlayer.get();
                    var clan = clanPlayer.getClan();
                    
                    // Модифицируем рендер сообщения
                    event.renderer((source, sourceDisplayName, message, viewer) -> {
                        return net.kyori.adventure.text.Component.text()
                            .append(net.kyori.adventure.text.Component.text("[" + clan.getTag() + "] ", getClanTagColor(clan.getClanLevel())))
                            .append(sourceDisplayName)
                            .append(net.kyori.adventure.text.Component.text(": ", net.kyori.adventure.text.format.NamedTextColor.WHITE))
                            .append(message)
                            .build();
                    });
                }
            });
        }
    }
    
    /**
     * Fallback для серверов без Paper API
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        // Этот метод будет вызван только если AsyncChatEvent недоступен
        if (!plugin.getConfig().getBoolean("chat.enabled", true)) {
            return;
        }
        
        Player player = event.getPlayer();
        
        if (plugin.getChatService().isInClanChatMode(player)) {
            event.setCancelled(true);
            
            String message = event.getMessage();
            
            // Отправляем в чат клана
            plugin.getChatService().sendClanMessage(player, message).exceptionally(ex -> {
                plugin.getLogger().warning("Ошибка при отправке сообщения в чат клана: " + ex.getMessage());
                return false;
            });
        }
    }
    
    /**
     * Убираем игрока из режима чата клана при выходе
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getChatService().removeFromChatMode(event.getPlayer());
    }
    
    /**
     * Получить цвет тега клана по уровню
     */
private TextColor getClanTagColor(int level) {
    var config = plugin.getConfig().getConfigurationSection("display.tag-colors");
    if (config == null) {
        return NamedTextColor.GRAY;
    }

    int closestLevel = 0;
    for (String key : config.getKeys(false)) {
        try {
            int configLevel = Integer.parseInt(key);
            if (configLevel <= level && configLevel > closestLevel) {
                closestLevel = configLevel;
            }
        } catch (NumberFormatException ignored) {}
    }

    String raw = config.getString(String.valueOf(closestLevel), "gray");

    // 1) Пытаемся распознать по имени (red, green, light_purple и т.п.)
    NamedTextColor named = NamedTextColor.NAMES.value(raw.toLowerCase(Locale.ROOT));
    if (named != null) return named;

    // 2) Пытаемся прочитать HEX (#RRGGBB)
    TextColor hex = TextColor.fromHexString(raw);
    if (hex != null) return hex;

    // 3) Фолбэк
    return NamedTextColor.GRAY;
}
}