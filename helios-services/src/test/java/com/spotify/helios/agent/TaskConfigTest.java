/*
 * Copyright (c) 2014 Spotify AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.helios.agent;

import com.spotify.helios.common.descriptors.HealthCheck;
import com.spotify.helios.common.descriptors.Job;
import com.spotify.helios.common.descriptors.PortMapping;
import com.spotify.helios.common.descriptors.ServiceEndpoint;
import com.spotify.helios.common.descriptors.ServicePorts;
import com.spotify.helios.serviceregistration.ServiceRegistration;
import com.spotify.helios.serviceregistration.ServiceRegistration.EndpointHealthCheck;
import org.junit.Test;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TaskConfigTest {
  private static final String HOST = "HOST";
  private static final String IMAGE = "spotify:17";
  private static final String PORT_NAME = "default-port";
  private static final int EXTERNAL_PORT = 20000;
  private static final Job JOB = Job.newBuilder()
    .setName("foobar")
    .setCommand(asList("foo", "bar"))
    .setImage(IMAGE)
    .setVersion("4711")
    .addPort(PORT_NAME, PortMapping.of(8080, EXTERNAL_PORT))
    .addRegistration(ServiceEndpoint.of("service", "http"), ServicePorts.of(PORT_NAME))
    .build();

  @Test
  public void testRegistrationWithHttpHealthCheck() throws Exception {
    final String path = "/health";

    final Job job = JOB.toBuilder()
      .setHealthCheck(HealthCheck.newHttpHealthCheck()
        .setPath(path)
        .setPort(PORT_NAME).build())
      .build();

    final TaskConfig taskConfig = TaskConfig.builder()
      .namespace("test")
      .host(HOST)
      .job(job)
      .build();

    ServiceRegistration.Endpoint endpoint = taskConfig.registration().getEndpoints().get(0);
    assertEquals(path, endpoint.getHealthCheck().getPath());
    assertEquals(EndpointHealthCheck.HTTP, endpoint.getHealthCheck().getType());
    assertEquals(EXTERNAL_PORT, endpoint.getPort());
  }

  @Test
  public void testRegistrationWithTcpHealthCheck() throws Exception {
    final Job job = JOB.toBuilder()
      .setHealthCheck(HealthCheck.newTcpHealthCheck()
        .setPort(PORT_NAME).build())
      .build();

    final TaskConfig taskConfig = TaskConfig.builder()
      .namespace("test")
      .host(HOST)
      .job(job)
      .build();

    ServiceRegistration.Endpoint endpoint = taskConfig.registration().getEndpoints().get(0);
    assertEquals(EndpointHealthCheck.TCP, endpoint.getHealthCheck().getType());
    assertEquals(EXTERNAL_PORT, endpoint.getPort());
  }

  @Test
  public void testRegistrationWithoutHealthCheck() throws Exception {
    final TaskConfig taskConfig = TaskConfig.builder()
      .namespace("test")
      .host(HOST)
      .job(JOB)
      .build();

    ServiceRegistration.Endpoint endpoint = taskConfig.registration().getEndpoints().get(0);
    assertNull(endpoint.getHealthCheck());
  }
}
