package com.go2group.hipchat.components;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;

public class ConfigurationManager {

	private static final String PLUGIN_STORAGE_KEY = "com.atlassian.labs.hipchat";
	private static final String HIPCHAT_AUTH_TOKEN_KEY = "hipchat-auth-token";
	private static final String HIPCHAT_SERVER_URL = "hipchat-server-url";
	private static final String HIPCHAT_SHOW_COMMENTS = "hipchat-show-comments";
	private static final String API_URL = "https://api.hipchat.com";

	private final PluginSettingsFactory pluginSettingsFactory;

	public ConfigurationManager(PluginSettingsFactory pluginSettingsFactory) {
		this.pluginSettingsFactory = pluginSettingsFactory;
	}

	public String getHipChatApiToken() {
		return (String) this.pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY).get(HIPCHAT_AUTH_TOKEN_KEY);
	}

	public void updateHipChatApiToken(String token) {
		this.pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY).put(HIPCHAT_AUTH_TOKEN_KEY, token);
	}
	
	public String getServerUrl() {
		String serverUrl = (String) this.pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY).get(HIPCHAT_SERVER_URL);
		return serverUrl != null ? serverUrl : API_URL;
	}

	public void setServerUrl(String serverUrl) {
		this.pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY).put(HIPCHAT_SERVER_URL, serverUrl);
	}

	public List<String> getHipChatRooms(String projectKey) {
		return Arrays.asList(StringUtils.split(StringUtils.defaultIfEmpty(getValue(projectKey), ""), ","));
	}

	private String getValue(String storageKey) {
		PluginSettings settings = pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY);
		Object storedValue = settings.get(storageKey);
		return storedValue == null ? "" : storedValue.toString();
	}

	public void setNotifyRooms(String projectKey, String rooms) {
		PluginSettings settings = pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY);
		settings.put(projectKey, rooms);
	}

	public List<String> getProjectEvents(String projectKey) {
		return Arrays.asList(StringUtils.split(StringUtils.defaultIfEmpty(getValue(projectKey + ".events"), ""), ","));
	}

	public void setProjectEvents(String projectKey, String events) {
		PluginSettings settings = pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY);
		settings.put(projectKey + ".events", events);
	}

	public String getProjectJql(String projectKey) {
		return getValue(projectKey + ".jql");
	}

	public void setProjectJql(String projectKey, String jql) {
		PluginSettings settings = pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY);
		settings.put(projectKey + ".jql", jql);
	}

	public String getProjectNotify(String projectKey) {
		return getValue(projectKey + ".notify");
	}

	public void setProjectNotify(String projectKey, String notify) {
		PluginSettings settings = pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY);
		settings.put(projectKey + ".notify", notify);
	}
	
	public String getShowComments() {
		String showComments = (String) this.pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY).get(HIPCHAT_SHOW_COMMENTS);
		return "Yes".equals(showComments) ? showComments : null;
	}
	
	public void setShowComments(String showComments) {
		PluginSettings settings = pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY);
		settings.put(HIPCHAT_SHOW_COMMENTS, showComments);
	}
}