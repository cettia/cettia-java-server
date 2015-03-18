/*
 * Copyright 2015 The Cettia Project
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

import io.cettia.platform.action.Action;
import io.cettia.platform.action.Actions;
import io.cettia.platform.action.ConcurrentActions;
import io.cettia.platform.action.VoidAction;
import io.cettia.transport.ServerTransport;
import io.cettia.transport.http.HttpTransportServer;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Default implementation of {@link Server}.
 * <p>
 * This implementation consumes {@link ServerTransport} and produces
 * {@link ServerSocket} following the Cettia protocol.
 * <p>
 * The following options are configurable.
 * <ul>
 * <li>{@link DefaultServer#setHeartbeat(int)}</li>
 * </ul>
 * 
 * @author Donghwan Kim
 */
public class DefaultServer implements Server {

    private final Logger log = LoggerFactory.getLogger(DefaultServer.class);
    private Set<ServerSocket> sockets = new CopyOnWriteArraySet<>();
    private int heartbeat = 20000;
    private int _heartbeat = 5000;
    private Actions<ServerSocket> socketActions = new ConcurrentActions<ServerSocket>()
    .add(new Action<ServerSocket>() {
        @Override
        public void on(final ServerSocket socket) {
            log.trace("{}'s request has opened", socket);
            sockets.add(socket);
            socket.onclose(new VoidAction() {
                @Override
                public void on() {
                    log.trace("{}'s request has been closed", socket);
                    sockets.remove(socket);
                }
            });
        }
    });

