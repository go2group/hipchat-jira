package com.go2group.jira.workflow;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.plugin.workflow.AbstractWorkflowPluginFactory;
import com.atlassian.jira.plugin.workflow.WorkflowPluginFunctionFactory;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.sal.api.net.ResponseException;
import com.go2group.hipchat.HipChatProxyClient;
import com.go2group.hipchat.components.ConfigurationManager;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.opensymphony.workflow.loader.AbstractDescriptor;
import com.opensymphony.workflow.loader.FunctionDescriptor;

/*
This is the factory class responsible for dealing with the UI for the post-function.
This is typically where you put default values into the velocity context and where you store user input.
 */

public class HipChatPostFunctionFactory extends AbstractWorkflowPluginFactory implements WorkflowPluginFunctionFactory {

    public static final String ROOMS_TO_NOTIFY_CSV_IDS_PARAM = "roomsToNotifyCsvIds";
    public static final String JQL_FILTER_PARAM = "jql";

    private final HipChatProxyClient hipChatApiClient;
    private final ConfigurationManager configurationManager;
    private final SearchService searchService;
    private final JiraAuthenticationContext authenticationContext;

    public HipChatPostFunctionFactory(HipChatProxyClient hipChatApiClient, ConfigurationManager configurationManager,
                                      SearchService searchService, JiraAuthenticationContext authenticationContext) {
        this.hipChatApiClient = hipChatApiClient;
        this.configurationManager = configurationManager;
        this.searchService = searchService;
        this.authenticationContext = authenticationContext;
    }

    @Override
    protected void getVelocityParamsForInput(Map<String, Object> velocityParams) {
        EditDto editDto;

        String hipChatApiToken = configurationManager.getHipChatApiToken();
		if (!Strings.isNullOrEmpty(hipChatApiToken)) {
            try {
                editDto = new EditDto(findAvailableRoomsDto(hipChatApiToken));
            } catch (ResponseException e) {
                editDto = new EditDto(true, true);
            }
        } else {
            editDto = new EditDto(false, false);
        }

        velocityParams.put("dto", editDto);
    }

    @Override
    protected void getVelocityParamsForEdit(Map<String, Object> velocityParams, AbstractDescriptor descriptor) {
        EditDto editDto;

        String hipChatApiToken = configurationManager.getHipChatApiToken();
		if (!Strings.isNullOrEmpty(hipChatApiToken)) {

            FunctionDescriptor functionDescriptor = (FunctionDescriptor) descriptor;

            String jql = Strings.nullToEmpty((String) functionDescriptor.getArgs().get(JQL_FILTER_PARAM));

            Iterable<String> roomsToNotifyIds = Splitter.on(",").split(Strings.nullToEmpty((String) functionDescriptor.getArgs().get(ROOMS_TO_NOTIFY_CSV_IDS_PARAM)));

            try {
                editDto = new EditDto(Lists.newArrayList(roomsToNotifyIds), jql, findAvailableRoomsDto(hipChatApiToken));
            } catch (ResponseException e) {
                editDto = new EditDto(true, true);
            }
        } else {
            editDto = new EditDto(false, false);
        }

        velocityParams.put("dto", editDto);
    }

    @Override
    protected void getVelocityParamsForView(Map<String, Object> velocityParams, AbstractDescriptor descriptor) {
        ViewDto viewDto;
        String hipChatApiToken = configurationManager.getHipChatApiToken();
		if (!Strings.isNullOrEmpty(hipChatApiToken)) {

            FunctionDescriptor functionDescriptor = (FunctionDescriptor) descriptor;

            String jql = Strings.nullToEmpty((String) functionDescriptor.getArgs().get(JQL_FILTER_PARAM));

            try {
                // seems to be counterintuitive to load the full list, but its actually more efficient than a request per room starting from 2 rooms
                final ImmutableMap<Long, HipChatProxyClient.Room> roomsById = Maps.uniqueIndex(hipChatApiClient.getRooms(hipChatApiToken), new Function<HipChatProxyClient.Room, Long>() {
                    @Override
                    public Long apply(HipChatProxyClient.Room from) {
                        return from.getRoomId();
                    }
                });

                Iterable<String> roomsToNotifyIds = Splitter.on(",").omitEmptyStrings().split(Strings.nullToEmpty((String) functionDescriptor.getArgs().get(ROOMS_TO_NOTIFY_CSV_IDS_PARAM)));
                viewDto = new ViewDto(Lists.newArrayList(Iterables.transform(roomsToNotifyIds, new Function<String, RoomDto>() {
                    @Override
                    public RoomDto apply(String from) {
                        Long roomId = Long.valueOf(from);
                        return new RoomDto(from, roomsById.containsKey(roomId) ? roomsById.get(roomId).getName() : null);
                    }
                })), jql);
            } catch (ResponseException e) {
                viewDto = new ViewDto(true, true);
            }
        } else {
            viewDto = new ViewDto(false, false);
        }

        velocityParams.put("dto", viewDto);
    }


