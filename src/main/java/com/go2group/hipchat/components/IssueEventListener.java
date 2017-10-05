package com.go2group.hipchat.components;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.Logger;
import org.ofbiz.core.entity.GenericValue;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.event.type.EventType;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.comments.Comment;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.ofbiz.OfBizDelegator;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.I18nHelper;
import com.atlassian.jira.util.collect.MapBuilder;
import com.atlassian.sal.api.ApplicationProperties;
import com.atlassian.sal.api.UrlMode;
import com.atlassian.sal.api.net.ResponseException;
import com.go2group.hipchat.HipChatProxyClient;
import com.go2group.hipchat.utils.SearchUtil;
import com.google.common.base.Strings;

public class IssueEventListener implements InitializingBean, DisposableBean {

//	private static final String WORKFLOW = "workflow";
//
//	private static final String EVENT_SOURCE = "eventsource";

	private static final Logger log = Logger.getLogger(IssueEventListener.class);

	private final EventPublisher eventPublisher;
	private final HipChatProxyClient hipChatApiClient;
	private final ConfigurationManager configurationManager;
	private final ApplicationProperties applicationProperties;
	private final I18nHelper.BeanFactory i18nBean;
	private final OfBizDelegator delegator;
	private final SearchService searchService;
	private final ProjectManager projectManager;

	public IssueEventListener(EventPublisher eventPublisher, HipChatProxyClient hipChatApiClient,
			ConfigurationManager configurationManager, ApplicationProperties applicationProperties,
			I18nHelper.BeanFactory i18n, SearchService searchService, OfBizDelegator delegator,
			ProjectManager projectManager) {
		this.eventPublisher = eventPublisher;
		this.hipChatApiClient = hipChatApiClient;
		this.configurationManager = configurationManager;
		this.applicationProperties = applicationProperties;
		this.i18nBean = i18n;
		this.searchService = searchService;
		this.delegator = delegator;
		this.projectManager = projectManager;
	}

