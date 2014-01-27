package controllers

import play.api.mvc.{Action, Controller}
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._
import models.Models
import play.api.libs.json._
import java.util.concurrent.TimeUnit
import play.api.libs.iteratee.{Concurrent, Enumerator, Enumeratee}
import play.api.libs.concurrent.Promise
import scala.util.Random
import play.api.Play.current
import play.api.libs.EventSource
import org.reactivecouchbase.play.PlayCouchbase
import java.util.UUID
import play.api.Play
import services.Stats

object StoreApplication extends Controller {

  def operations(from: String) = Action.async {
    val enumerator = ???
    // génération de bruit !!!
    val noise = Enumerator.generateM[JsObject](
      Promise.timeout(Some(
        Json.obj(
          "timestamp" -> System.currentTimeMillis(),
          "from" -> from,
          "message" -> "System ERROR"
        )
      ), 2000 + Random.nextInt(2000), TimeUnit.MILLISECONDS)
    )
    enumerator.map(e => Ok.chunked( e >- noise ))
  }
}
