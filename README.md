This project demonstrates two gotchas with using `Source.actorRef` that I feel
should either be fixed or clearly documented.

These gotchas are:

- The downstream seems to get attached *AFTER* the actorRef stream
  materialization value is returned. (if `bufferSize = 0`, and you send a
  message immediately after receiving receiving the actorRef, then
- The connected downstream runs in a different thread than the receiver actor;
  there is an async boundary between the two. This means that if you send
  messages very quickly, it's possible for the `Source.actorRef` to drop
  messages, simply because the downstream thread hasn't been scheduled to run
  yet, and that is causing backpressure.

Below is the output from running this program:

```
> run
[info] Compiling 1 Scala source to /Users/timcharper/src/goof/as-conflate-proof/target/scala-2.11/classes...
[info] Running m.Repro

In this example, the messages sent immediately after the actorRef is returned
are being dropped. However, after waiting 100ms, then sending a set more, 16
messages of them make it through the pipeline (I presume 16 is the async
boundary buffer size between the actorRef and the sink).

The fact that only 16 make it through is evidence that the Sink is running in a
separate thread than the receiving actor (whose mailbox contains the originally
sent messages).

Sent 1
Sent 2
Sent 3
Sent 4
Sent 5
Sent 6
Sent 7
Sent 8
Sent 9
Sent 10
Sent 11
Sent 12
Sent 13
Sent 14
Sent 15
Sent 16
Sent 17
Sent 18
Sent 19
Sent 20
Sent 21
Sent 22
Sent 23
Sent 24
Sent 25
Sent 26
Sent 27
Sent 28
Sent 29
Sent 30
Sent 31
Sent 32
Sent 33
Sent 34
Sent 35
Sent 36
Sent 37
Sent 38
Sent 39
Sent 40
Sent 41
Sent 42
Sent 43
Sent 44
Sent 45
Processed 11
Sent 46
Sent 47
Sent 48
Sent 49
Sent 50
Processed 12
Processed 13
Processed 14
Processed 15
Processed 16
Processed 17
Processed 18
Processed 19
Processed 20
Processed 21
Processed 22
Processed 23
Processed 24
Processed 25
Processed 26
all done! Result = Success(Done)
```

Program as run on an quad-core hyperthreaded processor, under `JDK 1.8.0_51`, with default JVM settings.t
