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

  val tuneIdSequencesMap = new collection.mutable.HashMap[UUID, collection.mutable.Set[NoteSequence]]
    with mutable.MultiMap[UUID, NoteSequence]

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
        tuneSet.flatMap(tune => tuneIdMap.get(tune)).foreach(tuneId => {
          sequenceTunesMap.addBinding(noteSequence, tuneId)
          tuneIdSequencesMap.addBinding(tuneId, noteSequence)
        })
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

  /**
    * For the given tune record id, returns a map of NoteSequences found in the tune along with the
    * tune record ids of the tunes that also contain those NoteSequences.
    *
    * @param tuneId The tune record id to find sequences for.
    * @return A Map of NoteSequence to tune record ids.
    */
  def getSequencesByTuneId(tuneId: UUID): Map[NoteSequence, Set[UUID]] = {
    sequenceTunesMap.filter(entry => entry._2.contains(tuneId)).map(entry => (entry._1, entry._2.toSet)).toMap
  }

}
