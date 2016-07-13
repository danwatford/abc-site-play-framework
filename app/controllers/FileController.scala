package controllers

import java.io.FileNotFoundException
import java.util.UUID
import javax.inject._

import play.api.libs.Files.TemporaryFile
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import services.{AbcFileRecord, AbcFileService, AbcTuneService}

import scala.concurrent.Future
import scala.io.Source
import scala.util.{Failure, Success, Try}

/**
  * Upload and Get controller for ABC Files.
  */
@Singleton
class FileController @Inject()(fileService: AbcFileService, tuneService: AbcTuneService) extends Controller {

  /**
    * Get a response returning all files.
    *
    * @return The files response.
    */
  def getFiles = Action.async {
    val fileRecordsFuture = fileService.getAllFileRecords

    fileRecordsFuture.map(fileRecords =>
      Ok(views.html.files(s"ABC Site: Files", fileRecords))
    )
  }

  /**
    * Get a response returning the file specified by the given file id.
    *
    * @param fileId The file id to lookup.
    * @return The file response.
    */
  def getFile(fileId: UUID) = Action.async {
    val fileRecordFuture = fileService.getFileRecord(fileId)

    fileRecordFuture.map {
      case Some(fileRecord) =>
        val tuneRecords = tuneService.getTuneRecordsByFileId(fileRecord.id)
        Ok(views.html.file(s"ABC Site: File ${fileRecord.id}", fileRecord, tuneRecords))

      case None =>
        NotFound
    }
  }

  /**
    * Receives an uploaded file.
    */
  def upload = Action.async(parse.multipartFormData) { request =>

    // Read the content of the uploaded file into a string.
    val contentFuture: Future[String] = Future(readRequestFile(request.body.file("abc_file"))).map {
      case Failure(ex) => throw ex
      case Success(content) => content
    }

    // Pass the content to the ABC File Service.
    val fileStoredFuture: Future[Try[AbcFileRecord]] =
      contentFuture.flatMap(content => fileService.storeFile("user", content))

    fileStoredFuture.map {
      case Success(fileRecord) =>
        Redirect(routes.FileController.getFile(fileRecord.id))

      case Failure(e) =>
        UnprocessableEntity(e.getMessage)
    }
  }

  /**
    * Read the content of the given FilePart, wrapping the result in a Try.
    *
    * @param fileOption The optional FilePart to be read.
    * @return A Try of String containing the content, or a FileNotFoundException failure if there was a  problem
    *         reading the file.
    */
  private def readRequestFile(fileOption: Option[FilePart[TemporaryFile]]): Try[String] = {
    fileOption.map {
      abcFile =>
        Try(Source.fromFile(abcFile.ref.file).mkString)
    }.getOrElse(new Failure(new FileNotFoundException("No file included in request")))
  }

}
