This example illustrates event sourcing with [Akka Persistence](https://doc.akka.io/docs/akka/2.6/typed/persistence.html).

it is based (okay, stolen from) the generated sample project [akka-samples-peristence-java]() but with some Kotlin
grammar and idiom applied. This project is open for improvement, so if you find some code that could be more Kotlin-like or better
in any way, then don't hesitate to prepare a PR.

Study the source code of [Main.kt](src/main/kotlin/Main.kt). A few things to note:

* The actor is implemented with the `EventSourcedBehavior`
* It defines `Command`, `Event` and `State`
* Commands define `replyTo: ActorRef` to send a confirmation when the event has been successfully persisted
* `State` is only updated in the event handler
* `withRetention` to enable [snapshotting](https://doc.akka.io/docs/akka/2.6/typed/persistence-snapshot.html)
* `onPersistFailure` defines restarts with backoff in case of failures

Tests are defined in [MainTests.kt](src/test/kotlin/MainTests.kt).
To run the tests, enter:

```
mvn test
```

The `ShoppingCart` application is expanded further in the [akka-sample-cqrs-java](https://developer.lightbend.com/docs/akka-platform-guide/microservices-tutorial/) sample. 
In that sample the events are tagged to be consumed by even processors to build other representations from the events,
or publish the events to other services.
