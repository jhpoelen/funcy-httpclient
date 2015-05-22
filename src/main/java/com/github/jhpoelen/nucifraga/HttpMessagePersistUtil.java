package com.github.jhpoelen.nucifraga;

import com.Ostermiller.util.MD5;
import com.Ostermiller.util.MD5OutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.conn.routing.RouteInfo;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.message.BasicHttpResponse;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class HttpMessagePersistUtil {
    protected static Map<String, Object> responseToMap(final HttpResponse res, final File destDir) throws IOException {
        return new TreeMap<String, Object>() {{
            Map<String, List<String>> resHeader = new TreeMap<String, List<String>>();
            Header[] allHeaders = res.getAllHeaders();
            for (final Header header : allHeaders) {
                List<String> values = resHeader.get(header.getName());
                if (values == null) {
                    resHeader.put(header.getName(), new ArrayList<String>() {{
                        add(header.getValue());
                    }});
                } else {
                    values.add(header.getValue());
                }
            }
            put("header", resHeader);

            Map<String, String> body = new TreeMap<String, String>();
            String md5 = persistStream(res.getEntity().getContent(), destDir);
            body.put("hash", md5);
            body.put("hashType", "md5");
            put("entity", body);

            put("statusLine", new TreeMap<String, String>() {{
                put("version", res.getStatusLine().getProtocolVersion().toString());
                put("status", Integer.toString(res.getStatusLine().getStatusCode()));
                put("reason", res.getStatusLine().getReasonPhrase());
            }});
        }};
    }

    public static void persist(final RouteInfo route, final HttpRequest req, final HttpResponse res, final File entityDestDir, File reqResDestDir) throws IOException {
        String requestHash = calculateRequestHash(route, req, entityDestDir);

        final Map<String, Object> reqRes = new TreeMap<String, Object>() {
            {
                put("response", responseToMap(res, entityDestDir));
                put("request", requestToMap(req, entityDestDir, null));
                put("route", routeToMap(route));
            }
        };
        persistStream(IOUtils.toInputStream(new Yaml().dump(reqRes)), reqResDestDir, requestHash);
    }

    public static String calculateRequestHash(final RouteInfo route, final HttpRequest req, final File entityDestDir) throws IOException {
        Map<String, Object> map = new TreeMap<String, Object> () {{
            put("response", requestToMap(req, entityDestDir, Arrays.asList("Host", "Accept-Encoding", "http.target_host")));
            put("route", routeToMap(route));
        }
        };
        return MD5.getHashString(new Yaml().dump(map), "UTF-8");
    }

    private static Map<String, String> routeToMap(final RouteInfo route) {
        return new TreeMap<String, String>() {{
                put("targetHost", route.getTargetHost().toURI());
            }
            };
    }

    protected static Map<String, Object> requestToMap(final HttpRequest req, final File destDir, final List<String> includeHeaders) throws IOException {
        return new TreeMap<String, Object>() {
            {
                Map<String, List<String>> reqHeader = new TreeMap<String, List<String>>();
                Header[] allHeaders = req.getAllHeaders();
                for (final Header header : allHeaders) {
                    if (includeHeaders == null || includeHeaders.contains(header.getName())) {
                        List<String> values = reqHeader.get(header.getName());
                        if (values == null) {
                            reqHeader.put(header.getName(), new ArrayList<String>() {{
                                add(header.getValue());
                            }});
                        } else {
                            values.add(header.getValue());
                        }
                    }
                }
                put("header", reqHeader);

                if (req instanceof HttpEntityEnclosingRequest) {
                    HttpEntityEnclosingRequest entityReq = (HttpEntityEnclosingRequest) req;
                    String md5 = persistStream(entityReq.getEntity().getContent(), destDir);
                    Map<String, String> body = new TreeMap<String, String>();
                    body.put("hash", md5);
                    body.put("hashType", "md5");
                    put("entity", body);
                }

                put("requestLine", new TreeMap<String, String>() {{
                    put("version", req.getRequestLine().getProtocolVersion().toString());
                    put("method", req.getRequestLine().getMethod());
                    put("uri", req.getRequestLine().getUri());
                }});
            }


        };
    }

    protected static String persistStream(InputStream is, File destDir) throws IOException {
        return persistStream(is, destDir, null);
    }

    protected static String persistStream(InputStream is, File destDir, String filename) throws IOException {
        File tmpFile = File.createTempFile("entity", "blob");

        OutputStream out = new FileOutputStream(tmpFile);
        MD5OutputStream os = new MD5OutputStream(out);
        IOUtils.copy(is, os);
        os.flush();
        IOUtils.closeQuietly(os);

        String hashString = os.getHashString();
        File destFile = new File(destDir, filename == null ? hashString : filename);
        if (destFile.exists()) {
            FileUtils.deleteQuietly(tmpFile);
        } else {
            FileUtils.moveFile(tmpFile, destFile);
        }
        return hashString;
    }

    public static CloseableHttpResponse respondTo(final RouteInfo route, final HttpRequest request, final File entityDestDir, File reqResDestDir) throws IOException {
        CloseableHttpResponse resp = null;
        String requestHash = calculateRequestHash(route, request, entityDestDir);
        File persistedRequestResponseFile = new File(reqResDestDir, requestHash);
        if (persistedRequestResponseFile.exists()) {
            TreeMap<String, Object> load = new Yaml().loadAs(new FileInputStream(persistedRequestResponseFile), new TreeMap<String, Object>().getClass());
            Map<String, Object> response1 = (Map<String, Object>) load.get("response");
            Map<String, String> statusLine = (Map<String, String>) response1.get("statusLine");
            Map<String, String> entity = (Map<String, String>) response1.get("entity");
            Map<String, List<String>> header = (Map<String, List<String>>) response1.get("header");

            final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, Integer.parseInt(statusLine.get("status")), statusLine.get("reason"));
            for (Map.Entry<String, List<String>> entry : header.entrySet()) {
                List<String> values = entry.getValue();
                for (String value : values) {
                    response.addHeader(entry.getKey(), value);
                }
            }

            File persistedEntity = new File(entityDestDir, entity.get("hash"));
            response.setEntity(new InputStreamEntity(new FileInputStream(persistedEntity)));
            resp = (CloseableHttpResponse) Proxy.newProxyInstance(
                    ResponseProxyHandler.class.getClassLoader(),
                    new Class<?>[]{CloseableHttpResponse.class},
                    new ResponseProxyHandler(response));
        }
        return resp;
    }

    public static void clean(File entityDestDir, File reqResDestDir) throws IOException {
        clean(entityDestDir);
        clean(reqResDestDir);
    }

    private static void clean(File dir) throws IOException {
        if (dir.exists()) {
            FileUtils.deleteQuietly(dir);
        }
        FileUtils.forceMkdir(dir);
    }
}
