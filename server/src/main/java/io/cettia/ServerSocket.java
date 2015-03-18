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
import io.cettia.transport.ServerTransport;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface used to interact with the remote socket.
 * <p>
 * {@code ServerSocket} produced by {@link Server} is used to send and receive
 * event to and from the remote socket. The normal usage to use
 * {@code ServerSocket} is to create a socket action and pass it to
 * {@link Server}. If you are going to hold a reference on {@code ServerSocket},
 * you should do something when it is closed through
 * {@link ServerSocket#onclose(Action)}.
 * <p>
 * Sockets may be accessed by multiple threads.
 * 
 * @author Donghwan Kim
 */
public interface ServerSocket extends AbstractServerSocket<ServerSocket> {

    /**
     * A URI used to connect. To work with URI parts, use {@link URI} or
     * something like that.
     */
    String uri();

    /**
     * A modifiable set of tag names.
     */
    Set<String> tags();

    /**
     * Adds a given event handler for a given event.
     * <p>
     * The allowed types for {@code T} are Java types corresponding to JSON
     * types.
     * <table>
     * <thead>
     * <tr>
     * <th>JSON</th>
     * <th>Java</th>
     * </tr>
     * </thead> <tbody>
     * <tr>
     * <td>Number</td>
     * <td>{@link Integer} or {@link Double}</td>
     * </tr>
     * <tr>
     * <td>String</td>
     * <td>{@link String}</td>
     * </tr>
     * <tr>
     * <td>Boolean</td>
     * <td>{@link Boolean}</td>
     * </tr>
     * <tr>
     * <td>Array</td>
     * <td>{@link List}, {@code List<T>} in generic</td>
     * </tr>
     * <tr>
     * <td>Object</td>
     * <td>{@link Map}, {@code Map<String, T>} in generic</td>
     * </tr>
     * <tr>
     * <td>null</td>
     * <td>{@code null}, {@link Void} for convenience</td>
     * </tr>
     * </tbody>
     * </table>
     * 
     * If the counterpart sends an event with callback, {@code T} should be
     * {@link Reply}.
     */
    <T> ServerSocket on(String event, Action<T> action);

    /**
     * Executed if the socket is closed for any reason. Equivalent to
     * <code>socket.on("close", action)</code>
     */
    ServerSocket onclose(Action<Void> action);

    /**
     * Executed if there was any error on the socket. You don't need to close it
     * explicitly on <code>error</code> event. Equivalent to
     * <code>socket.on("error", action)</code>
     */
    ServerSocket onerror(Action<Throwable> action);

    /**
     * Removes a given event handler for a given event.
     */
    <T> ServerSocket off(String event, Action<T> action);

    /**
     * Sends a given event with data attaching resolved callback.
     * <p>
     * For the allowed types for {@code T}, see
     * {@link ServerSocket#on(String, Action)}.
     */
    <T> ServerSocket send(String event, Object data, Action<T> resolved);

    /**
     * Sends a given event with data attaching resolved callback and rejected
     * callback.
     * <p>
     * For the allowed types for {@code T}, see
     * {@link ServerSocket#on(String, Action)}.
     */
    <T, U> ServerSocket send(String event, Object data, Action<T> resolved, Action<U> rejected);

    /**
     * Returns the underlying component. {@link ServerTransport} is available.
     */
    <T> T unwrap(Class<T> clazz);

    /**
     * Interface to deal with reply.
     * <p>
     * For the allowed types for {@code T}, see {@link ServerSocket#on(String, Action)}.
     * 
     * @author Donghwan Kim
     */
    interface Reply<T> {

        /**
         * The original data.
         */
        T data();

        /**
         * Resolves.
         */
        void resolve();

        /**
         * Resolves with the value.
         */
        void resolve(Object data);

        /**
         * Rejects.
         */
        void reject();

        /**
         * Rejects with the reason.
         */
        void reject(Object error);

    }

}
