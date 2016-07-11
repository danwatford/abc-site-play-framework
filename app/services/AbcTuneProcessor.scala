package services

import java.util.UUID

import com.foomoo.abc.tune.AbcTune

/**
  * A trait for processing AbcTunes.
  */
trait AbcTuneProcessor {

  /** Consume ABC Tunes.
    *
    * @param tunes The ABC Tunes to consume.
    */
  def process(tunes: Seq[(UUID, AbcTune)]): Unit

}
