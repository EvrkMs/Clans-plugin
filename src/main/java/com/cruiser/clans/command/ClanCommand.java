package com.cruiser.clans.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import com.cruiser.clans.ClanPlugin;
import com.cruiser.clans.orm.entity.ClanEntity;
import com.cruiser.clans.orm.entity.ClanRole;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Основная команда для управления кланами
 * /clan <подкоманда> [аргументы]
 */
public class ClanCommand implements CommandExecutor, TabCompleter {
    
    private final ClanPlugin plugin;
    
    public ClanCommand(ClanPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Команда доступна только игрокам", NamedTextColor.RED));
            return true;
        }
        
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "help" -> sendHelp(player);
            case "create" -> handleCreate(player, args);
            case "disband" -> handleDisband(player);
            case "info" -> handleInfo(player, args);
            case "invite" -> handleInvite(player, args);
            case "accept" -> handleAccept(player);
            case "decline" -> handleDecline(player);
            case "leave" -> handleLeave(player);
            case "kick" -> handleKick(player, args);
            case "promote" -> handlePromote(player, args);
            case "demote" -> handleDemote(player, args);
            case "transfer" -> handleTransfer(player, args);
            case "chat", "c" -> handleChat(player, args);
            case "list" -> handleList(player);
            case "top" -> handleTop(player);
            case "region" -> handleRegion(player, args);
            default -> {
                player.sendMessage(Component.text("Неизвестная команда. Используйте /clan help", NamedTextColor.RED));
            }
        }
        
        return true;
    }
    
    private void sendHelp(Player player) {
        player.sendMessage(Component.text()
            .append(Component.text("========== ", NamedTextColor.GRAY))
            .append(Component.text("Команды кланов", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.text(" ==========", NamedTextColor.GRAY))
            .build());
        
        player.sendMessage(Component.text("/clan create <название> <тег>", NamedTextColor.YELLOW)
            .append(Component.text(" - Создать клан", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/clan disband", NamedTextColor.YELLOW)
            .append(Component.text(" - Распустить клан", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/clan info [клан]", NamedTextColor.YELLOW)
            .append(Component.text(" - Информация о клане", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/clan invite <игрок>", NamedTextColor.YELLOW)
            .append(Component.text(" - Пригласить игрока", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/clan accept", NamedTextColor.YELLOW)
            .append(Component.text(" - Принять приглашение", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/clan decline", NamedTextColor.YELLOW)
            .append(Component.text(" - Отклонить приглашение", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/clan leave", NamedTextColor.YELLOW)
            .append(Component.text(" - Покинуть клан", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/clan kick <игрок>", NamedTextColor.YELLOW)
            .append(Component.text(" - Выгнать игрока", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/clan promote <игрок>", NamedTextColor.YELLOW)
            .append(Component.text(" - Повысить игрока", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/clan demote <игрок>", NamedTextColor.YELLOW)
            .append(Component.text(" - Понизить игрока", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/clan transfer <игрок>", NamedTextColor.YELLOW)
            .append(Component.text(" - Передать лидерство", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/clan chat [сообщение]", NamedTextColor.YELLOW)
            .append(Component.text(" - Чат клана", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/clan list", NamedTextColor.YELLOW)
            .append(Component.text(" - Список кланов", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/clan top", NamedTextColor.YELLOW)
            .append(Component.text(" - Топ кланов", NamedTextColor.GRAY)));

        if (plugin.getConfig().getBoolean("regions.enabled", true)) {
            player.sendMessage(Component.text("/clan region <подкоманда>", NamedTextColor.YELLOW)
                .append(Component.text(" - Управление регионом", NamedTextColor.GRAY)));
        }
    }
    
    private void handleCreate(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Использование: /clan create <название> <тег>", NamedTextColor.RED));
            return;
        }
        
        String name = args[1];
        String tag = args[2];
        
        plugin.getClanService().createClan(player, name, tag).thenAccept(result -> {
            plugin.getData().runSync(() -> {
                if (!result.isSuccess()) {
                    player.sendMessage(Component.text(result.getError(), NamedTextColor.RED));
                }
                // Успешное сообщение отправляется в самом сервисе
            });
        });
    }
    
    private void handleDisband(Player player) {
        plugin.getClanService().disbandClan(player).exceptionally(ex -> {
            plugin.getData().runSync(() ->
                player.sendMessage(Component.text("Ошибка при распуске клана", NamedTextColor.RED))
            );
            plugin.getLogger().warning("Ошибка disband: " + ex.getMessage());
            return false;
        });
    }
    
    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            // Информация о своем клане
            plugin.getData().findPlayerByUuid(player.getUniqueId()).thenAccept(optPlayer -> {
                if (optPlayer.isEmpty() || !optPlayer.get().isInClan()) {
                    player.sendMessage(Component.text("Вы не состоите в клане", NamedTextColor.RED));
                    return;
                }
                
                var clan = optPlayer.get().getClan();
                plugin.getData().runSync(() -> {
                    sendClanInfo(player, clan);
                });
            });
        } else {
            // Информация о другом клане
            String clanName = args[1];
            plugin.getData().findClanByName(clanName).thenAccept(optClan -> {
                plugin.getData().runSync(() -> {
                    if (optClan.isEmpty()) {
                        player.sendMessage(Component.text("Клан не найден", NamedTextColor.RED));
                        return;
                    }
                    sendClanInfo(player, optClan.get());
                });
            });
        }
    }
    
    private void sendClanInfo(Player player, com.cruiser.clans.orm.entity.ClanEntity clan) {
        player.sendMessage(Component.text()
            .append(Component.text("===== Клан: ", NamedTextColor.GRAY))
            .append(Component.text(clan.getName(), NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.text(" =====", NamedTextColor.GRAY))
            .build());
        
        player.sendMessage(Component.text("Тег: ", NamedTextColor.GRAY)
            .append(Component.text("[" + clan.getTag() + "]", NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("Уровень: ", NamedTextColor.GRAY)
            .append(Component.text(clan.getClanLevel(), NamedTextColor.GREEN)));
        player.sendMessage(Component.text("Опыт: ", NamedTextColor.GRAY)
            .append(Component.text(clan.getClanExp(), NamedTextColor.AQUA)));
        player.sendMessage(Component.text("Убийств: ", NamedTextColor.GRAY)
            .append(Component.text(clan.getTotalKills(), NamedTextColor.RED)));
        player.sendMessage(Component.text("Смертей: ", NamedTextColor.GRAY)
            .append(Component.text(clan.getTotalDeaths(), NamedTextColor.DARK_RED)));
        
        // Загружаем количество участников
        plugin.getData().getClanMembers(clan.getId()).thenAccept(members -> {
            plugin.getData().runSync(() -> {
                player.sendMessage(Component.text("Участников: ", NamedTextColor.GRAY)
                    .append(Component.text(members.size() + "/" + clan.getMaxMembers(), NamedTextColor.WHITE)));
            });
        });
    }
    
    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Использование: /clan invite <игрок>", NamedTextColor.RED));
            return;
        }
        
        String targetName = args[1];
        plugin.getClanService().invitePlayer(player, targetName).exceptionally(ex -> {
            plugin.getData().runSync(() ->
                player.sendMessage(Component.text("Ошибка при приглашении игрока", NamedTextColor.RED))
            );
            plugin.getLogger().warning("Ошибка invite: " + ex.getMessage());
            return false;
        });
    }
    
    private void handleAccept(Player player) {
        plugin.getClanService().acceptInvite(player).exceptionally(ex -> {
            plugin.getData().runSync(() ->
                player.sendMessage(Component.text("Ошибка при принятии приглашения", NamedTextColor.RED))
            );
            plugin.getLogger().warning("Ошибка accept: " + ex.getMessage());
            return false;
        });
    }
    
    private void handleDecline(Player player) {
        plugin.getData().findPlayerByUuid(player.getUniqueId()).thenAccept(optPlayer -> {
            if (optPlayer.isPresent() && optPlayer.get().hasPendingInvite()) {
                optPlayer.get().clearInvite();
                plugin.getData().savePlayer(optPlayer.get()).thenRun(() -> {
                    plugin.getData().runSync(() -> {
                        player.sendMessage(Component.text("Приглашение отклонено", NamedTextColor.YELLOW));
                    });
                });
            } else {
                plugin.getData().runSync(() -> {
                    player.sendMessage(Component.text("У вас нет активных приглашений", NamedTextColor.RED));
                });
            }
        });
    }
    
    private void handleLeave(Player player) {
        plugin.getMemberService().leaveClan(player);
    }
    
    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Использование: /clan kick <игрок>", NamedTextColor.RED));
            return;
        }
        String targetName = args[1];
        plugin.getMemberService().kickMember(player, targetName);
    }
    
    private void handleList(Player player) {
        plugin.getData().query(session -> 
            session.createQuery("FROM ClanEntity c ORDER BY c.clanLevel DESC, c.totalKills DESC", ClanEntity.class)
                .setMaxResults(20)
                .list()
        ).thenAccept(clans -> {
            plugin.getData().runSync(() -> {
                player.sendMessage(Component.text("===== Список кланов =====", NamedTextColor.GOLD, TextDecoration.BOLD));
                
                if (clans.isEmpty()) {
                    player.sendMessage(Component.text("Нет созданных кланов", NamedTextColor.GRAY));
                    return;
                }
                
                for (ClanEntity clan : clans) {
                    player.sendMessage(Component.text()
                        .append(Component.text("[" + clan.getTag() + "] ", NamedTextColor.GRAY))
                        .append(Component.text(clan.getName(), NamedTextColor.WHITE))
                        .append(Component.text(" - Уровень " + clan.getClanLevel(), NamedTextColor.GREEN))
                        .build());
                }
            });
        });
    }
    
    private void handlePromote(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Использование: /clan promote <игрок>", NamedTextColor.RED));
            return;
        }
        String targetName = args[1];
        plugin.getMemberService().promoteMember(player, targetName);
    }
    
    private void handleDemote(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Использование: /clan demote <игрок>", NamedTextColor.RED));
            return;
        }
        String targetName = args[1];
        plugin.getMemberService().demoteMember(player, targetName);
    }
    
    private void handleTransfer(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Использование: /clan transfer <игрок>", NamedTextColor.RED));
            return;
        }
        String targetName = args[1];
        plugin.getMemberService().transferLeadership(player, targetName);
    }
    
    private void handleChat(Player player, String[] args) {
        if (args.length < 2) {
            // Переключить режим чата
            plugin.getChatService().toggleClanChatMode(player).exceptionally(ex -> {
                plugin.getData().runSync(() ->
                    player.sendMessage(Component.text("Ошибка переключения чата", NamedTextColor.RED))
                );
                plugin.getLogger().warning("Ошибка toggle chat: " + ex.getMessage());
                return false;
            });
        } else {
            // Отправить сообщение в чат клана
            String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            plugin.getChatService().sendClanMessage(player, message).exceptionally(ex -> {
                plugin.getData().runSync(() ->
                    player.sendMessage(Component.text("Ошибка отправки сообщения", NamedTextColor.RED))
                );
                plugin.getLogger().warning("Ошибка clan chat: " + ex.getMessage());
                return false;
            });
        }
    }
    
    private void handleTop(Player player) {
        plugin.getData().getTopClansByKills(10).thenAccept(clans -> {
            plugin.getData().runSync(() -> {
                player.sendMessage(Component.text("===== Топ 10 кланов =====", NamedTextColor.GOLD, TextDecoration.BOLD));
                
                int position = 1;
                for (var clan : clans) {
                    player.sendMessage(Component.text()
                        .append(Component.text(position + ". ", NamedTextColor.YELLOW))
                        .append(Component.text("[" + clan.getTag() + "] ", NamedTextColor.GRAY))
                        .append(Component.text(clan.getName(), NamedTextColor.WHITE))
                        .append(Component.text(" - ", NamedTextColor.GRAY))
                        .append(Component.text(clan.getTotalKills() + " убийств", NamedTextColor.RED))
                        .build());
                    position++;
                }
            });
        });
    }

    private void handleRegion(Player player, String[] args) {
        if (!plugin.getConfig().getBoolean("regions.enabled", true)) {
            player.sendMessage(Component.text("Клановые регионы отключены", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sendRegionHelp(player);
            return;
        }

        String sub = args[1].toLowerCase();
        switch (sub) {
            case "help" -> sendRegionHelp(player);
            case "marker" -> handleRegionMarker(player, args);
            case "info" -> handleRegionInfo(player);
            case "remove" -> handleRegionRemove(player);
            default -> sendRegionHelp(player);
        }
    }

    private void sendRegionHelp(Player player) {
        player.sendMessage(Component.text()
            .append(Component.text("===== ", NamedTextColor.GRAY))
            .append(Component.text("Команды регионов", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.text(" =====", NamedTextColor.GRAY))
            .build());

        player.sendMessage(Component.text("/clan region marker <тип>", NamedTextColor.YELLOW)
            .append(Component.text(" - Получить маркер", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/clan region info", NamedTextColor.YELLOW)
            .append(Component.text(" - Информация о регионе", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/clan region remove", NamedTextColor.YELLOW)
            .append(Component.text(" - Удалить регион", NamedTextColor.GRAY)));

        player.sendMessage(Component.text("Доступные маркеры:", NamedTextColor.AQUA));
        var markerSection = plugin.getConfig().getConfigurationSection("regions.marker-blocks");
        if (markerSection != null) {
            for (String markerType : markerSection.getKeys(false)) {
                String displayName = markerSection.getString(markerType + ".display-name", markerType);
                int minLevel = markerSection.getInt(markerType + ".min-clan-level", 1);
                int maxRadius = markerSection.getInt(markerType + ".max-radius", 25);

                player.sendMessage(Component.text("  " + markerType, NamedTextColor.WHITE)
                    .append(Component.text(" - " + displayName, NamedTextColor.GRAY))
                    .append(Component.text(" (Уровень " + minLevel + ", " + maxRadius + " блоков)", NamedTextColor.DARK_GRAY)));
            }
        }
    }

    private void handleRegionMarker(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Использование: /clan region marker <тип>", NamedTextColor.RED));
            return;
        }

        String markerType = args[2].toUpperCase();
        plugin.getRegionService().giveRegionMarker(player, markerType);
    }

    private void handleRegionInfo(Player player) {
        plugin.getData().findPlayerByUuid(player.getUniqueId()).thenAccept(optPlayer -> {
            if (optPlayer.isEmpty() || !optPlayer.get().isInClan()) {
                plugin.getData().runSync(() ->
                    player.sendMessage(Component.text("Вы не состоите в клане", NamedTextColor.RED))
                );
                return;
            }

            plugin.getData().findClanRegion(optPlayer.get().getClan().getId()).thenAccept(optRegion -> {
                plugin.getData().runSync(() -> {
                    if (optRegion.isEmpty()) {
                        player.sendMessage(Component.text("У вашего клана нет региона", NamedTextColor.RED));
                        return;
                    }

                    var region = optRegion.get();
                    player.sendMessage(Component.text("===== Информация о регионе =====", NamedTextColor.GOLD, TextDecoration.BOLD));
                    player.sendMessage(Component.text("Мир: ", NamedTextColor.GRAY)
                        .append(Component.text(region.getWorldName(), NamedTextColor.WHITE)));
                    player.sendMessage(Component.text("Тип маркера: ", NamedTextColor.GRAY)
                        .append(Component.text(region.getMarkerType(), NamedTextColor.YELLOW)));
                    player.sendMessage(Component.text("Размер: ", NamedTextColor.GRAY)
                        .append(Component.text(region.getRegionSize() + " блоков²", NamedTextColor.GREEN)));
                    player.sendMessage(Component.text("Центр: ", NamedTextColor.GRAY)
                        .append(Component.text(region.getMarker1X() + ", " + region.getMarker1Y() + ", " + region.getMarker1Z(), NamedTextColor.WHITE)));
                    if (region.hasSecondMarker()) {
                        player.sendMessage(Component.text("Второй маркер: ", NamedTextColor.GRAY)
                            .append(Component.text(region.getMarker2X() + ", " + region.getMarker2Y() + ", " + region.getMarker2Z(), NamedTextColor.WHITE)));
                    }
                });
            });
        });
    }

    private void handleRegionRemove(Player player) {
        plugin.getData().findPlayerByUuid(player.getUniqueId()).thenAccept(optPlayer -> {
            if (optPlayer.isEmpty() || !optPlayer.get().isInClan()) {
                plugin.getData().runSync(() ->
                    player.sendMessage(Component.text("Вы не состоите в клане", NamedTextColor.RED))
                );
                return;
            }

            if (optPlayer.get().getRole() != ClanRole.LEADER) {
                plugin.getData().runSync(() ->
                    player.sendMessage(Component.text("Только лидер может удалять регион", NamedTextColor.RED))
                );
                return;
            }

            plugin.getData().findClanRegion(optPlayer.get().getClan().getId()).thenAccept(optRegion -> {
                if (optRegion.isEmpty()) {
                    plugin.getData().runSync(() ->
                        player.sendMessage(Component.text("У вашего клана нет региона", NamedTextColor.RED))
                    );
                    return;
                }

                var region = optRegion.get();
                plugin.getData().deleteRegion(region.getId()).thenRun(() -> {
                    plugin.getData().runSync(() ->
                        player.sendMessage(Component.text("Регион клана удалён", NamedTextColor.GREEN))
                    );
                });
            });
        });
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> commands = new ArrayList<>(Arrays.asList(
                "help", "create", "disband", "info", "invite",
                "accept", "decline", "leave", "kick",
                "promote", "demote", "transfer", "chat", "c",
                "list", "top"
            ));

            if (plugin.getConfig().getBoolean("regions.enabled", true)) {
                commands.add("region");
            }

            return commands;
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "region":
                    return Arrays.asList("help", "marker", "info", "remove");
                case "invite", "kick", "promote", "demote", "transfer":
                    // Возвращаем список онлайн игроков
                    return plugin.getServer().getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
                case "info":
                    try {
                        return plugin.getData().query(session ->
                            session.createQuery("SELECT c.name FROM ClanEntity c", String.class).list()
                        ).join().stream()
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .toList();
                    } catch (Exception ignored) {
                        return new ArrayList<>();
                    }
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("region") && args[1].equalsIgnoreCase("marker")) {
            var markerSection = plugin.getConfig().getConfigurationSection("regions.marker-blocks");
            if (markerSection != null) {
                return markerSection.getKeys(false).stream()
                    .filter(key -> key.toLowerCase().startsWith(args[2].toLowerCase()))
                    .toList();
            }
        }

        return new ArrayList<>();
    }
}