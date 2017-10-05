package com.go2group.jira.webwork;

import java.util.List;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.atlassian.sal.api.ApplicationProperties;
import com.atlassian.sal.api.UrlMode;
import com.go2group.hipchat.HipChatProxyClient;
import com.go2group.hipchat.components.ConfigurationManager;

public class HipChatShareIssue extends JiraWebActionSupport {

	private static final long serialVersionUID = 1730424341299748179L;

	private String message;
	private String color;
	private String roomOption;
	private String notify;
	private String issueKey;

	private final HipChatProxyClient hipChatApiClient;
	private final ConfigurationManager configurationManager;
	private final IssueManager issueManager;
	private final ApplicationProperties applicationProperties;

	public HipChatShareIssue(HipChatProxyClient hipChatApiClient, ConfigurationManager configurationManager,
			IssueManager issueManager, ApplicationProperties applicationProperties) {
		this.hipChatApiClient = hipChatApiClient;
		this.configurationManager = configurationManager;
		this.issueManager = issueManager;
		this.applicationProperties = applicationProperties;
	}

	@Override
	public String doDefault() throws Exception {
		return super.doDefault();
	}

	@Override
	public String doExecute() throws Exception {
		String baseUrl = applicationProperties.getBaseUrl(UrlMode.ABSOLUTE);
		if (!StringUtils.isEmpty(message)) {
			String authToken = configurationManager.getHipChatApiToken();
			if (authToken != null) {
				Issue issue = this.issueManager.getIssueObject(issueKey);
				if (issue != null) {
					List<String> roomsToNotify = this.configurationManager.getHipChatRooms(issue.getProjectObject()
							.getKey());
					ApplicationUser loggedInuser = getLoggedInUser();
					String updatedMessage = "<a href=\"" + baseUrl + "/secure/ViewProfile.jspa?name="
							+ StringEscapeUtils.escapeHtml(loggedInuser.getName()) + "\">"
							+ StringEscapeUtils.escapeHtml(loggedInuser.getDisplayName()) + "</a> Shared <a href=\""
							+ baseUrl + "/browse/" + issueKey + "\">" + issueKey
							+ "</a> with the following message:<br><code>" + message + "</code>";
					for (String room : roomsToNotify) {
						this.hipChatApiClient.notifyRoom(authToken, room, updatedMessage, color, "html", getNotify());
					}
				}
			}
		} else {
			addErrorMessage("Message cannot be empty");
			return SUCCESS;
		}
		return getRedirect(baseUrl + "/browse/" + issueKey);
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getColor() {
		return color;
	}

	public void setColor(String color) {
		this.color = color;
	}

	public String getRoomOption() {
		return roomOption;
	}

	public void setRoomOption(String roomOption) {
		this.roomOption = roomOption;
	}

	public String getNotify() {
		return notify;
	}

	public void setNotify(String notify) {
		this.notify = notify;
	}

	public String getIssueKey() {
		return issueKey;
	}

	public void setIssueKey(String issueKey) {
		this.issueKey = issueKey;
	}

}
