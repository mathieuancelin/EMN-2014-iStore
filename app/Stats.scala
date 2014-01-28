package services

import java.util.concurrent.{TimeUnit, ConcurrentLinkedQueue}
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Concurrent
import play.api.libs.json.{Json, JsValue}
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.atomic.AtomicInteger
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._

object Stats {

  lazy val (enumerator, channel) = Concurrent.broadcast[JsValue]
  lazy val queue = new ConcurrentLinkedQueue[JsValue]()
  lazy val tick = new AtomicInteger(0)
  lazy val totalSales = new AtomicInteger(0)

  def pushSale(sale: JsValue) = {
    queue.offer(sale)
  }

  Akka.system.scheduler.schedule(FiniteDuration(0, TimeUnit.MILLISECONDS), FiniteDuration(5000, TimeUnit.MILLISECONDS)) {
    val size = queue.size()
    queue.clear()
    tick.incrementAndGet()
    totalSales.addAndGet(size)
    val totalSeconds = tick.get() * 5
    val perMinute = (BigDecimal(totalSeconds) / BigDecimal(totalSales.get())) * BigDecimal(60)
    val perHour = perMinute * BigDecimal(60)
    val perDay = perHour * BigDecimal(24)
    channel.push(
      Json.obj(
        "nbrSalesPerMinute" -> perMinute,
        "nbrSalesPerHour" -> perHour,
        "nbrSalesPerDay" -> perDay
      )
    )
  }
}
