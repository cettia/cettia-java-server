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
import io.cettia.transport.ServerTransport;

import java.net.URI;
import java.util.Map;
import java.util.Set;

/**
 * Interface used to interact with the remote socket.
 * <p/>
 * Instances may be accessed by multiple threads.
 *
 * @author Donghwan Kim
 */
public interface ServerSocket extends AbstractServerSocket<ServerSocket> {

  /**
   * The identifier of the socket.
   */
  String id();

  /**
   * The current state of the socket.
   */
  State state();

  /**
   * A URI used to connect. To work with URI parts, use {@link URI} or something like that.
   */
  String uri();

  /**
   * A tags of this socket.
   */
  Set<String> tags();

  /**
   * An attributes of this socket.
   */
  Map<String, Object> attributes();

  /**
   * Returns the value mapped to the given name.
   */
  <T> T get(String name);

  /**
   * Associates the value with the given name in the socket.
   */
  ServerSocket set(String name, Object value);

  /**
   * Removes the mapping associated with the given name.
   */
  ServerSocket remove(String name);

  /**
   * Adds a given event handler for a given event.
   * <p/>
   * The allowed types for {@code T} is determined by a format which is used to deserialize
   * transport message. Now that format corresponds to
   * <a href="https://github.com/FasterXML/jackson-databind">Jackson</a>'s data format. By default,
   * built-in data format is used for text message, and
   * <a href="https://github.com/msgpack/msgpack-java/tree/develop/msgpack-jackson">MessagePack data
   * format</a> is used for binary message.
   * <p/>
   * If the counterpart sends an event with callback, {@code T} should be
   * {@link Reply}.
   */
  <T> ServerSocket on(String event, Action<T> action);

  /**
   * Adds an open event handler to be called when the handshake is performed successfully and
   * communication is possible.
   * <p/>
   * Equivalent to <code>socket.on("open", action)</code>
   */
  ServerSocket onopen(Action<Void> action);

  /**
   * Adds a close event handler to be called when the underlying transport is closed for any reason.
   * <p/>
   * Equivalent to <code>socket.on("close", action)</code>
   */
  ServerSocket onclose(Action<Void> action);

  /**
   * Adds an error event handler to be called if there was any error on the socket.
   * <p/>
   * Equivalent to <code>socket.on("error", action)</code>
   */
  ServerSocket onerror(Action<Throwable> action);

  /**
   * Adds a cache event handler to be called if one of <code>send</code> methods is called when
   * there is no connection. The given value is an array of arguments of
   * {@link #send(String, Object, Action, Action)}.
   * <p/>
   * Equivalent to <code>socket.on("cache", action)</code>
   */
  ServerSocket oncache(Action<Object[]> action);

  /**
   * Adds a delete event handler to be called when the socket is in the closed state for a long
   * time i.e. 1 minute and deleted from the server. As the end of the life cycle,
   * <code>delete</code> event is called only once.
   * <p/>
   * Equivalent to <code>socket.on("delete", action)</code>
   */
  ServerSocket ondelete(Action<Void> action);

  /**
   * Removes a given event handler for a given event.
   */
  <T> ServerSocket off(String event, Action<T> action);

  /**
   * Sends a given event with data attaching resolved callback.
   * <p/>
   * For the allowed types for {@code T}, see {@link ServerSocket#on(String, Action)}.
   */
  <T> ServerSocket send(String event, Object data, Action<T> resolved);

  /**
   * Sends a given event with data attaching resolved callback and rejected callback.
   * <p/>
   * For the allowed types for {@code T}, see {@link ServerSocket#on(String, Action)}.
   */
  <T, U> ServerSocket send(String event, Object data, Action<T> resolved, Action<U> rejected);

  /**
   * Returns the underlying component.
   * <p/>
   * {@link ServerTransport} is available.
   */
  <T> T unwrap(Class<T> clazz);

  /**
   * Enumeration of the state of the socket.
   *
   * @author Donghwan Kim
   */
  enum State {

    /**
     * A state where the communication is possible.
     */
    OPENED,

    /**
     * A state the underlying connection is disconnected temporarily.
     */
    CLOSED,

    /**
     * A state where it is deleted from the server because of long periods of disconnection. A
     * deleted socket shouldn't be used.
     */
    DELETED

  }

  /**
   * Interface to deal with reply.
   * <p/>
   * For the allowed types for {@code T}, see {@link ServerSocket#on(String, Action)}.
   *
   * @author Donghwan Kim
   */
  interface Reply<T> {

    /**
     * The original data.
     */
    T data();

    /**
     * Resolves.
     */
    void resolve();

    /**
     * Resolves with the value.
     */
    void resolve(Object data);

    /**
     * Rejects.
     */
    void reject();

    /**
     * Rejects with the reason.
     */
    void reject(Object error);

  }

}
