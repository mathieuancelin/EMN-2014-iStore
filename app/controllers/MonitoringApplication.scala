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

  def index(role: String) = Action {
    Ok(views.html.monitoring.monitoring(role))
  }

  def feed(role: String, lower: Int, higher: Int) = Action {
    
    ???

    val pipeline = (lr >- paris >- nantes >- lyon)

    Ok.feed( pipeline.through( ??? ).through( EventSource() ) ).as("text/event-stream")
  }

}
