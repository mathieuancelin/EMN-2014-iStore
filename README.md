iStore
=============

nous allons écrire une application web réactive reproduisant un système de vente depuis des terminaux mobiles et le monitoring de ces ventes dans le plus pur style WOA. Au mieux vous ferez tourner cette application sur trois serveurs différents.

L'architecture globale du système est la suivante :

```
 ________               ______________      
| Mobile | <=========> | Front mobile | 
 --------               --------------  
                               |
                               v 
 _______________        _______________      
|   Database    | <**> | Back business |  
 ---------------        ---------------      
                               ^
                               |
 ____________           __________________      
| Navigateur | ~~~~~~> | Front monitoring | 
 ------------           ------------------      
                                                                          

 * <====> : websocket
 * <----> : appel HTTP
 * <~~~~> : SSE    

```

Quelques astuces utiles
-----------------------

* Lancer une tâches dans n secondes

```scala
Akka.system.scheduler.scheduleOnce(FiniteDuration(0, TimeUnit.MILLISECONDS), FiniteDuration(5000, TimeUnit.MILLISECONDS)) {
  // TODO
}
```

* Lancer une tâche periodiquement toutes les n secondes

```scala
Akka.system.scheduler.schedule(FiniteDuration(0, TimeUnit.MILLISECONDS), FiniteDuration(5000, TimeUnit.MILLISECONDS)) {
  // TODO
}
```

* Créer un enumerator impératif

```scala
val (enumerator, channel) = Concurrent.broadcast[JsValue]
channel.push(myJsValue)
```

* Lire une valeur dans le fichier de configuration

```scala
import play.api.Play.current
Play.configuration.getString("urls.performsale").getOrElse("http://localhost:9000/performSale")
```

* Créer un sérialiseur/désérialiseur JSON pour une case class

```scala
val saleFmt = Json.format[Sale]
```

* Créer une action WebSocket

```scala
def websocket = WebSocket.using[JsValue] { rh =>
  val (out, channel) = Concurrent.broadcast[JsValue]
  val in = Iteratee.foreach[JsValue] { obj =>
    ...
    channel.push( obj )
    ...
  }
  (in, out)
}
```

* Créer une action SSE

```scala
def sse = Action {
  Ok.feed( enumerator.through( EventSource() ) ).as("text/event-stream")
}
```

* Entremeler des enumerators

```scala
val enumerator1 = ...
val enumerator2 = ...
val enumerator3 = enumerator1 >- enumerator2
```

* Enumerator générateur d'évènements

```scala
val noise = Enumerator.generateM[JsObject](
  Promise.timeout(Some(
    Json.obj(
      "Hello" -> "World!"
    )
  ), 2000, TimeUnit.MILLISECONDS)
)
```

* Transformer un appel de webservices en Enumerator

```scala
def getStream(request: WSRequestHolder): Enumerator[Array[Byte]] = {
  val (iteratee, enumerator) = Concurrent.joined[Array[Byte]]
  request.get(_ => iteratee).map(_.run)
  enumerator
}
```

* Transformations JSON

```scala
val hello: JsValue = Json.parse("""{"hello":"world!"}""")
val str: String = Json.stringify(hello)
```

Le frontend mobile
-------------------

Cette application est plutôt simple. Elle doit proposer une interface mobile permettant à un vendeur de choisir la ville du magasin puis de passer des ventes de produits. La liste des ventes et des produits sera à votre charge, elle pourra être backée par une base de données, un fichier ou tout ce que vous jugerez nécessaire. L'interface mobile sera reliée à l'application via une WebSocket qui permettra de transmettre les ventes au format JSON. Ces ventes seront ensuite transmisent au backend business via des appels de web services (à la volée ou en mode batch, à vous de voir).

Pour envoyer une vente au backend business, vous utiliserez l'API de webservices de Play :

```scala
 WS.url(myUrl).post[JsValue](mySaleAsJsValue).onComplete {
  case Failure(_) => Logger.error("error while sending data to store app")
  case Success(_) => Logger.info("sale sent to the store app")
}
```

Commencez par faire des IHM simplistes afin de vous concentrer sur la partie serveur.




Le backend business
--------------------


Le frontend de monitoring
---------------------------

Cette application est certainement la plus complexe tant pour la partie serveur que pour la partie UI.
Il sera nécessaire ici de consommer plusieurs services depuis le backend business, de les traiter puis de les aggréger dans une UI de monitoring affichant toutes les opérations en temps réel.

La première chose a faire est de consommer les services venant du backend business. Vous utiliserez le client webservice pour consommer les streams d'objets JSON représentant les ventes en train d'être effectuées (n'hésitez pas à utiliser la section astuces) ou les messages de status système.

Il vous faudra également traiter ces ventes 





