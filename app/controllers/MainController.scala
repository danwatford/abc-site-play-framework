package controllers

import java.util.UUID
import javax.inject.Inject

import com.foomoo.abc.service.SubsequenceMatchService.NoteSequence
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._
import services._


/**
  * Main controller for the ABC Parser Website.
  *
  * This controller provides provides the front page.
  */
class MainController @Inject()(fileStorage: AbcFileService,
                               tuneStorage: AbcTuneService,
                               tuneSequenceStorage: AbcTuneSequenceService,
                               statusService: Status) extends Controller {

  /**
    * The ABC Parser main page.
    */
  def index = Action.async {

    for (
      recentFiles <- fileStorage.getRecentAbcFileRecords;
      filesCount <- statusService.getFileCount
    ) yield {
      val tuneSequences: Map[NoteSequence, Set[UUID]] = tuneSequenceStorage.getSequences(8)

      val tuneIdSetsSequencesMap: Map[Set[UUID], NoteSequence] = tuneSequences.map(_.swap)

      val tunesRecordsSequencesMap: Map[Set[AbcTuneRecord], NoteSequence] =
        tuneIdSetsSequencesMap.map(entry => (tuneStorage.getTuneRecordsById(entry._1), entry._2))

      val printableTuneSequences: Map[String, Set[String]] = tunesRecordsSequencesMap.map {
        case (tuneRecords, noteSequence) => {
          val noteSequenceString = noteSequence.map(_.note).mkString(" ")
          (noteSequenceString, tuneRecords.map(tuneRecord => tuneRecord.tune.titles.mkString("/")))
          // TODO Add file id to output
        }
      }.filter(_._2.size >= 8)


      Ok(views.html.index(recentFiles,
        tuneStorage.getRecentAbcTunes(10),
        printableTuneSequences,
        filesCount,
        tuneStorage.getTuneCount))
    }

  }

}