    public ImmutableMap<String, String> getDescriptorParams(Map<String, Object> formParams) {
        String[] roomsIds = (String[]) formParams.get("roomsToNotify");
        String roomsToNotifyCsvIds = roomsIds != null ? Joiner.on(",").join(roomsIds) : "";
        String jql = extractSingleParam(formParams, "jql");

        // the current user (admin) probably won't be the user who will run the query, but it's sufficient to just test if query is valid
        SearchService.ParseResult parseResult = searchService.parseQuery(authenticationContext.getLoggedInUser(), jql);
        if (!parseResult.isValid()) {
            throw new IllegalArgumentException("The supplied JQL query is not valid");
        }

        return ImmutableMap.of(ROOMS_TO_NOTIFY_CSV_IDS_PARAM, roomsToNotifyCsvIds, JQL_FILTER_PARAM, jql);
    }

    private Collection<RoomDto> findAvailableRoomsDto(String hipChatApiToken) throws ResponseException {
        return Collections2.transform(hipChatApiClient.getRooms(hipChatApiToken), new Function<HipChatProxyClient.Room, RoomDto>() {
            @Override
            public RoomDto apply(HipChatProxyClient.Room from) {
                return new RoomDto(from.getRoomId().toString(), from.getName());
            }
        });
    }

    public static class ViewDto {

        private final String jql;
        private final Collection<RoomDto> roomsToNotify;
        private final boolean apiTokenConfigured;
        private final boolean responseError;

        public ViewDto(Collection<RoomDto> roomsToNotify, String jql) {
            this.roomsToNotify = roomsToNotify;
            this.jql = jql;
            this.apiTokenConfigured = true;
            this.responseError = false;
        }

        public ViewDto(boolean apiTokenConfigured, boolean responseError) {
            this.roomsToNotify = Collections.emptyList();
            this.jql = null;
            this.apiTokenConfigured = apiTokenConfigured;
            this.responseError = responseError;
        }

        public Collection<RoomDto> getRoomsToNotify() {
            return roomsToNotify;
        }

        public String getJql() {
            return jql;
        }

        public boolean isApiTokenConfigured() {
            return apiTokenConfigured;
        }

        public boolean isResponseError() {
            return responseError;
        }
    }

    public static class EditDto {

        private final Collection<String> roomsToNotifyIds;
        private final String jql;
        private final Collection<RoomDto> rooms;
        private final boolean apiTokenConfigured;
        private final boolean responseError;

        public EditDto(Collection<String> roomsToNotifyIds, String jql, Collection<RoomDto> rooms) {
            this.roomsToNotifyIds = roomsToNotifyIds;
            this.jql = jql;
            this.rooms = rooms;
            this.apiTokenConfigured = true;
            this.responseError = false;
        }

        public EditDto(boolean apiTokenConfigured, boolean responseError) {
            this.roomsToNotifyIds = Collections.emptyList();
            this.rooms = Collections.emptyList();
            this.jql = null;
            this.apiTokenConfigured = apiTokenConfigured;
            this.responseError = responseError;
        }

        public EditDto(Collection<RoomDto> rooms) {
            this(Collections.<String>emptyList(), null, rooms);
        }

        public Collection<String> getRoomsToNotifyIds() {
            return roomsToNotifyIds;
        }

        public String getJql() {
            return jql;
        }

        public Collection<RoomDto> getRooms() {
            return rooms;
        }

        public boolean isApiTokenConfigured() {
            return apiTokenConfigured;
        }

        public boolean isResponseError() {
            return responseError;
        }
    }

    public static class RoomDto {

        private final String id;
        private final String name;

        public RoomDto(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }
}