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
   * Adds a socket event handler to be called when the socket has been created in this server.
   */
  Server onsocket(Action<ServerSocket> action);

  /**
   * Returns a sentence with a predicate that always matches.
   *
   * @deprecated As of 1.2, it's deprecated in favor of {@link ServerSocketPredicates}. Use
   * {@link Server#find(ServerSocketPredicate)} with {@link ServerSocketPredicates#all()}, i.e.
   * <code>server.find(ServerSocketPredicates.all())</code>.
   */
  @Deprecated
  default Sentence all() {
    return new Sentence(this, ServerSocketPredicates.all());
  }

  /**
   * Executes the given action through a sentence returned by {@link Server#all()}.
   *
   * @deprecated As of 1.2, it's deprecated in favor of {@link ServerSocketPredicates}. Use
   * {@link Server#find(ServerSocketPredicate)} with {@link ServerSocketPredicates#all()} and
   * {@link Sentence#execute(SerializableAction)}, i.e. <code>server.find(ServerSocketPredicates.all
   * ()).execute(action)</code>.
   */
  @Deprecated
  default Server all(SerializableAction<ServerSocket> action) {
    all().execute(action);
    return this;
  }

  /**
   * Returns a sentence with a predicate that tests the socket tags against the given tags.
   *
   * @deprecated As of 1.2, it's deprecated in favor of {@link ServerSocketPredicates}. Use
   * {@link Server#find(ServerSocketPredicate)} with
   * {@link ServerSocketPredicates#tag(String...)}, i.e.
   * <code>server.find(ServerSocketPredicates.tag(names))</code>.
   */
  @Deprecated
  default Sentence byTag(String... names) {
    return new Sentence(this, ServerSocketPredicates.tag(names));
  }

  /**
   * Executes the given action through a sentence returned by {@link Server#byTag(String...)}.
   *
   * @deprecated As of 1.2, it's deprecated in favor of {@link ServerSocketPredicates}. Use
   * {@link Server#find(ServerSocketPredicate)} with
   * {@link ServerSocketPredicates#tag(String...)} and
   * {@link Sentence#execute(SerializableAction)}, i.e. <code>server.find(ServerSocketPredicates
   * .tag(name)).execute(action)</code>.
   */
  @Deprecated
  default Server byTag(String name, SerializableAction<ServerSocket> action) {
    return byTag(new String[]{name}, action);
  }

  /**
   * Executes the given action through a sentence returned by {@link Server#byTag(String...)}.
   *
   * @deprecated As of 1.2, it's deprecated in favor of {@link ServerSocketPredicates}. Use
   * {@link Server#find(ServerSocketPredicate)} with
   * {@link ServerSocketPredicates#tag(String...)} and
   * {@link Sentence#execute(SerializableAction)}, i.e. <code>server.find(ServerSocketPredicates
   * .tag(names)).execute(action)</code>.
   */
  @Deprecated
  default Server byTag(String[] names, SerializableAction<ServerSocket> action) {
    byTag(names).execute(action);
    return this;
  }

}
