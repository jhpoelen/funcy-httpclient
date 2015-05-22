package org.eol.globi.util;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.execchain.ClientExecChain;
import org.apache.http.protocol.HttpContext;

import java.io.File;
import java.io.IOException;

public class FuncyHttpClientBuilder extends HttpClientBuilder {

    private File entityDir = new File("http-archive/entity/");
    private File requestResponseDir = new File("http-archive/request-response/");


    public static FuncyHttpClientBuilder create() {
        final FuncyHttpClientBuilder builder = new FuncyHttpClientBuilder();
        builder
                .addInterceptorLast(new HttpResponseInterceptor() {
                    public void process(
                            final HttpResponse response,
                            final HttpContext context) throws HttpException, IOException {
                        HttpClientContext clientContext = HttpClientContext.adapt(context);
                        HttpMessagePersistUtil.persist(clientContext.getHttpRoute(), clientContext.getRequest(), response, builder.getEntityDir(), builder.getRequestResponseDir());
                        CloseableHttpResponse archivedResponse = HttpMessagePersistUtil.respondTo(clientContext.getHttpRoute(), clientContext.getRequest(), builder.getEntityDir(), builder.getRequestResponseDir());
                        response.setEntity(new InputStreamEntity(archivedResponse.getEntity().getContent()));
                    }
                });
        return builder;
    }

    @Override
    protected ClientExecChain decorateMainExec(final ClientExecChain mainExec) {
        return new ClientExecChain() {
            public CloseableHttpResponse execute(HttpRoute route, HttpRequestWrapper request, HttpClientContext clientContext, HttpExecutionAware execAware) throws IOException, HttpException {
                CloseableHttpResponse resp = HttpMessagePersistUtil.respondTo(route, request, getEntityDir(), getRequestResponseDir());
                return resp == null ? mainExec.execute(route, request, clientContext, execAware) : resp;
            }
        };
    }

    public void clean() throws IOException {
        HttpMessagePersistUtil.clean(getEntityDir(), getRequestResponseDir());
    }

    public FuncyHttpClientBuilder setEntityDir(final File entityDir) {
        this.entityDir = entityDir;
        return this;
    }

    public FuncyHttpClientBuilder setRequestionResponseDir(final File requestResponseDir) {
        this.requestResponseDir = requestResponseDir;
        return this;
    }

    public File getEntityDir() {
        return entityDir;
    }

    public File getRequestResponseDir() {
        return requestResponseDir;
    }


}
