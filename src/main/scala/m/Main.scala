package m

import akka.stream.scaladsl._
import akka.stream._
import akka.actor._
import akka.stream.stage._
import scala.collection.mutable

trait IngestionMethods[A] {
  def submit(value: A): Unit
  def complete(): Unit
}


class Ingestor[A] extends GraphStageWithMaterializedValue[SourceShape[A], IngestionMethods[A]] {
  /* We only have one thread dequeuing items, so it's safe to say that if the
   * queue is not empty, then there will be an item available to pull.
   */
  private val buffer = new java.util.concurrent.ConcurrentLinkedQueue[A]

  val out = Outlet[A]("Ingestor.out")
  val shape = SourceShape.of(out)

  private var downstreamWaiting = false
  private var downstreamFinished = false
  private var completing = false

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, IngestionMethods[A]) = {

    class IngestorLogic(shape: Shape) extends GraphStageLogic(shape) with IngestionMethods[A] {
      private val scheduled = new java.util.concurrent.atomic.AtomicBoolean(false)
      private val processScheduled = getAsyncCallback[Unit] { (_) =>
        if (!scheduled.compareAndSet(true, false)) {
          throw new Exception("Code should never reach here. Error. Critical pain. No no no. Requesting repairs.")
        }

        if ((downstreamWaiting) && (! buffer.isEmpty)) {
          downstreamWaiting = false
          push(out, buffer.poll())
        }

      }
      def submit(value: A): Unit = {
        if (completing) return ()
        buffer.offer(value)

        /* if scheduled is still true after we've offered our value, then it is
        guaranteed to be processed, because processing always occurs AFTER
        transition from true to false */
        if (scheduled.compareAndSet(false, true)) {
          processScheduled.invoke(())
        }
      }

      def complete(): Unit = {
        completing = true
        getAsyncCallback[Unit] { (_) =>
          emitMultiple(out, buffer.iterator)
          completeStage()
        }.invoke(())
      }

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          if (buffer.isEmpty) {
            downstreamWaiting = true
          } else {
            push(out, buffer.poll())
          }
        }
      })
    }

    val logic = new IngestorLogic(shape)

    (logic, logic: IngestionMethods[A])
  }
}

object Repro extends App {
  implicit val system = ActorSystem("test")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  val (input, completed) = Source.fromGraph(new Ingestor[Int]).
    toMat(Sink.foreach { n =>
      Thread.sleep(1)
      println(s"Processed ${n}")
    })(Keep.both).
    run

  (1 to 10).foreach { n =>
    Thread.sleep(10)
    input.submit(n)
    println(s"Sent ${n}")
  }
  (11 to 50).foreach { n =>
    input.submit(n)
    println(s"Sent ${n}")
  }

  input.complete()
  println(s"Sent complete")

  (51 to 100).foreach { n =>
    input.submit(n)
    println(s"Sent ${n}")
  }

  completed.onComplete { result =>
    println(s"all done! Result = ${result}")
    system.terminate()
  }
}
