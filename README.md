# Cettia

Cettia is a full-featured real-time web application framework for Java that you can use to exchange events between server and client in real-time. It is meant for when you run into issues which are tricky to resolve with WebSocket, JSON, and switch statement per se:

- Avoiding repetitive boilerplate code
- Supporting environments where WebSocket is not available
- Handling both text and binary data together
- Recovering missed events
- Providing multi-device user experience
- Scaling out an application, and so on.

It offers a reliable full duplex message channel and elegant patterns to achieve better user experience in the real-time web, and is compatible with any web frameworks on the Java Virtual Machine.

---

The following is a summary of the Cettia starter kit to help you get started quickly. In the summary, comments starting with `##` refer to a title of a related chapter in the tutorial, [Building Real-Time Web Applications With Cettia](https://cettia.io/guides/cettia-tutorial/), where you can find a detailed explanation. You may want to highlight the `##`.

Maven dependencies.

```xml
<!-- ## Setting Up the Project -->
<!-- To write a Cettia application -->
<dependency>
  <groupId>io.cettia</groupId>
  <artifactId>cettia-server</artifactId>
  <version>1.1.0</version>
</dependency>
<!-- To run a Cettia application on Servlet 3 and Java WebSocket API 1 -->
<!-- Besides them, you can also use Spring WebFlux, Spring MVC, Grizzly, Vert.x, Netty, and so on -->
<dependency>
  <groupId>io.cettia.asity</groupId>
  <artifactId>asity-bridge-servlet3</artifactId>
  <version>2.0.0</version>
</dependency>
<dependency>
  <groupId>io.cettia.asity</groupId>
  <artifactId>asity-bridge-jwa1</artifactId>
  <version>2.0.0</version>
</dependency>
```

A class to play with the Cettia server. Import statements, verbose try-catch blocks, empty methods, etc. are skipped for brevity.

```Java
@WebListener
public class CettiaConfigListener implements ServletContextListener {
  public void contextInitialized(ServletContextEvent event) {
    // Cettia part
    // If you don't want to form a cluster,
    // replace the following line with `Server server = new DefaultServer();`
    ClusteredServer server = new ClusteredServer();
    HttpTransportServer httpAction = new HttpTransportServer().ontransport(server);
    WebSocketTransportServer wsAction = new WebSocketTransportServer().ontransport(server);

    // If a client opens a socket, the server creates and passes a ServerSocket to socket handlers
    server.onsocket((ServerSocket socket) -> {
      // ## Socket Lifecycle
      Action<Void> logState = v -> System.out.println(socket + " " + socket.state());
      socket.onopen(logState).onclose(logState).ondelete(logState);

      // ## Sending and Receiving Events
      // An `echo` event handler where any received echo event is sent back
      socket.on("echo", data -> socket.send("echo", data));

      // ## Attributes and Tags
      // Attributes and tags are contexts to store the socket state in the form of Map and Set
      String username = findParam(socket.uri(), "username");
      if (username == null) {
        // Attaches a tag to the socket
        socket.tag("nonmember");
      } else {
        // Associates an attribute with the the socket
        socket.set("username", username);
      }

      // ## Working with Sockets
      // A `chat` event handler to send a given chat event to every socket in every server in the cluster
      socket.on("chat", data -> server.all().send("chat", data));

      // ## Finder Methods and Sentence
      if (username != null) {
        // A myself event handler to send a given myself event to sockets whose username is the same
        socket.on("myself", data -> {
          // Find sockets by the attribute and deal with them directly
          server.find(s -> username.equals(s.get("username"))).execute(s -> s.send("myself"));
        });

        // How to allow only one socket per username
        boolean onlyOneSocket = Boolean.parseBoolean(findParam(socket.uri(), "onlyOneSocket"));
        if (onlyOneSocket) {
          // Finds sockets whose username is the same except this socket
          String me = socket.id();
          server.find(s -> username.equals(s.get("username")) && !me.equals(s.id()))
          // Sends a `signout` event to prevent reconnection and closes a connection
          .send("signout").close();
          // As of 1.2, it can be written more concisely
          // `server.byAttr("username", username).exclude(socket).send("signout").close();`
        }
      }

      // ## Recovering Missed Events
      Queue<Object[]> queue = new ConcurrentLinkedQueue<>();
      // Caches events that fail to send due to disconnection
      socket.oncache(args -> queue.offer(args));
      // Sends cached events on the next connection
      socket.onopen(v -> {
        while (socket.state() == ServerSocket.State.OPENED && !queue.isEmpty()) {
          Object[] args = queue.poll();
          socket.send((String) args[0], args[1], (Action<?>) args[2], (Action<?>) args[3]);
        }
      });
      // If the client fails to connect within 1 minute after disconnection,
      // You may want to consider notifying the user of finally missed events, like push notifications
      socket.ondelete(v -> queue.forEach(args -> {
        System.out.println(socket + " missed event - name: " + args[0] + ", data: " + args[1]);
      }));
    });

    // ## Working with Sockets
    // To deal with sockets, inject the server wherever you want
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    // Sends a welcome event to sockets representing user not signed in every 5 seconds
    executor.scheduleAtFixedRate(() -> server.byTag("nonmember").send("welcome"), 0, 5, SECONDS);

    // ## Plugging Into the Web Framework
    // Cettia is designed to run on any web framework seamlessly on the JVM
    // Note how `httpAction` and `wsAction` are plugged into Servlet and Java API for Websocket
    ServletContext context = event.getServletContext();
    AsityServlet asityServlet = new AsityServlet().onhttp(/* ㅇㅅㅇ */ httpAction);
    ServletRegistration.Dynamic reg = context.addServlet(AsityServlet.class.getName(), asityServlet);
    reg.setAsyncSupported(true);
    reg.addMapping("/cettia");

    ServerContainer container = (ServerContainer) context.getAttribute(ServerContainer.class.getName());
    ServerEndpointConfig.Configurator configurator = new ServerEndpointConfig.Configurator() {
      public <T> T getEndpointInstance(Class<T> endpointClass) {
        AsityServerEndpoint asityServerEndpoint = new AsityServerEndpoint();
        asityServerEndpoint.onwebsocket(/* ㅇㅅㅇ */ wsAction);
        return endpointClass.cast(asityServerEndpoint);
      }
    };
    container.addEndpoint(ServerEndpointConfig.Builder.create(AsityServerEndpoint.class, "/cettia")
      .configurator(configurator).build());

    // ## Scaling a Cettia Application
    // Any publish-subscribe messaging system can be used to scale a Cettia application horizontally,
    // and it doesn’t require any modification in the existing application.
    HazelcastInstance hazelcast = HazelcastInstanceFactory.newHazelcastInstance(new Config());
    ITopic<Map<String, Object>> topic = hazelcast.getTopic("cettia");
    // It publishes messages given by the server
    server.onpublish(message -> topic.publish(message));
    // It relays published messages to the server
    topic.addMessageListener(message -> server.messageAction().on(message.getMessageObject()));
  }
}
```

Here's an example with the Spring WebFlux 5 to show Cettia's framework-agnostic nature. Consult Asity’s [Run Anywhere](http://asity.cettia.io/#run-anywhere) section for how to plug a Cettia application into other various frameworks.

```java
@SpringBootApplication
@EnableWebFlux
public class CettiaServer {
  @Bean
  public RouterFunction<ServerResponse> httpMapping(HttpTransportServer httpAction) {
    AsityHandlerFunction asityHandlerFunction = new AsityHandlerFunction();
    asityHandlerFunction.onhttp(/* ㅇㅅㅇ */ httpAction);

    RequestPredicate isNotWebSocket = headers(h -> !"websocket".equalsIgnoreCase(h.asHttpHeaders().getUpgrade()));
    return route(path("/cettia").and(isNotWebSocket), asityHandlerFunction);
  }

  @Bean
  public HandlerMapping wsMapping(WebSocketTransportServer wsAction) {
    AsityWebSocketHandler asityWebSocketHandler = new AsityWebSocketHandler();
    asityWebSocketHandler.onwebsocket(/* ㅇㅅㅇ */ wsAction);
    Map<String, WebSocketHandler> map = new LinkedHashMap<>();
    map.put("/cettia", asityWebSocketHandler);

    SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
    mapping.setUrlMap(map);

    return mapping;
  }
}
```

You need minimal HTML to load the `cettia` object. Also, if you have a `cettia-client` npm module installed, you should be able to load the `cettia` object with `require("cettia-client/cettia-bundler");` and `require("cettia-client");` in Webpack and Node, respectively.

```html
 <!DOCTYPE html>
 <title>index</title>
 <script src="https://unpkg.com/cettia-client@1.0.1/cettia-browser.min.js"></script>
```

Below is the JavaScript code to play with the `cettia` object. Open the above page and its developer console in several browsers, run the script, and watch results on the fly.

```javascript
// ## Opening a Socket
// Manipulates the below params object to play with the server implementation
var params = {
  username: "flowersinthesand",
  onlyOneSocket: true
};
// Let's assume that each key and value are already encoding safe
var query = Object.keys(params).filter(k => params[k]).map(k => `${k}=${params[k]}`).join("&");
var socket = cettia.open("/cettia?" + query);

// ## Socket Lifecycle
var logState = () => console.log(socket.state());
socket.on("connecting", logState).on("open", logState).on("close", logState);
socket.on("waiting", (delay, attempts) => console.log(socket.state(), delay, attempts));

// ## Sending and Receiving Events
["echo", "chat", "myself", "welcome"].forEach(event => {
  socket.on(event, data => console.log(event, data));
});
socket.on("signout", () => {
  console.log("signout", "You've been signed out since you signed in on another device");
  // It prevents reconnection
  socket.close();
});

// This open event handler registered through `once` is called at maximum once
socket.once("open", () => {
  // Sends an echo event to be returned
  socket.send("echo", "Hello world");
  // Sends a chat event to be broadcast to every sockets in the server
  socket.send("chat", {text: "I'm a text"});
  // Sends an event to sockets whose username is the same
  // with composite data consisting of text data and binary data
  socket.send("myself", {text: "I'm a text", binary: new TextEncoder().encode("I'm a binary")});
});
```

The full source code for the starter kit is available at the repository, https://github.com/cettia/cettia-starter-kit. If you want to dig deeper, read an introductory tutorial to Cettia, [Building Real-Time Web Applications With Cettia](https://cettia.io/guides/cettia-tutorial/). It explains the reason behind key design decisions that the Cettia team have made in the Cettia, as well as various patterns and features required to build real-time oriented applications without compromise with Cettia.

---

If you run into issues or have questions or are interested and would like to be more involved, feel free to join the [mailing list](http://groups.google.com/group/cettia) and share your feedback. Also, follow [@cettia_project](https://twitter.com/cettia_project) or [@flowersits](https://twitter.com/flowersits) on Twitter for the latest news and updates.
