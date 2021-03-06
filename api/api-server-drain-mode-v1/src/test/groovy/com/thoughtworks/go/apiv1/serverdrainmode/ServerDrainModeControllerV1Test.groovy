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

package com.thoughtworks.go.apiv1.serverdrainmode

import com.thoughtworks.go.api.SecurityTestTrait
import com.thoughtworks.go.api.base.JsonOutputWriter
import com.thoughtworks.go.api.spring.ApiAuthenticationHelper
import com.thoughtworks.go.apiv1.serverdrainmode.representers.DrainModeInfoRepresenter
import com.thoughtworks.go.apiv1.serverdrainmode.representers.DrainModeSettingsRepresenter
import com.thoughtworks.go.server.domain.ServerDrainMode
import com.thoughtworks.go.server.service.DrainModeService
import com.thoughtworks.go.server.service.JobInstanceService
import com.thoughtworks.go.server.service.support.toggle.FeatureToggleService
import com.thoughtworks.go.server.service.support.toggle.Toggles
import com.thoughtworks.go.spark.AdminUserSecurity
import com.thoughtworks.go.spark.ControllerTrait
import com.thoughtworks.go.spark.SecurityServiceTrait
import com.thoughtworks.go.util.TestingClock
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mock

import java.sql.Timestamp

import static com.thoughtworks.go.api.base.JsonUtils.toObjectString
import static org.assertj.core.api.Assertions.assertThat
import static org.mockito.Mockito.*
import static org.mockito.MockitoAnnotations.initMocks

class ServerDrainModeControllerV1Test implements SecurityServiceTrait, ControllerTrait<ServerDrainModeControllerV1> {
  @BeforeEach
  void setUp() {
    initMocks(this)
  }

  @Mock
  DrainModeService drainModeService

  @Mock
  FeatureToggleService featureToggleService

  @Mock
  JobInstanceService jobInstanceService

  TestingClock testingClock = new TestingClock()

  @Override
  ServerDrainModeControllerV1 createControllerInstance() {
    new ServerDrainModeControllerV1(new ApiAuthenticationHelper(securityService, goConfigService), drainModeService, jobInstanceService, featureToggleService, testingClock)
  }

  @Nested
  class Get {
    @Nested
    class Security implements SecurityTestTrait, AdminUserSecurity {

      @Override
      String getControllerMethodUnderTest() {
        return "show"
      }

      @Override
      void makeHttpCall() {
        getWithApiHeader(controller.controllerPath('/settings'))
      }
    }

    @Nested
    class AsAdminUser {
      @BeforeEach
      void setUp() {
        enableSecurity()
        loginAsAdmin()
      }

      @Test
      void 'get drain mode settings'() {
        def drainMode = new ServerDrainMode()

        when(drainModeService.get()).thenReturn(drainMode)
        when(featureToggleService.isToggleOn(Toggles.SERVER_DRAIN_MODE_API_TOGGLE_KEY)).thenReturn(true)

        getWithApiHeader(controller.controllerPath('/settings'))

        assertThatResponse()
          .isOk()
          .hasContentType(controller.mimeType)
          .hasBody(toObjectString({ DrainModeSettingsRepresenter.toJSON(it, drainMode) }))
      }

      @Test
      void 'should return not found when SERVER_DRAIN_MODE_API_TOGGLE_KEY is turned off'() {
        def drainMode = new ServerDrainMode()

        when(drainModeService.get()).thenReturn(drainMode)
        when(featureToggleService.isToggleOn(Toggles.SERVER_DRAIN_MODE_API_TOGGLE_KEY)).thenReturn(false)

        getWithApiHeader(controller.controllerPath())

        assertThatResponse()
          .isNotFound()
      }
    }
  }

  @Nested
  class UpdateDrainModeState {
    @Nested
    class Security implements SecurityTestTrait, AdminUserSecurity {

      @Override
      String getControllerMethodUnderTest() {
        return "updateDrainModeState"
      }

      @Override
      void makeHttpCall() {
        post(controller.controllerPath('/settings'), [:])
      }
    }

    @Nested
    class AsAdminUser {
      @BeforeEach
      void setUp() {
        enableSecurity()
        loginAsAdmin()

        when(featureToggleService.isToggleOn(Toggles.SERVER_DRAIN_MODE_API_TOGGLE_KEY)).thenReturn(true)
      }

