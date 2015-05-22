# nucifraga
a httpclient that replays initial response to a request

# why?
Web resources are great, but they change over time. The nucifraga archives http request-response pairs.
On executing a request that has a response in the archive, the archived response is returned. This mean that
the client is [purely functional](http://en.wikipedia.org/wiki/Purely_functional): given a specific input, it always
returns the same output.

The specific use case that started this library is open science: many data providers offer data apis. In order to
reproduce results using these apis, the responses have to be saved so that they can be replayed later.

Note that archiving is very different from caching. Http caching is part of the (http specification)[http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html]
and allows for servers to tell their clients which content that may be cached and for how long.

The name [nucifraga](http://eol.org/pages/92861/overview) is the scientific name of a genus of bird commonly referred to as [nutcrackers](http://eol.org/pages/92861/overview). These birds are pretty good at located thousands of nuts that they buried earlier. Thanks to [Owen Pietrokowsky](http://rightbrainscience.wordpress.com) for helping come up with the name.

# how does it work?
New http requests and their responses are saved in directories. On repeat request, the saved responses are read from disk and returned to the client.

This library is built on [Apache's HttpClient](http://hc.apache.org/httpcomponents-client-ga/), a pretty widely used and configurable http client.

# how to use?

```java
HttpClient client = Nucifraga.create().build();

String firstResponse = client.execute(new HttpGet("http://www.timeapi.org/utc/now"), new BasicResponseHandler());
String secondResponse = client.execute(new HttpGet("http://www.timeapi.org/utc/now"), new BasicResponseHandler());
// secondResponse is the same as the firstResponse, because the first response is used for same following request


// configure archive directories
clientBuilder = Nucifraga.create()
    .setEntityDir(new File("archive/entity"))
    .setResponseRequestDir(new File("archive/request-response"));

// clean archive
clientBuilder.clean();
```

# related project
If you are looking for libraries specifically geared towards testing, please see [Betamax](https://github.com/robfletcher/betamax) .

If you are looking for a http client that implements "formal" http caching, see [Apache HttpClient Cache](http://hc.apache.org/httpcomponents-client-ga/tutorial/html/caching.html).
