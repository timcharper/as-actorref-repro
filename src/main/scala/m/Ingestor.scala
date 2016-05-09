package m

import akka.stream._
import akka.stream.stage._

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

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, IngestionMethods[A]) = {
    class IngestorLogic(shape: Shape) extends GraphStageLogic(shape) with IngestionMethods[A] {
      private var downstreamWaiting = false
      private var completing = false

      private var initialized = false
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
        if (!initialized) return

        /* if scheduled is still true after we've offered our value, then it is
        guaranteed to be processed, because processing always occurs AFTER
        transition from true to false */
        if (scheduled.compareAndSet(false, true))
          processScheduled.invoke(())
      }

      def complete(): Unit = {
        completing = true
        getAsyncCallback[Unit] { (_) =>
          emitMultiple(out, buffer.iterator)
          completeStage()
        }.invoke(())
      }

      override def preStart(): Unit = {
        initialized = true
        if (scheduled.compareAndSet(false, true)) {
          processScheduled.invoke(())
        }
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