      @Test
      void 'should return not found when SERVER_DRAIN_MODE_API_TOGGLE_KEY is turned off'() {
        when(featureToggleService.isToggleOn(Toggles.SERVER_DRAIN_MODE_API_TOGGLE_KEY)).thenReturn(false)
        def headers = [
          'accept'      : controller.mimeType,
          'content-type': 'application/json'
        ]

        postWithApiHeader(controller.controllerPath('/settings'), headers, [:])

        assertThatResponse()
          .isNotFound()
      }

      @Test
      void 'update server drain mode settings'() {
        def newDrainModeState = false
        def data = [drain: newDrainModeState]

        def headers = [
          'accept'      : controller.mimeType,
          'content-type': 'application/json'
        ]

        when(drainModeService.get())
          .thenReturn(new ServerDrainMode(newDrainModeState, currentUserLoginName().toString(), testingClock.currentTime()))

        postWithApiHeader(controller.controllerPath('/settings'), headers, data)

        def captor = ArgumentCaptor.forClass(ServerDrainMode.class)
        verify(drainModeService).update(captor.capture())
        def drainModeStateBeingSaved = captor.getValue()
        assertThat(drainModeStateBeingSaved.isDrainMode()).isFalse()
        assertThat(drainModeStateBeingSaved.updatedBy()).isEqualTo(currentUserLoginName().toString())
        assertThat(drainModeStateBeingSaved.updatedOn()).isEqualTo(new Timestamp(testingClock.currentTimeMillis()))

        assertThatResponse()
          .isOk()
          .hasContentType(controller.mimeType)
          .hasJsonBody([
          "_links"   : [
            "self": [
              "href": "http://test.host/go/api/admin/drain_mode/settings"
            ],
            "doc" : [
              "href": "https://api.gocd.org/current/#drain-mode-settings"
            ]
          ],
          "_embedded": [
            "drain"     : false,
            "updated_by": currentUserLoginName().toString(),
            "updated_on": JsonOutputWriter.jsonDate(testingClock.currentTime())
          ]
        ])
      }

      @Test
      void 'should save error out when property drain is not present in payload'() {
        def data = [junk: ""]

        def headers = [
          'accept'      : controller.mimeType,
          'content-type': 'application/json'
        ]

        postWithApiHeader(controller.controllerPath('/settings'), headers, data)

        assertThatResponse()
          .isUnprocessableEntity()
          .hasContentType(controller.mimeType)
          .hasJsonMessage("Json `{\\\"junk\\\":\\\"\\\"}` does not contain property 'drain'")

        verifyZeroInteractions(drainModeService)
      }
    }
  }

  @Nested
  class Info {
    @Nested
    class Security implements SecurityTestTrait, AdminUserSecurity {

      @Override
      String getControllerMethodUnderTest() {
        return "getDrainModeInfo"
      }

      @Override
      void makeHttpCall() {
        getWithApiHeader(controller.controllerPath('/info'))
      }
    }

    @Nested
    class AsAdminUser {
      @BeforeEach
      void setUp() {
        enableSecurity()
        loginAsAdmin()
      }

      @Test
      void 'get drain mode info'() {
        def runningMDUs = []
        def runningJobs = []
        when(drainModeService.getRunningMDUs()).thenReturn(runningMDUs)
        when(jobInstanceService.allBuildingJobs()).thenReturn(runningJobs)
        when(featureToggleService.isToggleOn(Toggles.SERVER_DRAIN_MODE_API_TOGGLE_KEY)).thenReturn(true)

        getWithApiHeader(controller.controllerPath('/info'))

        assertThatResponse()
          .isOk()
          .hasContentType(controller.mimeType)
          .hasBody(toObjectString({ DrainModeInfoRepresenter.toJSON(it, true, runningMDUs, runningJobs) }))
      }

      @Test
      void 'should return not found when SERVER_DRAIN_MODE_API_TOGGLE_KEY is turned off'() {
        when(featureToggleService.isToggleOn(Toggles.SERVER_DRAIN_MODE_API_TOGGLE_KEY)).thenReturn(false)

        getWithApiHeader(controller.controllerPath('/info'))

        assertThatResponse()
          .isNotFound()
      }
    }
  }
}
