package com.go2group.hipchat.utils;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.query.Query;

public class SearchUtil {

	public static boolean matchesJql(SearchService searchService, String jql, Issue issue, ApplicationUser caller) throws SearchException {
		SearchService.ParseResult parseResult = searchService.parseQuery(caller, jql);
		if (parseResult.isValid()) {
			Query query = JqlQueryBuilder.newBuilder(parseResult.getQuery()).where().and().issue().eq(issue.getKey())
					.buildQuery();

			return searchService.searchCount(caller, query) > 0;

		}
		return false;
	}

}
