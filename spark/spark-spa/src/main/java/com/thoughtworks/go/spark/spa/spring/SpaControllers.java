/*
 * Copyright 2018 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thoughtworks.go.spark.spa.spring;

import com.thoughtworks.go.plugin.access.analytics.AnalyticsExtension;
import com.thoughtworks.go.server.service.PipelineConfigService;
import com.thoughtworks.go.server.service.SecurityService;
import com.thoughtworks.go.server.service.support.toggle.FeatureToggleService;
import com.thoughtworks.go.server.service.support.toggle.Toggles;
import com.thoughtworks.go.spark.SparkController;
import com.thoughtworks.go.spark.spa.*;
import com.thoughtworks.go.spark.spring.SPAAuthenticationHelper;
import com.thoughtworks.go.spark.spring.SparkSpringController;
import com.thoughtworks.go.util.SystemEnvironment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SpaControllers implements SparkSpringController {
    private static final String DEFAULT_LAYOUT_PATH = "layouts/single_page_app.vm";
    private static final String COMPONENT_LAYOUT_PATH = "layouts/component_layout.vm";

    private final List<SparkController> sparkControllers = new ArrayList<>();

    @Autowired
    public SpaControllers(SPAAuthenticationHelper authenticationHelper, VelocityTemplateEngineFactory templateEngineFactory,
                          SecurityService securityService, PipelineConfigService pipelineConfigService,
                          SystemEnvironment systemEnvironment, AnalyticsExtension analyticsExtension,
                          FeatureToggleService featureToggleService) {

        LayoutTemplateProvider componentAware = () -> featureToggleService.isToggleOn(Toggles.COMPONENTS) ? COMPONENT_LAYOUT_PATH : DEFAULT_LAYOUT_PATH;
        LayoutTemplateProvider defaultTemplate = () -> DEFAULT_LAYOUT_PATH;

        final LayoutTemplateProvider elasticProfileSPAPath = () -> featureToggleService.isToggleOn(Toggles.USE_OLD_ELASTIC_PROFILE_SPA) ? DEFAULT_LAYOUT_PATH : COMPONENT_LAYOUT_PATH;

        sparkControllers.add(new RolesController(authenticationHelper, templateEngineFactory.create(RolesController.class, defaultTemplate)));
        sparkControllers.add(new AuthConfigsController(authenticationHelper, templateEngineFactory.create(AuthConfigsController.class, defaultTemplate)));
        sparkControllers.add(new AgentsControllerController(authenticationHelper, templateEngineFactory.create(AgentsControllerController.class, defaultTemplate), securityService, systemEnvironment));
        sparkControllers.add(new PluginsController(authenticationHelper, templateEngineFactory.create(PluginsController.class, componentAware), securityService));
        sparkControllers.add(new ElasticProfilesController(authenticationHelper, templateEngineFactory.create(ElasticProfilesController.class, elasticProfileSPAPath)));
        sparkControllers.add(new NewDashboardController(authenticationHelper, templateEngineFactory.create(NewDashboardController.class, defaultTemplate), securityService, systemEnvironment, pipelineConfigService));
        sparkControllers.add(new ArtifactStoresController(authenticationHelper, templateEngineFactory.create(ArtifactStoresController.class, defaultTemplate)));
        sparkControllers.add(new AnalyticsController(authenticationHelper, templateEngineFactory.create(AnalyticsController.class, defaultTemplate), systemEnvironment, analyticsExtension, pipelineConfigService));
        sparkControllers.add(new DataSharingSettingsController(authenticationHelper, templateEngineFactory.create(DataSharingSettingsController.class, defaultTemplate)));
        sparkControllers.add(new DrainModeController(authenticationHelper, templateEngineFactory.create(DrainModeController.class, () -> COMPONENT_LAYOUT_PATH), featureToggleService));
        sparkControllers.add(new ConfigReposController(authenticationHelper, templateEngineFactory.create(ConfigReposController.class, () -> COMPONENT_LAYOUT_PATH)));
        sparkControllers.add(new KitchenSinkController(templateEngineFactory.create(KitchenSinkController.class, () -> COMPONENT_LAYOUT_PATH)));
    }

    @Override
    public void setupRoutes() {
        for (SparkController sparkController : sparkControllers) {
            sparkController.setupRoutes();
        }
    }
}
