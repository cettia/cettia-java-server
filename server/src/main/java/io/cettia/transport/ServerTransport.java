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
package io.cettia.transport;

import io.cettia.asity.action.Action;

import java.net.URI;
import java.nio.ByteBuffer;

/**
 * Represents a server-side full duplex message channel ensuring there is no
 * message loss and no idle connection.
 * <p/>
 * Implementations are thread safe.
 *
 * @author Donghwan Kim
 */
public interface ServerTransport {

  /**
   * A URI used to connect. To work with URI parts, use {@link URI} or
   * something like that.
   */
  String uri();

  /**
   * Executed if there was any error on the connection. You don't need to
   * close it explicitly.
   */
  ServerTransport onerror(Action<Throwable> action);

  /**
   * Attaches an action for the text message.
   */
  ServerTransport ontext(Action<String> action);

  /**
   * Attaches an action for the binary message.
   */
  ServerTransport onbinary(Action<ByteBuffer> action);

  /**
   * Sends a text message through the connection.
   */
  ServerTransport send(String data);

  /**
   * Sends a binary message through the connection.
   */
  ServerTransport send(ByteBuffer data);

  /**
   * Attaches an action for the close event. After this event, the instance
   * shouldn't be used and all the other events will be disabled.
   */
  ServerTransport onclose(Action<Void> action);

  /**
   * Closes the connection. This method has no side effect if called more than
   * once.
   */
  void close();

  /**
   * Returns the underlying component.
   */
  <T> T unwrap(Class<T> clazz);

}
