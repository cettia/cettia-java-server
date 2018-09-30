/*
 * Copyright 2018 the original author or authors.
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

import java.util.Arrays;
import java.util.Objects;

/**
 * It consists of static methods that return various useful {@link ServerSocketPredicate}s.
 * <p/>
 * Inspired by Spring WebFlux's <code>RequestPredicates</code>.
 *
 * @author Donghwan Kim
 */
public abstract class ServerSocketPredicates {

  /**
   * Returns a predicate that always matches.
   */
  public static ServerSocketPredicate all() {
    return socket -> true;
  }

  /**
   * Returns a predicate that tests the socket tags against the given tags.
   */
  public static ServerSocketPredicate tag(String... tags) {
    return socket -> socket.tags().containsAll(Arrays.asList(tags));
  }

  /**
   * Returns a predicate that tests the socket attributes against the given key-value pair. In a
   * clustered environment, <code>value<code> should implement {@link java.io.Serializable}.
   */
  public static ServerSocketPredicate attr(String key, Object value) {
    return socket -> Objects.equals(socket.get(key), value);
  }

  /**
   * Returns a predicate that tests the socket id against the given socket's id. It can be used
   * to exclude a certain socket in conjunction with {@link ServerSocketPredicate#negate} like
   * <code>server.find(id(socket).negate())</code>.
   */
  public static ServerSocketPredicate id(ServerSocket socket) {
    return id(Objects.requireNonNull(socket).id());
  }

  /**
   * Returns a predicate that tests the socket id against the given socket id. It can be used
   * to exclude a certain socket in conjunction with {@link ServerSocketPredicate#negate} like
   * <code>server.find(id(id).negate())</code>.
   */
  public static ServerSocketPredicate id(String id) {
    return socket -> socket.id().equals(id);
  }

}
