This is a quick benchmark of 3 different methods to ingest non-stream data into
akka streams. It shows that a custom GraphStage modeling an unbounded input
buffer for Akka Streams performs significantly faster than `Source.actorRef`, and
that `Source.queue` is the slowest of the three.

Below is the output from running this program (first sample was dropped from totals):

```
'actor: 1908ms
'queue: 856ms
'ingestor: 125ms
========================================
'actor: 435ms
'queue: 732ms
'ingestor: 123ms
========================================
'actor: 360ms
'queue: 678ms
'ingestor: 118ms
========================================
'actor: 641ms
'queue: 786ms
'ingestor: 111ms
========================================
'actor: 395ms
'queue: 855ms
'ingestor: 132ms
========================================
'actor: 716ms
'queue: 921ms
'ingestor: 122ms
========================================
'actor: 456ms
'queue: 843ms
'ingestor: 120ms
========================================
'actor: 364ms
'queue: 812ms
'ingestor: 121ms
========================================
'actor: 654ms
'queue: 817ms
'ingestor: 116ms
========================================
'actor: 510ms
'queue: 789ms
'ingestor: 112ms
========================================
'actor: 790ms
'queue: 649ms
'ingestor: 190ms
========================================
'actor: 575ms
'queue: 789ms
'ingestor: 121ms
========================================
'actor: 418ms
'queue: 829ms
'ingestor: 94ms
========================================
'actor: 588ms
'queue: 851ms
'ingestor: 97ms
========================================
'actor: 447ms
'queue: 815ms
'ingestor: 119ms
========================================
'actor - Total: 7349 Avg: 524
'queue - Total: 11166 Avg: 797
'ingestor - Total: 1696 Avg: 121
```

Program as run on an quad-core hyperthreaded processor, under `JDK 1.8.0_51`, with default JVM settings.t
