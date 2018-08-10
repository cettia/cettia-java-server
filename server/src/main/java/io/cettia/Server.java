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

/**
 * Interface used to interact with sockets.
 * <p/>
 * Instances may be accessed by multiple threads.
 *
 * @author Donghwan Kim
 */
public interface Server extends Action<ServerTransport> {

  /**
   * Returns a sentence that matches the given predicate for sockets.
   */
  Sentence find(ServerSocketPredicate predicate);

  /**
   * Executes the given action retrieving sockets that matched the given predicate.
   */
  Server find(ServerSocketPredicate predicate, SerializableAction<ServerSocket> action);

  /**
   * Returns a sentence that every socket in this server has to follow.
   */
  Sentence all();

  /**
   * Executes the given action retrieving every socket in this server.
   */
  Server all(SerializableAction<ServerSocket> action);

  /**
   * Returns a sentence that the socket tagged with the given tags in this server have to follow.
   */
  Sentence byTag(String... names);

  /**
   * Executes the given action retrieving the socket tagged with the given tag in this server.
   */
  Server byTag(String name, SerializableAction<ServerSocket> action);

  /**
   * Executes the given action retrieving the socket tagged with the given tags in this server.
   */
  Server byTag(String[] names, SerializableAction<ServerSocket> action);

  /**
   * Adds a socket event handler to be called when the socket has been created in this server.
   */
  Server onsocket(Action<ServerSocket> action);

}
