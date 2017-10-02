package de.selebrator.multilevelwhitelist;

import org.bukkit.permissions.Permission;

import java.util.List;

public class Whitelist {
	private String name;
	private String displayName;
	private int priority;
	private List<String> message;

	private Permission permission;

	public Whitelist(String name, String displayName, int priority, List<String> message) {
		this.name = name;
		this.displayName = displayName;
		this.priority = priority;
		this.message = message;
		this.permission = new Permission("whitelist.list." + name.toLowerCase());
	}

	public String getName() {
		return name;
	}

	public String getDisplayName() {
		return displayName;
	}

	public int getPriority() {
		return priority;
	}

	public List<String> getMessage() {
		return message;
	}

	public Permission getPermission() {
		return permission;
	}

	@Override
	public String toString() {
		return "Whitelist{name=" + name + ", displayname=" + displayName + ", priority=" + priority + ", message=" + message.toString() + "}";
	}
}
