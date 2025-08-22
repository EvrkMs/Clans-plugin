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
import com.cruiser.clans.orm.entity.ClanPlayerEntity;
import com.cruiser.clans.orm.entity.ClanRole;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Административные команды для управления кланами
 * /clanadmin <подкоманда> [аргументы]
 */
public class ClanAdminCommand implements CommandExecutor, TabCompleter {
    
    private final ClanPlugin plugin;
    
    public ClanAdminCommand(ClanPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("clan.admin")) {
            sender.sendMessage(Component.text("У вас нет прав для использования этой команды", NamedTextColor.RED));
            return true;
        }
        
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "help" -> sendHelp(sender);
            case "reload" -> handleReload(sender);
            case "disband" -> handleDisband(sender, args);
            case "setlevel" -> handleSetLevel(sender, args);
            case "addmember" -> handleAddMember(sender, args);
            case "removemember" -> handleRemoveMember(sender, args);
            case "stats" -> handleStats(sender);
            case "info" -> handleInfo(sender, args);
            case "list" -> handleList(sender);
            case "setmax" -> handleSetMaxMembers(sender, args);
            case "resetstats" -> handleResetStats(sender, args);
            default -> {
                sender.sendMessage(Component.text("Неизвестная команда. Используйте /clanadmin help", NamedTextColor.RED));
            }
        }
        
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text()
            .append(Component.text("========== ", NamedTextColor.GRAY))
            .append(Component.text("Админ команды кланов", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.text(" ==========", NamedTextColor.GRAY))
            .build());
        
        sender.sendMessage(Component.text("/clanadmin reload", NamedTextColor.YELLOW)
            .append(Component.text(" - Перезагрузить конфигурацию", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/clanadmin disband <клан>", NamedTextColor.YELLOW)
            .append(Component.text(" - Распустить клан", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/clanadmin setlevel <клан> <уровень>", NamedTextColor.YELLOW)
            .append(Component.text(" - Установить уровень клана", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/clanadmin addmember <игрок> <клан>", NamedTextColor.YELLOW)
            .append(Component.text(" - Добавить игрока в клан", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/clanadmin removemember <игрок>", NamedTextColor.YELLOW)
            .append(Component.text(" - Удалить игрока из клана", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/clanadmin setmax <клан> <лимит>", NamedTextColor.YELLOW)
            .append(Component.text(" - Установить лимит участников", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/clanadmin resetstats <клан/игрок>", NamedTextColor.YELLOW)
            .append(Component.text(" - Сбросить статистику", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/clanadmin stats", NamedTextColor.YELLOW)
            .append(Component.text(" - Статистика БД", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/clanadmin info <клан>", NamedTextColor.YELLOW)
            .append(Component.text(" - Детальная информация о клане", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/clanadmin list", NamedTextColor.YELLOW)
            .append(Component.text(" - Список всех кланов", NamedTextColor.GRAY)));
    }
    
    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        sender.sendMessage(Component.text("Конфигурация перезагружена", NamedTextColor.GREEN));
        
        // Обновляем отображение для всех игроков
        plugin.getDisplayService().initialize();
        sender.sendMessage(Component.text("Отображение кланов обновлено", NamedTextColor.GREEN));
    }
    
    private void handleDisband(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Использование: /clanadmin disband <клан>", NamedTextColor.RED));
            return;
        }
        
        String clanName = args[1];
        
        plugin.getData().findClanByName(clanName).thenCompose(optClan -> {
            if (optClan.isEmpty()) {
                plugin.getData().runSync(() -> {
                    sender.sendMessage(Component.text("Клан не найден", NamedTextColor.RED));
                });
                return null;
            }
            
            ClanEntity clan = optClan.get();
            
            // Уведомляем всех членов клана
            return plugin.getData().getClanMembers(clan.getId()).thenCompose(members -> {
                plugin.getData().runSync(() -> {
                    for (ClanPlayerEntity member : members) {
                        Player player = plugin.getServer().getPlayer(member.getUuidAsUUID());
                        if (player != null && player.isOnline()) {
                            player.sendMessage(Component.text("Клан был распущен администратором", NamedTextColor.RED));
                            plugin.getDisplayService().updatePlayerDisplay(player);
                        }
                    }
                });
                
                // Удаляем клан
                return plugin.getData().deleteClan(clan.getId()).thenApply(v -> {
                    plugin.getData().runSync(() -> {
                        sender.sendMessage(Component.text("Клан \"" + clan.getName() + "\" распущен", NamedTextColor.GREEN));
                        plugin.getDisplayService().removeClanTeam(clan.getId());
                    });
                    return null;
                });
            });
        });
    }
    
    private void handleSetLevel(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Использование: /clanadmin setlevel <клан> <уровень>", NamedTextColor.RED));
            return;
        }
        
        String clanName = args[1];
        int level;
        
        try {
            level = Integer.parseInt(args[2]);
            if (level < 1) {
                sender.sendMessage(Component.text("Уровень должен быть больше 0", NamedTextColor.RED));
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Неверный формат уровня", NamedTextColor.RED));
            return;
        }
        
        plugin.getData().findClanByName(clanName).thenCompose(optClan -> {
            if (optClan.isEmpty()) {
                plugin.getData().runSync(() -> {
                    sender.sendMessage(Component.text("Клан не найден", NamedTextColor.RED));
                });
                return null;
            }
            
            ClanEntity clan = optClan.get();
            clan.setClanLevel(level);
            clan.setClanExp(0); // Сбрасываем опыт при установке уровня
            
            return plugin.getData().updateClan(clan).thenApply(updated -> {
                plugin.getData().runSync(() -> {
                    sender.sendMessage(Component.text("Уровень клана \"" + clan.getName() + "\" установлен на " + level, NamedTextColor.GREEN));
                    
                    // Обновляем отображение для всех членов клана
                    plugin.getDisplayService().updateClanDisplay(clan.getId());
                });
                return null;
            });
        });
    }
    
    private void handleAddMember(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Использование: /clanadmin addmember <игрок> <клан>", NamedTextColor.RED));
            return;
        }
        
        String playerName = args[1];
        String clanName = args[2];
        
        plugin.getData().findPlayerByName(playerName).thenCompose(optPlayer -> {
            if (optPlayer.isEmpty()) {
                plugin.getData().runSync(() -> {
                    sender.sendMessage(Component.text("Игрок не найден", NamedTextColor.RED));
                });
                return null;
            }
            
            ClanPlayerEntity player = optPlayer.get();
            
            if (player.isInClan()) {
                plugin.getData().runSync(() -> {
                    sender.sendMessage(Component.text("Игрок уже состоит в клане", NamedTextColor.RED));
                });
                return null;
            }
            
            return plugin.getData().findClanByName(clanName).thenCompose(optClan -> {
                if (optClan.isEmpty()) {
                    plugin.getData().runSync(() -> {
                        sender.sendMessage(Component.text("Клан не найден", NamedTextColor.RED));
                    });
                    return null;
                }
                
                ClanEntity clan = optClan.get();
                
                player.setClan(clan);
                player.setRole(ClanRole.MEMBER);
                player.setJoinedAt(java.time.Instant.now());
                
                return plugin.getData().savePlayer(player).thenApply(saved -> {
                    plugin.getData().runSync(() -> {
                        sender.sendMessage(Component.text("Игрок " + playerName + " добавлен в клан \"" + clan.getName() + "\"", NamedTextColor.GREEN));
                        
                        // Обновляем отображение если игрок онлайн
                        Player bukkitPlayer = plugin.getServer().getPlayer(player.getUuidAsUUID());
                        if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                            plugin.getDisplayService().updatePlayerDisplay(bukkitPlayer);
                            bukkitPlayer.sendMessage(Component.text("Вы были добавлены в клан \"" + clan.getName() + "\" администратором", NamedTextColor.GREEN));
                        }
                    });
                    return null;
                });
            });
        });
    }
    
    private void handleRemoveMember(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Использование: /clanadmin removemember <игрок>", NamedTextColor.RED));
            return;
        }
        
        String playerName = args[1];
        
        plugin.getData().findPlayerByName(playerName).thenCompose(optPlayer -> {
            if (optPlayer.isEmpty()) {
                plugin.getData().runSync(() -> {
                    sender.sendMessage(Component.text("Игрок не найден", NamedTextColor.RED));
                });
                return null;
            }
            
            ClanPlayerEntity player = optPlayer.get();
            
            if (!player.isInClan()) {
                plugin.getData().runSync(() -> {
                    sender.sendMessage(Component.text("Игрок не состоит в клане", NamedTextColor.RED));
                });
                return null;
            }
            
            String clanName = player.getClan().getName();
            
            player.setClan(null);
            player.setRole(ClanRole.MEMBER);
            player.setJoinedAt(null);
            player.setClanContribution(0);
            
            return plugin.getData().savePlayer(player).thenApply(saved -> {
                plugin.getData().runSync(() -> {
                    sender.sendMessage(Component.text("Игрок " + playerName + " удален из клана \"" + clanName + "\"", NamedTextColor.GREEN));
                    
                    // Обновляем отображение если игрок онлайн
                    Player bukkitPlayer = plugin.getServer().getPlayer(player.getUuidAsUUID());
                    if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                        plugin.getDisplayService().updatePlayerDisplay(bukkitPlayer);
                        bukkitPlayer.sendMessage(Component.text("Вы были удалены из клана администратором", NamedTextColor.RED));
                    }
                });
                return null;
            });
        });
    }
    
    private void handleStats(CommandSender sender) {
        plugin.getData().getClansCount().thenCombine(
            plugin.getData().getPlayersInClansCount(),
            (clansCount, playersCount) -> {
                plugin.getData().runSync(() -> {
                    sender.sendMessage(Component.text("===== Статистика БД =====", NamedTextColor.GOLD, TextDecoration.BOLD));
                    sender.sendMessage(Component.text("Всего кланов: ", NamedTextColor.GRAY)
                        .append(Component.text(clansCount, NamedTextColor.WHITE)));
                    sender.sendMessage(Component.text("Игроков в кланах: ", NamedTextColor.GRAY)
                        .append(Component.text(playersCount, NamedTextColor.WHITE)));
                    sender.sendMessage(Component.text("Состояние пула: ", NamedTextColor.GRAY)
                        .append(Component.text(plugin.getOrmManager().getPoolStats(), NamedTextColor.AQUA)));
                    sender.sendMessage(Component.text("Режим чата клана: ", NamedTextColor.GRAY)
                        .append(Component.text(plugin.getChatService().getClanChatModeCount() + " игроков", NamedTextColor.YELLOW)));
                });
                return null;
            }
        );
    }
    
    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Использование: /clanadmin info <клан>", NamedTextColor.RED));
            return;
        }
        
        String clanName = args[1];
        
        plugin.getData().findClanByName(clanName).thenCompose(optClan -> {
            if (optClan.isEmpty()) {
                plugin.getData().runSync(() -> {
                    sender.sendMessage(Component.text("Клан не найден", NamedTextColor.RED));
                });
                return null;
            }
            
            ClanEntity clan = optClan.get();
            
            return plugin.getData().getClanMembers(clan.getId()).thenApply(members -> {
                plugin.getData().runSync(() -> {
                    sender.sendMessage(Component.text("===== Информация о клане =====", NamedTextColor.GOLD, TextDecoration.BOLD));
                    sender.sendMessage(Component.text("ID: ", NamedTextColor.GRAY)
                        .append(Component.text(clan.getId(), NamedTextColor.WHITE)));
                    sender.sendMessage(Component.text("Название: ", NamedTextColor.GRAY)
                        .append(Component.text(clan.getName(), NamedTextColor.YELLOW)));
                    sender.sendMessage(Component.text("Тег: ", NamedTextColor.GRAY)
                        .append(Component.text("[" + clan.getTag() + "]", NamedTextColor.AQUA)));
                    sender.sendMessage(Component.text("Уровень: ", NamedTextColor.GRAY)
                        .append(Component.text(clan.getClanLevel(), NamedTextColor.GREEN)));
                    sender.sendMessage(Component.text("Опыт: ", NamedTextColor.GRAY)
                        .append(Component.text(clan.getClanExp(), NamedTextColor.LIGHT_PURPLE)));
                    sender.sendMessage(Component.text("Участников: ", NamedTextColor.GRAY)
                        .append(Component.text(members.size() + "/" + clan.getMaxMembers(), NamedTextColor.WHITE)));
                    sender.sendMessage(Component.text("Создан: ", NamedTextColor.GRAY)
                        .append(Component.text(clan.getCreatedAt().toString(), NamedTextColor.WHITE)));
                    sender.sendMessage(Component.text("Лидер UUID: ", NamedTextColor.GRAY)
                        .append(Component.text(clan.getLeaderUuid(), NamedTextColor.WHITE)));
                    sender.sendMessage(Component.text("Убийств/Смертей: ", NamedTextColor.GRAY)
                        .append(Component.text(clan.getTotalKills() + "/" + clan.getTotalDeaths(), NamedTextColor.RED)));
                });
                return null;
            });
        });
    }
    
    private void handleList(CommandSender sender) {
        plugin.getData().query(session -> 
            session.createQuery("FROM ClanEntity c ORDER BY c.clanLevel DESC, c.totalKills DESC", ClanEntity.class)
                .list()
        ).thenAccept(clans -> {
            plugin.getData().runSync(() -> {
                sender.sendMessage(Component.text("===== Список всех кланов =====", NamedTextColor.GOLD, TextDecoration.BOLD));
                
                if (clans.isEmpty()) {
                    sender.sendMessage(Component.text("Нет созданных кланов", NamedTextColor.GRAY));
                    return;
                }
                
                for (ClanEntity clan : clans) {
                    sender.sendMessage(Component.text()
                        .append(Component.text("[" + clan.getTag() + "] ", NamedTextColor.GRAY))
                        .append(Component.text(clan.getName(), NamedTextColor.WHITE))
                        .append(Component.text(" - Ур." + clan.getClanLevel(), NamedTextColor.GREEN))
                        .append(Component.text(" - " + clan.getTotalKills() + " убийств", NamedTextColor.RED))
                        .build());
                }
            });
        });
    }
    
    private void handleSetMaxMembers(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Использование: /clanadmin setmax <клан> <лимит>", NamedTextColor.RED));
            return;
        }
        
        String clanName = args[1];
        int maxMembers;
        
        try {
            maxMembers = Integer.parseInt(args[2]);
            if (maxMembers < 1) {
                sender.sendMessage(Component.text("Лимит должен быть больше 0", NamedTextColor.RED));
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Неверный формат числа", NamedTextColor.RED));
            return;
        }
        
        plugin.getData().findClanByName(clanName).thenCompose(optClan -> {
            if (optClan.isEmpty()) {
                plugin.getData().runSync(() -> {
                    sender.sendMessage(Component.text("Клан не найден", NamedTextColor.RED));
                });
                return null;
            }
            
            ClanEntity clan = optClan.get();
            clan.setMaxMembers(maxMembers);
            
            return plugin.getData().updateClan(clan).thenApply(updated -> {
                plugin.getData().runSync(() -> {
                    sender.sendMessage(Component.text("Лимит участников клана \"" + clan.getName() + "\" установлен на " + maxMembers, NamedTextColor.GREEN));
                });
                return null;
            });
        });
    }
    
    private void handleResetStats(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Использование: /clanadmin resetstats <клан/игрок>", NamedTextColor.RED));
            return;
        }
        
        String target = args[1];
        
        // Сначала пробуем найти клан
        plugin.getData().findClanByName(target).thenAccept(optClan -> {
            if (optClan.isPresent()) {
                ClanEntity clan = optClan.get();
                clan.setTotalKills(0);
                clan.setTotalDeaths(0);
                clan.setClanExp(0);
                
                plugin.getData().updateClan(clan).thenRun(() -> {
                    plugin.getData().runSync(() -> {
                        sender.sendMessage(Component.text("Статистика клана \"" + clan.getName() + "\" сброшена", NamedTextColor.GREEN));
                    });
                });
            } else {
                // Если не клан, ищем игрока
                plugin.getData().findPlayerByName(target).thenAccept(optPlayer -> {
                    if (optPlayer.isPresent()) {
                        ClanPlayerEntity player = optPlayer.get();
                        player.setKills(0);
                        player.setDeaths(0);
                        player.setClanContribution(0);
                        
                        plugin.getData().savePlayer(player).thenRun(() -> {
                            plugin.getData().runSync(() -> {
                                sender.sendMessage(Component.text("Статистика игрока " + target + " сброшена", NamedTextColor.GREEN));
                            });
                        });
                    } else {
                        plugin.getData().runSync(() -> {
                            sender.sendMessage(Component.text("Клан или игрок не найден", NamedTextColor.RED));
                        });
                    }
                });
            }
        });
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("clan.admin")) {
            return new ArrayList<>();
        }
        
        if (args.length == 1) {
            return Arrays.asList("help", "reload", "disband", "setlevel", "addmember", 
                                "removemember", "stats", "info", "list", "setmax", "resetstats");
        }
        
        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "disband", "setlevel", "info", "setmax", "resetstats" -> {
                }
                case "addmember", "removemember" -> {
                    // Возвращаем список онлайн игроков
                    return plugin.getServer().getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .toList();
                }
            }
            // TODO: Возвращать список кланов
        }
        
        return new ArrayList<>();
    }
}