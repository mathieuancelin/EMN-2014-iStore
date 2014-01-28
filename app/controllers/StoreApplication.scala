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

  lazy val couchbase = Play.configuration.getBoolean("activate.couchbase").getOrElse(false)
  lazy val (globalEnumerator, globalChannel) = Concurrent.broadcast[JsObject]

  def index = Action {
    Ok(views.html.store.odometer())
  }

  def odometer = Action {
    Ok.feed( Stats.enumerator.through( EventSource() ) ).as("text/event-stream")
  }

  def insertSale(sale: JsValue): Future[String] = {
    import org.reactivecouchbase.CouchbaseRWImplicits.jsValueToDocumentWriter
    PlayCouchbase.cappedBucket("emn", 200, true)
      .insert(UUID.randomUUID().toString, sale).map(_.getMessage)
  }

  def performSale = Action.async(parse.json) { request =>
    Models.saleFmt.reads(request.body) match {
      case JsSuccess(sale, _) => insertSale(request.body).map { status =>
        Stats.pushSale(request.body)
        Ok(Json.obj("success" -> status))
      }
      case JsError(_) => Future(PreconditionFailed(Json.obj("error" -> "not a sale")))
    }
  }

  def sales(from: String): Future[Enumerator[JsObject]] = {
    import org.reactivecouchbase.CouchbaseRWImplicits.documentAsJsObjectReader
    PlayCouchbase.cappedBucket("emn", 200, true).tail[JsObject](System.currentTimeMillis(), 200L, TimeUnit.MILLISECONDS)
      .map(_.through(Enumeratee.filter(e => (e \ "from").as[String] == from)))
  }

  def operations(from: String) = Action.async {
    val enumerator = sales(from)
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
