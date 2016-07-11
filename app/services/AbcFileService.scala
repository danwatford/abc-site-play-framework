package services

import java.util.UUID
import javax.inject.{Inject, Singleton}

import com.foomoo.stringstore.service._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
  * Service for reading and writing ABC file content.
  */
@Singleton
class AbcFileService @Inject()(abcFileProcessor: AbcFileProcessor) {

  val idFileRecord = collection.mutable.HashMap[UUID, AbcFileRecord]()
  val filesStack = collection.mutable.Stack[AbcFileRecord]()
  val stringsService: StringsService = new MongoStringsService

  // Retrieve and convert all previously stored tune files.
  // Return a future which can be used to delay other operations until all existing tune files processed.
  val filesLoadedFuture: Future[Unit] = getAllFileContent.map(_ foreach {
    case (uuid, content) =>
      addFileIfValid(content, uuid)
  }).fallbackTo(Future {})

  /**
    * Retrieve the records of the most recent 4 files added to the non-persistent store.
    *
    * @return List of AbcFileRecords for most recently added files.
    */
  def getRecentAbcFileRecords: Future[List[AbcFileRecord]] = filesLoadedFuture.map(_ => {
    filesStack.take(4).toList
  })

  /**
    * If the given string is found to be valid by the configured AbcFileProcessor it will be added to the
    * persistent storage. If the string was not already in the persistent storage it will be added to the
    * non-persistent store and submitted for processing by the configured AbcFileProcessor.
    *
    * @param user    The user who added the file.
    * @param content The file content.
    * @return
    */
  def storeFile(user: String, content: String): Future[Try[AbcFileRecord]] = {

    abcFileProcessor.isValid(content) match {
      case Success(_) =>
        filesLoadedFuture.map(_ => {
          val result = stringsService.addString(user, content)

          // Did the file already exist in the store?
          val fileId: UUID = result.getStringId
          if (result.isExistingString) {

            // Existing file so retrieve its record from the non-persistent store.
            // If the record is not present it may not have been loaded yet or it may have been found as
            // invalid by the AbcFileProcessor.
            idFileRecord.get(fileId)
              .map(Success(_)).getOrElse(Failure(new IllegalArgumentException("Cannot store file.")))
          } else {
            // New file so add to non-persistent store.
            addFile(content, fileId)
          }
        })

      case Failure(e) => Future {
        Failure(e)
      }
    }
  }

  /**
    * Add the file to non-persistent storage (i.e. data structures in this class) if it is found to be valid by
    * the configured AbcFileProcessor. If added the file with be submitted for processing by the configured
    * AbcFileProcessor.
    *
    * @param content The content to add to non-persistent storage.
    * @param id      The id of the file in storage.
    * @return A Try of AbcFileRecord where success means the file content was added to storage and failure
    *         means the file content could not be processed.
    */
  private def addFileIfValid(content: String, id: UUID): Try[AbcFileRecord] = {

    val validityTry: Try[Unit] = abcFileProcessor.isValid(content)

    validityTry.flatMap(_ => addFile(content, id))
  }

  /**
    * Add the file to non-persistent storage (i.e. data structures in this class) submit it for processing by
    * the configured AbcFileProcessor.
    *
    * @param content The content to add to non-persistent storage.
    * @param id      The id of the file in storage.
    * @return A Try of AbcFileRecord where success means the file content was added to storage and failure
    *         means the file content could not be processed.
    */
  private def addFile(content: String, id: UUID): Try[AbcFileRecord] = {

    val record = AbcFileRecord(id, content)
    idFileRecord(id) = record
    filesStack.push(record)

    abcFileProcessor.process(content, id)

    Success(record)
  }

  /**
    * Retrieve all StringSummary objects for the ABC File contents in the string-store.
    *
    * @return The StringSummary objects.
    */
  private def getAllFileSummaries: Future[List[StringSummary]] = {

    Future {
      val fileSummaries = stringsService.getAllStringSummaries
      fileSummaries.asScala.toList
    }
  }

  /**
    * Retrieve all String content of all ABC Files in the string-store.
    *
    * @return Map of string id (which is used as the file id) to string content.
    */
  private def getAllFileContent: Future[Map[UUID, String]] = {

    val idsFuture = getAllFileSummaries.map(_.map(_.getId))

    idsFuture.map(ids =>
      stringsService.getStringsContent(ids: _*).asScala.toMap
    )
  }

}

/** Represents the id and the content of an AbcFile stored by this service. */
case class AbcFileRecord(id: UUID, content: String)