	@EventListener
	public void onIssueEvent(IssueEvent issueEvent) throws ResponseException {
		Long eventTypeId = issueEvent.getEventTypeId();
		Issue issue = issueEvent.getIssue();
		ApplicationUser user = issueEvent.getUser();
		I18nHelper i18n = i18nBean.getInstance(user);

		String projectKey = issue.getProjectObject().getKey();
		String jqlFilter = this.configurationManager.getProjectJql(projectKey);
		try {
			if (Strings.isNullOrEmpty(jqlFilter) || SearchUtil.matchesJql(searchService, jqlFilter, issue, user)) {

				List<String> roomsToNotify = this.configurationManager.getHipChatRooms(projectKey);
				String notify = this.configurationManager.getProjectNotify(projectKey);

				if (roomsToNotify.size() > 0) {
					List<String> eventsToNotify = this.configurationManager.getProjectEvents(projectKey);
					switch (eventTypeId.intValue()) {
					case 1:
						if (canNotify(eventsToNotify, EventType.ISSUE_CREATED_ID)) {
							String msg = getMessage(issue, user, i18n.getText("hipchat.created.message"), true);
							notifyRooms(roomsToNotify, msg, notify);
						}
						break;
					case 2:
						if (canNotify(eventsToNotify, EventType.ISSUE_UPDATED_ID)) {
							String msg = getMessage(issue, user, i18n.getText("hipchat.updated.message"), true);
							notifyRooms(roomsToNotify, msg, notify);
						}
						break;
					case 3:
						if (canNotify(eventsToNotify, EventType.ISSUE_ASSIGNED_ID)) {
							ApplicationUser assignee = issue.getAssignee();
							String msg = getMessage(issue, user, i18n.getText(
									"hipchat.assigned.message",
									applicationProperties.getBaseUrl(UrlMode.ABSOLUTE)
											+ "/secure/ViewProfile.jspa?name="
											+ StringEscapeUtils.escapeHtml(assignee.getName()),
									StringEscapeUtils.escapeHtml(assignee.getDisplayName())), true);
							notifyRooms(roomsToNotify, msg, notify);
						}
						break;
					case 8:
						if (canNotify(eventsToNotify, EventType.ISSUE_DELETED_ID)) {
							String msg = getMessage(issue, user, i18n.getText("hipchat.deleted.message"), false);
							notifyRooms(roomsToNotify, msg, notify);
						}
						break;
					case 6:
						if (canNotify(eventsToNotify, EventType.ISSUE_COMMENTED_ID)) {
							Comment comment = issueEvent.getComment();

							String msg = getMessage(
									issue,
									user,
									i18n.getText(
											"hipchat.commented.message",
											applicationProperties.getBaseUrl(UrlMode.ABSOLUTE)
													+ "/browse/"
													+ issue.getKey()
													+ "?focusedCommentId="
													+ comment.getId()
													+ "&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel#comment-"
													+ comment.getId()), true);

							for (String room : roomsToNotify) {
								String hipChatApiToken = configurationManager.getHipChatApiToken();
								if (hipChatApiToken != null) {
									hipChatApiClient.notifyRoom(hipChatApiToken, room, msg, null, "html", notify);
									if ("Yes".equals(configurationManager.getShowComments())) {
										hipChatApiClient.notifyRoom(hipChatApiToken, room, comment.getBody(), null,
												"text", notify);
									}
								}
							}
						}
						break;
					case 9:
						GenericValue changeLog = issueEvent.getChangeLog();
						if (changeLog != null) {
							Long changeLogId = changeLog.getLong("id");
							List<GenericValue> changeItems = this.delegator.findByAnd("ChangeItem",
									MapBuilder.build("group", changeLogId, "field", "Project"));
							if (changeItems != null && changeItems.size() > 0) {
								String oldProjectId = changeItems.get(0).getString("oldvalue");
								Project oldProject = this.projectManager.getProjectObj(new Long(oldProjectId));
								String newProjectName = changeItems.get(0).getString("newstring");
								if (oldProject != null) {
									String msg = getMessage(
											issue,
											user,
											i18n.getText("hipchat.moved.message", oldProject.getName(), newProjectName),
											true);

									List<String> eventsToNotifyForOldProject = this.configurationManager
											.getProjectEvents(oldProject.getKey());
									if (canNotify(eventsToNotify, EventType.ISSUE_MOVED_ID)
											&& canNotify(eventsToNotifyForOldProject, EventType.ISSUE_MOVED_ID)) {
										Set<String> allRooms = new HashSet<String>(roomsToNotify);
										allRooms.addAll(this.configurationManager.getHipChatRooms(oldProject.getKey()));
										notifyRooms(new ArrayList<String>(allRooms), msg, notify);
									} else if (canNotify(eventsToNotify, EventType.ISSUE_MOVED_ID)) {
										notifyRooms(roomsToNotify, msg, notify);
									} else if (canNotify(eventsToNotifyForOldProject, EventType.ISSUE_MOVED_ID)) {
										notifyRooms(this.configurationManager.getHipChatRooms(oldProject.getKey()),
												msg, notify);
									}
								}
							}
						}
						break;
					default:
						if (canNotify(eventsToNotify, -1L)) {
							GenericValue statusLog = issueEvent.getChangeLog();
							if (statusLog != null) {
								Long changeLogId = statusLog.getLong("id");
								List<GenericValue> changeItems = this.delegator.findByAnd("ChangeItem",
										MapBuilder.build("group", changeLogId, "field", "status"));
								if (changeItems != null && changeItems.size() > 0) {
									String oldStatus = changeItems.get(0).getString("oldstring");
									String newStatus = changeItems.get(0).getString("newstring");

									String msg = getMessage(issue, user,
											i18n.getText("hipchat.status.changed.message", oldStatus, newStatus), true);
									notifyRooms(roomsToNotify, msg, notify);
								}
							}
						}
						break;
					}
				}
			}
		} catch (SearchException e) {
			log.error("Error filtering using JQL rule:" + jqlFilter, e);
		}
	}

	private boolean canNotify(List<String> eventsToNotify, Long eventType) {
		String event = eventType.equals(-1L) ? "-1L" : eventType.toString();
		return eventsToNotify.size() == 0 || eventsToNotify.contains(event);
	}

	private String getMessage(Issue issue, ApplicationUser user, String message, boolean linkToIssue) {
		String issueString = linkToIssue ? "<a href=\"" + applicationProperties.getBaseUrl(UrlMode.ABSOLUTE)
				+ "/browse/" + issue.getKey() + "\"><b>" + issue.getKey() + "</b></a>: " + issue.getSummary() + " "
				: "<b>" + issue.getKey() + ":</b> " + issue.getSummary() + " ";
		String msg = "<img src=\"" + applicationProperties.getBaseUrl(UrlMode.ABSOLUTE)
				+ issue.getIssueType().getIconUrl() + "\" width=16 height=16 />&nbsp;" + issueString + message
				+ " by <a href=\"" + applicationProperties.getBaseUrl(UrlMode.ABSOLUTE)
				+ "/secure/ViewProfile.jspa?name=" + StringEscapeUtils.escapeHtml(user.getName()) + "\">"
				+ StringEscapeUtils.escapeHtml(user.getDisplayName()) + "</a>.";
		return msg;
	}

	private void notifyRooms(List<String> roomsToNotify, String msg, String notify) throws ResponseException {
		String authToken = configurationManager.getHipChatApiToken();
		if (authToken != null) {
			for (String room : roomsToNotify) {
				hipChatApiClient.notifyRoom(authToken, room, msg, null, "html", notify);
			}
		}
	}

	@Override
	public void destroy() throws Exception {
		this.eventPublisher.unregister(this);
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		this.eventPublisher.register(this);
	}
}
