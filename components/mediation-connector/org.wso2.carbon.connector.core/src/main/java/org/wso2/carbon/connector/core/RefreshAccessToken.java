/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.connector.core;

import org.apache.axiom.om.OMText;
import org.apache.commons.lang.StringUtils;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseLog;
import org.apache.synapse.config.Entry;
import org.apache.synapse.registry.Registry;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * This class can be used by connectors to refresh OAuth 2.0 access tokens by setting the following mandatory
 * properties in message context: uri.var.hostName, uri.var.refreshToken. By default this class constructs the refresh
 * url in the format "{uri.var.hostName}/services/oauth2/token?grant_type=refresh_token&client_id=
 * {uri.var.clientId}&client_secret={uri.var.clientSecret}&refresh_token={uri.var.refreshToken}&format=json".
 * Here client_id and client_secret are optional. If you want to use a different url please set the custom url to
 * uri.var.customRefreshUrl in message context prior to using this class mediator.
 *
 * After refresh call this will set the uri.var.accessToken, and uri.var.apiUrl in the message context to be used by
 * subsequent calls.
 */
public class RefreshAccessToken extends AbstractConnector {
    protected static final String PROPERTY_PREFIX = "uri.var.";

    @Override
    public void connect(MessageContext messageContext) throws ConnectException {
        Registry registry = messageContext.getConfiguration().getRegistry();
        String accessTokenRegistryPath = (String) messageContext.getProperty(PROPERTY_PREFIX +
                "accessTokenRegistryPath");
        if (StringUtils.isEmpty(accessTokenRegistryPath)) {
            throw new ConnectException("Access token registry path not provided for access token storage and reuse.");
        }
        handleRefresh(messageContext, registry, accessTokenRegistryPath);
    }

    /**
     *
     * @param messageContext
     * @param registry
     * @param accessTokenRegistryPath
     * @return returns a boolean. true - token refresh is needed. false - token refresh is not needed
     */
    protected boolean reuseSavedAccessToken(MessageContext messageContext, Registry registry,
                                            String accessTokenRegistryPath) {
        String savedAccessToken = null;
        Entry propEntry = messageContext.getConfiguration().getEntryDefinition(accessTokenRegistryPath);
        if (propEntry == null) {
            propEntry = new Entry();
            propEntry.setType(Entry.REMOTE_ENTRY);
            propEntry.setKey(accessTokenRegistryPath);
        }
        registry.getResource(propEntry, new Properties());
        if (propEntry.getValue() != null) {
            if (propEntry.getValue() instanceof OMText) {
                savedAccessToken = ((OMText) propEntry.getValue()).getText();
            } else {
                savedAccessToken = propEntry.getValue().toString();
            }
            messageContext.setProperty(PROPERTY_PREFIX + "accessToken", savedAccessToken);
            return false;
        } else {
            return true;
        }
    }

    protected void handleRefresh(MessageContext messageContext, Registry registry, String accessTokenRegistryPath)
            throws ConnectException {
        SynapseLog synLog = getLog(messageContext);
        Set propertyKeySet = messageContext.getPropertyKeySet();
        propertyKeySet.remove("Accept-Encoding");

        if (synLog.isTraceOrDebugEnabled()) {
            synLog.traceOrDebug("Start : Salesforce Refresh Access Token mediator.");

            if (synLog.isTraceTraceEnabled()) {
                synLog.traceTrace("Message : " + messageContext.getEnvelope());
            }
        }

        try {
            String jsonResponse = sendPost(messageContext);
            extractAndSetPropertyAndRegistryResource(messageContext, jsonResponse, registry, accessTokenRegistryPath);
        } catch (IOException e) {
            synLog.error(e);
            throw new ConnectException(e, "Error while executing POST request to refresh the access token");
        } catch (JSONException e) {
            synLog.error(e);
            throw new ConnectException(e, "Error while processing the response message");
        } finally {
            propertyKeySet.remove("Cache-Control");
            propertyKeySet.remove("Pragma");
        }
    }

    protected String getPostData(MessageContext messageContext) {
        String customRefreshUrl = (String) messageContext.getProperty(PROPERTY_PREFIX + "customRefreshUrl");

        if (StringUtils.isNotEmpty(customRefreshUrl)) {
            return customRefreshUrl;
        } else {
            StringBuilder urlStringBuilder = new StringBuilder();
            urlStringBuilder.append("grant_type=refresh_token");
            String clientId = (String) messageContext.getProperty(PROPERTY_PREFIX + "clientId");
            if (StringUtils.isNotEmpty(clientId)) {
                urlStringBuilder.append("&client_id=").append(clientId);
            }
            String clientSecret = (String) messageContext.getProperty(PROPERTY_PREFIX + "clientSecret");
            if (StringUtils.isNotEmpty(clientSecret)) {
                urlStringBuilder.append("&client_secret=").append(clientSecret);
            }
            urlStringBuilder.append("&refresh_token=").append(messageContext.getProperty(PROPERTY_PREFIX +
                    "refreshToken"));
            urlStringBuilder.append("&format=json");
            return urlStringBuilder.toString();
        }
    }

    protected void extractAndSetPropertyAndRegistryResource(MessageContext messageContext,
                                                            String jsonResponse,
                                                            Registry registry, String accessTokenRegistryPath)
            throws IOException, ConnectException, JSONException {
        JSONObject jsonObject = new JSONObject(jsonResponse);

        String accessToken = jsonObject.getString("access_token");
        messageContext.setProperty(PROPERTY_PREFIX + "accessToken", accessToken);

        if(!jsonObject.isNull("instance_url")) {
            String instanceUrl = jsonObject.getString("instance_url");
            messageContext.setProperty(PROPERTY_PREFIX + "apiUrl", instanceUrl);
        }

        String systemTime = Long.toString(System.currentTimeMillis());

        if(StringUtils.isNotEmpty(accessToken)) {
            registry.newNonEmptyResource(accessTokenRegistryPath, false, "text/plain", systemTime, "timestamp");
            registry.updateResource(accessTokenRegistryPath, accessToken);
        }
    }

    private String sendPost(MessageContext messageContext) throws IOException, ConnectException {

        String tokenEndpoint = messageContext.getProperty(PROPERTY_PREFIX + "tokenEndpointUrl").toString();
        byte[] postData = getPostData(messageContext).getBytes( StandardCharsets.UTF_8 );
        URL tokenEndpointUrl = new URL(tokenEndpoint);
        HttpURLConnection connection = (HttpURLConnection) tokenEndpointUrl.openConnection();
        try {
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                wr.write(postData);
            }
            if (Pattern.matches("4[0-9][0-9]", String.valueOf(connection.getResponseCode()))) {
                throw new ConnectException("Refresh call returned HTTP Status code " +
                        connection.getResponseCode() + ". " +
                        connection.getResponseMessage());
            }
            if (connection.getResponseMessage() == null) {
                throw new ConnectException("Empty response received for refresh access token call");
            } else {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder content;
                    String line;
                    content = new StringBuilder();
                    while ((line = br.readLine()) != null) {
                        content.append(line);
                        content.append(System.lineSeparator());
                    }
                    return content.toString();
                }
            }
        } finally {
            connection.disconnect();
        }
    }
}
