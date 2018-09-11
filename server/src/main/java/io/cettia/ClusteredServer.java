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

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link Server} implementation for clustering.
 * <p/>
 * With this implementation, {@code server.all(action)} have {@code action} be executed with
 * every socket in every server in the cluster.
 * <p/>
 * This implementation adopts the publish and subscribe model from Java Message Service to
 * support clustering. Here, the exchanged message represents method invocation to be executed by
 * every server in the cluster. The following methods create such messages.
 * <ul>
 * <li>{@link Server#all()}</li>
 * <li>{@link Server#all(Action)}</li>
 * <li>{@link Server#byTag(String...)}</li>
 * <li>{@link Server#byTag(String, Action)}</li>
 * <li>{@link Server#byTag(String[], Action)}</li>
 * </ul>
 * A message created by this server is passed to {@link ClusteredServer#onpublish(Action)} and a
 * message created by other servers is expected to be passed to
 * {@link ClusteredServer#messageAction()}. Therefore, what you need to do is to publish a
 * message given through {@link ClusteredServer#onpublish(Action)} to every server in the cluster
 * and to subscribe a published message by other servers to delegate it to
 * {@link ClusteredServer#messageAction()}.
 * <p/>
 * Accordingly, such message must be able to be serialized and you have to pass
 * {@link Action} implementing {@link Serializable}. However, serialization of inner classes
 * doesn't work in some cases as expected so that always use {@link Sentence} instead of action
 * if possible unless you use lambda expressions.
 *
 * @author Donghwan Kim
 * @see <a
 * href="http://docs.oracle.com/javase/7/docs/platform/serialization/spec/serial-arch
 * .html#4539">Note
 * of the Serializable Interface</a>
 */
public class ClusteredServer extends DefaultServer {

  private Actions<Map<String, Object>> publishActions = new ConcurrentActions<>();
  private Action<Map<String, Object>> messageAction = map -> {
    String methodName = (String) map.get("method");
    Object[] args = (Object[]) map.get("args");
    switch (methodName) {
      case "find":
        ClusteredServer.super.find((ServerSocketPredicate) args[0], (SerializableAction<ServerSocket>) args[1]);
        break;
      case "all":
        ClusteredServer.super.all((SerializableAction<ServerSocket>) args[0]);
        break;
      case "byTag":
        ClusteredServer.super.byTag((String[]) args[0], (SerializableAction<ServerSocket>) args[1]);
        break;
      default:
        throw new IllegalArgumentException("Illegal method name in processing message: "
          + methodName);
    }
  };

  @Override
  public Server find(ServerSocketPredicate predicate, SerializableAction<ServerSocket> action) {
    publishMessage("find", predicate, action);
    return this;
  }

  @Override
  public Server all(SerializableAction<ServerSocket> action) {
    publishMessage("all", action);
    return this;
  }

  @Override
  public Server byTag(String[] names, SerializableAction<ServerSocket> action) {
    publishMessage("byTag", names, action);
    return this;
  }

  private void publishMessage(String method, Object... args) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("method", method);
    map.put("args", args);
    publishActions.fire(Collections.unmodifiableMap(map));
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
