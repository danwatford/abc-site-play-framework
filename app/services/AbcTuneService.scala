package services

import java.util.UUID
import javax.inject.{Inject, Singleton}

import com.foomoo.abc.notation.AbcNotationFile
import com.foomoo.abc.notation.parsing.AbcNotationParser
import com.foomoo.abc.tune.conversion.AbcNotationConverter
import com.foomoo.abc.tune.{AbcTune, AbcTuneBuilder}

import scala.util.parsing.input.CharSequenceReader
import scala.util.{Failure, Success, Try}

/**
  * Storage for tunes.
  *
  * De-duplicates tunes by content. Keeps references to the files that a tune has been found in.
  */
@Singleton
class AbcTuneService @Inject()(tuneProcessor: AbcTuneProcessor) extends AbcFileProcessor {

  val hashTuneRecord = collection.mutable.HashMap[Int, AbcTuneRecord]()
  val idTuneRecord = collection.mutable.HashMap[UUID, AbcTuneRecord]()
  val tunesStack = collection.mutable.Stack[AbcTune]()

  val titleTuneRecord = collection.mutable.HashMap[String, AbcTuneRecord]()

  private def parseFileContent(fileContent: String): Try[AbcNotationFile] =
    AbcNotationParser.file(new CharSequenceReader(fileContent)) match {
      case AbcNotationParser.NoSuccess(msg, next) =>
        Failure(new IllegalArgumentException(msg + "\nNext is: " + next.pos))
      case AbcNotationParser.Success(ts, _) =>
        Success(ts)
    }

  def addFromFileContent(fileId: UUID, fileContent: String): Try[List[AbcTune]] =

    parseFileContent(fileContent).map(abcNotationFile => {

      val abcTunes = abcNotationFile.tunes.map(abcNotationTune => AbcNotationConverter.convertTune(abcNotationTune, abcNotationFile.fileHeader))

      val addTuneResults: List[(AbcTuneRecord, Boolean)] = abcTunes.map(abcTune => addTune(abcTune, fileId))

      val tuneRecords = addTuneResults.map(_._1)
      val newTuneRecords = addTuneResults.filter(_._2).map(_._1)

      tuneProcessor.process(newTuneRecords.map(record => (record.id, record.tune)))

      abcTunes
    })


  /**
    * Add the given AbcTune to the store, returning its associated record object.
    *
    * Deduplicates the tune against others already in the store, merging tunes if appropriate.
    *
    * @param abcTune The tune to add to the store.
    * @return Tuple of AbcRecord and Boolean. If the boolean is true then the new record was added to the store.
    *         If the boolean was false then the record was an existing record already in the store that matched the tune.
    *         Even if the record was existing, the tune in the record may have been modified in a merge operation - e.g.
    *         merging tune titles.
    */
  private def addTune(abcTune: AbcTune, fileId: UUID): (AbcTuneRecord, Boolean) = {

    existingTune(abcTune) match {
      case Some(AbcTuneRecord(tuneId, _, _)) => (mergeTuneToRecord(tuneId, abcTune, fileId), false)

      case None =>
        val tuneRecord: AbcTuneRecord = addNewTune(abcTune, fileId)
        (tuneRecord, true)
    }
  }

  private def existingTune(abcTune: AbcTune): Option[AbcTuneRecord] = {

    existingTuneByHash(abcTune).orElse(existingTuneByTitle(abcTune))
  }

  private def existingTuneByHash(abcTune: AbcTune): Option[AbcTuneRecord] = {
    val tuneHash = abcTune.bodyElements.hashCode()

    hashTuneRecord.get(tuneHash)
  }

  private def existingTuneByTitle(abcTune: AbcTune): Option[AbcTuneRecord] = {

    abcTune.titles.find(titleTuneRecord.contains).flatMap(titleTuneRecord.get)
  }

  private def addNewTune(abcTune: AbcTune, fileId: UUID): AbcTuneRecord = {
    val tuneId = UUID.randomUUID()
    val abcTuneRecord = AbcTuneRecord(tuneId, abcTune, Set(fileId))
    val tuneHash = abcTune.bodyElements.hashCode()

    hashTuneRecord(tuneHash) = abcTuneRecord
    idTuneRecord(tuneId) = abcTuneRecord
    tunesStack.push(abcTune)

    abcTuneRecord
  }

  private def mergeTuneToRecord(tuneId: UUID, abcTune: AbcTune, fileId: UUID): AbcTuneRecord = {

    idTuneRecord(tuneId) match {
      case AbcTuneRecord(_, tune, fileIds) =>

        val newTuneBuilder = new AbcTuneBuilder(tune)

        abcTune.titles.filterNot(newTuneBuilder.titles.contains(_)).foreach(newTuneBuilder.addTitle)

        val abcTuneRecord = AbcTuneRecord(tuneId, newTuneBuilder.build(), fileIds + fileId)

        val tuneHash = abcTune.bodyElements.hashCode()

        hashTuneRecord(tuneHash) = abcTuneRecord
        idTuneRecord(tuneId) = abcTuneRecord

        abcTuneRecord
    }
  }

  def getAllAbcTunes: Iterable[AbcTune] = idTuneRecord.values.map(_.tune)

  def getRecentAbcTunes(limit: Int): Iterable[AbcTune] = tunesStack.take(limit)

  def getTuneCount: Long = idTuneRecord.size

  def idToTitle(id: UUID): String = {
    idTuneRecord(id).tune.titles.mkString("/")
  }

  /**
    * Get the AbcTuneRecords corresponding to the given tune record ids.
    *
    * @param ids The tune record ids to lookup.
    * @return The found AbcTuneRecords. Any unknown ids will be absent from the results.
    */
  def getTuneRecordsById(ids: Set[UUID]): Set[AbcTuneRecord] =
    idTuneRecord.filter(entry => ids.contains(entry._1)).values.toSet

  /** Determine whether the String content is considered a valid ABC File.
    *
    * @param content The ABC File content to test.
    * @return A Try where Success means the content is considered a valid ABC File, and Failure indicates a problem with the content.
    */
  override def isValid(content: String): Try[Unit] = parseFileContent(content).map(_ => {})

  /** Consume the ABC File content.
    *
    * @param content The ABC File content.
    * @param id      The ABC File ID.
    */
  override def process(content: String, id: UUID): Unit = addFromFileContent(id, content)
}

case class AbcTuneRecord(id: UUID, tune: AbcTune, fileIds: Set[UUID])

