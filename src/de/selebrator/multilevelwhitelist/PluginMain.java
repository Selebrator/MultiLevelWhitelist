package de.selebrator.multilevelwhitelist;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PluginMain extends JavaPlugin implements Listener, CommandExecutor {
	private final String[] commands = {
			"§7---------------§8[§3" + this.getDescription().getName() + " - help§8]§7---------------",
			"§a/whitelist info §7- Gives a explanation of what the plugin does",
			"§a/whitelist list §7- List all registered Whitelists",
			"§a/whitelist on <name> §7- activate whitelist <name> if registered",
			"§a/whitelist off <name> §7- deactivate whitelist <name> if registered",
			"§a/whitelist toggle <name> §7- toggle the state of whitelist <name> if registered"
	};
	private final String[] info = {
			"§7---------------§8[§3" + this.getDescription().getName() + " - info§8]§7---------------",
			"MultiLevelWhitelist Allows you to have multiple whitelists that can be individual turned on and off on demand",
			"A Player is allowed to join if they are on at least one active whitelist",
			"Players are considered to be on a whitelist if they have the permission 'whitelist.list.<whitelist name>'",
			"If a player is on NONE of the active whitelists they will be kicked with the message of that whitelist with the highest priority"
	};
	private String message_no_permission;
	private String message_on;
	private String message_on_list;
	private String message_off;
	private String message_off_list;
	private String message_failes_join;
	private Map<String, Whitelist> registeredWhitelists = new HashMap<>();
	private List<String> activeWhitelists = new ArrayList<>();


	@Override
	public void onEnable() {
		this.loadConfig();
		Bukkit.getPluginManager().registerEvents(this, this);
		getCommand("whitelist").setExecutor(this);
	}

	private void loadConfig() {
		this.saveDefaultConfig();
		this.message_no_permission = ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("message_no_permission"));
		this.message_on = ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("message_on"));
		this.message_on_list = ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("message_on_list"));
		this.message_off = ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("message_off"));
		this.message_off_list = ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("message_off_list"));
		this.message_failes_join = ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("message_failes_join"));

		ConfigurationSection whitelists = this.getConfig().getConfigurationSection("whitelists");
		Map<String, Object> whitelistNames = whitelists.getValues(false);
		for(String whitelistName : whitelistNames.keySet()) {
			Map<String, Object> mappings = ((ConfigurationSection) whitelistNames.get(whitelistName)).getValues(false);
			String displayName = (String) mappings.getOrDefault("displayname", whitelistName);
			displayName = ChatColor.translateAlternateColorCodes('&', displayName);
			int priority = (int) mappings.getOrDefault("priority", 1);
			List message = (List) mappings.getOrDefault("message", "You are not whitelisted on this server.");
			List<String> coloredMessage = new ArrayList<>(message.size());
			for(Object line : message)
				coloredMessage.add(ChatColor.translateAlternateColorCodes('&', (String) line));
			this.registeredWhitelists.put(whitelistName.toLowerCase(), new Whitelist(whitelistName, displayName, priority, coloredMessage));
		}
	}

	@Override
	public void onDisable() {

	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

		if(args.length == 0) {
			if(!checkPermission(sender, "whitelist.help"))
				return true;

			for(String line : this.commands)
				sender.sendMessage(line);
			return true;
		}

		if(args.length == 1) {
			if(args[0].equalsIgnoreCase("list")) {
				if(!checkPermission(sender, "whitelist.list"))
					return true;

				for(String name : this.registeredWhitelists.keySet()) {
					boolean active = this.activeWhitelists.contains(name);
					String message = translateNamePlaceholder(active ? this.message_on_list :  this.message_off_list, name);
					sender.sendMessage(message);
				}
				return true;
			} else if(args[0].equalsIgnoreCase("info")) {
				if(!checkPermission(sender, "whitelist.info"))
					return true;

				for(String line : this.info)
					sender.sendMessage(line);
				return true;
			}
		} else if(args.length == 2) {
			if(args[0].equalsIgnoreCase("on")) {
				if(!checkPermission(sender, "whitelist.modify.on"))
					return true;

				String name = args[1].toLowerCase();
				activateWhitelist(name, sender);
				return true;
			} else if(args[0].equalsIgnoreCase("off")) {
				if(!checkPermission(sender, "whitelist.modify.off"))
					return true;

				String name = args[1].toLowerCase();
				this.deactivateWhitelist(name, sender);
				return true;
			} else if(args[0].equalsIgnoreCase("toggle")) {
				if(!checkPermission(sender, "whitelist.modify.toggle"))
					return true;

				String name = args[1].toLowerCase();
				if(this.activeWhitelists.contains(name))
					deactivateWhitelist(name, sender);
				else
					activateWhitelist(name, sender);
				return true;
			}
		}
		return false;
	}

	private boolean checkPermission(CommandSender sender, String permission) {
		if(sender.hasPermission(permission))
			return true;
		else {
			sender.sendMessage(this.message_no_permission);
			return false;
		}
	}

	private String translateNamePlaceholder(String message, String name) {
		return message
				.replaceAll("%whitelist_displayname%", this.registeredWhitelists.get(name).getDisplayName())
				.replaceAll("%whitelist_name%", name);
	}

	private void activateWhitelist(String name, CommandSender sender) {
		if(this.registeredWhitelists.containsKey(name)) {
			this.activeWhitelists.add(name);
			String notification = translateNamePlaceholder(this.message_on, name);
			this.notify(notification, sender, new Permission("whitelist.notify.on"));
		} else {
			sender.sendMessage("Whitelist " + name + "is not registered in the config.");
		}
	}

	private void deactivateWhitelist(String name, CommandSender sender) {
		if(this.registeredWhitelists.containsKey(name)) {
			this.activeWhitelists.remove(name);
			String notification = translateNamePlaceholder(this.message_off, name);
			this.notify(notification, sender, new Permission("whitelist.notify.off"));
		} else {
			sender.sendMessage("Whitelist " + name + "is not registered in the config.");
		}
	}

	private void notify(String message, CommandSender sender, Permission permission) {
		sender.sendMessage(message);
		if(!(sender instanceof ConsoleCommandSender))
			this.getServer().getConsoleSender().sendMessage(message);
		for(Player player : this.getServer().getOnlinePlayers()) {
			if(player.hasPermission(permission) && player != sender)
				player.sendMessage(message);
		}
	}

	@EventHandler
	private void onJoin(PlayerJoinEvent event) {
		if(event.getPlayer().hasPermission("whitelist.list.*"))
			return;

		Whitelist highestPriority = new Whitelist("null", "null",0, new ArrayList<>());
		for(String name : this.activeWhitelists) {
			Whitelist whitelist = this.registeredWhitelists.get(name);
			if(!event.getPlayer().hasPermission(whitelist.getPermission())) {
				if(whitelist.getPriority() > highestPriority.getPriority())
					highestPriority = whitelist;
			} else {
				return;
			}
		}

		String message = highestPriority.getMessage().stream()
				.collect(Collectors.joining("\n§r"));

		if(highestPriority.getPriority() > 0) {
			event.getPlayer().kickPlayer(message);

			String notification = this.message_failes_join
					.replaceAll("%player_uuid%", event.getPlayer().getUniqueId().toString())
					.replaceAll("%player_displayname%", event.getPlayer().getDisplayName())
					.replaceAll("%player_name%", event.getPlayer().getName());

			notify(notification, this.getServer().getConsoleSender(), new Permission("whitelist.notify.joinfail"));
		}


	}
}
