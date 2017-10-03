package de.selebrator.multilevelwhitelist;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PluginMain extends JavaPlugin implements Listener, CommandExecutor {
	private File saveFile = new File(this.getDataFolder() + "/activelists.yml");
	FileConfiguration save;

	private final String[] commands = {
			"§7---------------§8[§3" + this.getDescription().getName() + " - help§8]§7---------------",
			"§a/whitelist info §7- Gives a explanation of what the plugin does",
			"§a/whitelist list §7- List all registered Whitelists",
			"§a/whitelist on <name> §7- activate whitelist <name> if registered",
			"§a/whitelist off <name> §7- deactivate whitelist <name> if registered",
			"§a/whitelist toggle <name> §7- toggle the state of whitelist <name> if registered",
			"§a/whitelist kick §7- kick all players that are on none of the active whitelists"
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
	private String message_kick;
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
		this.message_kick = ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("message_kick"));

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
		this.save = YamlConfiguration.loadConfiguration(this.saveFile);
		this.activeWhitelists = this.save.getStringList("activelists");
	}

	@Override
	public void onDisable() {
		this.save.set("activelists", this.activeWhitelists);
		try {
			this.save.save(this.saveFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
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
			} else if(args[0].equalsIgnoreCase("kick")) {
				if(!checkPermission(sender, "whitelist.kick"))
					return true;

				long kickedPlayersCount = this.getServer().getOnlinePlayers().stream()
						.filter(player -> !mayPlayerStay(player))
						.peek(this::kick)
						.peek(player -> System.out.println(player.getDisplayName()))
						.count();
				String notification = this.message_kick.replaceAll("%count%", String.valueOf(kickedPlayersCount));
				notify(notification, sender, new Permission("whitelist.notify.kick"));
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

	private boolean mayPlayerStay(Player player) {
		return this.activeWhitelists.size() == 0 || player.hasPermission("whitelist.list.*") || this.activeWhitelists.stream().anyMatch(name -> player.hasPermission(this.registeredWhitelists.get(name).getPermission()));
	}

	private Whitelist getHighestActiveWhitelist() {
		return this.activeWhitelists.stream()
				.map(this.registeredWhitelists::get)
				.reduce((w1, w2) -> (w1.getPriority() > w2.getPriority() ? w1 : w2))
				.orElse(new Whitelist("null", "null",0, new ArrayList<>()));
	}

	private void kick(Player player) {
		Whitelist highestPriority = getHighestActiveWhitelist();
		String message = highestPriority.getMessage().stream()
				.collect(Collectors.joining("\n§r"));

		if(highestPriority.getPriority() > 0)
			player.kickPlayer(message);
	}

	@EventHandler
	private void onJoin(PlayerLoginEvent event) {
		Player player = event.getPlayer();
		if(!this.mayPlayerStay(player)) {
			String message = getHighestActiveWhitelist().getMessage().stream()
					.collect(Collectors.joining("\n§r"));
			event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, message);

			String notification = this.message_failes_join
					.replaceAll("%player_uuid%", player.getUniqueId().toString())
					.replaceAll("%player_displayname%", player.getDisplayName())
					.replaceAll("%player_name%", player.getName());

			notify(notification, this.getServer().getConsoleSender(), new Permission("whitelist.notify.joinfail"));
		}
	}
}
