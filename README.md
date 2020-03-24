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

**Short-answer**: setting up clients should probably be encapsulated by your server framework to ensure connection pools are reused properly. For example in Witchcraft:

```groovy
FooServiceBlocking fooService = witchcraft.conjureClients().client(FooServiceBlocking.class, "foo-service").get();

// then you can make network calls by just calling a method
List<Item> items = fooService.getItems();
```

**Long answer**

Dialogue is built around the `Channel` abstraction, with many different implementations that often add a little bit of behaviour and then delegate to another inner Channel.

```java
public interface Channel {
    ListenableFuture<Response> execute(Endpoint endpoint, Request request);
}
```

For example, the [UserAgentChannel](https://github.com/palantir/dialogue/blob/develop/dialogue-core/src/main/java/com/palantir/dialogue/core/UserAgentChannel.java) just augments the request with a `user-agent` header and then calls a delegate.


## Blocking or async?

Of the two generated interfaces `FooServiceBlocking` and `FooServiceAync`, the blocking version is usually appropriate for 80% of use-cases, and results in much simpler control flow and error-handling. The async version returns Guava [`ListenableFutures`](https://github.com/google/guava/wiki/ListenableFutureExplained) so is a lot more fiddly to use. `Futures.addCallback` and `FluentFuture` are your friend here.


## Design and motivation

The API is influenced by gRPC's Java library.

## Contributing

For instructions on how to set up your local development environment, check out the
[CONTRIBUTING.md](./CONTRIBUTING.md).
