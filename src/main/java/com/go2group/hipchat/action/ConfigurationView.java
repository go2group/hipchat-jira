package com.go2group.hipchat.action;

import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.go2group.hipchat.components.ConfigurationManager;
import com.google.common.base.Strings;

public class ConfigurationView extends JiraWebActionSupport {

	private static final long serialVersionUID = 3726606056575506631L;

	private final ConfigurationManager configurationManager;

	private boolean saved;
	private boolean error;
	private String fakeHipChatAuthToken;
	private String serverUrl;
	private String showComments;

	public ConfigurationView(ConfigurationManager configurationManager) {
		this.configurationManager = configurationManager;
	}

	public String getFakeHipChatAuthToken() {
		return fakeHipChatAuthToken;
	}

	public boolean isSaved() {
		return saved;
	}

	public void setSaved(boolean saved) {
		this.saved = saved;
	}

	@Override
	protected String doExecute() throws Exception {
		fakeHipChatAuthToken = Strings.repeat("#", Strings.nullToEmpty(configurationManager.getHipChatApiToken())
				.length());
		serverUrl = configurationManager.getServerUrl();
		showComments = configurationManager.getShowComments();
		return SUCCESS;
	}

	public String getServerUrl() {
		return serverUrl;
	}

	public String getShowComments() {
		return showComments;
	}

	public boolean isError() {
		return error;
	}

	public void setError(boolean error) {
		this.error = error;
	}
}
