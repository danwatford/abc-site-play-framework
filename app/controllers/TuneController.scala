package controllers

import java.util.UUID
import javax.inject._

import com.foomoo.abc.service.SubsequenceMatchService.NoteSequence
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._
import services.{AbcFileService, AbcTuneSequenceService, AbcTuneService}

import scala.concurrent.Future

/**
  * Controller for accessing tunes.
  */
class TuneController @Inject()(tuneService: AbcTuneService,
                               fileService: AbcFileService,
                               sequenceService: AbcTuneSequenceService) extends Controller {

  /**
    * Get a response returning all tunes.
    *
    * @return The files response.
    */
  def getTunes = Action {

    val tuneRecords = tuneService.getAllAbcTunes

    Ok(views.html.tunes(s"ABC Site: Tunes", tuneRecords.toSeq))
  }

  /**
    * Get a response returning the tune specified by the given tune id.
    *
    * @param tuneId The tune id to lookup.
    * @return The tune response.
    */
  def getTune(tuneId: UUID) = Action.async {
    tuneService.getTuneRecordById(tuneId) match {
      case Some(tuneRecord) =>
        val fileRecordsFuture = fileService.getFileRecordsById(tuneRecord.fileIds)

        // Retrieve the note sequences fort the requested tune.
        // Filter the current tune out of the set of tune ids for each note sequence.
        val sequencesTuneIdsMap: Map[NoteSequence, Set[UUID]] =
          sequenceService.getSequencesByTuneId(tuneId).filter(entry => entry._2.size > 1)
            .mapValues(tuneIds => tuneIds - tuneId)

        // There will likely be multiple sequences all mapping to the same set of tunes.
        // Only keep one sequence for a particular set of tunes by making the set of tune ids
        // the key in a map.
        val tuneIdsSequencesMap = sequencesTuneIdsMap.map(_.swap)

        // Turn the note sequences into a printable string.
        val tuneIdsSequenceStringMap =
          tuneIdsSequencesMap.mapValues(noteSequence => noteSequence.map(_.note).mkString(" "))

        // Map the note sequence strings to the AbcTuneRecords.
        val sequenceStringTuneRecordsMap =
          tuneIdsSequenceStringMap.map(_.swap).mapValues(tuneIds => tuneService.getTuneRecordsById(tuneIds))

        fileRecordsFuture.map(fileRecords =>
          Ok(views.html.tune(s"ABC Site: Tune ${tuneRecord.id}", tuneRecord, fileRecords, sequenceStringTuneRecordsMap))
        )

      case None =>
        Future {
          NotFound
        }
    }
  }

}
