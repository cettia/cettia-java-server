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
import io.cettia.asity.http.ServerHttpExchange;
import io.cettia.asity.websocket.ServerWebSocket;

/**
 * Interface used to interact with transports.
 * <p/>
 * {@code TransportServer} consumes resource like {@link ServerHttpExchange} or
 * {@link ServerWebSocket} and produces {@link ServerTransport} following the
 * corresponding Cettia transport protocol.
 * <p/>
 * Instances may be accessed by multiple threads.
 *
 * @author Donghwan Kim
 */
public interface TransportServer<T> extends Action<T> {

  /**
   * Registers an action to be called when the transport has been opened.
   */
  TransportServer<T> ontransport(Action<ServerTransport> action);

}
