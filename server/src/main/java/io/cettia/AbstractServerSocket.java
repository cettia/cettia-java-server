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

/**
 * {@code AbstractSocket} consists of a set of common functionality of
 * {@link Sentence} and {@link ServerSocket}.
 *
 * @author Donghwan Kim
 */
public interface AbstractServerSocket<T> {

  /**
   * Sends a given event without data.
   */
  T send(String event);

  /**
   * Sends a given event with data.
   */
  T send(String event, Object data);

  /**
   * Closes the socket.
   */
  void close();

  /**
   * Attaches given tags to the socket.
   */
  T tag(String... names);

  /**
   * Detaches given tags from the socket.
   */
  T untag(String... names);

}
