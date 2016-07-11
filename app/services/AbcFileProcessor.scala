package services

import java.util.UUID

import scala.util.Try

/**
  * A trait for processing AbcFile content to determine whether the content is valid and to submit the content for further processing, for example breaking into tunes.
  */
trait AbcFileProcessor {

  /** Determine whether the String content is considered a valid ABC File.
    *
    * @param content The ABC File content to test.
    * @return A Try where Success means the content is considered a valid ABC File, and Failure indicates a problem with the content.
    */
  def isValid(content: String): Try[Unit]

  /** Consume the ABC File content.
    *
    * @param content The ABC File content.
    * @param id      The ABC File ID.
    */
  def process(content: String, id: UUID): Unit

}
