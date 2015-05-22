package org.eol.globi.util;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.StopWatch;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class FuncyHttpClientBuilderIT {

    private FuncyHttpClientBuilder clientBuilder;

    @Before
    public void clean() throws IOException {
        clientBuilder = FuncyHttpClientBuilder.create()
                .setEntityDir(new File("target/test-archive/http-entity/"))
                .setRequestionResponseDir(new File("target/test-archive/http-request-response/"));
        clientBuilder.clean();
    }

    //@After
    public void cleanup() throws IOException {
        clientBuilder.clean();
    }

    @Test
    public void cachingForNonCacheable() throws IOException {
        assertArchiving(new HttpGet("http://eol.org"));
    }

    @Test
    public void cachingForPostNonCacheable() throws IOException {
        HttpPost request = new HttpPost("http://eol.org");
        request.setEntity(new StringEntity("hello world"));
        assertArchiving(request);
    }

    @Test
    public void cachingForCacheable() throws IOException {
        String nonCacheableUri = "http://media.eol.org/content/2009/07/24/04/21692_98_68.jpg";
        assertArchiving(new HttpGet(nonCacheableUri));
    }

    @Test
    public void cachingForSSL() throws IOException {
        assertArchiving(new HttpGet("https://www.google.com"));
        assertArchiving(new HttpGet("http://www.google.com"));
    }

    protected void assertArchiving(HttpUriRequest request) throws IOException {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        HttpClient httpClient = clientBuilder.build();
        HttpResponse firstResponse = httpClient.execute(request);
        stopWatch.stop();
        long firstDelay = stopWatch.getTime();
        stopWatch.reset();
        stopWatch.start();
        HttpResponse secondResponse = httpClient.execute(request);
        long secondDelay = stopWatch.getTime();
        stopWatch.stop();
        assertThat("expected second delay [" + secondDelay + "] ms to be at least 10x shorter than first [" + firstDelay + "] ms",
                secondDelay, is(lessThan(firstDelay / 5)));
        assertThat(secondResponse.getAllHeaders().length, is(firstResponse.getAllHeaders().length));
        Header[] allHeaders = secondResponse.getAllHeaders();
        for (Header allHeader : allHeaders) {
            boolean hasHit = false;
            for (Header header : firstResponse.getAllHeaders()) {
                if (StringUtils.equals(allHeader.getName(), header.getName())
                        && StringUtils.equals(allHeader.getValue(), header.getValue())) {
                    hasHit = true;
                }
            }
            assertThat(allHeader.getName() + ": " + allHeader.getValue() + ":has no hit", hasHit, is(true));
        }
        String firstResponseContent = IOUtils.toString(firstResponse.getEntity().getContent());
        String secondResponseContent = IOUtils.toString(secondResponse.getEntity().getContent());
        assertThat(firstResponseContent, is(secondResponseContent));
    }
}
