package services

import java.util.UUID
import javax.inject.{Inject, Singleton}

import akka.stream.scaladsl.Source
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import play.api.mvc.MultipartFormData.DataPart

import scala.concurrent.Future

/**
  * Provides access to file storage.
  */
@Singleton
class FileStorage @Inject() (ws: WSClient) {
  val serviceUrl = "http://localhost:8080/abcstore-0.1-SNAPSHOT"

  def storeFile(user: String, content: String): Future[UUID] = {

    val addFileRequest = Json.obj("user" -> user)

    val multipartSource = Source(DataPart("content", content) :: DataPart("request", addFileRequest.toString()) :: List())

    val request: WSRequest = ws.url(serviceUrl + "/files")
    val responseFuture: Future[WSResponse] = request.post(multipartSource)

    implicit val context = play.api.libs.concurrent.Execution.Implicits.defaultContext

    responseFuture.map { response =>
      (response.json \ "fileId").as[UUID]
    }
  }

}