    @Override
    public void on(ServerTransport transport) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("heartbeat", "" + heartbeat);
        map.put("_heartbeat", "" + _heartbeat);
        socketActions.fire(new DefaultServerSocket(transport, map));
    }

    @Override
    public Sentence all() {
        return new Sentence(new Action<Action<ServerSocket>>() {
            @Override
            public void on(Action<ServerSocket> action) {
                all(action);
            }
        });
    }

    @Override
    public Server all(Action<ServerSocket> action) {
        for (ServerSocket socket : sockets) {
            action.on(socket);
        }
        return this;
    }

    @Override
    public Sentence byTag(final String... names) {
        return new Sentence(new Action<Action<ServerSocket>>() {
            @Override
            public void on(Action<ServerSocket> action) {
                byTag(names, action);
            }
        });
    }

    @Override
    public Server byTag(String name, Action<ServerSocket> action) {
        return byTag(new String[] { name }, action);
    }

    @Override
    public Server byTag(String[] names, Action<ServerSocket> action) {
        List<String> nameList = Arrays.asList(names);
        for (ServerSocket socket : sockets) {
            if (socket.tags().containsAll(nameList)) {
                action.on(socket);
            }
        }
        return this;
    }

    @Override
    public Server onsocket(Action<ServerSocket> action) {
        socketActions.add(action);
        return this;
    }

    /**
     * A heartbeat interval in milliseconds to maintain a connection alive and
     * prevent server from holding idle connections. The default is
     * <code>20</code>s and should be larger than <code>5</code>s.
     */
    public void setHeartbeat(int heartbeat) {
        this.heartbeat = heartbeat;
    }

    /**
     * To speed up the protocol tests. Not for production use.
     */
    public void set_heartbeat(int _heartbeat) {
        this._heartbeat = _heartbeat;
    }

    private static class DefaultServerSocket implements ServerSocket {
        private final ServerTransport transport;
        private ObjectMapper mapper = new ObjectMapper();
        private AtomicInteger eventId = new AtomicInteger();
        private Set<String> tags = new CopyOnWriteArraySet<>();
        private ConcurrentMap<String, Actions<Object>> actionsMap = new ConcurrentHashMap<>();
        private ConcurrentMap<String, Map<String, Action<Object>>> callbacksMap = new ConcurrentHashMap<>();
        private AtomicReference<Timer> heartbeatTimer = new AtomicReference<>();

        public DefaultServerSocket(final ServerTransport transport, Map<String, String> query) {
            this.transport = transport;
            actionsMap.put("error", new ConcurrentActions<>());
            transport.onerror(new Action<Throwable>() {
                @Override
                public void on(Throwable throwable) {
                    actionsMap.get("error").fire(throwable);
                }
            });
            actionsMap.put("close", new ConcurrentActions<>(new Actions.Options().once(true).memory(true)));
            transport.onclose(new VoidAction() {
                @Override
                public void on() {
                    actionsMap.get("close").fire();
                }
            });
            transport.ontext(new Action<String>() {
                @Override
                public void on(String text) {
                    final Map<String, Object> event = parseEvent(text);
                    Actions<Object> actions = actionsMap.get(event.get("type"));
                    if (actions != null) {
                        if ((Boolean) event.get("reply")) {
                            final AtomicBoolean sent = new AtomicBoolean();
                            actions.fire(new Reply<Object>() {
                                @Override
                                public Object data() {
                                    return event.get("data");
                                }

                                @Override
                                public void resolve() {
                                    resolve(null);
                                }

                                @Override
                                public void resolve(Object value) {
                                    sendReply(value, false);
                                }

                                @Override
                                public void reject() {
                                    reject(null);
                                }

                                @Override
                                public void reject(Object value) {
                                    sendReply(value, true);
                                }

                                private void sendReply(Object value, boolean exception) {
                                    if (sent.compareAndSet(false, true)) {
                                        Map<String, Object> result = new LinkedHashMap<String, Object>();
                                        result.put("id", event.get("id"));
                                        result.put("data", value);
                                        result.put("exception", exception);
                                        send("reply", result);
                                    }
                                }
                            });
                        } else {
                            actions.fire(event.get("data"));
                        }
                    }
                }
            });
            on("reply", new Action<Map<String, Object>>() {
                @Override
                public void on(Map<String, Object> info) {
                    Map<String, Action<Object>> callbacks = callbacksMap.remove(info.get("id"));
                    Action<Object> action = (Boolean) info.get("exception") ? callbacks.get("rejected") : callbacks.get("resolved");
                    action.on(info.get("data"));
                }
            });
            final int heartbeat = Integer.parseInt(query.get("heartbeat"));
            heartbeatTimer.set(createCloseTimer(heartbeat));
            on("heartbeat", new VoidAction() {
                @Override
                public void on() {
                    heartbeatTimer.getAndSet(createCloseTimer(heartbeat)).cancel();
                    send("heartbeat");
                }
            });
            on("close", new VoidAction() {
                @Override
                public void on() {
                    heartbeatTimer.get().cancel();
                }
            });
            transport.send("?" + HttpTransportServer.formatQuery(query));
        }

        @Override
        public String uri() {
            return transport.uri();
        }

        @Override
        public Set<String> tags() {
            return tags;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> ServerSocket on(String event, Action<T> action) {
            Actions<Object> actions = actionsMap.get(event);
            if (actions == null) {
                Actions<Object> value = new ConcurrentActions<>();
                actions = actionsMap.putIfAbsent(event, value);
                if (actions == null) {
                    actions = value;
                }
            }
            actions.add((Action<Object>) action);
            return this;
        }

        @Override
        public ServerSocket onclose(Action<Void> action) {
            return on("close", action);
        }

        @Override
        public ServerSocket onerror(Action<Throwable> action) {
            return on("error", action);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> ServerSocket off(String event, Action<T> action) {
            Actions<Object> actions = actionsMap.get(event);
            if (actions != null) {
                actions.remove((Action<Object>) action);
            }
            return this;
        }

        @Override
        public ServerSocket send(String event) {
            return send(event, null);
        }

        @Override
        public ServerSocket send(String event, Object data) {
            return send(event, data, null);
        }

        @Override
        public <T> ServerSocket send(String type, Object data, Action<T> resolved) {
            return send(type, data, resolved, null);
        }
        
        @SuppressWarnings("unchecked")
        @Override
        public <T, U> ServerSocket send(String type, Object data, Action<T> resolved, Action<U> rejected) {
            String id = "" + eventId.incrementAndGet();
            Map<String, Object> event = new LinkedHashMap<String, Object>();
            event.put("id", id);
            event.put("type", type);
            event.put("data", data);
            event.put("reply", resolved != null || rejected != null);

            String text = stringifyEvent(event);
            transport.send(text);
            if (resolved != null || rejected != null) {
                Map<String, Action<Object>> cbs = new LinkedHashMap<String, Action<Object>>();
                cbs.put("resolved", (Action<Object>) resolved);
                cbs.put("rejected", (Action<Object>) rejected);
                callbacksMap.put(id, cbs);
            }
            return this;
        }

        @Override
        public void close() {
            transport.close();
        }

        @Override
        public ServerSocket tag(String... names) {
            tags.addAll(Arrays.asList(names));
            return this;
        }

        @Override
        public ServerSocket untag(String... names) {
            tags.removeAll(Arrays.asList(names));
            return this;
        }

        @Override
        public <T> T unwrap(Class<T> clazz) {
            return ServerTransport.class.isAssignableFrom(clazz) ? clazz.cast(transport) : null;
        }
        
        private Map<String, Object> parseEvent(String text) {
            try {
                return mapper.readValue(text, new TypeReference<Map<String, Object>>() {});
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        
        private String stringifyEvent(Map<String, Object> event) {
            try {
                return mapper.writeValueAsString(event);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        private Timer createCloseTimer(int heartbeat) {
            Timer timer = new Timer(true);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    actionsMap.get("error").fire(new HeartbeatFailedException());
                    close();
                }
            }, heartbeat);
            return timer;
        }
    }

}
