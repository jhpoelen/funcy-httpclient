package org.eol.globi.util;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

// from apache client
public class ResponseProxyHandler implements InvocationHandler {

    private static final Method CLOSE_METHOD;

    static {
        try {
            CLOSE_METHOD = Closeable.class.getMethod("close");
        } catch (final NoSuchMethodException ex) {
            throw new Error(ex);
        }
    }

    private final HttpResponse original;

    public ResponseProxyHandler(final HttpResponse original) {
        super();
        this.original = original;
    }

    public void close() throws IOException {
        HttpEntity entity = original.getEntity();
        if (entity != null) {
            if (entity.isStreaming()) {
                IOUtils.closeQuietly(entity.getContent());
            }
        }
    }

    public Object invoke(
            final Object proxy, final Method method, final Object[] args) throws Throwable {
        if (method.equals(CLOSE_METHOD)) {
            close();
            return null;
        } else {
            try {
                return method.invoke(this.original, args);
            } catch (final InvocationTargetException ex) {
                final Throwable cause = ex.getCause();
                if (cause != null) {
                    throw cause;
                } else {
                    throw ex;
                }
            }
        }
    }

}
