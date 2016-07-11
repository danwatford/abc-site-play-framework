package services

import java.util.UUID
import javax.inject.Singleton

import com.foomoo.abc.service.SubsequenceMatchService
import com.foomoo.abc.service.SubsequenceMatchService.NoteSequence
import com.foomoo.abc.tune.AbcTune
import play.api.Logger

import scala.collection.mutable

/**
  * Storage for note sequences in tunes
  */
@Singleton
class AbcTuneSequenceService extends AbcTuneProcessor {

  val sequenceTunesMap = new collection.mutable.HashMap[NoteSequence, collection.mutable.Set[UUID]]
    with mutable.MultiMap[NoteSequence, UUID]

  /**
    * Process the given tuples of tune record ids and tunes to extract note sequences and maintain a mapping between
    * note sequences and tune record ids.
    *
    * @param abcIdTunes The tuples of abc tune record ids and tunes.
    */
  def addFromAbcTunes(abcIdTunes: Seq[(UUID, AbcTune)]): Unit = {

    Logger.debug(s"addFromAbcTunes called with ${abcIdTunes.size} tunes")

    // Build map of tune ids to tunes.
    val tuneIdMap: Map[AbcTune, UUID] = abcIdTunes.map(_.swap).toMap

    val sequences: Map[NoteSequence, Set[AbcTune]] =
      SubsequenceMatchService.getSubSequenceTunes(8, tuneIdMap.keys.toSeq)

    Logger.debug(s"Extracted ${sequences.size} sequences")

    sequences.foreach {
      case (noteSequence, tuneSet) =>
        tuneSet.flatMap(tune => tuneIdMap.get(tune))
          .foreach(tuneId => sequenceTunesMap.addBinding(noteSequence, tuneId))
    }
  }

  /** Consume ABC Tunes.
    *
    * @param tunes The ABC Tunes to console.
    */
  override def process(tunes: Seq[(UUID, AbcTune)]): Unit = addFromAbcTunes(tunes)

  /**
    * Returns the note sequences which have the given minimum number of tunes containing them.
    *
    * @param tuneCount The minimum number of tunes which should contain each of the returned note sequennces.
    */
  def getSequences(tuneCount: Int): Map[NoteSequence, Set[UUID]] = {
    sequenceTunesMap.filter(_._2.size >= tuneCount).map(entry => entry._1 -> entry._2.toSet).toMap
  }

}
