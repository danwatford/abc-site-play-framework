package controllers

import java.util.UUID
import javax.inject.Inject

import com.foomoo.abc.service.SubsequenceMatchService.NoteSequence
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._
import services._

/**
  * Front page controller for the ABC Parser Website.
  */
class MainController @Inject()(fileStorage: AbcFileService,
                               tuneStorage: AbcTuneService,
                               tuneSequenceStorage: AbcTuneSequenceService,
                               statusService: Status) extends Controller {

  // Minimum number of tunes that must include a note sequence for it to be shown on the front page.
  val minTunesPerSequence = 8

  // The number of note sequences to be displayed on the front page.
  val displayedNoteSequences = 10

  /**
    * The ABC Parser main page.
    */
  def index = Action.async {

    for (
      recentFiles <- fileStorage.getRecentAbcFileRecords;
      filesCount <- statusService.getFileCount
    ) yield {
      val tuneSequences: Map[NoteSequence, Set[UUID]] = tuneSequenceStorage.getSequences(minTunesPerSequence)

      val tuneIdSetsSequencesMap: Map[Set[UUID], NoteSequence] = tuneSequences.map(_.swap)

      val tunesRecordsSequencesMap: Map[Set[AbcTuneRecord], NoteSequence] =
        tuneIdSetsSequencesMap.map(entry => (tuneStorage.getTuneRecordsById(entry._1), entry._2))

      val printableTuneSequences: Map[String, Set[AbcTuneRecord]] = tunesRecordsSequencesMap.map {
        case (tuneRecords, noteSequence) =>
          val noteSequenceString = noteSequence.map(_.note).mkString(" ")
          (noteSequenceString, tuneRecords)
      }.filter(_._2.size >= minTunesPerSequence).take(displayedNoteSequences)

      Ok(views.html.index(recentFiles,
        tuneStorage.getRecentAbcTunes(10),
        printableTuneSequences,
        filesCount,
        tuneStorage.getTuneCount))
    }

  }

}
