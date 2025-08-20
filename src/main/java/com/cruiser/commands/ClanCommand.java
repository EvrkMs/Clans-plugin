package com.cruiser.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import com.cruiser.ClanPlugin;
import com.cruiser.models.Clan;
import com.cruiser.models.ClanMember;

public class ClanCommand implements CommandExecutor, TabCompleter {
    private final ClanPlugin plugin;
    
    // Список подкоманд для tab-completion
    private final List<String> subCommands = Arrays.asList(
        "create", "disband", "invite", "accept", "deny", "leave",
        "kick", "promote", "demote", "info", "list", "top",
        "sethome", "home", "deposit", "withdraw", "ally", "unally",
        "chat", "help"
    );
    
    // Подкоманды, доступные только членам клана
    private final List<String> memberCommands = Arrays.asList(
        "leave", "info", "home", "chat"
    );
    
    // Подкоманды, доступные только офицерам и лидеру
    private final List<String> officerCommands = Arrays.asList(
        "invite", "kick", "sethome"
    );
    
    // Подкоманды, доступные только лидеру
    private final List<String> leaderCommands = Arrays.asList(
        "disband", "promote", "demote", "deposit", "withdraw", "ally", "unally"
    );
    
    public ClanCommand(ClanPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Эта команда доступна только игрокам!");
            return true;
        }
        
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        ClanMember member = plugin.getPlayerDataManager().getOrCreatePlayer(
            player.getUniqueId(), player.getName()
        );
        
