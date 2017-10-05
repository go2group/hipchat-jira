package com.go2group.jira.workflow;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.workflow.function.issue.AbstractJiraFunctionProvider;
import com.atlassian.sal.api.ApplicationProperties;
import com.atlassian.sal.api.UrlMode;
import com.atlassian.sal.api.executor.ThreadLocalDelegateExecutorFactory;
import com.atlassian.templaterenderer.TemplateRenderer;
import com.go2group.hipchat.HipChatProxyClient;
import com.go2group.hipchat.components.ConfigurationManager;
import com.go2group.hipchat.utils.SearchUtil;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.WorkflowException;
import com.opensymphony.workflow.loader.ActionDescriptor;
import com.opensymphony.workflow.loader.StepDescriptor;
import com.opensymphony.workflow.loader.WorkflowDescriptor;

public class HipChatPostFunction extends AbstractJiraFunctionProvider {

	public static final String NOTIFICATION_TEMPLATE_PATH = "/templates/postfunctions/hip-chat-notification.vm";
	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final HipChatProxyClient hipChatApiClient;
	private final SearchService searchService;
	private final ApplicationProperties applicationProperties;
	private final ThreadLocalDelegateExecutorFactory threadLocalDelegateExecutorFactory;
	private final Executor threadLocalExecutor;
	private final TemplateRenderer templateRenderer;
	private final ConfigurationManager configurationManager;

	public HipChatPostFunction(ApplicationProperties applicationProperties, SearchService searchService,
			HipChatProxyClient hipChatApiClient, ThreadLocalDelegateExecutorFactory threadLocalDelegateExecutorFactory,
			TemplateRenderer templateRenderer, ConfigurationManager configurationManager) {
		this.applicationProperties = applicationProperties;
		this.hipChatApiClient = hipChatApiClient;
		this.searchService = searchService;
		this.threadLocalDelegateExecutorFactory = threadLocalDelegateExecutorFactory;
		this.templateRenderer = templateRenderer;
		this.configurationManager = configurationManager;
		this.threadLocalExecutor = threadLocalDelegateExecutorFactory.createExecutor(Executors
				.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat(
						getClass().getSimpleName() + "-pool-thread-%d").build()));
	}

	public void execute(Map transientVars, Map args, PropertySet ps) throws WorkflowException {
		if (Strings.isNullOrEmpty(configurationManager.getHipChatApiToken())) {
			return;
		}

		Issue issue = getIssue(transientVars);

		WorkflowDescriptor descriptor = (WorkflowDescriptor) transientVars.get("descriptor");
		Integer actionId = (Integer) transientVars.get("actionId");
		ActionDescriptor action = descriptor.getAction(actionId);
		Issue originalIssue = (Issue) transientVars.get("originalissueobject");
		String firstStepName = "";
		if (originalIssue != null) {
			Status status = originalIssue.getStatus();
			firstStepName = status.getName();
		}

		String actionName = action.getName();
		StepDescriptor endStep = descriptor.getStep(action.getUnconditionalResult().getStep());

		Iterable<String> roomsToNotifyIds = Splitter
				.on(",")
				.omitEmptyStrings()
				.split(Strings.nullToEmpty((String) args.get(HipChatPostFunctionFactory.ROOMS_TO_NOTIFY_CSV_IDS_PARAM)));

		if (roomsToNotifyIds.iterator().hasNext()) {
			String jql = (String) args.get(HipChatPostFunctionFactory.JQL_FILTER_PARAM);

			try {
				if (Strings.isNullOrEmpty(jql) || SearchUtil.matchesJql(searchService, jql, issue, getCallerUser(transientVars, args))) {

					NotificationDto notificationDto = new NotificationDto(applicationProperties.getBaseUrl(UrlMode.AUTO), issue,
							getCallerUser(transientVars, args), firstStepName, endStep, actionName);

					StringWriter messageWriter = new StringWriter();
					templateRenderer.render(NOTIFICATION_TEMPLATE_PATH,
							ImmutableMap.<String, Object> of("dto", notificationDto), messageWriter);

					threadLocalExecutor.execute(threadLocalDelegateExecutorFactory
							.createRunnable(new SendNotificationRunnable(hipChatApiClient, configurationManager, roomsToNotifyIds,
									messageWriter.toString(), logger)));
				}
			} catch (SearchException e) {
				throw new WorkflowException(e);
			} catch (IOException e) {
				throw new WorkflowException(e);
			}
		}
	}

	private static class SendNotificationRunnable implements Runnable {

		private final HipChatProxyClient hipChatApiClient;
		private final Iterable<String> roomsToNotifyIds;
		private final String message;
		private final Logger logger;
		private final ConfigurationManager configurationManager;

		public SendNotificationRunnable(HipChatProxyClient hipChatApiClient, ConfigurationManager configurationManager, Iterable<String> roomsToNotifyIds,
				String message, Logger logger) {
			this.hipChatApiClient = hipChatApiClient;
			this.configurationManager = configurationManager;
			this.roomsToNotifyIds = roomsToNotifyIds;
			this.message = message;
			this.logger = logger;
		}

		@Override
		public void run() {
			String authToken = configurationManager.getHipChatApiToken();
			if (authToken != null) {
				for (String roomsToNotifyId : roomsToNotifyIds) {
					hipChatApiClient.notifyRoom(authToken, roomsToNotifyId, message, null, "html", "false");
				}
			}
		}
	}

	public static class NotificationDto {

		private final String baseUrl;
		private final Issue issue;
		private final ApplicationUser actor;
		private final String firstStepName;
		private final StepDescriptor endStep;
		private final String actionName;

		public NotificationDto(String baseUrl, Issue issue, ApplicationUser actor, String firstStepName, StepDescriptor endStep,
				String actionName) {
			this.baseUrl = baseUrl;
			this.issue = issue;
			this.actor = actor;
			this.firstStepName = firstStepName;
			this.endStep = endStep;
			this.actionName = actionName;
		}

		public String getBaseUrl() {
			return baseUrl;
		}

		public Issue getIssue() {
			return issue;
		}

		public ApplicationUser getActor() {
			return actor;
		}

		public String getFirstStepName() {
			return firstStepName;
		}

		public StepDescriptor getEndStep() {
			return endStep;
		}

		public String getActionName() {
			return actionName;
		}
	}
}