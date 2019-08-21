/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.mediation.transport.handlers.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.wso2.carbon.utils.ConfigurationContextService;

/**
 * Declarative service component Integrator component.
 */
@Component(
        name = "org.wso2.carbon.integrator.core.internal.ContextServiceComponent",
        immediate = true)
public class ContextServiceComponent {

    private static final Log log = LogFactory.getLog(ContextServiceComponent.class);

    private static ConfigurationContextService contextService;

    @Activate
    protected void activate(ComponentContext context) {

        log.debug("Activating Integrator component");
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {

        log.debug("Deactivating Integrator component");
    }

    @Reference(
            name = "configuration.context.service",
            service = org.wso2.carbon.utils.ConfigurationContextService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetConfigurationContextService")
    protected void setConfigurationContextService(ConfigurationContextService contextService) {

        setContextService(contextService);
    }

    protected static void setContextService(ConfigurationContextService contextService) {

        ContextServiceComponent.contextService = contextService;
    }

    protected void unsetConfigurationContextService(ConfigurationContextService contextService) {

        unsetConfigurationService();
    }

    protected static void unsetConfigurationService() {

        ContextServiceComponent.contextService = null;
    }

    public static ConfigurationContextService getContextService() {

        return contextService;
    }

}
