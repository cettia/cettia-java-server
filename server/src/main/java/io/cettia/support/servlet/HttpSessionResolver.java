/*
 * Copyright 2017 the original author or authors.
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
package io.cettia.support.servlet;

import io.cettia.ServerSocket;
import io.cettia.asity.http.ServerHttpExchange;
import io.cettia.asity.websocket.ServerWebSocket;
import io.cettia.transport.ServerTransport;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpointConfig;

/**
 * A helper to resolve {@link HttpSession} from an application running on Servlet 3 and Java
 * WebSocket API 1 environments.
 * <p>
 * In case of Java WebSocket API, {@link HttpSession} must be put into a map returned by
 * {@link ServerEndpointConfig#getUserProperties()} with the
 * <code>javax.servlet.http.HttpSession</code> key when building {@link ServerEndpointConfig}.
 * <pre>
 * ServerEndpointConfig config = ServerEndpointConfig.Builder.create(AsityServerEndpoint.class,
 * "/cettia")
 * .configurator(new Configurator() {
 *     {@literal @}Override
 *     protected &lt;T&gt; T getEndpointInstance(Class&lt;T&gt; endpointClass) throws
 *       InstantiationException {
 *       return endpointClass.cast(new AsityServerEndpoint().onwebsocket(wsTransportServer));
 *     }
 *
 *     {@literal @}Override
 *     protected void modifyHandshake(ServerEndpointConfig config, HandshakeRequest request,
 *                                    HandshakeResponse response) {
 *       HttpSession httpSession = (HttpSession) request.getHttpSession();
 *       <strong>config.getUserProperties().put(HttpSession.class.getName(), httpSession);</strong>
 *     }
 * })
 * .build();
 * </pre>
 *
 * @author Donghwan Kim
 */
public class HttpSessionResolver {

  /**
   * Resolves the current session.
   * <p>
   * Note that it doesn't create a session and returns <code>null</code> if there's no
   * current session.
   */
  public HttpSession resolve(ServerSocket socket) {
    ServerTransport transport = socket.unwrap(ServerTransport.class);

    ServerWebSocket ws = transport.unwrap(ServerWebSocket.class);
    if (ws != null) {
      String key = HttpSession.class.getName();
      return (HttpSession) ws.unwrap(Session.class).getUserProperties().get(key);
    }

    ServerHttpExchange http = transport.unwrap(ServerHttpExchange.class);
    if (http != null) {
      return http.unwrap(HttpServletRequest.class).getSession(false);
    }

    return null;
  }

}
