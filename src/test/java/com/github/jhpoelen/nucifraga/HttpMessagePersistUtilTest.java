package com.github.jhpoelen.nucifraga;

import org.apache.http.Header;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicRequestLine;
import org.apache.http.message.BasicStatusLine;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

public class HttpMessagePersistUtilTest {

    public static final File ENTITY_DEST_DIR = new File("target/http-entity");
    public static final File REQ_RES_DEST_DIR = new File("target/http-request-response");

    @Before
    @After
    public void clean() throws IOException {
        HttpMessagePersistUtil.clean(ENTITY_DEST_DIR, REQ_RES_DEST_DIR);
    }


    @Test
    public void responseToYaml() throws IOException {
        final HttpResponse res = testResponse();
        Map<String, Object> resMeta = HttpMessagePersistUtil.responseToMap(res, ENTITY_DEST_DIR);
        assertImportExport(resMeta);
    }

    private HttpResponse testResponse() {
        final HttpResponse res = new BasicHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK"));
        res.addHeader("key1", "value1");
        res.addHeader("key2", "value2-a");
        res.addHeader("key2", "value2-b");
        res.setEntity(new StringEntity("testing 123", "UTF-8"));
        return res;
    }

    private void assertImportExport(Map<String, Object> resMeta) {
        Yaml yaml = new Yaml();
        String yamlString = yaml.dump(resMeta);
        Object obj = yaml.load(yamlString);
        assertThat(yaml.dump(obj), is(yamlString));
    }

    @Test
    public void requestToYaml() throws IOException {
        final HttpEntityEnclosingRequest req = testRequest();
        Map<String, Object> resMeta = HttpMessagePersistUtil.requestToMap(req, ENTITY_DEST_DIR, Arrays.asList("Host", "Accept-Encoding"));
        assertImportExport(resMeta);
    }

    @Test
    public void requestToYamlIncludeSpecificHeadersOnly() throws IOException {
        final HttpEntityEnclosingRequest req = testRequest();
        req.setHeader("stable", "123");
        req.setHeader("instable", "4");

        final HttpEntityEnclosingRequest anotherReq = testRequest();
        anotherReq.setHeader("stable", "123");
        anotherReq.setHeader("another instable", "4");

        Map<String, Object> reqMap = HttpMessagePersistUtil.requestToMap(req, ENTITY_DEST_DIR, Arrays.asList("stable", "another stable"));
        Map<String, Object> anotherReqMap = HttpMessagePersistUtil.requestToMap(anotherReq, ENTITY_DEST_DIR, Arrays.asList("stable", "another stable"));

        Yaml yaml = new Yaml();
        assertThat(yaml.dump(reqMap), is(yaml.dump(anotherReqMap)));

        Map<String, Object> reqMapAll = HttpMessagePersistUtil.requestToMap(req, ENTITY_DEST_DIR, null);
        Map<String, Object> anotherReqMapAll = HttpMessagePersistUtil.requestToMap(anotherReq, ENTITY_DEST_DIR, null);

        assertThat(yaml.dump(reqMapAll), is(not(yaml.dump(anotherReqMapAll))));
    }

    private HttpEntityEnclosingRequest testRequest() {
        final HttpEntityEnclosingRequest req = new BasicHttpEntityEnclosingRequest(new BasicRequestLine("GET", "/api/something", HttpVersion.HTTP_1_1));
        req.setHeader("key3", "value3");
        req.setHeader("key4", "value4");
        req.setEntity(new StringEntity("testing 456s", "UTF-8"));
        return req;
    }

    @Test
    public void persistRequestResponse() throws IOException {
        HttpRoute testRoute = new HttpRoute(new HttpHost("goo", 80, "http"));
        HttpRoute anotherTestRoute = new HttpRoute(new HttpHost("goo", 443, "https"));
        assertThat(HttpMessagePersistUtil.respondTo(testRoute, testRequest(), ENTITY_DEST_DIR, REQ_RES_DEST_DIR), is(nullValue()));
        HttpEntityEnclosingRequest req = testRequest();
        HttpResponse res = testResponse();
        HttpMessagePersistUtil.persist(testRoute, req, res, ENTITY_DEST_DIR, REQ_RES_DEST_DIR);
        assertThat(HttpMessagePersistUtil.respondTo(anotherTestRoute, testRequest(), ENTITY_DEST_DIR, REQ_RES_DEST_DIR), is(Matchers.nullValue()));
        CloseableHttpResponse archivedRes = HttpMessagePersistUtil.respondTo(testRoute, testRequest(), ENTITY_DEST_DIR, REQ_RES_DEST_DIR);
        assertThat(archivedRes, is(notNullValue()));

        assertThat(res.getAllHeaders().length, is(res.getAllHeaders().length));

        Set<String> names = new HashSet<String>();
        for (Header header : res.getAllHeaders()) {
            names.add(header.getName());
        }

        for (String name : names) {
            Header[] headers = res.getHeaders(name);
            List<String> values = new ArrayList<String>();
            for (Header header : headers) {
                values.add(header.getValue());
            }
            Header[] archivedHeaders = archivedRes.getHeaders(name);
            List<String> archivedValues = new ArrayList<String>();
            for (Header header : archivedHeaders) {
                archivedValues.add(header.getValue());
            }
            assertThat("missing header for name [" + name + "]", archivedValues, containsInAnyOrder(values.toArray()));
        }

    }


}
