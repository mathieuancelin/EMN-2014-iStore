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

  def index(role: String) = Action {
    Ok(views.html.monitoring.monitoring(role))
  }

  def feed(role: String, lower: Int, higher: Int) = Action {
    
    ???
    
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

    Ok.feed( pipeline.through( ??? ).through( EventSource() ) ).as("text/event-stream")
  }

}
