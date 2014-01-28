package controllers

import play.api.mvc.{WebSocket, Action, Controller}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json._
import play.api.libs.iteratee.{Concurrent, Iteratee}
import play.api.Play.current
import play.api.Play
import play.api.libs.ws.WS
import java.util.concurrent.atomic.AtomicInteger
import play.api.mvc.WebSocket.FrameFormatter._
import play.Logger
import models.{Sale, Models}
import play.api.libs.json.JsSuccess
import scala.util.{Failure, Random}

object MobileApplication extends Controller {

  val performSaleURL = Play.configuration.getString("urls.performsale").getOrElse("http://localhost:9000/performSale")

  def index = Action {
    Ok(views.html.mobile.list(Models.cities))
  }

  def sale(from: String) = Action {
    Ok(views.html.mobile.sale(from))
  }

  def performSale(from: String) = WebSocket.using[JsValue] { rh =>
    val counter = new AtomicInteger(0)
    val (out, channel) = Concurrent.broadcast[JsValue]
    val in = Iteratee.foreach[JsValue] {
      case obj: JsValue => {
        Models.mobileSaleFmt.reads(obj) match {
          case JsSuccess(sale, _) => {
            val sendSale = Sale(
              sale.id,
              sale.price,
              sale.product.id,
              if(Random.nextBoolean()) "public" else "private",
              sale.vendorId,
              from,
              System.currentTimeMillis()
            )
            WS.url(performSaleURL).post[JsValue](Models.saleFmt.writes(sendSale)).onComplete {
              case Failure(err) => Logger.error("error while sending data to store app")
              case _ => channel.push(Json.obj("nbrSales" -> counter.incrementAndGet()))
            }
          }
          case JsError(e) => Logger.error(s"Error while receiving sale : $e")
        }
      }
      case _ => Logger.info("Dafuq I've just seen ???")
    }
    (in, out)
  }
}
