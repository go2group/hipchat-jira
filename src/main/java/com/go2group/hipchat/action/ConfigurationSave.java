package com.go2group.hipchat.action;

import com.atlassian.jira.security.xsrf.RequiresXsrfCheck;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.atlassian.sal.api.net.ResponseException;
import com.go2group.hipchat.HipChatProxyClient;
import com.go2group.hipchat.components.ConfigurationManager;
import com.google.common.base.Strings;

public class ConfigurationSave extends JiraWebActionSupport {

	private static final long serialVersionUID = 7422493683617295959L;
	private final ConfigurationManager configurationManager;
	private final HipChatProxyClient hipChatProxyClient;

	private String hipChatAuthToken;
	private String serverUrl;
	private String showComments;
	
	public ConfigurationSave(ConfigurationManager configurationManager, HipChatProxyClient hipChatProxyClient) {
		this.configurationManager = configurationManager;
		this.hipChatProxyClient = hipChatProxyClient;
	}

	public void setHipChatAuthToken(String hipChatAuthToken) {
		this.hipChatAuthToken = hipChatAuthToken;
	}

	public void setServerUrl(String serverUrl) {
		this.serverUrl = serverUrl;
	}

	public void setShowComments(String showComments) {
		this.showComments = showComments;
	}

	@Override
	@RequiresXsrfCheck
	protected String doExecute() throws Exception {
		// only change the token if this is a real update
		String fakeTokenCandidate = Strings.repeat("#", Strings.nullToEmpty(configurationManager.getHipChatApiToken()).length());
		
		if (!fakeTokenCandidate.equals(hipChatAuthToken) && serverUrl != null) {
			try {
				this.hipChatProxyClient.getRooms(hipChatAuthToken, serverUrl);
				// Proceed if no error
				configurationManager.updateHipChatApiToken(this.hipChatAuthToken);
				configurationManager.setServerUrl(serverUrl);
			} catch (ResponseException re) {
				//Ah-oh, not an admin token?
				return getRedirect("/secure/admin/HipChatConfigurationView.jspa?error=true");
			}
		}

		//Comments should be allowed to change irrespective of token change
		if ("Yes".equals(showComments)){
			configurationManager.setShowComments(showComments);
		} else {
			configurationManager.setShowComments(null);
		}

		return getRedirect("/secure/admin/HipChatConfigurationView.jspa?saved=true");
	}
}