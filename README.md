<p align="right">
<a href="https://autorelease.general.dmz.palantir.tech/palantir/dialogue"><img src="https://img.shields.io/badge/Perform%20an-Autorelease-success.svg" alt="Autorelease"></a>
</p>

# Dialogue [![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg)](https://opensource.org/licenses/Apache-2.0)

_Dialogue is a client-side library for HTTP-based RPC, designed to work well with [Conjure](https://palantir.github.io/conjure)-defined APIs._

## Features

- **ConcurrencyLimiters**: additive increase multiplicative decrease (AIMD) concurrency limiters ensure bursty traffic doesn't overload upstream servers.
- **Client-side node selection**: by making load balancing decisions in the client, Dialogue avoids the necessity for an L7 proxy (and its associated latency penalty).
- **Queue**: in the case where all nodes are limited (e.g. during a spike in traffic), requests are added to a FIFO queue and processed as soon as the one of the ConcurrencyLimiters has capacity.
- **Retries**: requests are retried a constant number of times, if possible.
- **Live reloading**: uris can be added or removed without losing ConcurrencyLimiter or node selection states.
- **Content decoding**: JSON, [SMILE](https://github.com/FasterXML/jackson-dataformats-binary/tree/master/smile) and [CBOR](https://github.com/FasterXML/jackson-dataformats-binary/tree/master/cbor) are supported by default, with user-defined encodings also supported.
- **Streaming**: requests and responses are streamed without buffering the entire body into memory.

## Observability

- **Zipkin-style tracing**: internal operations are instrumented using Zipkin-style [tracing-java spans](https://github.com/palantir/tracing-java), and `X-B3-TraceId` headers are propagated
- **Metrics**: Timers, meters and gauges are defined using [metric-schema](https://github.com/palantir/dialogue/blob/develop/dialogue-core/src/main/metrics/dialogue-core-metrics.yml) and stored in a [Tritium TaggedMetricRegistry](https://github.com/palantir/tritium).
- **Structured logging**: SLF4J logs are designed to be rendered as JSON, with every parameter declaratively named.

## Usage

Dialogue works best with Conjure-generated client bindings, i.e. for a given Conjure-defined `FooService`, the
[conjure-java](https://github.com/palantir/conjure-java) code generator produces two java interfaces:
`FooServiceBlocking` and `FooServiceAsync`. See the [conjure-java generated client bindings][] section below for more
details.

### Production usage

Your server framework should provide an abstraction to create clients that handle uri live-reloading and reuse connection pools. For example in Witchcraft, you can create a `FooServiceBlocking` like so:

```groovy
FooServiceBlocking fooService = witchcraft.conjureClients().client(FooServiceBlocking.class, "foo-service").get();

// network call:
List<Item> items = fooService.getItems();
```

The non-blocking instance can be constructed similarly:

```groovy
FooServiceAsync fooService = witchcraft.conjureClients().client(FooServiceAsync.class, "foo-service").get();

ListenableFuture<List<Item>> items = fooService.getItems();
```

### Under the hood

If the Witchcraft method above is not available, you can construct clients manually using a factory provided by
`com.palantir.dialogue:dialogue-clients`.

```java
DialogueClients.ReloadingFactory clients = DialogueClients.create(servicesConfigBlockRefreshable).withUserAgent(agent);

FooServiceBlocking client = clients.get(FooServiceBlocking.class, "foo-service");
FooServiceAsync client2 = clients.get(FooServiceAsync.class, "foo-service");
```

It's important to construct the ReloadingFactory _once_ for the lifetime of your JVM, and construct all clients from
this single clientfactory instance. This ensures that underlying Apache connection pools are reused correctly, and that
many clients all talking to the same URI will share the same concurrency limiter.

This abstraction uses [Apache HttpClient](https://hc.apache.org/httpcomponents-client-ga/) under the hood, as it is a reliable and performant HTTP client. (See alternatives below.)

_It is possible to construct a `DialogueChannel` manually which allows you to use alternative raw clients such as OkHttp
instead of Apache._

[conjure-java generated client bindings]: #conjure-java-generated-client-bindings
## conjure-java generated client bindings

Dialogue works best with generated client bindings, i.e. for a given Conjure-defined `FooService`, the [conjure-java](https://github.com/palantir/conjure-java) code generator can produce two java interfaces: `FooServiceBlocking` and `FooServiceAsync`. Generating these at compile-time means that making a request involves zero reflection - all serializers and deserializers are already set up in advance, so that zero efficiency compromises are made. A sample `getThing` endpoint with some path params, query params and a request body looks like this:

```java
@Override
public ListenableFuture<Thing> getThing(
        AuthHeader authHeader, String pathParam, List<ResourceIdentifier> queryKey, MyRequest body) {
    Request.Builder _request = Request.builder();
    _request.putHeaderParams("Authorization", plainSerDe.serializeBearerToken(authHeader.getBearerToken()));
    _request.putPathParams("path", plainSerDe.serializeString(pathParam));
    for (ResourceIdentifier queryKeyElement : queryKey) {
        _request.putQueryParams("queryKey", plainSerDe.serializeRid(queryKeyElement));
    }
    _request.body(myRequestSerializer.serialize(body));
    return runtime.clients()
            .call(channel, DialogueSampleEndpoints.getThing, _request.build(), thingDeserializer);
}
```

## Blocking or async

Of the two generated interfaces `FooServiceBlocking` and `FooServiceAync`, the blocking version is usually appropriate for 98% of use-cases, and results in much simpler control flow and error-handling. The async version returns Guava [`ListenableFutures`](https://github.com/google/guava/wiki/ListenableFutureExplained) so is a lot more fiddly to use. `Futures.addCallback` and `FluentFuture` are your friend here.

[dialogue-annotations-processor generated client bindings]: #dialogue-annotations-processor-generated-client-bindings

## dialogue-annotations-processor generated client bindings

``dialogue-annotations-processor`` is a retrofit replacement for use-cases where a service needs to talk to a
non-conjure server.

To setup the annotation simply add (make sure you are
using [gradle-processors](https://github.com/palantir/gradle-processors)):

```gradle
dependencies {
    annotationProcessor 'com.palantir.dialogue:dialogue-annotations-processor'
    implementation 'com.palantir.dialogue:dialogue-annotations'
}
```

Next create an annotated interface that describes the service you need to talk to, appropriately annotated
with ``@DialogueService``:

```java
import com.palantir.dialogue.DialogueService;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.annotations.Request;
import java.util.OptionalInt;
import java.util.UUID;

@DialogueService(MyServiceDialogueServiceFactory.class)
public interface MyService {

    @Request(method = HttpMethod.POST, path = "/params/{myPathParam}/{myPathParam2}", accept=MyCustomResponseDeserializer.class)
    MyCustomResponse params(
            @Request.QueryParam("q") String query,
            // Path parameter variable name must match the request path component
            @Request.PathParam UUID myPathParam,
            @Request.PathParam(encoder = MyCustomParamTypeEncoder.class) MyCustomParamType myPathParam2,
            // converts an custom type into a Multimap<String, String>
            @Request.QueryMap(encoder = MyCustomTypeEncoder.class) MyCustomQueryParamType myCustomQueryParam,
            @Request.Header("Custom-Header") int requestHeaderValue,
            // Headers can be optional
            @Request.Header("Custom-Optional-Header") OptionalInt maybeRequestHeaderValue,
            // Custom encoding classes may be provided for the request and response.
            @Request.Body(MySerializableTypeBodySerializer.class) MySerializableType body);
}
```

Features:

* Async request handling: simply make your method return ```ListenableFuture<Foo>```.
* Custom parameter types: ```@Request.(Header|PathParam|QueryParam)(encoder=MyCustomParamTypeEncoder.class)```.
* Custom serialization/deserialization: add ```@Request.Body(MySerializableTypeBodySerializer.class)```
  or ```@Request(accept=MyCustomResponseDeserializer.class)```.
* Custom serialization from ```Map```, ```Multimap``` and custom types into query parameters. This functions
  similarly to the Feign ```QueryMap``` feature, but with added control of customizing the serialization to
  query parameters.
* Authentication: builtin ```Authorization``` header handling if an annotated method has an ```AuthHeader``` parameter.

See more examples [on how to define clients](dialogue-annotations-example/src/main/java/com/palantir/myservice/example/MyService.java) and [use the generated code](dialogue-annotations-example/src/test/java/com/palantir/myservice/example/MyServiceIntegrationTest.java).

## Design

Dialogue is built around the `Channel` abstraction, with many different internal implementations that often add a little bit of behaviour and then delegate to another inner Channel.

```java
public interface Channel {
    ListenableFuture<Response> execute(Endpoint endpoint, Request request);
}
```

For example, the [TraceEnrichingChannel](dialogue-core/src/main/java/com/palantir/dialogue/core/TraceEnrichingChannel.java) just augments the request with a zipkin-style tracing headers and then calls a delegate.

_This API is influenced by gRPC's [Java library](https://github.com/grpc/grpc-java), which has a similar [Channel](https://github.com/grpc/grpc-java/blob/master/api/src/main/java/io/grpc/Channel.java) concept._

## Behaviour

### Concurrency limits

Every request passes through a pair of [AIMD](https://en.wikipedia.org/wiki/Additive_increase/multiplicative_decrease)
concurrency limiters.
There are two types of concurrency limiter: per-host, and per-endpoint. The former based on failures that
indicate the target host is in a degraded state, and the latter based on failures that are coupled to
an individual endpoint.
Each concurrency limiter operates in conjunction with a queue to stage pending requests until a
permit becomes available.

#### Limiter Diagram

```
+---------+   +----------+      +-------------+    +--------------------+   +--------------------------+   +----------------------------+
| Request +-->+Request Queue+-->+Node Selector+--->+Host Limiter (node0)+-->+Endpoint Queue(node0,ping)+-->+Endpoint Limiter(node0,ping)+---------+
+---------+   +----------+      +--------------+   +---------------------+  +--------------------------+   +----------------------------+         |
                                               |                         |                                                                        |
                                               |                         |  +---------------------------+  +-----------------------------+        |
                                               |                         +->+Endpoint Queue(node0,hello)+->+Endpoint Limiter(node0,hello)+----v   v
                                               |                            +---------------------------+  +-----------------------------+   +----+-------+
                                               |                                                                                             |HTTP Request|
                                               |                                                                                             +--+-+-------+
                                               |   +--------------------+   +--------------------------+   +----------------------------+       ^ ^
                                               +-->+Host Limiter (node1)+-->+Endpoint Queue(node1,ping)+-->+Endpoint Limiter(node1,ping)+-------+ |
                                                   +---------------------+  +--------------------------+   +----------------------------+         |
                                                                         |                                                                        |
                                                                         |  +---------------------------+  +-----------------------------+        |
                                                                         +->+Endpoint Queue(node1,hello)+->+Endpoint Limiter(node1,hello)+--------+
                                                                            +---------------------------+  +-----------------------------+
```

#### Host limits

Each host has a concurrency limiter which protects servers by stopping requests getting out the door on the client-side.
Permits are decreased after receiving 308 or 501-599 response, or encounting a network error (`IOException`).
Otherwise, they are increased.

#### Endpoint limits

Each endpoint has a concurrency limiter which is distinct for each host. This allows servers to provide backpressure with
additional specificity in the form of 429 status QoS responses. Permits are decreased after
receiving a 429 or 500 response code. Otherwise, they are increased.

### Node selection strategies
When configured with multiple uris, Dialogue has several strategies for choosing which upstream to route requests to.
The default strategy is `PIN_UNTIL_ERROR`, although users can choose alternatives such as `ROUND_ROBIN` when building a ClientConfiguration
object. Note that the choice of an appropriate strategy usually depends on the _upstream_ server's behaviour, i.e. if its
performance relies heavily on warm caches, or if successive requests must land on the same node to successfully complete
a transaction. To solve this problem without needing code changes in all clients, servers can recommend a
NodeSelectionStrategy (see below).

### Server-recommended node selection strategies
Servers can inform clients of their recommended strategies by including the
`Node-Selection-Strategy` response header. Values are separated by commas and are ordered by preference. See [available strategies](dialogue-core/src/main/java/com/palantir/dialogue/core/DialogueNodeSelectionStrategy.java).
```
Node-Selection-Strategy: BALANCED,PIN_UNTIL_ERROR
```
When the header is present, it takes precedence over user-selected strategies. Servers are free to omit this value.

### NodeSelectionStrategy.ROUND_ROBIN
Used to balance requests across many servers better than the
default PIN_UNTIL_ERROR. The actual algorithm has evolved from naive Round Robin, then to Random Selection and now
makes smarter decisions based on stats about each host (see
[BalancedNodeSelectionStrategyChannel.java](dialogue-core/src/main/java/com/palantir/dialogue/core/BalancedNodeSelectionStrategyChannel.java)). This fixes a dramatic failure
mode when a single server is very slow (this can be seen empirically in the simulations). Note that unlike concurrency limiters, this node selection strategy never *prevents* a request getting out the door,
it just *ranks* hosts to try to deliver the best possive client-perceived response time (and success rate).

Specifically, it keeps track of the number of in flight requests for each host, and also records every failure it sees for each host. A
request is then routed to the the host with the lowest `inflight + 10*recent_failures`.

The ROUND_ROBIN strategy is _not_ appropriate for transactional use cases where successive requests must land on the
same node, and it's also not optimal for use-cases where there are many nodes and cache affinity is very important.

### Sticky requests

Dialogue channels can be configured to stick to a single host: after first request is successfully executed on a host,
all subsequent requests will be routed to the same host. This strategy is useful for transactional workflows,
where all requests tied to a particular transaction may need to be executed on the same host.

The implementation reuses the same [limiter pipeline](#limiter-diagram), with some adjustments (simplified to show a single host/endpoint only):

```
Sticky Request +-->+Per Sticky Channel queue+-->+Per Sticky Channel Limiter+-->+Node Selector+--->+Host Limiter (node0)+-->+Endpoint Queue(node0,ping)+-->+Endpoint Limiter(node0,ping)
```

Each sticky channel gets its own queue. If a sticky channel has no requests in-flight, ``Host Limiter`` is sidestepped (the request is let through regardless of current concurrency limits).
This means there is a potential for many low-bandwidth sticky channels to compete with regular channels.

## Alternative HTTP clients

Dialogue is not coupled to a single HTTP client library - this repo contains implementations based on [OkHttp](https://square.github.io/okhttp/), Java's [HttpURLConnection](https://docs.oracle.com/javase/8/docs/api/java/net/HttpURLConnection.html), the new Java11 HttpClient as well as the aforementioned [Apache HttpClient](https://hc.apache.org/httpcomponents-client-ga/).  We endorse the Apache client because as it performed best in our benchmarks and affords granular control over connection pools.

## History

Dialogue is the product of years of learning from operating thousands of Java servers across hundreds of deployments. [Previous incarnations](https://github.com/palantir/conjure-java-runtime) relied on Feign, Retrofit2 and OkHttp.

## Contributing

For instructions on how to set up your local development environment, check out the
[CONTRIBUTING.md](./CONTRIBUTING.md).
