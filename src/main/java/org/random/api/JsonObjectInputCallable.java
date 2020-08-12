package org.random.api;

import com.google.gson.JsonObject;
import java.util.concurrent.Callable;

/**
 * Allow an input JsonObject to be set for a Callable.
 */
public class JsonObjectInputCallable<T> implements Callable<T> {

    protected JsonObject input;

    public void setInput(JsonObject input) {
        this.input = input;
    }

    @Override
    public T call() throws Exception {
        return null;
    }
}
