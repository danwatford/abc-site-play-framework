package controllers

import java.io.FileNotFoundException
import java.util.UUID
import javax.inject._

import play.api.libs.Files.TemporaryFile
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import services.FileStorage

import scala.concurrent.Future
import scala.io.Source
import scala.util.{Failure, Success, Try}

/**
  * Upload controller for the ABC Parser Website.
  *
  * This controller provides pages to allow users to upload ABC files to storage.
  */
@Singleton
class FileController @Inject()(fileStorage: FileStorage) extends Controller {

  /**
    * Action to render a form to upload an ABC file.
    */
  def uploadForm = Action.async {
    Future(Ok(views.html.abcupload()))
  }

  /**
    * Async handler to receive an uploaded file.
    */
  def upload = Action.async(parse.multipartFormData) { request =>

    // Read the content of the uploaded file into a string.
    val contentFuture: Future[String] = Future(readRequestFile(request.body.file("abc_file"))).map {
      case Failure(ex) => throw ex
      case Success(content) => content
    }

    // Pass the content to the storage service.
    val fileStoredFuture: Future[UUID] = contentFuture.flatMap(content => fileStorage.storeFile("dan", content))

    // Create a response using the stored file UUID.
    val responseFuture: Future[Result] = fileStoredFuture.map(fileId => Ok(s"File uploaded: $fileId"))

    // If there was an error in any stage create an error response.
    responseFuture.recover {
      case ex => Redirect(routes.FileController.upload()).flashing("error" -> ex.getMessage)
    }
  }

  def fileStatus

  /**
    * Read the content of the given FilePart, wrapping the result in a Try.
    *
    * @param fileOption The optional FilePart to be read.
    * @return A Try of String containing the content, or a FileNotFoundException failure if there was a  problem
    *         reading the file.
    */
  private def readRequestFile(fileOption: Option[FilePart[TemporaryFile]]): Try[String] = {
    fileOption.map { abcFile =>
      Try(Source.fromFile(abcFile.ref.file).mkString)
    }.getOrElse(new Failure(new FileNotFoundException("No file included in request")))
  }

}
