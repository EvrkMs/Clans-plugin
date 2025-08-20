package com.yourname.clans.commands;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.yourname.clans.ClansPlugin;
import com.yourname.clans.data.ClanDataManager;
import com.yourname.clans.model.Clan;

public class ClanCommand implements CommandExecutor {
    
    private final ClanDataManager dataManager;
    
    public ClanCommand(ClansPlugin plugin) {
        this.dataManager = plugin.getDataManager();
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
        
        switch (subCommand) {
            case "create" -> handleCreate(player, args);
            case "invite" -> handleInvite(player, args);
            case "accept" -> handleAccept(player);
            case "deny" -> handleDeny(player);
            case "leave" -> handleLeave(player);
            case "disband" -> handleDisband(player);
            case "promote" -> handlePromote(player, args);
            case "demote" -> handleDemote(player, args);
            case "kick" -> handleKick(player, args);
            case "chat" -> handleChat(player, args);
            case "ff" -> handleFriendlyFire(player, args);
            case "home" -> handleHome(player, args);
            case "info" -> handleInfo(player, args);
            default -> sendHelp(player);
        }
        
        return true;
    }
    
    private void handleCreate(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Использование: /clan create <tag> <name>");
            return;
        }
        
        String tag = args[1];
        if (tag.length() > 6) {
            player.sendMessage(ChatColor.RED + "Тег клана не может быть длиннее 6 символов!");
            return;
        }
        