        switch (subCommand) {
            case "create" -> handleCreate(player, member, args);
            case "disband" -> handleDisband(player, member);
            case "invite" -> handleInvite(player, member, args);
            case "accept" -> handleAccept(player, member, args);
            case "deny" -> handleDeny(player, member, args);
            case "leave" -> handleLeave(player, member);
            case "kick" -> handleKick(player, member, args);
            case "promote" -> handlePromote(player, member, args);
            case "demote" -> handleDemote(player, member, args);
            case "info" -> handleInfo(player, member, args);
            case "list" -> handleList(player);
            case "top" -> handleTop(player);
            case "sethome" -> handleSetHome(player, member);
            case "home" -> handleHome(player, member);
            case "deposit" -> handleDeposit(player, member, args);
            case "withdraw" -> handleWithdraw(player, member, args);
            case "ally" -> handleAlly(player, member, args);
            case "unally" -> handleUnally(player, member, args);
            case "chat" -> handleChat(player, member, args);
            case "help" -> sendHelp(player);
            default -> player.sendMessage(ChatColor.RED + "Неизвестная команда. Используйте /clan help");
        }
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }
        
        List<String> completions = new ArrayList<>();
        ClanMember member = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        
        if (args.length == 1) {
            // Первый аргумент - подкоманда
            String input = args[0].toLowerCase();
            
            if (member == null || !member.hasClan()) {
                // Игрок не в клане - показываем только доступные команды
                completions.addAll(Arrays.asList("create", "accept", "deny", "list", "top", "help"));
            } else {
                // Игрок в клане - показываем команды в зависимости от роли
                completions.addAll(Arrays.asList("info", "list", "top", "help"));
                completions.addAll(memberCommands);
                
                if (member.getRole().getPower() >= Clan.ClanRole.OFFICER.getPower()) {
                    completions.addAll(officerCommands);
                }
                
                if (member.isLeader()) {
                    completions.addAll(leaderCommands);
                }
            }
            
            // Фильтруем по введенному тексту
            return completions.stream()
                .filter(s -> s.startsWith(input))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        }
        
        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            String input = args[1].toLowerCase();
            
            switch (subCommand) {
                case "invite", "kick", "promote", "demote" -> {
                    // Предлагаем онлайн игроков
                    if (member != null && member.hasClan()) {
                        Clan clan = plugin.getClanManager().getClanById(member.getClanId());
                        if (clan != null) {
                            if (subCommand.equals("invite")) {
                                // Для приглашения - игроки не в клане
                                return Bukkit.getOnlinePlayers().stream()
                                    .filter(p -> !clan.isMember(p.getUniqueId()))
                                    .map(Player::getName)
                                    .filter(name -> name.toLowerCase().startsWith(input))
                                    .collect(Collectors.toList());
                            } else {
                                // Для остальных - члены клана
                                return clan.getMembers().stream()
                                    .filter(m -> !m.getUuid().equals(player.getUniqueId()))
                                    .map(ClanMember::getName)
                                    .filter(name -> name.toLowerCase().startsWith(input))
                                    .collect(Collectors.toList());
                            }
                        }
                    }
                }
                case "accept", "deny" -> {
                    // Предлагаем названия кланов, от которых есть приглашения
                    return plugin.getClanManager().getAllClans().stream()
                        .filter(clan -> clan.hasInvite(player.getUniqueId()))
                        .map(Clan::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .collect(Collectors.toList());
                }
                case "info" -> {
                    // Предлагаем названия всех кланов
                    return plugin.getClanManager().getAllClanNames().stream()
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .collect(Collectors.toList());
                }
                case "ally", "unally" -> {
                    // Предлагаем названия других кланов
                    if (member != null && member.hasClan()) {
                        Clan playerClan = plugin.getClanManager().getClanById(member.getClanId());
                        return plugin.getClanManager().getAllClans().stream()
                            .filter(clan -> clan.getId() != playerClan.getId())
                            .map(Clan::getName)
                            .filter(name -> name.toLowerCase().startsWith(input))
                            .collect(Collectors.toList());
                    }
                }
                case "deposit", "withdraw" -> {
                    // Предлагаем примеры сумм
                    return Arrays.asList("100", "500", "1000", "5000", "10000").stream()
                        .filter(s -> s.startsWith(input))
                        .collect(Collectors.toList());
                }
            }
        }
        
        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            // Третий аргумент команды create - тег клана
            return Collections.singletonList("[тег]");
        }
        
        return completions;
    }
    
    // Обработчики команд
    private void handleCreate(Player player, ClanMember member, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Использование: /clan create <название> <тег>");
            return;
        }
        
        if (member.hasClan()) {
            player.sendMessage(ChatColor.RED + "Вы уже состоите в клане!");
            return;
        }
        
        String name = args[1];
        String tag = args[2];
        
        if (name.length() > 16 || name.length() < 3) {
            player.sendMessage(ChatColor.RED + "Название клана должно быть от 3 до 16 символов!");
            return;
        }
        
        if (tag.length() > 5 || tag.length() < 2) {
            player.sendMessage(ChatColor.RED + "Тег клана должен быть от 2 до 5 символов!");
            return;
        }
        
        Clan clan = plugin.getClanManager().createClan(name, tag, player.getUniqueId(), player.getName());
        
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "Клан с таким названием или тегом уже существует!");
            return;
        }
        
        member.setClanId(clan.getId());
        member.setRole(Clan.ClanRole.LEADER);
        
        player.sendMessage(ChatColor.GREEN + "Клан " + ChatColor.GOLD + name + 
            ChatColor.GREEN + " [" + tag + "] успешно создан!");
    }
    
    private void handleDisband(Player player, ClanMember member) {
        if (!member.hasClan()) {
            player.sendMessage(ChatColor.RED + "Вы не состоите в клане!");
            return;
        }
        
        if (!member.isLeader()) {
            player.sendMessage(ChatColor.RED + "Только лидер может распустить клан!");
            return;
        }
        
        Clan clan = plugin.getClanManager().getClanById(member.getClanId());
        
        // Уведомляем всех членов
        for (UUID memberUuid : clan.getOnlineMembers()) {
            Player p = Bukkit.getPlayer(memberUuid);
            if (p != null) {
                p.sendMessage(ChatColor.RED + "Клан " + clan.getName() + " был распущен лидером!");
            }
        }
        
        // Удаляем клан из всех игроков
        for (ClanMember m : clan.getMembers()) {
            m.setClanId(0);
            m.setRole(Clan.ClanRole.MEMBER);
        }
        
        plugin.getClanManager().disbandClan(clan);
        player.sendMessage(ChatColor.GREEN + "Клан успешно распущен!");
    }
    
    private void handleInvite(Player player, ClanMember member, String[] args) {
        if (!member.hasClan()) {
            player.sendMessage(ChatColor.RED + "Вы не состоите в клане!");
            return;
        }
        
        if (member.getRole().getPower() < Clan.ClanRole.OFFICER.getPower()) {
            player.sendMessage(ChatColor.RED + "Только офицеры и лидер могут приглашать игроков!");
            return;
        }
        
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Использование: /clan invite <игрок>");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Игрок не найден!");
            return;
        }
        
        Clan clan = plugin.getClanManager().getClanById(member.getClanId());
        
        if (clan.isMember(target.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Этот игрок уже в вашем клане!");
            return;
        }
        
        if (clan.isFull()) {
            player.sendMessage(ChatColor.RED + "Клан заполнен! Максимум: " + clan.getMaxMembers());
            return;
        }
        
        clan.addInvite(target.getUniqueId());
        
        target.sendMessage(ChatColor.GREEN + "Вас пригласили в клан " + ChatColor.GOLD + 
            clan.getName() + ChatColor.GREEN + "!");
        target.sendMessage(ChatColor.YELLOW + "Используйте /clan accept " + clan.getName() + 
            " чтобы принять или /clan deny " + clan.getName() + " чтобы отклонить");
        
        player.sendMessage(ChatColor.GREEN + "Приглашение отправлено игроку " + target.getName());
    }
    
    private void handleAccept(Player player, ClanMember member, String[] args) {
        if (member.hasClan()) {
            player.sendMessage(ChatColor.RED + "Вы уже состоите в клане!");
            return;
        }
        
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Использование: /clan accept <название клана>");
            return;
        }
        
        Clan clan = plugin.getClanManager().getClanByName(args[1]);
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "Клан не найден!");
            return;
        }
        
        if (!clan.hasInvite(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "У вас нет приглашения от этого клана!");
            return;
        }
        
        if (clan.isFull()) {
            player.sendMessage(ChatColor.RED + "Клан заполнен!");
            return;
        }
        
        clan.removeInvite(player.getUniqueId());
        clan.addMember(member);
        member.setClanId(clan.getId());
        member.setRole(Clan.ClanRole.MEMBER);
        
        // Уведомляем членов клана
        for (UUID memberUuid : clan.getOnlineMembers()) {
            Player p = Bukkit.getPlayer(memberUuid);
            if (p != null) {
                p.sendMessage(ChatColor.GREEN + player.getName() + " присоединился к клану!");
            }
        }
    }
    
    private void handleDeny(Player player, ClanMember member, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Использование: /clan deny <название клана>");
            return;
        }
        
        Clan clan = plugin.getClanManager().getClanByName(args[1]);
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "Клан не найден!");
            return;
        }
        
        if (!clan.hasInvite(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "У вас нет приглашения от этого клана!");
            return;
        }
        
        clan.removeInvite(player.getUniqueId());
        player.sendMessage(ChatColor.YELLOW + "Вы отклонили приглашение от клана " + clan.getName());
    }
    
    private void handleLeave(Player player, ClanMember member) {
        if (!member.hasClan()) {
            player.sendMessage(ChatColor.RED + "Вы не состоите в клане!");
            return;
        }
        
        if (member.isLeader()) {
            player.sendMessage(ChatColor.RED + "Лидер не может покинуть клан! Передайте лидерство или распустите клан.");
            return;
        }
        
        Clan clan = plugin.getClanManager().getClanById(member.getClanId());
        clan.removeMember(player.getUniqueId());
        
        plugin.getPlayerDataManager().leaveClan(player.getUniqueId());
        
        player.sendMessage(ChatColor.YELLOW + "Вы покинули клан " + clan.getName());
        
        // Уведомляем членов клана
        for (UUID memberUuid : clan.getOnlineMembers()) {
            Player p = Bukkit.getPlayer(memberUuid);
            if (p != null) {
                p.sendMessage(ChatColor.YELLOW + player.getName() + " покинул клан!");
            }
        }
    }
    
    // Остальные методы handleKick, handlePromote и т.д. следуют той же логике
    // Из-за ограничения размера я покажу только структуру
    
    private void handleKick(Player player, ClanMember member, String[] args) {
        if (!member.hasClan()) {
            player.sendMessage(ChatColor.RED + "Вы не состоите в клане!");
            return;
        }
        
        if (member.getRole().getPower() < Clan.ClanRole.OFFICER.getPower()) {
            player.sendMessage(ChatColor.RED + "Только офицеры и лидер могут исключать игроков!");
            return;
        }
        
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Использование: /clan kick <игрок>");
            return;
        }
        
        Clan clan = plugin.getClanManager().getClanById(member.getClanId());
        String targetName = args[1];
        
        ClanMember targetMember = null;
        for (ClanMember m : clan.getMembers()) {
            if (m.getName().equalsIgnoreCase(targetName)) {
                targetMember = m;
                break;
            }
        }
        
        if (targetMember == null) {
            player.sendMessage(ChatColor.RED + "Игрок не найден в клане!");
            return;
        }
        
        if (targetMember.getUuid().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Вы не можете исключить себя!");
            return;
        }
        
        if (targetMember.isLeader()) {
            player.sendMessage(ChatColor.RED + "Нельзя исключить лидера клана!");
            return;
        }
        
        if (targetMember.getRole().getPower() >= member.getRole().getPower() && !member.isLeader()) {
            player.sendMessage(ChatColor.RED + "Вы не можете исключить игрока с таким же или выше рангом!");
            return;
        }
        
        // Удаляем из клана
        clan.removeMember(targetMember.getUuid());
        plugin.getPlayerDataManager().leaveClan(targetMember.getUuid());
        
        // Уведомления
        Player targetPlayer = Bukkit.getPlayer(targetMember.getUuid());
        if (targetPlayer != null) {
            targetPlayer.sendMessage(ChatColor.RED + "Вы были исключены из клана " + clan.getName());
        }
        
        for (UUID memberUuid : clan.getOnlineMembers()) {
            Player p = Bukkit.getPlayer(memberUuid);
            if (p != null) {
                p.sendMessage(ChatColor.YELLOW + targetMember.getName() + " был исключен из клана игроком " + player.getName());
            }
        }
    }
    
    private void handlePromote(Player player, ClanMember member, String[] args) {
        if (!member.hasClan()) {
            player.sendMessage(ChatColor.RED + "Вы не состоите в клане!");
            return;
        }
        
        if (!member.isLeader()) {
            player.sendMessage(ChatColor.RED + "Только лидер может повышать игроков!");
            return;
        }
        
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Использование: /clan promote <игрок>");
            return;
        }
        
        Clan clan = plugin.getClanManager().getClanById(member.getClanId());
        String targetName = args[1];
        
        ClanMember targetMember = null;
        for (ClanMember m : clan.getMembers()) {
            if (m.getName().equalsIgnoreCase(targetName)) {
                targetMember = m;
                break;
            }
        }
        
        if (targetMember == null) {
            player.sendMessage(ChatColor.RED + "Игрок не найден в клане!");
            return;
        }
        
        if (targetMember.getUuid().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Вы не можете повысить себя!");
            return;
        }
        
        if (targetMember.getRole() == Clan.ClanRole.OFFICER) {
            // Передача лидерства
            targetMember.setRole(Clan.ClanRole.LEADER);
            member.setRole(Clan.ClanRole.OFFICER);
            clan.setLeaderUuid(targetMember.getUuid());
            
            player.sendMessage(ChatColor.GREEN + "Вы передали лидерство игроку " + targetMember.getName());
            
            Player targetPlayer = Bukkit.getPlayer(targetMember.getUuid());
            if (targetPlayer != null) {
                targetPlayer.sendMessage(ChatColor.GOLD + "Вы стали новым лидером клана!");
            }
        } else if (targetMember.getRole() == Clan.ClanRole.MEMBER) {
            // Повышение до офицера
            targetMember.setRole(Clan.ClanRole.OFFICER);
            
            player.sendMessage(ChatColor.GREEN + "Игрок " + targetMember.getName() + " повышен до офицера!");
            
            Player targetPlayer = Bukkit.getPlayer(targetMember.getUuid());
            if (targetPlayer != null) {
                targetPlayer.sendMessage(ChatColor.GREEN + "Вы были повышены до офицера!");
            }
        } else {
            player.sendMessage(ChatColor.RED + "Этот игрок уже имеет максимальный ранг!");
            return;
        }
        
        // Уведомляем клан
        for (UUID memberUuid : clan.getOnlineMembers()) {
            Player p = Bukkit.getPlayer(memberUuid);
            if (p != null && !p.equals(player) && !p.getUniqueId().equals(targetMember.getUuid())) {
                p.sendMessage(ChatColor.YELLOW + targetMember.getName() + " получил повышение!");
            }
        }
    }
    
    private void handleDemote(Player player, ClanMember member, String[] args) {
        if (!member.hasClan()) {
            player.sendMessage(ChatColor.RED + "Вы не состоите в клане!");
            return;
        }
        
        if (!member.isLeader()) {
            player.sendMessage(ChatColor.RED + "Только лидер может понижать игроков!");
            return;
        }
        
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Использование: /clan demote <игрок>");
            return;
        }
        
        Clan clan = plugin.getClanManager().getClanById(member.getClanId());
        String targetName = args[1];
        
        ClanMember targetMember = null;
        for (ClanMember m : clan.getMembers()) {
            if (m.getName().equalsIgnoreCase(targetName)) {
                targetMember = m;
                break;
            }
        }
        
        if (targetMember == null) {
            player.sendMessage(ChatColor.RED + "Игрок не найден в клане!");
            return;
        }
        
        if (targetMember.getUuid().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Вы не можете понизить себя!");
            return;
        }
        
        if (targetMember.getRole() == Clan.ClanRole.MEMBER) {
            player.sendMessage(ChatColor.RED + "Этот игрок уже имеет минимальный ранг!");
            return;
        }
        
        if (targetMember.getRole() == Clan.ClanRole.OFFICER) {
            targetMember.setRole(Clan.ClanRole.MEMBER);
            
            player.sendMessage(ChatColor.YELLOW + "Игрок " + targetMember.getName() + " понижен до участника!");
            
            Player targetPlayer = Bukkit.getPlayer(targetMember.getUuid());
            if (targetPlayer != null) {
                targetPlayer.sendMessage(ChatColor.YELLOW + "Вы были понижены до участника!");
            }
            
            // Уведомляем клан
            for (UUID memberUuid : clan.getOnlineMembers()) {
                Player p = Bukkit.getPlayer(memberUuid);
                if (p != null && !p.equals(player) && !p.getUniqueId().equals(targetMember.getUuid())) {
                    p.sendMessage(ChatColor.YELLOW + targetMember.getName() + " был понижен в должности!");
                }
            }
        } else {
            player.sendMessage(ChatColor.RED + "Нельзя понизить лидера!");
        }
    }
    
    private void handleInfo(Player player, ClanMember member, String[] args) {
        Clan clan = null;
        
        if (args.length >= 2) {
            clan = plugin.getClanManager().getClanByName(args[1]);
        } else if (member.hasClan()) {
            clan = plugin.getClanManager().getClanById(member.getClanId());
        }
        
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "Клан не найден! Используйте /clan info <название>");
            return;
        }
        
        player.sendMessage(ChatColor.GOLD + "╔════════════════════════════════");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.AQUA + ChatColor.BOLD + clan.getName() + 
            ChatColor.GRAY + " [" + clan.getTag() + "]");
        player.sendMessage(ChatColor.GOLD + "╠════════════════════════════════");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Уровень: " + 
            ChatColor.WHITE + clan.getLevel() + " ⭐");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Баланс: " + 
            ChatColor.GREEN + "$" + String.format("%.2f", clan.getBalance()));
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Участников: " + 
            ChatColor.WHITE + clan.getMemberCount() + "/" + clan.getMaxMembers());
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Онлайн: " + 
            ChatColor.GREEN + clan.getOnlineMembers().size() + " игроков");
        
        // Найдем лидера
        String leaderName = "Неизвестен";
        for (ClanMember m : clan.getMembers()) {
            if (m.getUuid().equals(clan.getLeaderUuid())) {
                leaderName = m.getName();
                break;
            }
        }
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Лидер: " + 
            ChatColor.LIGHT_PURPLE + leaderName);
        
        // Союзники
        if (!clan.getAllies().isEmpty()) {
            StringBuilder allies = new StringBuilder();
            for (int allyId : clan.getAllies()) {
                Clan ally = plugin.getClanManager().getClanById(allyId);
                if (ally != null) {
                    if (allies.length() > 0) allies.append(", ");
                    allies.append(ally.getName());
                }
            }
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Союзники: " + 
                ChatColor.WHITE + allies.toString());
        }
        
        // Дом клана
        if (clan.getHome() != null) {
            Location home = clan.getHome();
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Дом: " + 
                ChatColor.WHITE + String.format("%.0f, %.0f, %.0f", 
                home.getX(), home.getY(), home.getZ()));
        }
        
        player.sendMessage(ChatColor.GOLD + "╠════════════════════════════════");
        
        // Список членов если это свой клан
        if (member.hasClan() && member.getClanId() == clan.getId()) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.AQUA + "Состав клана:");
            
            List<ClanMember> leaders = new ArrayList<>();
            List<ClanMember> officers = new ArrayList<>();
            List<ClanMember> members = new ArrayList<>();
            
            for (ClanMember m : clan.getMembers()) {
                switch (m.getRole()) {
                    case LEADER -> leaders.add(m);
                    case OFFICER -> officers.add(m);
                    case MEMBER -> members.add(m);
                }
            }
            
            // Сортируем по KDR
            Comparator<ClanMember> kdrComparator = (a, b) -> 
                Double.compare(b.getKDR(), a.getKDR());
            
            officers.sort(kdrComparator);
            members.sort(kdrComparator);
            
            // Выводим по ролям
            for (ClanMember m : leaders) {
                String status = Bukkit.getPlayer(m.getUuid()) != null ? 
                    ChatColor.GREEN + "●" : ChatColor.GRAY + "○";
                player.sendMessage(ChatColor.GOLD + "║ " + status + " " + 
                    ChatColor.DARK_RED + "[Л] " + ChatColor.WHITE + m.getName() +
                    ChatColor.GRAY + " K/D: " + String.format("%.2f", m.getKDR()));
            }
            
            for (ClanMember m : officers) {
                String status = Bukkit.getPlayer(m.getUuid()) != null ? 
                    ChatColor.GREEN + "●" : ChatColor.GRAY + "○";
                player.sendMessage(ChatColor.GOLD + "║ " + status + " " + 
                    ChatColor.GOLD + "[О] " + ChatColor.WHITE + m.getName() +
                    ChatColor.GRAY + " K/D: " + String.format("%.2f", m.getKDR()));
            }
            
            for (ClanMember m : members) {
                String status = Bukkit.getPlayer(m.getUuid()) != null ? 
                    ChatColor.GREEN + "●" : ChatColor.GRAY + "○";
                player.sendMessage(ChatColor.GOLD + "║ " + status + " " + 
                    ChatColor.GRAY + "[У] " + ChatColor.WHITE + m.getName() +
                    ChatColor.GRAY + " K/D: " + String.format("%.2f", m.getKDR()));
            }
        }
        
        player.sendMessage(ChatColor.GOLD + "╚════════════════════════════════");
    }
    
    private void handleList(Player player) {
        Collection<Clan> clans = plugin.getClanManager().getAllClans();
        
        if (clans.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "На сервере пока нет кланов!");
            return;
        }
        
        player.sendMessage(ChatColor.GOLD + "=== Список кланов (" + clans.size() + ") ===");
        
        int page = 1;
        int perPage = 10;
        int count = 0;
        
        for (Clan clan : clans) {
            if (count >= perPage) break;
            
            String onlineStatus = ChatColor.GRAY + " [" + clan.getOnlineMembers().size() + "/" + 
                clan.getMemberCount() + " онлайн]";
            
            player.sendMessage(ChatColor.YELLOW + "• " + ChatColor.WHITE + clan.getName() + 
                ChatColor.GRAY + " [" + clan.getTag() + "]" + 
                ChatColor.GREEN + " Ур." + clan.getLevel() + onlineStatus);
            count++;
        }
        
        if (clans.size() > perPage) {
            player.sendMessage(ChatColor.GRAY + "Используйте /clan list <страница> для других страниц");
        }
    }
    
    private void handleTop(Player player) {
        List<Clan> clans = new ArrayList<>(plugin.getClanManager().getAllClans());
        
        if (clans.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "На сервере пока нет кланов!");
            return;
        }
        
        // Сортируем по уровню и балансу
        clans.sort((c1, c2) -> {
            int levelCompare = Integer.compare(c2.getLevel(), c1.getLevel());
            if (levelCompare != 0) return levelCompare;
            return Double.compare(c2.getBalance(), c1.getBalance());
        });
        
        player.sendMessage(ChatColor.GOLD + "=== ТОП 10 Кланов ===");
        
        for (int i = 0; i < Math.min(10, clans.size()); i++) {
            Clan clan = clans.get(i);
            String place = switch(i) {
                case 0 -> ChatColor.GOLD + "🥇";
                case 1 -> ChatColor.GRAY + "🥈";
                case 2 -> ChatColor.YELLOW + "🥉";
                default -> ChatColor.WHITE + String.valueOf(i + 1) + ".";
            };
            
            player.sendMessage(place + " " + ChatColor.AQUA + clan.getName() + 
                ChatColor.GRAY + " [" + clan.getTag() + "]" +
                ChatColor.GREEN + " Ур." + clan.getLevel() + 
                ChatColor.GOLD + " $" + String.format("%.2f", clan.getBalance()) +
                ChatColor.GRAY + " (" + clan.getMemberCount() + " чел.)");
        }
    }
    
    private void handleSetHome(Player player, ClanMember member) {
        if (!member.hasClan()) {
            player.sendMessage(ChatColor.RED + "Вы не состоите в клане!");
            return;
        }
        
        if (member.getRole().getPower() < Clan.ClanRole.OFFICER.getPower()) {
            player.sendMessage(ChatColor.RED + "Только офицеры и лидер могут устанавливать дом клана!");
            return;
        }
        
        Clan clan = plugin.getClanManager().getClanById(member.getClanId());
        Location location = player.getLocation();
        
        clan.setHome(location);
        
        player.sendMessage(ChatColor.GREEN + "Дом клана установлен на ваших координатах!");
        
        // Уведомляем членов клана
        for (UUID memberUuid : clan.getOnlineMembers()) {
            Player p = Bukkit.getPlayer(memberUuid);
            if (p != null && !p.equals(player)) {
                p.sendMessage(ChatColor.GREEN + player.getName() + " установил новый дом клана!");
            }
        }
    }
    
    private void handleHome(Player player, ClanMember member) {
        if (!member.hasClan()) {
            player.sendMessage(ChatColor.RED + "Вы не состоите в клане!");
            return;
        }
        
        Clan clan = plugin.getClanManager().getClanById(member.getClanId());
        
        if (clan.getHome() == null) {
            player.sendMessage(ChatColor.RED + "Дом клана не установлен!");
            return;
        }
        
        double cost = plugin.getConfig().getDouble("clans.home_teleport_cost", 100.0);
        int delay = plugin.getConfig().getInt("clans.home_teleport_delay", 5);
        
        // Проверка экономики (если подключена Vault)
        // Для простоты пока без экономики
        
        player.sendMessage(ChatColor.YELLOW + "Телепортация через " + delay + " секунд... Не двигайтесь!");
        
        Location originalLocation = player.getLocation().clone();
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Проверяем что игрок не двигался
            if (player.getLocation().distance(originalLocation) > 1.0) {
                player.sendMessage(ChatColor.RED + "Телепортация отменена - вы двигались!");
                return;
            }
            
            player.teleport(clan.getHome());
            player.sendMessage(ChatColor.GREEN + "Вы телепортированы в дом клана!");
        }, delay * 20L);
    }
    
    private void handleDeposit(Player player, ClanMember member, String[] args) {
        if (!member.hasClan()) {
            player.sendMessage(ChatColor.RED + "Вы не состоите в клане!");
            return;
        }
        
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Использование: /clan deposit <сумма>");
            return;
        }
        
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Неверная сумма!");
            return;
        }
        
        if (amount <= 0) {
            player.sendMessage(ChatColor.RED + "Сумма должна быть положительной!");
            return;
        }
        
        // Здесь должна быть проверка экономики через Vault
        // Для примера просто добавляем в баланс клана
        
        Clan clan = plugin.getClanManager().getClanById(member.getClanId());
        clan.deposit(amount);
        
        player.sendMessage(ChatColor.GREEN + "Вы внесли $" + amount + " в казну клана!");
        player.sendMessage(ChatColor.YELLOW + "Новый баланс: $" + String.format("%.2f", clan.getBalance()));
        
        // Уведомляем членов клана
        for (UUID memberUuid : clan.getOnlineMembers()) {
            Player p = Bukkit.getPlayer(memberUuid);
            if (p != null && !p.equals(player)) {
                p.sendMessage(ChatColor.GREEN + player.getName() + " внёс $" + amount + " в казну клана!");
            }
        }
    }
    
    private void handleWithdraw(Player player, ClanMember member, String[] args) {
        if (!member.hasClan()) {
            player.sendMessage(ChatColor.RED + "Вы не состоите в клане!");
            return;
        }
        
        if (!member.isLeader()) {
            player.sendMessage(ChatColor.RED + "Только лидер может снимать деньги с баланса клана!");
            return;
        }
        
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Использование: /clan withdraw <сумма>");
            return;
        }
        
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Неверная сумма!");
            return;
        }
        
        if (amount <= 0) {
            player.sendMessage(ChatColor.RED + "Сумма должна быть положительной!");
            return;
        }
        
        Clan clan = plugin.getClanManager().getClanById(member.getClanId());
        
        if (!clan.withdraw(amount)) {
            player.sendMessage(ChatColor.RED + "Недостаточно средств в казне клана!");
            player.sendMessage(ChatColor.YELLOW + "Текущий баланс: $" + String.format("%.2f", clan.getBalance()));
            return;
        }
        
        // Здесь должна быть выдача денег через Vault
        
        player.sendMessage(ChatColor.GREEN + "Вы сняли $" + amount + " из казны клана!");
        player.sendMessage(ChatColor.YELLOW + "Новый баланс: $" + String.format("%.2f", clan.getBalance()));
        
        // Уведомляем членов клана
        for (UUID memberUuid : clan.getOnlineMembers()) {
            Player p = Bukkit.getPlayer(memberUuid);
            if (p != null && !p.equals(player)) {
                p.sendMessage(ChatColor.YELLOW + player.getName() + " снял $" + amount + " из казны клана!");
            }
        }
    }
    
    private void handleAlly(Player player, ClanMember member, String[] args) {
        if (!member.hasClan()) {
            player.sendMessage(ChatColor.RED + "Вы не состоите в клане!");
            return;
        }
        
        if (!member.isLeader()) {
            player.sendMessage(ChatColor.RED + "Только лидер может создавать союзы!");
            return;
        }
        
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Использование: /clan ally <название клана>");
            return;
        }
        
        Clan myClan = plugin.getClanManager().getClanById(member.getClanId());
        Clan targetClan = plugin.getClanManager().getClanByName(args[1]);
        
        if (targetClan == null) {
            player.sendMessage(ChatColor.RED + "Клан не найден!");
            return;
        }
        
        if (targetClan.getId() == myClan.getId()) {
            player.sendMessage(ChatColor.RED + "Вы не можете создать союз с собственным кланом!");
            return;
        }
        
        if (myClan.isAlly(targetClan.getId())) {
            player.sendMessage(ChatColor.RED + "Вы уже в союзе с этим кланом!");
            return;
        }
        
        // Добавляем союз (обоюдный)
        myClan.addAlly(targetClan.getId());
        targetClan.addAlly(myClan.getId());
        
        // Сохраняем в БД
        plugin.getDatabaseManager().executeAsync(conn -> {
            String sql = "INSERT INTO clan_alliances (clan1_id, clan2_id) VALUES (?, ?)";
            try (var ps = conn.prepareStatement(sql)) {
                ps.setInt(1, Math.min(myClan.getId(), targetClan.getId()));
                ps.setInt(2, Math.max(myClan.getId(), targetClan.getId()));
                ps.executeUpdate();
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка сохранения союза: " + e.getMessage());
            }
        });
        
        player.sendMessage(ChatColor.GREEN + "Союз с кланом " + targetClan.getName() + " создан!");
        
        // Уведомляем оба клана
        for (UUID memberUuid : myClan.getOnlineMembers()) {
            Player p = Bukkit.getPlayer(memberUuid);
            if (p != null && !p.equals(player)) {
                p.sendMessage(ChatColor.GREEN + "Ваш клан заключил союз с " + targetClan.getName() + "!");
            }
        }
        
        for (UUID memberUuid : targetClan.getOnlineMembers()) {
            Player p = Bukkit.getPlayer(memberUuid);
            if (p != null) {
                p.sendMessage(ChatColor.GREEN + "Клан " + myClan.getName() + " заключил с вами союз!");
            }
        }
    }
    
    private void handleUnally(Player player, ClanMember member, String[] args) {
        if (!member.hasClan()) {
            player.sendMessage(ChatColor.RED + "Вы не состоите в клане!");
            return;
        }
        
        if (!member.isLeader()) {
            player.sendMessage(ChatColor.RED + "Только лидер может разрывать союзы!");
            return;
        }
        
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Использование: /clan unally <название клана>");
            return;
        }
        
        Clan myClan = plugin.getClanManager().getClanById(member.getClanId());
        Clan targetClan = plugin.getClanManager().getClanByName(args[1]);
        
        if (targetClan == null) {
            player.sendMessage(ChatColor.RED + "Клан не найден!");
            return;
        }
        
        if (!myClan.isAlly(targetClan.getId())) {
            player.sendMessage(ChatColor.RED + "Вы не в союзе с этим кланом!");
            return;
        }
        
        // Удаляем союз (обоюдно)
        myClan.removeAlly(targetClan.getId());
        targetClan.removeAlly(myClan.getId());
        
        // Удаляем из БД
        plugin.getDatabaseManager().executeAsync(conn -> {
            String sql = "DELETE FROM clan_alliances WHERE (clan1_id = ? AND clan2_id = ?) OR (clan1_id = ? AND clan2_id = ?)";
            try (var ps = conn.prepareStatement(sql)) {
                ps.setInt(1, myClan.getId());
                ps.setInt(2, targetClan.getId());
                ps.setInt(3, targetClan.getId());
                ps.setInt(4, myClan.getId());
                ps.executeUpdate();
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка удаления союза: " + e.getMessage());
            }
        });
        
        player.sendMessage(ChatColor.YELLOW + "Союз с кланом " + targetClan.getName() + " разорван!");
        
        // Уведомляем оба клана
        for (UUID memberUuid : myClan.getOnlineMembers()) {
            Player p = Bukkit.getPlayer(memberUuid);
            if (p != null && !p.equals(player)) {
                p.sendMessage(ChatColor.YELLOW + "Союз с кланом " + targetClan.getName() + " разорван!");
            }
        }
        
        for (UUID memberUuid : targetClan.getOnlineMembers()) {
            Player p = Bukkit.getPlayer(memberUuid);
            if (p != null) {
                p.sendMessage(ChatColor.YELLOW + "Клан " + myClan.getName() + " разорвал союз!");
            }
        }
    }
    
    private void handleChat(Player player, ClanMember member, String[] args) {
        if (!member.hasClan()) {
            player.sendMessage(ChatColor.RED + "Вы не состоите в клане!");
            return;
        }
        
        // Переключаем режим чата через EventListener
        plugin.getEventListener().toggleClanChat(player);
    }
    
    private void sendHelp(Player player) {
        ClanMember member = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        
        player.sendMessage(ChatColor.GOLD + "=== Команды кланов ===");
        
        // Основные команды
        player.sendMessage(ChatColor.YELLOW + "/clan create <название> <тег>" + ChatColor.WHITE + " - создать клан");
        player.sendMessage(ChatColor.YELLOW + "/clan list" + ChatColor.WHITE + " - список всех кланов");
        player.sendMessage(ChatColor.YELLOW + "/clan top" + ChatColor.WHITE + " - топ 10 кланов");
        player.sendMessage(ChatColor.YELLOW + "/clan info [клан]" + ChatColor.WHITE + " - информация о клане");
        
        if (member != null && member.hasClan()) {
            player.sendMessage(ChatColor.GOLD + "--- Команды для членов клана ---");
            player.sendMessage(ChatColor.YELLOW + "/clan leave" + ChatColor.WHITE + " - покинуть клан");
            player.sendMessage(ChatColor.YELLOW + "/clan home" + ChatColor.WHITE + " - телепорт в дом клана");
            player.sendMessage(ChatColor.YELLOW + "/clan chat" + ChatColor.WHITE + " - переключить клановый чат");
            player.sendMessage(ChatColor.YELLOW + "/clan deposit <сумма>" + ChatColor.WHITE + " - пополнить казну");
            
            if (member.getRole().getPower() >= Clan.ClanRole.OFFICER.getPower()) {
                player.sendMessage(ChatColor.GOLD + "--- Команды для офицеров ---");
                player.sendMessage(ChatColor.YELLOW + "/clan invite <игрок>" + ChatColor.WHITE + " - пригласить игрока");
                player.sendMessage(ChatColor.YELLOW + "/clan kick <игрок>" + ChatColor.WHITE + " - исключить игрока");
                player.sendMessage(ChatColor.YELLOW + "/clan sethome" + ChatColor.WHITE + " - установить дом клана");
            }
            
            if (member.isLeader()) {
                player.sendMessage(ChatColor.GOLD + "--- Команды для лидера ---");
                player.sendMessage(ChatColor.YELLOW + "/clan disband" + ChatColor.WHITE + " - распустить клан");
                player.sendMessage(ChatColor.YELLOW + "/clan promote <игрок>" + ChatColor.WHITE + " - повысить игрока");
                player.sendMessage(ChatColor.YELLOW + "/clan demote <игрок>" + ChatColor.WHITE + " - понизить игрока");
                player.sendMessage(ChatColor.YELLOW + "/clan withdraw <сумма>" + ChatColor.WHITE + " - снять из казны");
                player.sendMessage(ChatColor.YELLOW + "/clan ally <клан>" + ChatColor.WHITE + " - создать союз");
                player.sendMessage(ChatColor.YELLOW + "/clan unally <клан>" + ChatColor.WHITE + " - разорвать союз");
            }
        } else {
            player.sendMessage(ChatColor.YELLOW + "/clan accept <клан>" + ChatColor.WHITE + " - принять приглашение");
            player.sendMessage(ChatColor.YELLOW + "/clan deny <клан>" + ChatColor.WHITE + " - отклонить приглашение");
        }
        
        player.sendMessage(ChatColor.YELLOW + "/clan help" + ChatColor.WHITE + " - показать эту помощь");
    }
}