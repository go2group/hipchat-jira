package com.go2group.jira.webwork;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.go2group.hipchat.HipChatProxyClient;
import com.go2group.hipchat.HipChatProxyClient.Room;
import com.go2group.hipchat.components.ConfigurationManager;

public class AnnouncementAction extends JiraWebActionSupport {
	private static final long serialVersionUID = -7422430977881201919L;

//	private static final Logger log = LoggerFactory.getLogger(AnnouncementAction.class);
	private final HipChatProxyClient hipChatApiClient;
	private final ConfigurationManager configurationManager;

	private Collection<Room> rooms;
	private String[] roomsToNotify;
	private boolean messagePosted;
	private String message;
	private String color;
	private String format;
	private String roomOption;
	private String notify;

	public AnnouncementAction(HipChatProxyClient hipChatApiClient, ConfigurationManager configurationManager) {
		this.hipChatApiClient = hipChatApiClient;
		this.configurationManager = configurationManager;
	}

	@Override
	public String doDefault() throws Exception {
		String authToken = configurationManager.getHipChatApiToken();
		rooms = this.hipChatApiClient.getRooms(authToken);
		return INPUT;
	}

	@Override
	public String doExecute() throws Exception {
		if (message != null) {
			String authToken = configurationManager.getHipChatApiToken();
			if (authToken != null) {
				rooms = this.hipChatApiClient.getRooms(authToken);
				//message = getMessageWithUrls(message);
				this.messagePosted = true;
				if ("all".equals(roomOption)) {
					for (Room room : rooms) {
						this.hipChatApiClient.notifyRoom(authToken, room.getRoomId().toString(), message, color, format, getNotify());
					}
				} else if ("subscribed".equals(roomOption)) {
					Set<String> rooms = new HashSet<String>();
					List<Project> projects = ComponentAccessor.getProjectManager().getProjectObjects();
					for (Project project : projects) {
						List<String> roomsToNotify = this.configurationManager.getHipChatRooms(project.getKey());
						rooms.addAll(roomsToNotify);
					}
					for (String room : rooms) {
						this.hipChatApiClient.notifyRoom(authToken, room, message, color, format, getNotify());
					}
				} else if (roomsToNotify != null) {
					for (String room : roomsToNotify) {
						this.hipChatApiClient.notifyRoom(authToken, room, message, color, format, getNotify());
					}
				} else {
					this.messagePosted = false;
				}
			}
		}
		return SUCCESS;
	}

	public Collection<Room> getRooms() {
		return rooms;
	}

	public void setRooms(Collection<Room> rooms) {
		this.rooms = rooms;
	}

	public String[] getRoomsToNotify() {
		return roomsToNotify;
	}

	public void setRoomsToNotify(String[] roomsToNotify) {
		this.roomsToNotify = roomsToNotify;
	}

	public boolean isMessagePosted() {
		return messagePosted;
	}

	public void setMessagePosted(boolean messagePosted) {
		this.messagePosted = messagePosted;
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

	public String getFormat() {
		return format;
	}

	public void setFormat(String format) {
		this.format = format;
	}

	public String getNotify() {
		return notify;
	}

	public void setNotify(String notify) {
		this.notify = notify;
	}
}
