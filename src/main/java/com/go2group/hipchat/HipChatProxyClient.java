package com.go2group.hipchat;

import java.net.URLEncoder;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import com.atlassian.sal.api.executor.ThreadLocalDelegateExecutorFactory;
import com.atlassian.sal.api.net.Request;
import com.atlassian.sal.api.net.RequestFactory;
import com.atlassian.sal.api.net.Response;
import com.atlassian.sal.api.net.ResponseException;
import com.atlassian.sal.api.net.ReturningResponseHandler;
import com.go2group.hipchat.components.ConfigurationManager;
import com.go2group.hipchat.utils.InvalidAuthTokenException;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class HipChatProxyClient implements DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(HipChatProxyClient.class);
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().configure(
			DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	private final ConfigurationManager configurationManager;
	private final RequestFactory<Request<?, Response>> requestFactory;
	private final ExecutorService executorService;
	private final ThreadLocalDelegateExecutorFactory executorFactory;

	public HipChatProxyClient(ConfigurationManager configurationManager,
			RequestFactory<Request<?, Response>> requestFactory, ThreadLocalDelegateExecutorFactory executorFactory) {
		this.configurationManager = configurationManager;
		this.requestFactory = requestFactory;
		this.executorFactory = executorFactory;
		this.executorService = executorFactory.createExecutorService(Executors.newFixedThreadPool(1));
	}
	
	public Collection<Room> getRooms() throws ResponseException {
		return getRooms(configurationManager.getHipChatApiToken(), configurationManager.getServerUrl());
	}
	
	public Collection<Room> getRooms(final String authToken) throws ResponseException {
		return getRooms(authToken, configurationManager.getServerUrl());
	}

	public Collection<Room> getRooms(final String authToken, final String serverUrl) throws ResponseException {
		Preconditions.checkState(!Strings.isNullOrEmpty(authToken),
				"The HipCHat API OAuth token can not be empty");

		try {
			String url = serverUrl + "/v1/rooms/list?auth_token="
					+ URLEncoder.encode(authToken, Charsets.UTF_8.toString());
			Request<?, Response> request = requestFactory.createRequest(Request.MethodType.GET, url);
			String response = request.execute();
			log.debug("Response from Hipchat on getRooms:"+response);
			
			JsonParser jsonParser = OBJECT_MAPPER.getJsonFactory().createJsonParser(response);

			// skip root node and go to the rooms array directly
			while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
				if ("rooms".equals(jsonParser.getCurrentName())) {
					jsonParser.nextValue();
					return jsonParser.<List<Room>> readValueAs(new TypeReference<List<Room>>() {
					});
				}
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new ResponseException(e);
		}
		throw new ResponseException("Unable to parse API response, can not find the rooms JSON property");
	}

	/**
	 * Send a message to a room. This method does not block and executes the
	 * request asynchronously.
	 * 
	 * @param room
	 *            ID or name of the room.
	 * @param message
	 *            The message body. Must be valid XHTML. HTML entities must be
	 *            escaped (e.g.: &amp; instead of &). May contain basic tags: a,
	 *            b, i, strong, em, br, img, pre, code. 5000 characters max.
	 */
	public void notifyRoom(final String authToken, final String room, final String message, final String color,
			final String format, final String notify) {
		if (StringUtils.isEmpty(authToken)) {
			return;
		}
		final boolean notifyRooms = StringUtils.isEmpty(notify) ? false : Boolean.parseBoolean(notify);

		Runnable postRequest = new Runnable() {
			@Override
			public void run() {
				final String url = configurationManager.getServerUrl() + "/v1/rooms/message?auth_token=" + authToken;
				final Request<?, Response> request = requestFactory.createRequest(Request.MethodType.POST, url);
				request.addRequestParameters("room_id", room, "from", "JIRA", "message", message, "color",
						color == null ? "yellow" : color, "format", "json", "message_format", format, "notify",
						notifyRooms ? "1" : "0");
				try {
					String response  = request.executeAndReturn(new ResponseBodyReturningHandler());
					log.debug("Response from Hipchat on notifyRoom:"+response);
				} catch (ResponseException e) {
					log.error("Failed to notify rooms", e);
				}
			}
		};
		Runnable executeRequest = executorFactory.createRunnable(postRequest);
		executorService.execute(executeRequest);
	}

	private class ResponseBodyReturningHandler implements ReturningResponseHandler<Response, String> {
		@Override
		public String handle(Response response) throws ResponseException {
			if (response.getStatusCode() == 401) {
				throw new InvalidAuthTokenException();
			}
			return response.getResponseBodyAsString();
		}
	}

	/**
	 * A simple mapping class for HipCHat room. There are only needed
	 * properties.
	 */
	public static class Room {

		private final Long roomId;
		private final String name;

		@JsonCreator
		public Room(@JsonProperty("room_id") Long roomId, @JsonProperty("name") String name) {
			this.roomId = roomId;
			this.name = name;
		}

		public Long getRoomId() {
			return roomId;
		}

		public String getName() {
			return name;
		}
	}

	@Override
	public void destroy() throws Exception {
		executorService.shutdown();
	}
}