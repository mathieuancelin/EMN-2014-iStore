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
  ???
}
```

* Lancer une tâche periodiquement toutes les n secondes

```scala
Akka.system.scheduler.schedule(FiniteDuration(0, TimeUnit.MILLISECONDS), FiniteDuration(5000, TimeUnit.MILLISECONDS)) {
  ???
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
val enumerator1 = ???
val enumerator2 = ???
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

Cette application est plutôt simple. Elle doit proposer une interface mobile permettant à un vendeur de choisir la ville du magasin puis de passer des ventes de produits. La liste des ventes et des produits sera à votre charge, elle pourra être backée par une base de données, un fichier ou tout ce que vous jugerez nécessaire. L'interface mobile sera reliée à l'application via une WebSocket (voir les slides et les astuces) qui permettra de transmettre les ventes au format JSON. Ces ventes seront ensuite transmisent au backend business via des appels de web services (à la volée ou en mode batch, à vous de voir).

Pour envoyer une vente au backend business, vous utiliserez l'API de webservices de Play :

```scala
 WS.url(myUrl).post[JsValue](mySaleAsJsValue).onComplete {
  case Failure(_) => Logger.error("error while sending data to store app")
  case Success(_) => Logger.info("sale sent to the store app")
}
```

vous pouvez également les readers Json pour vérifier que les ventes envoyées par l'application mobile sont bonnes

```scala
mobileSaleFmt.reads(obj) match {
  case JsSuccess(sale, _) => ???
  case JsError(e) => Logger.error(s"Error while receiving mobile sale : $e")
}
```

noubliez pas de compléter l'IHM de sélection de ville

Le backend business
--------------------

Cette partie va servir à deux choses :

* capter les ventes en provenance du frontend mobile
* exposer des flux de vente par localisation au serveur de montoring

Pour capter les ventes, il vous sera possible d'utiliser un body parser Json afin d'extraire directement du Json du corps de la requête:

```scala
def performSale = Action.async(parse.json) { request =>
  saleFmt.reads(request.body) match {
    case JsSuccess(sale, _) => ???
    case JsError(_) => ???
  }
}
```

Dans un premier temps, cette action poussera les nouvelles ventes vers un simple enumerator impératif (`lazy val (globalEnumerator, globalChannel) = Concurrent.broadcast[JsObject]`) . Nous verrons ensuite comment utiliser une base de données NoSQL pour persister ces ventes

Pour l'exposition des ventes, nous allons utiliser l'API `Ok.stream( globalEnumerator )` afin d'envoyer les ventes au fil de l'eau.

De plus, ces flux de données comprendrons un peu de bruit, en effet comme tout système informatique classique, celui-ci va générer des erreurs. Combinerez donc l'enumerator de données avec un enumerator générant du bruit de manière aléatoire (voir l'enumerator générateur d'évènements).


Le frontend de monitoring
---------------------------

Cette application est certainement la plus complexe tant pour la partie serveur que pour la partie UI.
Il sera nécessaire ici de consommer plusieurs services depuis le backend business, de les traiter puis de les aggréger dans une UI de monitoring affichant toutes les opérations en temps réel.

La première chose a faire est de consommer les services venant du backend business. Vous utiliserez le client webservice pour consommer les streams d'objets JSON représentant les ventes en train d'être effectuées (n'hésitez pas à utiliser la section astuces) ou les messages de status système.

Il vous faudra également traiter ces ventes afin que seule les données correspondantes aux filtre de l'utilisateurs soient remontées

Vous allez donc consommer de multiples services venant du backend business (un par ville). Vous ce faire, vous utiliserez l'astuce pour transformer un appel de webservice en enumerator. De là vous allez pouvoir combiner divers enumeratee pour transformer les tableaux de bytes en instances de case class modèle.


Il vous faudra également traiter ces ventes pour prendre en compte le role de connexion (MANAGER ou EMPLOYEE) ainsi que les limites de prix de ventes. Il vous sera très simple de rajouter des enumeratee 'métier' permettant de filter les objets au fur et à mesure. Pour celà, l'enumeratee `collect semble tout indiqué`

```scala
val secure: Enumeratee[Event, Event] = Enumeratee.collect[Event] {
  case s: models.Status if role == "MANAGER" => s
  case o@Sale(_, _, _, "public", _, _, _) => o
  case o@Sale(_, _, _, "private",_, _, _) if role == "MANAGER" => o
}

val inBounds: Enumeratee[Event, Event] = Enumeratee.collect[Event] {
  case s: models.Status => s
  case o@Sale(_, amount, _, _, _, _, _) if amount > lower && amount < higher => o
}
```

une fois votre flux prêt à l'envoi, il ne reste plus qu'à en faire un service de type SSE (voir les astuces)

Utiliser l'application
-----------------------

http://localhost:9000/    =>   application mobile
http://localhost:9000/monitoring?role=MANAGER  =>  monitoring des ventes en temps réel

Bonus: ReactiveCouchbase
------------------------

Vous pouvez utiliser la base Couchbase pour stocker vos ventes (www.couchbase.com). N'hésitez pas à me demander pour l'implémentation.






