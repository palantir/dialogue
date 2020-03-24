# Dialogue

_Dialogue is a client-side library for HTTP-based RPC, designed to work well with [Conjure](https://palantir.github.io/conjure)-defined APIs._

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


### Building a client

**Production usage**: your server framework should provide an abstraction to create clients to ensure that connection pools are reused wherever possible. For example in Witchcraft, you can create a `FooServiceBlocking` like so:

```groovy
FooServiceBlocking fooService = witchcraft.conjureClients().client(FooServiceBlocking.class, "foo-service").get();

// then you can make network calls by just calling the java method
List<Item> items = fooService.getItems();
```

**Under the hood**

For granular control, you might want to interact with the `DialogueChannel` builder directly.

```java
Channel channel = DialogueChannel.builder()
      .channelName("my-channel")
      .clientConfiguration(conf)
      .channelFactory(uri -> ApacheHttpClientChannels.createSingleUri(uri, apache))
      .build();
```

This sets up all of the smart functionality in Dialogue, and gives you the flexibility to choose what channel to use for a request to a single uri. We recommend the [Apache HttpClient](https://hc.apache.org/httpcomponents-client-ga/) - in the example above, the `ApacheHttpClientChannels` class is provided by `com.palantir.dialogue:dialogue-apache-hc4-client`.

You'd manually construct a client using the static `of` method:

```groovy
FooServiceBlocking fooService = FooServiceBlocking.of(channel, DefaultConjureRuntime.builder().build());
```

_In this example, DefaultConjureRuntime is provided by `com.palantir.dialogue:dialogue-serde`._


In tests, you might use:

```groovy
Channel channel = ApacheHttpClientChannels.create(ClientConfiguration.builder()
        .from(ClientConfigurations.of(serviceConfiguration))
        .userAgent(userAgent)
        .build());
```

## Blocking or async?

Of the two generated interfaces `FooServiceBlocking` and `FooServiceAync`, the blocking version is usually appropriate for 80% of use-cases, and results in much simpler control flow and error-handling. The async version returns Guava [`ListenableFutures`](https://github.com/google/guava/wiki/ListenableFutureExplained) so is a lot more fiddly to use. `Futures.addCallback` and `FluentFuture` are your friend here.


## Design and motivation

Dialogue is built around the `Channel` abstraction, with many different internal implementations that often add a little bit of behaviour and then delegate to another inner Channel.

```java
public interface Channel {
    ListenableFuture<Response> execute(Endpoint endpoint, Request request);
}
```

For example, the [UserAgentChannel](https://github.com/palantir/dialogue/blob/develop/dialogue-core/src/main/java/com/palantir/dialogue/core/UserAgentChannel.java) just augments the request with a `user-agent` header and then calls a delegate.

_This API is influenced by gRPC's [Java library](https://github.com/grpc/grpc-java), which has a similar [Channel](https://github.com/grpc/grpc-java/blob/master/api/src/main/java/io/grpc/Channel.java) concept._

## Contributing

For instructions on how to set up your local development environment, check out the
[CONTRIBUTING.md](./CONTRIBUTING.md).
