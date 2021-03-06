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

import com.google.common.collect.ImmutableList;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerTimeoutException;
import com.spotify.docker.client.ImageNotFoundException;
import com.spotify.docker.client.ImagePullFailedException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.ContainerState;
import com.spotify.docker.client.messages.ImageInfo;
import com.spotify.helios.common.Clock;
import com.spotify.helios.common.HeliosRuntimeException;
import com.spotify.helios.common.descriptors.Job;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.net.URI;
import java.util.concurrent.ExecutionException;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TaskRunnerTest {

  private static final String IMAGE = "spotify:17";
  private static final Job JOB = Job.newBuilder()
      .setName("foobar")
      .setCommand(asList("foo", "bar"))
      .setImage(IMAGE)
      .setVersion("4711")
      .build();
  private static final String HOST = "HOST";

  @Mock private DockerClient mockDocker;
  @Mock private StatusUpdater statusUpdater;
  @Mock private Clock clock;
  @Mock private ContainerDecorator containerDecorator;

  @Test
  public void test() throws Throwable {
    final TaskRunner tr = TaskRunner.builder()
        .delayMillis(0)
        .config(TaskConfig.builder()
                    .namespace("test")
                    .host(HOST)
                    .job(JOB)
                    .containerDecorators(ImmutableList.of(containerDecorator))
                    .build())
        .docker(mockDocker)
        .listener(new TaskRunner.NopListener())
        .build();

    tr.run();

    try {
      tr.resultFuture().get();
      fail("this should throw");
    } catch (Exception t) {
      assertTrue(t instanceof ExecutionException);
      assertEquals(HeliosRuntimeException.class, t.getCause().getClass());
    }
  }

  @Test
  public void testPullTimeoutVariation() throws Throwable {
    doThrow(new DockerTimeoutException("x", new URI("http://example.com"), null))
        .when(mockDocker).pull(IMAGE);

    doThrow(new ImageNotFoundException("not found"))
        .when(mockDocker).inspectImage(IMAGE);

    final TaskRunner tr = TaskRunner.builder()
        .delayMillis(0)
        .config(TaskConfig.builder()
                    .namespace("test")
                    .host(HOST)
                    .job(JOB)
                    .containerDecorators(ImmutableList.of(containerDecorator))
                    .build())
        .docker(mockDocker)
        .listener(new TaskRunner.NopListener())
        .build();

    tr.run();

    try {
      tr.resultFuture().get();
      fail("this should throw");
    } catch (Exception t) {
      assertTrue(t instanceof ExecutionException);
      assertEquals(ImagePullFailedException.class, t.getCause().getClass());
    }
  }

  @Test
  public void testContainerNotRunningVariation() throws Throwable {
    final TaskRunner.NopListener mockListener = mock(TaskRunner.NopListener.class);
    final ImageInfo mockImageInfo = mock(ImageInfo.class);
    final ContainerCreation mockCreation = mock(ContainerCreation.class);
    final HealthChecker mockHealthChecker = mock(HealthChecker.class);

    final ContainerInfo stopped = new ContainerInfo() {
      @Override
      public ContainerState state() {
        final ContainerState state = mock(ContainerState.class);
        when(state.running()).thenReturn(false);
        when(state.error()).thenReturn("container is a potato");
        return state;
      }
    };

    when(mockCreation.id()).thenReturn("potato");
    when(mockDocker.inspectContainer(anyString())).thenReturn(stopped);
    when(mockDocker.inspectImage(IMAGE)).thenReturn(mockImageInfo);
    when(mockDocker.createContainer(any(ContainerConfig.class), anyString()))
            .thenReturn(mockCreation);
    when(mockHealthChecker.check(anyString())).thenReturn(false);

    final TaskRunner tr = TaskRunner.builder()
            .delayMillis(0)
            .config(TaskConfig.builder()
                    .namespace("test")
                    .host(HOST)
                    .job(JOB)
                    .containerDecorators(ImmutableList.of(containerDecorator))
                    .build())
            .docker(mockDocker)
            .listener(mockListener)
            .healthChecker(mockHealthChecker)
            .build();

    tr.run();

    try {
      tr.resultFuture().get();
      fail("this should throw");
    } catch (Exception t) {
      assertTrue(t instanceof ExecutionException);
      assertEquals(RuntimeException.class, t.getCause().getClass());
      verify(mockListener).failed(t.getCause(), "container is a potato");
    }
  }
}
