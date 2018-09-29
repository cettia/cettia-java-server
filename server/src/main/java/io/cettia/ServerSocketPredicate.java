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

import java.io.Serializable;
import java.util.Objects;

/**
 * A predicate to filter {@link ServerSocket}.
 * <p/>
 * This interface will extend <code>java.util.function.Predicate</code>.
 *
 * @author Donghwan Kim
 */
public interface ServerSocketPredicate extends Serializable {

  /**
   * Evaluates this predicate on the socket.
   *
   * @return {@code true} if the socket matches the predicate, otherwise {@code false}
   */
  boolean test(ServerSocket socket);

  /**
   * Returns a composed predicate that represents a short-circuiting logical AND of this
   * predicate and another.
   */
  default ServerSocketPredicate and(ServerSocketPredicate that) {
    Objects.requireNonNull(that);
    return socket -> test(socket) && that.test(socket);
  }

  /**
   * Returns a composed predicate that represents a short-circuiting logical OR of this predicate
   * and another.
   */
  default ServerSocketPredicate or(ServerSocketPredicate that) {
    Objects.requireNonNull(that);
    return socket -> test(socket) || that.test(socket);
  }

  /**
   * Returns a predicate that represents the logical negation of this predicate.
   */
  default ServerSocketPredicate negate() {
    return socket -> !test(socket);
  }

}
