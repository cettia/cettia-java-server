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
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.websocket.Session;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HttpSessionResolverTest {

  @Test
  public void resolveFromWebSocketTransport() {
    ServerSocket socket = mock(ServerSocket.class);
    ServerTransport transport = mock(ServerTransport.class);
    when(socket.unwrap(ServerTransport.class)).thenReturn(transport);

    ServerWebSocket ws = mock(ServerWebSocket.class);
    when(transport.unwrap(ServerWebSocket.class)).thenReturn(ws);

    Session session = mock(Session.class);
    when(ws.unwrap(Session.class)).thenReturn(session);

    Map<String, Object> userProperties = new LinkedHashMap<>();
    HttpSession httpSession = mock(HttpSession.class);
    userProperties.put(HttpSession.class.getName(), httpSession);

    when(session.getUserProperties()).thenReturn(userProperties);

    HttpSessionResolver resolver = new HttpSessionResolver();
    HttpSession actualHttpSession = resolver.resolve(socket);

    assertThat(actualHttpSession, is(httpSession));
  }

  @Test
  public void resolveFromHttpBasedTransport() {
    ServerSocket socket = mock(ServerSocket.class);
    ServerTransport transport = mock(ServerTransport.class);
    when(socket.unwrap(ServerTransport.class)).thenReturn(transport);

    ServerHttpExchange http = mock(ServerHttpExchange.class);
    when(transport.unwrap(ServerHttpExchange.class)).thenReturn(http);

    HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
    when(http.unwrap(HttpServletRequest.class)).thenReturn(httpServletRequest);

    HttpSession httpSession = mock(HttpSession.class);
    when(httpServletRequest.getSession(false)).thenReturn(httpSession);

    HttpSessionResolver resolver = new HttpSessionResolver();
    HttpSession actualHttpSession = resolver.resolve(socket);

    assertThat(actualHttpSession, is(httpSession));
  }

}
