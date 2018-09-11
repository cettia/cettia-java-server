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
package io.cettia.transport.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cettia.asity.action.Action;
import io.cettia.asity.action.Actions;
import io.cettia.asity.action.ConcurrentActions;
import io.cettia.asity.http.HttpStatus;
import io.cettia.asity.http.ServerHttpExchange;
import io.cettia.transport.BaseServerTransport;
import io.cettia.transport.ServerTransport;
import io.cettia.transport.TransportServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HTTP implementation of {@link TransportServer}.
 * <p/>
 * It processes transport whose URI whose protocol is either {@code http} or
 * {@code https} and transport parameter is either {@code stream} or
 * {@code longpoll} like {@code http://localhost:8080/cettia?transport=stream}.
 *
 * @author Donghwan Kim
 */
// TODO Extract constants
public class HttpTransportServer implements TransportServer<ServerHttpExchange> {

  private final Logger log = LoggerFactory.getLogger(HttpTransportServer.class);
  private Map<String, BaseTransport> transports = new ConcurrentHashMap<>();
  private Actions<ServerTransport> transportActions = new ConcurrentActions<ServerTransport>()
  .add(t -> {
    final BaseTransport transport = (BaseTransport) t;
    log.trace("{}'s request has opened", transport);
    transports.put(transport.id(), transport);
    transport.onclose($ -> {
      log.trace("{}'s request has been closed", transport);
      transports.remove(transport.id());
    });
  });

  /**
   * For internal use only.
   */
  public static Map<String, String> parseQuery(String uri) {
    Map<String, String> map = new LinkedHashMap<>();
    String query = URI.create(uri).getQuery();
    if (query == null || query.equals("")) {
      return Collections.unmodifiableMap(map);
    }
    String[] params = query.split("&");
    for (String param : params) {
      try {
        String[] pair = param.split("=", 2);
        String name = URLDecoder.decode(pair[0], "UTF-8");
        if (name.equals("")) {
          continue;
        }
        map.put(name, pair.length > 1 ? URLDecoder.decode(pair[1], "UTF-8") : "");
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      }
    }
    return Collections.unmodifiableMap(map);
  }

  /**
   * For internal use only.
   */
  public static String formatQuery(Map<String, String> params) {
    StringBuilder query = new StringBuilder();
    for (Entry<String, String> entry : params.entrySet()) {
      try {
        query.append(URLEncoder.encode(entry.getKey(), "UTF-8"))
        .append("=")
        .append(URLEncoder.encode(entry.getValue(), "UTF-8"))
        .append("&");
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      }
    }
    return query.deleteCharAt(query.length() - 1).toString();
  }

  @Override
  public void on(final ServerHttpExchange http) {
    final Map<String, String> params = parseQuery(http.uri());
    http.setHeader("cache-control", "no-cache, no-store, must-revalidate")
    .setHeader("pragma", "no-cache")
    .setHeader("expires", "0")
    .setHeader("access-control-allow-origin", http.header("origin") != null ? http.header
      ("origin") : "*")
    .setHeader("access-control-allow-headers", "content-type")
    .setHeader("access-control-allow-credentials", "true");
    switch (http.method()) {
      case OPTIONS: {
        http.end();
        break;
      }
      case GET: {
        switch (params.get("cettia-transport-when")) {
          case "open": {
            String transportName = params.get("cettia-transport-name");
            switch (transportName) {
              case "stream":
                transportActions.fire(new StreamTransport(http));
                break;
              case "longpoll":
                transportActions.fire(new LongpollTransport(http));
                break;
              default:
                log.error("Transport, {}, is not implemented", transportName);
                http.setStatus(HttpStatus.NOT_IMPLEMENTED).end();
                break;
            }
            break;
          }
          case "poll": {
            String id = params.get("cettia-transport-id");
            BaseTransport transport = transports.get(id);
            if (transport != null && transport instanceof LongpollTransport) {
              ((LongpollTransport) transport).refresh(http);
            } else {
              log.error("Long polling transport#{} is not found", id);
              http.setStatus(HttpStatus.INTERNAL_SERVER_ERROR).end();
            }
            break;
          }
          case "abort": {
            String id = params.get("cettia-transport-id");
            if (id != null) {
              BaseTransport transport = transports.get(id);
              if (transport != null) {
                transport.close();
              }
            }
            http.setHeader("content-type", "text/javascript; charset=utf-8").end();
            break;
          }
          default:
            log.error("when, {}, is not supported", params.get("cettia-transport-when"));
            http.setStatus(HttpStatus.NOT_IMPLEMENTED).end();
            break;
        }
        break;
      }
      case POST: {
        final String id = params.get("cettia-transport-id");
        switch (http.header("content-type") == null ? "" : http.header("content-type")
          .toLowerCase()) {
          case "text/plain; charset=utf-8":
          case "text/plain; charset=utf8":
          case "text/plain;charset=utf-8":
          case "text/plain;charset=utf8":
            http.onbody((String body) -> {
              BaseTransport transport = transports.get(id);
              if (transport != null) {
                transport.handleText(body.substring("data=".length()));
              } else {
                log.error("A POST message arrived but no transport#{} is found", id);
                http.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
              }
              http.end();
            })
            .readAsText();
            break;
          case "application/octet-stream":
            http.onbody((ByteBuffer body) -> {
              BaseTransport transport = transports.get(id);
              if (transport != null) {
                transport.handleBinary(body);
              } else {
                log.error("A POST message arrived but no transport#{} is found", id);
                http.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
              }
              http.end();
            })
            .readAsBinary();
            break;
          default:
            BaseTransport transport = transports.get(id);
            if (transport != null) {
              // TODO improve
              transport.handleError(new RuntimeException("protocol"));
              transport.close();
            }
            http.setStatus(HttpStatus.INTERNAL_SERVER_ERROR).end();
            break;
        }
        break;
      }
      default:
        log.error("HTTP method, {}, is not supported", http.method());
        http.setStatus(HttpStatus.METHOD_NOT_ALLOWED).end();
        break;
    }
  }

  @Override
  public HttpTransportServer ontransport(Action<ServerTransport> action) {
    transportActions.add(action);
    return this;
  }

  /**
   * Base class for HTTP transport.
   *
   * @author Donghwan Kim
   */
  private static abstract class BaseTransport extends BaseServerTransport {

    protected final ServerHttpExchange http;
    protected final Map<String, String> params;
    protected String id = UUID.randomUUID().toString();
    // For JSON processing in long polling
    protected ObjectMapper mapper = new ObjectMapper();

    public BaseTransport(ServerHttpExchange http) {
      this.params = parseQuery(http.uri());
      this.http = http;
    }

    public String id() {
      return id;
    }

    @Override
    public String uri() {
      return http.uri();
    }

    public void handleText(String text) {
      textActions.fire(text);
    }

    public void handleBinary(ByteBuffer binary) {
      binaryActions.fire(binary);
    }

    public void handleError(Throwable error) {
      errorActions.fire(error);
    }

    /**
     * {@link ServerHttpExchange} is available.
     */
    @Override
    public <T> T unwrap(Class<T> clazz) {
      return ServerHttpExchange.class.isAssignableFrom(clazz) ? clazz.cast(http) : null;
    }

  }

  /**
   * Represents a server-side HTTP Streaming transport.
   *
   * @author Donghwan Kim
   */
  private static class StreamTransport extends BaseTransport {

    private final static String TEXT_2KB = CharBuffer.allocate(2048).toString().replace('\0', ' ');

    public StreamTransport(ServerHttpExchange http) {
      super(http);
      Map<String, String> query = new LinkedHashMap<>();
      query.put("cettia-transport-version", "1.0");
      query.put("cettia-transport-id", id);
      http.onfinish($ -> closeActions.fire())
      .onerror(throwable -> errorActions.fire(throwable))
      .onclose($ -> closeActions.fire())
      .setHeader("content-type", "text/" + ("true".equals(params.get("cettia-transport-sse")) ? "event-stream" :
        "plain") + "; charset=utf-8")
      .write(TEXT_2KB + "\ndata: ?" + formatQuery(query) + "\n\n");
    }

    @Override
    protected void doSend(String data) {
      sendEventStreamMessage("1" + data);
    }

    @Override
    protected void doSend(ByteBuffer data) {
      byte[] bytes = new byte[data.remaining()];
      data.get(bytes);
      sendEventStreamMessage("2" + Base64.getEncoder().encodeToString(bytes));
    }

    private synchronized void sendEventStreamMessage(String data) {
      String payload = "";
      for (String line : data.split("\r\n|\r|\n")) {
        payload += "data: " + line + "\n";
      }
      payload += "\n";
      http.write(payload);
    }

    @Override
    public synchronized void doClose() {
      http.end();
    }

  }

  /**
   * Represents a server-side HTTP Long Polling transport.
   *
   * @author Donghwan Kim
   */
  private static class LongpollTransport extends BaseTransport {

    private AtomicReference<ServerHttpExchange> httpRef = new AtomicReference<>();
    private AtomicBoolean aborted = new AtomicBoolean();
    // Regard it as http.endedWithMessage
    private AtomicBoolean endedWithMessage = new AtomicBoolean();
    private AtomicReference<Timer> closeTimer = new AtomicReference<>();
    private Queue<Object> cache = new ConcurrentLinkedQueue<>();

    public LongpollTransport(ServerHttpExchange http) {
      super(http);
      refresh(http);
    }

    public void refresh(ServerHttpExchange http) {
      final Map<String, String> parameters = parseQuery(http.uri());
      http.onfinish($ -> {
        if (parameters.get("cettia-transport-when").equals("poll") && !endedWithMessage.get()) {
          closeActions.fire();
        } else {
          Timer timer = new Timer(true);
          timer.schedule(new TimerTask() {
            @Override
            public void run() {
              closeActions.fire();
            }
          }, 3000);
          closeTimer.set(timer);
        }
      })
      .onerror(throwable -> errorActions.fire(throwable))
      .onclose($ -> closeActions.fire());
      String when = parameters.get("cettia-transport-when");
      switch (when) {
        case "open":
          Map<String, String> query = new LinkedHashMap<>();
          query.put("cettia-transport-version", "1.0");
          query.put("cettia-transport-id", id);
          endWithMessage(http, "?" + formatQuery(query));
          break;
        case "poll":
          endedWithMessage.set(false);
          Timer timer = closeTimer.getAndSet(null);
          if (timer != null) {
            timer.cancel();
          }
          if (aborted.get()) {
            http.end();
          } else {
            Object cached = cache.poll();
            if (cached != null) {
              // As cached is either String or ByteBuffer
              if (cached instanceof String) {
                endWithMessage(http, (String) cached);
              } else {
                endWithMessage(http, (ByteBuffer) cached);
              }
            } else {
              httpRef.set(http);
            }
          }
          break;
        default:
          // TODO improve
          errorActions.fire(new RuntimeException("protocol"));
          close();
          break;
      }
    }

    @Override
    protected void doSend(String data) {
      ServerHttpExchange http = httpRef.getAndSet(null);
      if (http != null) {
        endWithMessage(http, data);
      } else {
        cache.offer(data);
      }
    }

    // Regard it as http.endWithMessage
    private void endWithMessage(ServerHttpExchange http, String data) {
      endedWithMessage.set(true);
      boolean jsonp = "true".equals(params.get("cettia-transport-jsonp"));
      if (jsonp) {
        try {
          data = params.get("cettia-transport-callback") + "(" +  mapper.writeValueAsString(data) + ");";
        } catch (JsonProcessingException e) {
          throw new RuntimeException(e);
        }
      }
      http.setHeader("content-type", "text/" + (jsonp ? "javascript" : "plain") + "; " +
        "charset=utf-8").end(data);
    }

    @Override
    protected void doSend(ByteBuffer data) {
      ServerHttpExchange http = httpRef.getAndSet(null);
      if (http != null) {
        endWithMessage(http, data);
      } else {
        cache.offer(data);
      }
    }

    // Regard it as http.endWithMessage
    private void endWithMessage(ServerHttpExchange http, ByteBuffer data) {
      endedWithMessage.set(true);
      http.setHeader("content-type", "application/octet-stream").end(data);
    }

    @Override
    public void doClose() {
      ServerHttpExchange http = httpRef.getAndSet(null);
      if (http != null) {
        http.end();
      } else {
        aborted.set(true);
      }
    }

  }

}