        StringBuilder nameBuilder = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            nameBuilder.append(args[i]);
            if (i < args.length - 1) nameBuilder.append(" ");
        }
        String name = nameBuilder.toString();
        
        if (dataManager.getPlayerClan(player.getUniqueId()) != null) {
            player.sendMessage(ChatColor.RED + "Вы уже состоите в клане!");
            return;
        }
        
        if (dataManager.createClan(tag, name, player.getUniqueId())) {
            player.sendMessage(ChatColor.GREEN + "Клан '" + name + "' [" + tag + "] успешно создан!");
        } else {
            player.sendMessage(ChatColor.RED + "Клан с таким тегом уже существует!");
        }
    }
    
    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Использование: /clan invite <player>");
            return;
        }
        
        Clan clan = dataManager.getPlayerClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "Вы не состоите в клане!");
            return;
        }
        
        if (!clan.canManage(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "У вас нет прав для приглашения игроков!");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Игрок не найден!");
            return;
        }
        
        if (dataManager.getPlayerClan(target.getUniqueId()) != null) {
            player.sendMessage(ChatColor.RED + "Этот игрок уже состоит в клане!");
            return;
        }
        
        clan.addInvite(target.getUniqueId());
        player.sendMessage(ChatColor.GREEN + "Приглашение отправлено игроку " + target.getName());
        target.sendMessage(ChatColor.YELLOW + "Вас пригласили в клан '" + clan.getName() + "' [" + clan.getTag() + "]");
        target.sendMessage(ChatColor.YELLOW + "Используйте /clan accept для принятия или /clan deny для отклонения");
    }
    
    private void handleAccept(Player player) {
        Clan clanWithInvite = null;
        for (Clan clan : dataManager.getAllClans()) {
            if (clan.hasInvite(player.getUniqueId())) {
                clanWithInvite = clan;
                break;
            }
        }
        
        if (clanWithInvite == null) {
            player.sendMessage(ChatColor.RED + "У вас нет активных приглашений в кланы!");
            return;
        }
        
        if (dataManager.getPlayerClan(player.getUniqueId()) != null) {
            player.sendMessage(ChatColor.RED + "Вы уже состоите в клане!");
            return;
        }
        
        clanWithInvite.addMember(player.getUniqueId());
        dataManager.updatePlayerClan(player.getUniqueId(), clanWithInvite.getTag());
        
        player.sendMessage(ChatColor.GREEN + "Вы присоединились к клану '" + clanWithInvite.getName() + "' [" + clanWithInvite.getTag() + "]!");
        
        // Уведомление остальных участников клана
        for (UUID memberUuid : clanWithInvite.getMembers()) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null && !member.equals(player)) {
                member.sendMessage(ChatColor.YELLOW + player.getName() + " присоединился к клану!");
            }
        }
    }
    
    private void handleDeny(Player player) {
        boolean foundInvite = false;
        for (Clan clan : dataManager.getAllClans()) {
            if (clan.hasInvite(player.getUniqueId())) {
                clan.removeInvite(player.getUniqueId());
                foundInvite = true;
                player.sendMessage(ChatColor.YELLOW + "Приглашение в клан '" + clan.getName() + "' отклонено");
            }
        }
        
        if (!foundInvite) {
            player.sendMessage(ChatColor.RED + "У вас нет активных приглашений в кланы!");
        }
    }
    
    private void handleLeave(Player player) {
        Clan clan = dataManager.getPlayerClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "Вы не состоите в клане!");
            return;
        }
        
        if (clan.isOwner(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Владелец клана не может покинуть клан! Используйте /clan disband для расформирования.");
            return;
        }
        
        clan.removeMember(player.getUniqueId());
        dataManager.updatePlayerClan(player.getUniqueId(), null);
        
        player.sendMessage(ChatColor.YELLOW + "Вы покинули клан '" + clan.getName() + "'");
        
        // Уведомление остальных участников
        for (UUID memberUuid : clan.getMembers()) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null) {
                member.sendMessage(ChatColor.YELLOW + player.getName() + " покинул клан");
            }
        }
    }
    
    private void handleDisband(Player player) {
        Clan clan = dataManager.getPlayerClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "Вы не состоите в клане!");
            return;
        }
        
        if (!clan.isOwner(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Только владелец клана может его расформировать!");
            return;
        }
        
        // Уведомление всех участников
        for (UUID memberUuid : clan.getMembers()) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null) {
                member.sendMessage(ChatColor.RED + "Клан '" + clan.getName() + "' был расформирован!");
                dataManager.updatePlayerClan(memberUuid, null);
            }
        }
        
        dataManager.disbandClan(clan.getTag());
        player.sendMessage(ChatColor.RED + "Клан '" + clan.getName() + "' расформирован!");
    }
    
    private void handlePromote(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Использование: /clan promote <player>");
            return;
        }
        
        Clan clan = dataManager.getPlayerClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "Вы не состоите в клане!");
            return;
        }
        
        if (!clan.isOwner(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Только владелец клана может назначать офицеров!");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Игрок не найден!");
            return;
        }
        
        if (!clan.isMember(target.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Этот игрок не состоит в вашем клане!");
            return;
        }
        
        if (clan.isOfficer(target.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Этот игрок уже является офицером!");
            return;
        }
        
        clan.addOfficer(target.getUniqueId());
        player.sendMessage(ChatColor.GREEN + target.getName() + " назначен офицером клана!");
        target.sendMessage(ChatColor.GREEN + "Вы назначены офицером клана '" + clan.getName() + "'!");
    }
    
    private void handleDemote(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Использование: /clan demote <player>");
            return;
        }
        
        Clan clan = dataManager.getPlayerClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "Вы не состоите в клане!");
            return;
        }
        
        if (!clan.isOwner(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Только владелец клана может понижать офицеров!");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Игрок не найден!");
            return;
        }
        
        if (!clan.isOfficer(target.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Этот игрок не является офицером!");
            return;
        }
        
        clan.removeOfficer(target.getUniqueId());
        player.sendMessage(ChatColor.YELLOW + target.getName() + " понижен до обычного участника!");
        target.sendMessage(ChatColor.YELLOW + "Вы понижены до обычного участника клана '" + clan.getName() + "'!");
    }
    
    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Использование: /clan kick <player>");
            return;
        }
        
        Clan clan = dataManager.getPlayerClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "Вы не состоите в клане!");
            return;
        }
        
        if (!clan.canManage(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "У вас нет прав для исключения игроков!");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Игрок не найден!");
            return;
        }
        
        if (!clan.isMember(target.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Этот игрок не состоит в вашем клане!");
            return;
        }
        
        if (clan.isOwner(target.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Нельзя исключить владельца клана!");
            return;
        }
        
        if (clan.isOfficer(target.getUniqueId()) && !clan.isOwner(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Только владелец может исключить офицера!");
            return;
        }
        
        clan.removeMember(target.getUniqueId());
        dataManager.updatePlayerClan(target.getUniqueId(), null);
        
        player.sendMessage(ChatColor.YELLOW + target.getName() + " исключен из клана!");
        target.sendMessage(ChatColor.RED + "Вы исключены из клана '" + clan.getName() + "'!");
        
        // Уведомление остальных участников
        for (UUID memberUuid : clan.getMembers()) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null && !member.equals(player)) {
                member.sendMessage(ChatColor.YELLOW + target.getName() + " исключен из клана игроком " + player.getName());
            }
        }
    }
    
    private void handleChat(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Использование: /clan chat <message>");
            return;
        }
        
        Clan clan = dataManager.getPlayerClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "Вы не состоите в клане!");
            return;
        }
        
        StringBuilder messageBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            messageBuilder.append(args[i]);
            if (i < args.length - 1) messageBuilder.append(" ");
        }
        String message = messageBuilder.toString();
        
        String formattedMessage = ChatColor.GOLD + "[" + clan.getTag() + "] " + player.getName() + ": " + ChatColor.WHITE + message;
        
        // Отправка сообщения всем участникам клана
        for (UUID memberUuid : clan.getMembers()) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null) {
                member.sendMessage(formattedMessage);
            }
        }
    }
    
    private void handleFriendlyFire(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Использование: /clan ff <on|off>");
            return;
        }
        
        Clan clan = dataManager.getPlayerClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "Вы не состоите в клане!");
            return;
        }
        
        if (!clan.canManage(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "У вас нет прав для изменения настроек клана!");
            return;
        }
        
        String setting = args[1].toLowerCase();
        boolean friendlyFire;
        
        if (setting.equals("on") || setting.equals("true")) {
            friendlyFire = true;
        } else if (setting.equals("off") || setting.equals("false")) {
            friendlyFire = false;
        } else {
            player.sendMessage(ChatColor.RED + "Используйте 'on' или 'off'");
            return;
        }
        
        clan.setFriendlyFire(friendlyFire);
        
        String status = friendlyFire ? ChatColor.RED + "включен" : ChatColor.GREEN + "выключен";
        player.sendMessage(ChatColor.YELLOW + "Дружественный огонь " + status + ChatColor.YELLOW + " для клана!");
        
        // Уведомление остальных участников
        for (UUID memberUuid : clan.getMembers()) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null && !member.equals(player)) {
                member.sendMessage(ChatColor.YELLOW + "Дружественный огонь " + status + ChatColor.YELLOW + " игроком " + player.getName());
            }
        }
    }
    
    private void handleHome(Player player, String[] args) {
        Clan clan = dataManager.getPlayerClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "Вы не состоите в клане!");
            return;
        }
        
        if (args.length > 1 && args[1].equalsIgnoreCase("set")) {
            if (!clan.canManage(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "У вас нет прав для установки дома клана!");
                return;
            }
            
            clan.setHome(player.getLocation());
            player.sendMessage(ChatColor.GREEN + "Дом клана установлен в вашем текущем местоположении!");
            
            // Уведомление остальных участников
            for (UUID memberUuid : clan.getMembers()) {
                Player member = Bukkit.getPlayer(memberUuid);
                if (member != null && !member.equals(player)) {
                    member.sendMessage(ChatColor.YELLOW + player.getName() + " установил новый дом клана!");
                }
            }
        } else {
            if (clan.getHome() == null) {
                player.sendMessage(ChatColor.RED + "Дом клана не установлен!");
                return;
            }
            
            player.teleport(clan.getHome());
            player.sendMessage(ChatColor.GREEN + "Добро пожаловать домой!");
        }
    }
    
    private void handleInfo(Player player, String[] args) {
        Clan clan;
        
        if (args.length > 1) {
            clan = dataManager.getClan(args[1]);
            if (clan == null) {
                player.sendMessage(ChatColor.RED + "Клан не найден!");
                return;
            }
        } else {
            clan = dataManager.getPlayerClan(player.getUniqueId());
            if (clan == null) {
                player.sendMessage(ChatColor.RED + "Вы не состоите в клане! Укажите тег клана для просмотра информации.");
                return;
            }
        }
        
        player.sendMessage(ChatColor.GOLD + "=== Информация о клане ===");
        player.sendMessage(ChatColor.YELLOW + "Название: " + ChatColor.WHITE + clan.getName());
        player.sendMessage(ChatColor.YELLOW + "Тег: " + ChatColor.WHITE + "[" + clan.getTag() + "]");
        player.sendMessage(ChatColor.YELLOW + "Владелец: " + ChatColor.WHITE + Bukkit.getOfflinePlayer(clan.getOwner()).getName());
        player.sendMessage(ChatColor.YELLOW + "Участников: " + ChatColor.WHITE + clan.getTotalMembers());
        player.sendMessage(ChatColor.YELLOW + "Дружественный огонь: " + (clan.isFriendlyFire() ? ChatColor.RED + "включен" : ChatColor.GREEN + "выключен"));
        player.sendMessage(ChatColor.YELLOW + "Дом установлен: " + (clan.getHome() != null ? ChatColor.GREEN + "да" : ChatColor.RED + "нет"));
    }
    
    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Команды клана ===");
        player.sendMessage(ChatColor.YELLOW + "/clan create <tag> <name>" + ChatColor.WHITE + " - создать клан");
        player.sendMessage(ChatColor.YELLOW + "/clan invite <player>" + ChatColor.WHITE + " - пригласить игрока");
        player.sendMessage(ChatColor.YELLOW + "/clan accept" + ChatColor.WHITE + " - принять приглашение");
        player.sendMessage(ChatColor.YELLOW + "/clan deny" + ChatColor.WHITE + " - отклонить приглашение");
        player.sendMessage(ChatColor.YELLOW + "/clan leave" + ChatColor.WHITE + " - покинуть клан");
        player.sendMessage(ChatColor.YELLOW + "/clan disband" + ChatColor.WHITE + " - расформировать клан");
        player.sendMessage(ChatColor.YELLOW + "/clan promote <player>" + ChatColor.WHITE + " - назначить офицером");
        player.sendMessage(ChatColor.YELLOW + "/clan demote <player>" + ChatColor.WHITE + " - понизить офицера");
        player.sendMessage(ChatColor.YELLOW + "/clan kick <player>" + ChatColor.WHITE + " - исключить игрока");
        player.sendMessage(ChatColor.YELLOW + "/clan chat <message>" + ChatColor.WHITE + " - клан-чат");
        player.sendMessage(ChatColor.YELLOW + "/clan ff <on|off>" + ChatColor.WHITE + " - дружественный огонь");
        player.sendMessage(ChatColor.YELLOW + "/clan home [set]" + ChatColor.WHITE + " - дом клана");
        player.sendMessage(ChatColor.YELLOW + "/clan info [tag]" + ChatColor.WHITE + " - информация о клане");
    }
}