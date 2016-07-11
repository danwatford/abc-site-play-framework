package services

import java.util.UUID
import javax.inject.Singleton

import com.foomoo.stringstore.service.MongoStatusService

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Tracks processing of ABC files and tunes
  */
@Singleton
class Status {

  val stringStatusService = new MongoStatusService

  def getFileCount: Future[Long] = Future {
    stringStatusService.getStatus.getStrings
  }

}
