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

import io.cettia.ServerSocket.Reply;
import io.cettia.asity.bridge.jwa1.AsityServerEndpoint;
import io.cettia.asity.bridge.servlet3.AsityServlet;
import io.cettia.transport.http.HttpTransportServer;
import io.cettia.transport.websocket.WebSocketTransportServer;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRegistration;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;
import javax.websocket.server.ServerEndpointConfig.Configurator;
import java.util.Map;

public class ProtocolTest {

  private final Logger log = LoggerFactory.getLogger(ProtocolTest.class);

  @Test
  public void protocol() throws Exception {
    final DefaultServer server = new DefaultServer();
    server.onsocket(socket -> {
      log.debug("socket.uri() is {}", socket.uri());
      socket.on("abort", $ -> socket.close())
      .on("echo", data -> socket.send("echo", data))
      .on("/reply/inbound", (Reply<Map<String, Object>> reply) -> {
        Map<String, Object> data = reply.data();
        switch ((String) data.get("type")) {
          case "resolved":
            reply.resolve(data.get("data"));
            break;
          case "rejected":
            reply.reject(data.get("data"));
            break;
        }
      })
      .on("/reply/outbound", (Map<String, Object> data) -> {
        switch ((String) data.get("type")) {
          case "resolved":
            socket.send("test", data.get("data"), resolvedData -> socket.send("done",
              resolvedData));
            break;
          case "rejected":
            socket.send("test", data.get("data"), null, rejectedData -> socket.send("done",
              rejectedData));
            break;
          default:
            throw new IllegalStateException();
        }
      });
    });
    final HttpTransportServer httpTransportServer = new HttpTransportServer().ontransport(server);
    final WebSocketTransportServer wsTransportServer = new WebSocketTransportServer().ontransport
      (server);

    org.eclipse.jetty.server.Server jetty = new org.eclipse.jetty.server.Server();
    ServerConnector connector = new ServerConnector(jetty);
    jetty.addConnector(connector);
    connector.setPort(8000);
    ServletContextHandler handler = new ServletContextHandler();
    jetty.setHandler(handler);
    handler.addEventListener(new ServletContextListener() {
      @Override
      @SuppressWarnings("serial")
      public void contextInitialized(ServletContextEvent event) {
        ServletContext context = event.getServletContext();
        ServletRegistration regSetup = context.addServlet("/setup", new HttpServlet() {
          @Override
          protected void doGet(HttpServletRequest req, HttpServletResponse res) {
            Map<String, String[]> params = req.getParameterMap();
            if (params.containsKey("heartbeat")) {
              server.setHeartbeat(Integer.parseInt(params.get("heartbeat")[0]));
            }
            if (params.containsKey("_heartbeat")) {
              server.set_heartbeat(Integer.parseInt(params.get("_heartbeat")[0]));
            }
          }
        });
        regSetup.addMapping("/setup");
        // For HTTP transport
        Servlet servlet = new AsityServlet().onhttp(httpTransportServer);
        ServletRegistration.Dynamic reg = context.addServlet(AsityServlet.class.getName(), servlet);
        reg.setAsyncSupported(true);
        reg.addMapping("/cettia");
      }

      @Override
      public void contextDestroyed(ServletContextEvent sce) {
      }
    });
    // For WebSocket transport
    ServerContainer container = WebSocketServerContainerInitializer.configureContext(handler);
    ServerEndpointConfig config = ServerEndpointConfig.Builder.create(AsityServerEndpoint.class,
    "/cettia")
    .configurator(new Configurator() {
      @Override
      public <T> T getEndpointInstance(Class<T> endpointClass) {
        return endpointClass.cast(new AsityServerEndpoint().onwebsocket(wsTransportServer));
      }
    })
    .build();
    container.addEndpoint(config);

    jetty.start();

    CommandLine cmdLine = CommandLine.parse("./src/test/resources/node/node")
    .addArgument("./src/test/resources/runner")
    .addArgument("--cettia.transports")
    .addArgument("websocket,httpstream,httplongpoll");
    DefaultExecutor executor = new DefaultExecutor();
    // The exit value of mocha is the number of failed tests.
    executor.execute(cmdLine);

    jetty.stop();
  }

}
