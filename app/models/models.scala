package models

import play.api.libs.json.Json

case class Product(id: String, name: String, description: String)
case class MobileSale(id: String, price: Int, product: Product, vendorId: String)

trait Event
case class Sale(id: String, price: Int, productId: String, level: String, vendorId: String, from: String, timestamp: Long) extends Event

case class Status(message: String, from: String, timestamp: Long) extends Event

object Models {

  implicit val productFmt = Json.format[Product]
  implicit val mobileSaleFmt = Json.format[MobileSale]
  implicit val saleFmt = Json.format[Sale]
  implicit val statusFmt = Json.format[Status]

  val cities = Seq("Nantes", "Paris", "Lyon", "Bordeaux")

}