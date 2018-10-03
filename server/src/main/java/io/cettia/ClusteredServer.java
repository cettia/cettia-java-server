/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cettia;

import io.cettia.asity.action.Action;
import io.cettia.asity.action.Actions;
import io.cettia.asity.action.ConcurrentActions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link Server} implementation to support clustered environments.
 *
 * @see <a href="https://cettia.io/guides/cettia-tutorial/#scaling-a-cettia-application">Scaling a Cettia Application</a>
 * @author Donghwan Kim
 */
public class ClusteredServer extends DefaultServer {

  private Actions<Map<String, Object>> publishActions = new ConcurrentActions<>();
  private Action<Map<String, Object>> messageAction = map -> {
    ServerSocketPredicate predicate = (ServerSocketPredicate) map.get("predicate");
    SerializableAction<ServerSocket> action = (SerializableAction<ServerSocket>) map.get("action");
    ClusteredServer.super.find(predicate, action);
  };

  @Override
  public Server find(ServerSocketPredicate predicate, SerializableAction<ServerSocket> action) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("predicate", predicate);
    map.put("action", action);

    publishActions.fire(Collections.unmodifiableMap(map));
    return this;
  }

  /**
   * Adds an action to be called with a message to be published to every node in the cluster.
   */
  public Server onpublish(Action<Map<String, Object>> action) {
    publishActions.add(action);
    return this;
  }

  /**
   * An action to receive a message published from one of nodes in the cluster.
   */
  public Action<Map<String, Object>> messageAction() {
    return messageAction;
  }

}
