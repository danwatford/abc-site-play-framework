package services

import java.util.UUID
import javax.inject.{Inject, Singleton}

import com.foomoo.abc.notation.AbcNotationFile
import com.foomoo.abc.notation.parsing.AbcNotationParser
import com.foomoo.abc.tune.conversion.AbcNotationConverter
import com.foomoo.abc.tune.{AbcTune, AbcTuneBuilder}

import scala.collection.mutable
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
  val tuneIdsStack = collection.mutable.Stack[UUID]()

  val titleTuneIds = collection.mutable.HashMap[String, UUID]()
  val fileIdTuneIds = new collection.mutable.HashMap[UUID, collection.mutable.Set[UUID]]
    with mutable.MultiMap[UUID, UUID]

  /**
    * Parse the given ABC file content into an AbcNotationFile object.
    *
    * @param fileContent The ABC file content to parse.
    * @return A Try of AbcNotationFile representing the parsed ABC file. A Failure will be returned in the file content
    *         cannot be parsed without error.
    */
  private def parseFileContent(fileContent: String): Try[AbcNotationFile] =
    AbcNotationParser.file(new CharSequenceReader(fileContent)) match {
      case AbcNotationParser.NoSuccess(msg, next) =>
        Failure(new IllegalArgumentException(msg + "\nNext is: " + next.pos))
      case AbcNotationParser.Success(ts, _) =>
        Success(ts)
    }

  /**
    * Adds tunes to the store from the given file content.
    *
    * @param fileId      The id of the file.
    * @param fileContent The content of the file.
    * @return The list of AbcTunes extracted from the file content.
    */
  def addFromFileContent(fileId: UUID, fileContent: String): Try[List[AbcTune]] =

    parseFileContent(fileContent).map(abcNotationFile => {

      val abcTunes = abcNotationFile.tunes.map(abcNotationTune => AbcNotationConverter.convertTune(abcNotationTune, abcNotationFile.fileHeader))

      val addTuneResults: List[(AbcTuneRecord, Boolean)] = abcTunes.map(abcTune => addTune(abcTune, fileId))

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

  /**
    * Determine whether the given tune exists in the tune store.
    *
    * @param abcTune The tune to test for.
    * @return An Option of the AbcTuneRecord, None if the tune doesn't exist.
    */
  private def existingTune(abcTune: AbcTune): Option[AbcTuneRecord] = {

    existingTuneByHash(abcTune).orElse(existingTuneByTitle(abcTune))
  }

  /**
    * Determine whether the given tune exists in the tune store by testing the hash of the tunes's body elements.
    *
    * @param abcTune The tune to test for.
    * @return An Option of the AbcTuneRecord, None if the tune doesn't exist.
    */
  private def existingTuneByHash(abcTune: AbcTune): Option[AbcTuneRecord] = {

    val tuneHash = abcTune.bodyElements.hashCode()
    hashTuneRecord.get(tuneHash)
  }

  /**
    * Determine whether the given tune exists in the tune store by testing the tune's title.
    *
    * @param abcTune The tune to test for.
    * @return An Option of the AbcTuneRecord, None if the tune doesn't exist.
    */
  private def existingTuneByTitle(abcTune: AbcTune): Option[AbcTuneRecord] = {

    abcTune.titles.find(title => titleTuneIds.contains(title))
      .flatMap(title => titleTuneIds.get(title))
      .flatMap(id => getTuneRecordById(id))
  }

  /**
    * Add the given tune to the store.
    *
    * @param abcTune The tune to add.
    * @param fileId  The id of the file the tune was extracted from.
    * @return The new AbcTuneRecord of the added tune.
    */
  private def addNewTune(abcTune: AbcTune, fileId: UUID): AbcTuneRecord = {
    val tuneId = UUID.randomUUID()
    val abcTuneRecord = AbcTuneRecord(tuneId, abcTune, Set(fileId))
    val tuneHash = abcTune.bodyElements.hashCode()

    hashTuneRecord(tuneHash) = abcTuneRecord
    idTuneRecord(tuneId) = abcTuneRecord
    tuneIdsStack.push(tuneId)
    fileIdTuneIds.addBinding(fileId, tuneId)

    abcTune.titles.foreach(title => titleTuneIds(title) = tuneId)

    abcTuneRecord
  }

  /**
    * Merge the given tune with the existing tune identified by the given tune id.
    *
    * @param tuneId  The id of the already existing tune.
    * @param abcTune The tune to merge with the existing tune.
    * @param fileId  The id of the file that the given tune was extracted from.
    * @return The new AbcTuneRecord representing the resulting tune from the merge operation. The
    *         original tune id will be kept.
    */
  private def mergeTuneToRecord(tuneId: UUID, abcTune: AbcTune, fileId: UUID): AbcTuneRecord = {

    idTuneRecord(tuneId) match {
      case AbcTuneRecord(_, tune, fileIds) =>

        val newTuneBuilder = new AbcTuneBuilder(tune)

        abcTune.titles.filterNot(newTuneBuilder.titles.contains(_)).foreach(newTuneBuilder.addTitle)

        val abcTuneRecord = AbcTuneRecord(tuneId, newTuneBuilder.build(), fileIds + fileId)

        val tuneHash = abcTune.bodyElements.hashCode()

        hashTuneRecord(tuneHash) = abcTuneRecord
        idTuneRecord(tuneId) = abcTuneRecord
        fileIdTuneIds.addBinding(fileId, tuneId)

        abcTune.titles.foreach(title => titleTuneIds(title) = tuneId)

        abcTuneRecord
    }
  }

  def getAllAbcTunes: Iterable[AbcTuneRecord] = idTuneRecord.values

  def getRecentAbcTunes(limit: Int): Iterable[AbcTuneRecord] = getTuneRecordsById(tuneIdsStack.take(limit).toSet)

  def getTuneCount: Long = idTuneRecord.size

  /**
    * Get the AbcTuneRecord corresponding to the given tune record id.
    *
    * Note that AbcTuneRecords are snapshots of the tune record at a point in time, only the tune id from the
    * record will be maintained. AbcTuneRecords will be replaced in the store as other tunes are merged with them.
    *
    * @param id The tune record id to lookup.
    * @return Option of the found AbcTuneRecords. None if the tune record id is unknown.
    */
  def getTuneRecordById(id: UUID): Option[AbcTuneRecord] = idTuneRecord.get(id)

  /**
    * Get the AbcTuneRecords corresponding to the given tune record ids.
    *
    * Note that AbcTuneRecords are snapshots of the tune record at a point in time, only the tune id from the
    * record will be maintained. AbcTuneRecords will be replaced in the store as other tunes are merged with them.
    *
    * @param ids The tune record ids to lookup.
    * @return The found AbcTuneRecords. Any unknown ids will be absent from the results.
    */
  def getTuneRecordsById(ids: Set[UUID]): Set[AbcTuneRecord] =
    idTuneRecord.filter(entry => ids.contains(entry._1)).values.toSet

  /**
    * Get the AbcTuneRecords for tunes extracted from the file specified by the given file id.
    *
    * Note that AbcTuneRecords are snapshots of the tune record at a point in time, only the tune id from the
    * record will be maintained. AbcTuneRecords will be replaced in the store as other tunes are merged with them.
    *
    * @param fileId The id of the file to lookup tunes by.
    * @return The found AbcTuneRecords. The Set will be empty if no tunes found for the given file id.
    */
  def getTuneRecordsByFileId(fileId: UUID): Set[AbcTuneRecord] = {
    val tuneIdsOption = fileIdTuneIds.get(fileId).map(_.toSet)

    tuneIdsOption.map(tuneIds => getTuneRecordsById(tuneIds)).getOrElse(Set())
  }

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

/**
  * A snapshot in time of a tune's record.
  *
  * @param id      The id of the tune.
  * @param tune    The tune.
  * @param fileIds The ids of files that the tune has been found in.
  */
case class AbcTuneRecord(id: UUID, tune: AbcTune, fileIds: Set[UUID])

