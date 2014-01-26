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
import play.api.libs.json.JsObject
import scala.util.{Failure, Random}

object MobileApplication extends Controller {

  def sale(from: String) = Action {
    Ok(views.html.mobile.sale(from))
  }

}
