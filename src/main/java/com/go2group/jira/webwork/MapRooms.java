package com.go2group.jira.webwork;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.event.type.EventType;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.go2group.hipchat.HipChatProxyClient;
import com.go2group.hipchat.HipChatProxyClient.Room;
import com.go2group.hipchat.components.ConfigurationManager;
import com.google.common.base.Joiner;

public class MapRooms extends JiraWebActionSupport {
	private static final long serialVersionUID = -2654916556158696190L;

//	private static final Logger log = LoggerFactory.getLogger(MapRooms.class);
	private final HipChatProxyClient hipChatApiClient;
	private final ProjectManager projectManager;
	private final ConfigurationManager configurationManager;

	private final Long[] events = new Long[] { EventType.ISSUE_CREATED_ID, EventType.ISSUE_UPDATED_ID,
			EventType.ISSUE_ASSIGNED_ID, EventType.ISSUE_DELETED_ID, EventType.ISSUE_MOVED_ID, EventType.ISSUE_COMMENTED_ID };

	private Collection<Room> rooms;
	private List<String> roomsToNotifyIds;
	private List<String> eventsToNotifyIds;
	private String[] roomsToNotify;
	private String[] eventsToNotify;
	private String pKey;
	private String workflowEvents = "-1L";
	private String jql;
	private String notify;

	public MapRooms(HipChatProxyClient hipChatApiClient, ProjectManager projectManager,
			ConfigurationManager configurationManager) {
		this.hipChatApiClient = hipChatApiClient;
		this.projectManager = projectManager;
		this.configurationManager = configurationManager;
	}

	@Override
	public String doDefault() throws Exception {
		rooms = this.hipChatApiClient.getRooms(this.configurationManager.getHipChatApiToken());
		roomsToNotifyIds = this.configurationManager.getHipChatRooms(getpKey());
		eventsToNotifyIds = this.configurationManager.getProjectEvents(getpKey());
		jql = this.configurationManager.getProjectJql(getpKey());
		notify = this.configurationManager.getProjectNotify(getpKey());
		if (StringUtils.isEmpty(notify)) {
			notify = "true";
		}
		return INPUT;
	}

	@Override
	public String doExecute() throws Exception {
		String roomsToNotifyCsvIds = roomsToNotify != null ? Joiner.on(",").join(roomsToNotify) : "";
		this.configurationManager.setNotifyRooms(getpKey(), roomsToNotifyCsvIds);
		String eventsToNotifyCsvIds = eventsToNotify != null ? Joiner.on(",").join(eventsToNotify) : "";
		this.configurationManager.setProjectEvents(getpKey(), eventsToNotifyCsvIds);
		this.configurationManager.setProjectJql(getpKey(), jql);
		this.configurationManager.setProjectNotify(getpKey(), notify);
		return getRedirect("/plugins/servlet/project-config/" + getpKey() + "/summary");
	}

	public Project getProject() {
		return this.projectManager.getProjectObjByKey(getpKey());
	}

	public Collection<Room> getRooms() {
		return rooms;
	}

	public List<EventType> getEvents() {
		List<EventType> eventTypes = new ArrayList<EventType>();
		Collection<EventType> allEvents = ComponentAccessor.getEventTypeManager().getEventTypes();
		for (EventType eventType : allEvents) {
			if (Arrays.asList(events).contains(eventType.getId())) {
				eventTypes.add(eventType);
			}
		}
		return eventTypes;
	}

	public void setRooms(Collection<Room> rooms) {
		this.rooms = rooms;
	}

	public List<String> getRoomsToNotifyIds() {
		return roomsToNotifyIds;
	}

	public List<String> getEventsToNotifyIds() {
		return eventsToNotifyIds;
	}

	public String getpKey() {
		return pKey;
	}

	public void setpKey(String pKey) {
		this.pKey = pKey;
	}

	public String[] getRoomsToNotify() {
		return roomsToNotify;
	}

	public void setRoomsToNotify(String[] roomsToNotify) {
		this.roomsToNotify = roomsToNotify;
	}

	public String[] getEventsToNotify() {
		return eventsToNotify;
	}

	public void setEventsToNotify(String[] eventsToNotify) {
		this.eventsToNotify = eventsToNotify;
	}

	public String getWorkflowEvents() {
		return workflowEvents;
	}

	public String getJql() {
		return jql;
	}

	public void setJql(String jql) {
		this.jql = jql;
	}

	public String getNotify() {
		return notify;
	}

	public void setNotify(String notify) {
		this.notify = notify;
	}
}
