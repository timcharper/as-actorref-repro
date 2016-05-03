package m

import akka.stream.scaladsl._
import akka.stream._
import akka.actor._

object Repro extends App {
  implicit val system = ActorSystem("test")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  println("""
In this example, the messages sent immediately after the actorRef is returned
are being dropped. However, after waiting 100ms, then sending a set more, 16
messages of them make it through the pipeline (I presume 16 is the async
boundary buffer size between the actorRef and the sink).

The fact that only 16 make it through is evidence that the Sink is running in a
separate thread than the receiving actor (whose mailbox contains the originally
sent messages).
""")
  val (input, completed) = Source.actorRef[Int](0, OverflowStrategy.dropNew).
    toMat(Sink.foreach { n =>
      println(s"Processed ${n}")
    })(Keep.both).
    run

  (1 to 10).foreach { n =>
    input ! n
    println(s"Sent ${n}")
  }

  Thread.sleep(100)

  (11 to 50).foreach { n =>
    input ! n
    println(s"Sent ${n}")
  }

  input ! Status.Success("ok")

  completed.onComplete { result =>
    println(s"all done! Result = ${result}")
    system.terminate()
  }
}
