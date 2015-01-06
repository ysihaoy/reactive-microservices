import akka.actor.ActorSystem
import akka.http.Http
import akka.http.marshallers.sprayjson.SprayJsonSupport._
import akka.http.marshalling.ToResponseMarshallable
import akka.http.model.StatusCodes._
import akka.http.server.Directives._
import akka.stream.FlowMaterializer

case class Identity(id: Long)

case class Token(value: String, validTo: Long, identityId: Long, authMethods: Set[String])

case class CodeCard(id: Long, codes: Seq[String], userIdentifier: String)

case class RegisterResponse(identity: Identity, codesCard: CodeCard)

case class LoginRequest(userIdentifier: String, cardIndex: Long, codeIndex: Long, code: String)

case class ActivateCodeRequest(userIdentifier: String)

case class ActivateCodeResponse(cardIndex: Long, codeIndex: Long)

case class GetCodeCardRequest(userIdentifier: String)

case class GetCodeCardResponse(userIdentifier: String, codesCard: CodeCard)

object AuthCode extends App with AuthCodeJsonProtocol with AuthCodeConfig {
  implicit val actorSystem = ActorSystem()
  implicit val materializer = FlowMaterializer()
  implicit val dispatcher = actorSystem.dispatcher

  val repository = new Repository
  val gateway = new Gateway
  val service = new AuthCodeService(gateway,repository)

  Http().bind(interface = interface, port = port).startHandlingWith {
    logRequestResult("auth-code") {
      (path("register") & pathEndOrSingleSlash & post & optionalHeaderValueByName("Auth-Token")) { (tokenValue) =>
        complete {
          service.register(tokenValue).map {
            case Right(response) => ToResponseMarshallable(Created -> response)
            case Left(errorMessage) => ToResponseMarshallable(BadRequest -> errorMessage)
          }
        }
      } ~
      (path("login" / "activate") & pathEndOrSingleSlash & post & entity(as[ActivateCodeRequest])) { (request) =>
        complete {
          service.activateCode(request).map {
            case Right(response) => ToResponseMarshallable(OK -> response)
            case Left(errorMessage) => ToResponseMarshallable(BadRequest -> errorMessage)
          }
        }
      } ~
      (path("login") & pathEndOrSingleSlash & post & optionalHeaderValueByName("Auth-Token") & entity(as[LoginRequest])) { (tokenValue, request) =>
        complete {
          service.login(request, tokenValue).map {
            case Right(response) => ToResponseMarshallable(Created -> response)
            case Left(errorMessage) => ToResponseMarshallable(BadRequest -> errorMessage)
          }
        }
      } ~
      (path("codes") & pathEndOrSingleSlash & post & optionalHeaderValueByName("Auth-Token") & entity(as[GetCodeCardRequest])) { (tokenValue, request) =>
        complete {
          service.getCodeCard(request, tokenValue).map {
            case Right(response) => ToResponseMarshallable(OK -> response)
            case Left(errorMessage) => ToResponseMarshallable(BadRequest -> errorMessage)
          }
        }
      }
    }
  }
}


