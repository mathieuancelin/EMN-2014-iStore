package controllers

import play.api.mvc.{Action, Controller}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json._
import models.{Sale, Models, Event}
import play.api.libs.ws.WS.WSRequestHolder
import play.api.libs.iteratee.{Enumeratee, Concurrent, Enumerator}
import play.api.libs.ws.WS
import play.api.libs.EventSource
import play.api.Play
import play.api.Play.current

object MonitoringApplication extends Controller {

  val baseUrl = Play.configuration.getString("urls.stream").getOrElse("http://localhost:9000/operations?from=%s")

  val eventFmt = new Format[Event] {
    def reads(json: JsValue): JsResult[Event] = json \ "price" match {
      case _: JsUndefined => Models.statusFmt.reads(json)
      case _ => Models.saleFmt.reads(json)
    }
    def writes(e: Event): JsValue = e match {
      case o: Sale => Models.saleFmt.writes(o)
      case s: models.Status => Models.statusFmt.writes(s)
    }
  }

  def getStream[A <: Event](request: WSRequestHolder, reader: Reads[A]): Enumerator[Event] = {
    val (iteratee, enumerator) = Concurrent.joined[Array[Byte]]
    request.get(_ => iteratee).map(_.run)
    enumerator &> Enumeratee.map[Array[Byte]](Json.parse) ><>
      Enumeratee.map[JsValue](reader.reads) ><>
      Enumeratee.collect[JsResult[A]] {
        case JsSuccess(value, _) => value
      }
  }

  def index(role: String) = Action {
    Ok(views.html.monitoring.monitoring(role))
  }

  def feed(role: String, lower: Int, higher: Int) = Action {

    val lr = getStream( WS.url(baseUrl.format("Bordeaux")).withRequestTimeout(-1), eventFmt )
    val paris = getStream( WS.url(baseUrl.format("Paris")).withRequestTimeout(-1), eventFmt )
    val nantes = getStream( WS.url(baseUrl.format("Nantes")).withRequestTimeout(-1), eventFmt )
    val lyon = getStream( WS.url(baseUrl.format("Lyon")).withRequestTimeout(-1), eventFmt )

    val secure: Enumeratee[Event, Event] = Enumeratee.collect[Event] {
      case s: models.Status if role == "MANAGER" => s
      case o@Sale(_, _, _, "public", _, _, _) => o
      case o@Sale(_, _, _, "private",_, _, _) if role == "MANAGER" => o
    }

    val inBounds: Enumeratee[Event, Event] = Enumeratee.collect[Event] {
      case s: models.Status => s
      case o@Sale(_, amount, _, _, _, _, _) if amount > lower && amount < higher => o
    }

    val pipeline = (lr >- paris >- nantes >- lyon)

    val transformer =
        secure ><>
        inBounds ><>
        Enumeratee.map[Event](eventFmt.writes)

    Ok.feed( pipeline.through(transformer).through( EventSource() ) ).as("text/event-stream")
  }

}
