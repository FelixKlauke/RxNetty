/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package io.reactivex.netty.client;

import io.reactivex.netty.channel.Connection;
import io.reactivex.netty.client.events.ClientEventListener;
import io.reactivex.netty.events.ListenersHolder;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.annotations.Beta;
import rx.functions.Action1;
import rx.subscriptions.Subscriptions;

/**
 * An extension of {@link Observable} that adds the functionality of listening to all events generated by all
 * connections created by this {@code Observable}. One can listen to these events by subscribing using
 * {@link #subscribeForEvents(ClientEventListener)}
 *
 * @param <W> Type of object that is written to the connections created by this observable.
 * @param <R> Type of object that is read from the connections created by this observable.
 */
@Beta
public final class ConnectionObservable<R, W> extends Observable<Connection<R, W>> {

    private final OnSubcribeFunc<R, W> f;

    private ConnectionObservable(final OnSubcribeFunc<R, W> f) {
        super(new OnSubscribe<Connection<R, W>>() {
            @Override
            public void call(Subscriber<? super Connection<R, W>> subscriber) {
                f.call(subscriber);
            }
        });
        this.f = f;
    }

    /**
     * Subscribes a listener to listen for all events on all connections created by this observable.
     *
     * @param eventListener A listener to listen for events.
     *
     * @return Subcription unsubscribing from which will unregister the listener.
     */
    public Subscription subscribeForEvents(ClientEventListener eventListener) {
        return f.subscribeForEvents(eventListener);
    }

    /**
     * Creates a new {@code ConnectionObservable} that sends an error on each subscription.
     *
     * @param error Error to send for each subscription.
     *
     * @param <W> Type of object that is written to the connections created by the returned Observable.
     * @param <R> Type of object that is read from the connections created by the returned Observable.
     *
     * @return A new {@link ConnectionObservable}
     */
    public static <R, W> ConnectionObservable<R, W> forError(final Throwable error) {
        return new ConnectionObservable<>(new OnSubcribeFunc<R, W>() {
            @Override
            public Subscription subscribeForEvents(ClientEventListener eventListener) {
                return Subscriptions.empty();
            }

            @Override
            public void call(Subscriber<? super Connection<R, W>> subscriber) {
                subscriber.onError(error);
            }
        });
    }

    /**
     * Creates a new {@code ConnectionObservable} that delegates the subscriptions to the passed function.
     *
     * @param onSubscribe Function to handle the subscriptions.
     *
     * @param <W> Type of object that is written to the connections created by the returned Observable.
     * @param <R> Type of object that is read from the connections created by the returned Observable.
     *
     * @return A new {@link ConnectionObservable}
     */
    public static <R, W> ConnectionObservable<R, W> createNew(final OnSubcribeFunc<R, W> onSubscribe) {
        return new ConnectionObservable<>(onSubscribe);
    }

    /**
     * An extension of {@link Observable.OnSubscribe} to add {@link #subscribeForEvents(ClientEventListener)} for listening to
     * all events on all connections created from this function.
     *
     * @param <W> Type of object that is written to the connections created by this function.
     * @param <R> Type of object that is read from the connections created by this function.
     *
     * @see AbstractOnSubscribeFunc
     */
    public interface OnSubcribeFunc<R, W> extends OnSubscribe<Connection<R, W>> {

        /**
         * Subscribes a listener to listen for all events on all connections created by this observable.
         *
         * @param eventListener A listener to listen for events.
         *
         * @return Subcription, unsubscribing from which will unregisted the listener.
         */
        Subscription subscribeForEvents(ClientEventListener eventListener);

    }

    /**
     * An abstract implementation of {@link OnSubcribeFunc} that is useful for writing {@link OnSubcribeFunc} that
     * gets a {@link ConnectionObservable} from another source but returns a different {@link ConnectionObservable} to
     * the caller.
     *
     * @param <W> Type of object that is written to the connections created by this function.
     * @param <R> Type of object that is read from the connections created by this function.
     */
    public static abstract class AbstractOnSubscribeFunc<R, W> implements OnSubcribeFunc<R, W> {

        private final ListenersHolder<ClientEventListener> listeners = new ListenersHolder<>();

        @Override
        public final void call(Subscriber<? super Connection<R, W>> subscriber) {
            doSubscribe(subscriber, new Action1<ConnectionObservable<R, W>>() {
                @Override
                public void call(ConnectionObservable<R, W> connectionObservable) {
                    listeners.subscribeAllTo(connectionObservable);
                }
            });
        }

        @Override
        public final Subscription subscribeForEvents(ClientEventListener eventListener) {
            return listeners.subscribe(eventListener);
        }

        /**
         * Implementation of subscription handling with a handle to subscribe all listeners registered to this
         * function via {@link #subscribeForEvents(ClientEventListener)} to the passed {@link ConnectionObservable}
         *
         * @param sub Subcriber
         * @param subscribeAllListenersAction Action to invoke when a new {@link ConnectionObservable} is obtained from
         * another source and all listeners registered here are to be registered to this new
         * {@link ConnectionObservable}.
         */
        protected abstract void doSubscribe(Subscriber<? super Connection<R, W>> sub,
                                            Action1<ConnectionObservable<R, W>> subscribeAllListenersAction);
    }
}