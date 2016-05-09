package m

import java.time.temporal.ChronoUnit

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor._
import akka.stream._
import akka.stream.scaladsl._

object Repro extends App {

  def benchmark(body: => Unit): Long = {
    val a = java.time.ZonedDateTime.now()
    body
    val b = java.time.ZonedDateTime.now()
    a.until(b, ChronoUnit.MILLIS)
  }

  implicit val system = ActorSystem("test")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  val samples = (1 to 15).map { _ =>

    val iters = 1000000

    val actorBenchmark = benchmark {
      val (input, completed) = Source.actorRef[Int](Int.MaxValue, OverflowStrategy.fail).
        toMat(Sink.ignore)(Keep.both).
        run
      var i = 0
      while (i < iters) {
        input ! iters
        i+=1
      }
      input ! Status.Success(Unit)

      Await.result(completed, 2.minutes)
    }

    val queueBenchmark = benchmark {
      val (input, completed) = Source.queue(Int.MaxValue, OverflowStrategy.fail).
        toMat(Sink.ignore)(Keep.both).
        run
      var i = 0
      while (i < iters) {
        input.offer(i)
        i+=1
      }
      input.complete()

      Await.result(completed, 2.minutes)
    }

    val ingestorBenchmark = benchmark {
      val (input, completed) = Source.fromGraph(new Ingestor[Int]).
        toMat(Sink.ignore)(Keep.both).
        run
      var i = 0
      while (i < iters) {
        input.submit(i)
        i+=1
      }
      input.complete()

      Await.result(completed, 2.minutes)
    }

    val results = Map(
      'actor -> actorBenchmark,
      'queue -> queueBenchmark,
      'ingestor -> ingestorBenchmark)

    results.foreach { case (k, v) =>
      println(s"${k}: ${v}ms")
    }
    println(s"========================================")

    results
  }.toList.tail

  samples.reduce { (a, b) =>
    a.transform { (k, v) => v + b.getOrElse(k, 0L) }
  }.foreach { case (k, sum) =>
      println(s"${k} - Total: ${sum} Avg: ${sum / samples.length}")
  }
  system.terminate()
}
