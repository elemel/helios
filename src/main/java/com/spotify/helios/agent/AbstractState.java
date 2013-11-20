/**
 * Copyright (C) 2013 Spotify AB
 */

package com.spotify.helios.agent;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import com.spotify.helios.common.descriptors.JobId;
import com.spotify.helios.common.descriptors.Task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

abstract class AbstractState implements State {

  private static final Logger log = LoggerFactory.getLogger(AbstractState.class);

  private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
  private final ConcurrentMap<JobId, Task> jobs = Maps.newConcurrentMap();

  @Override
  public void addListener(final Listener listener) {
    listeners.add(listener);
    listener.tasksChanged(this);
  }

  @Override
  public void removeListener(final Listener listener) {
    listeners.remove(listener);
  }

  @Override
  public Map<JobId, Task> getTasks() {
    return ImmutableMap.copyOf(jobs);
  }

  protected void doAddJob(final JobId jobId, final Task descriptor) {
    log.debug("adding container: name={}, descriptor={}", jobId, descriptor);
    jobs.put(jobId, descriptor);
    fireContainersUpdated();
  }

  protected void doUpdateJob(final JobId jobId, final Task descriptor) {
    log.debug("updating container: name={}, descriptor={}", jobId, descriptor);
    jobs.put(jobId, descriptor);
    fireContainersUpdated();
  }

  protected Task doRemoveJob(final JobId jobId) {
    log.debug("removing application: name={}", jobId);
    final Task descriptor;
    descriptor = jobs.remove(jobId);
    fireContainersUpdated();
    return descriptor;
  }

  private void fireContainersUpdated() {
    for (final Listener listener : listeners) {
      try {
        listener.tasksChanged(this);
      } catch (Exception e) {
        log.error("listener threw exception", e);
      }
    }
  }
}
