/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.mediation.transport.handlers.requestprocessors.swagger.format;

import com.google.gson.JsonParser;
import org.apache.axis2.AxisFault;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.rest.API;
import org.wso2.carbon.core.transports.CarbonHttpRequest;
import org.wso2.carbon.core.transports.CarbonHttpResponse;
import org.wso2.carbon.core.transports.HttpGetRequestProcessor;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.rest.api.GenericApiObjectDefinition;
import org.wso2.carbon.rest.api.SwaggerConstants;
import org.wso2.carbon.rest.api.utils.GSONUtils;
import org.yaml.snakeyaml.Yaml;

/**
 * Provides Swagger definition for the API in YAML format.
 */
public class SwaggerYamlProcessor extends SwaggerGenerator implements HttpGetRequestProcessor {

    private static final Log log = LogFactory.getLog(SwaggerYamlProcessor.class);

    /**
     * Process incoming GET request and update the response with the swagger definition for the requested API.
     *
     * @param request              CarbonHttpRequest contains request information
     * @param response             CarbonHttpResponse which will be updated with response information
     * @param configurationContext axis2 configuration context
     * @throws Exception if any exception occurred during definition generation
     */
    public void process(CarbonHttpRequest request, CarbonHttpResponse response,
                        ConfigurationContext configurationContext) throws AxisFault {

        API api = getAPIFromSynapseConfig(request);

        if (api == null) {
            handleException(request.getRequestURI());
        } else {
            //Retrieve from registry
            String responseString;
            try {
                Yaml yamlDefinition = new Yaml();
                String defFromRegistry = retrieveFromRegistry(api, request);
                if (defFromRegistry != null) {
                    JsonParser jsonParser = new JsonParser();
                    responseString =
                            yamlDefinition.dumpAsMap(GSONUtils.gsonJsonObjectToMap(jsonParser.parse(defFromRegistry)));
                } else {
                    responseString = yamlDefinition.dumpAsMap(new GenericApiObjectDefinition(api).getDefinitionMap());
                }
            } catch (RegistryException e) {
                throw new AxisFault("Error occurred while retrieving swagger definition from registry", e);
            }

            updateResponse(response, responseString, SwaggerConstants.CONTENT_TYPE_YAML);
        }
    }
}
