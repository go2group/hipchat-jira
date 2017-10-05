/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.go2group.proxy;


import com.atlassian.sal.api.net.*;
import com.go2group.hipchat.components.ConfigurationManager;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;


/**
 * HTTP proxy that forwards requests to HipChat API. We need that because of the Cross-Domain Ajax request limitation.
 * <p/>
 * It adds the <pre><code>auth_token</code></pre> parameter to the request with one configured in for the HipChat plugin.
 * <p/>
 * This proxy is more than inspired by the Apache Pivot <pre><code>ProxyServlet</code></pre> class.
 */
public class HipChatApiProxyServlet extends HttpServlet {

	private static final long serialVersionUID = -3882579098246049863L;
	private final ConfigurationManager configurationManager;
    private final RequestFactory<Request<?, Response>> requestFactory;

    public HipChatApiProxyServlet(ConfigurationManager configurationManager, RequestFactory<Request<?, Response>> requestFactory) {
        this.configurationManager = configurationManager;
        this.requestFactory = requestFactory;
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {

        if (!"GET".equals(request.getMethod())) {
            throw new ServletException("The " + request.getMethod() + " method is not supported by " + getClass().getSimpleName());
        }

        String path = "?auth_token=" + configurationManager.getHipChatApiToken();

        String queryString = request.getQueryString();
        if (queryString != null) {
            path += "&" + queryString;
        }

        URL url;
        try {
            url = new URL(configurationManager.getServerUrl() + path);
        } catch (MalformedURLException exception) {
            throw new ServletException("Unable to construct URL.", exception);
        }

        Request<?, Response> salRequest = requestFactory.createRequest(
                Request.MethodType.GET, url.toString());

        salRequest.setFollowRedirects(false);

        // Write request headers to connection
        Enumeration<String> headerNames = request.getHeaderNames();

        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                Enumeration<String> headerValues = request.getHeaders(headerName);

                while (headerValues.hasMoreElements()) {
                    String headerValue = headerValues.nextElement();

                    if (!salRequest.getHeaders().containsKey(headerName)) {
                        salRequest.setHeader(headerName, headerValue);
                    } else {
                        salRequest.addHeader(headerName, headerValue);
                    }
                }
            }
        }

        try {
            salRequest.execute(new ForwardingResponseHandler(response));
        } catch (ResponseException e) {
            throw new ServletException("Can not forward a request to HipChat API", e);
        }
    }

    private static class ForwardingResponseHandler implements ResponseHandler<Response> {
        private final HttpServletResponse response;

        private ForwardingResponseHandler(HttpServletResponse response) {
            this.response = response;
        }

        @Override
        public void handle(Response salResponse) throws ResponseException {
            InputStream bodyAsStream = null;
            try {
                response.setStatus(salResponse.getStatusCode());

                for (Map.Entry<String, String> headerEntry : salResponse.getHeaders().entrySet()) {
                    if (!"Transfer-Encoding".equalsIgnoreCase(headerEntry.getKey())) {
                        if (response.containsHeader(headerEntry.getKey())) {
                            response.addHeader(headerEntry.getKey(), headerEntry.getValue());
                        } else {
                            response.setHeader(headerEntry.getKey(), headerEntry.getValue());
                        }
                    }
                }

                bodyAsStream = salResponse.getResponseBodyAsStream();
                ByteStreams.copy(bodyAsStream, response.getOutputStream());
                response.flushBuffer();
            } catch (IOException e) {
                throw new ResponseException("Can not forward request because of an IO problem with the peer request", e);
            } finally {
                Closeables.closeQuietly(bodyAsStream);
            }
        }
    }
}