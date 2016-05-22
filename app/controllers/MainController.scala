package controllers

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._

import scala.concurrent.Future

/**
  * Main controller for the ABC Parser Website.
  *
  * This controller provides provides the front page.
  */
class MainController extends Controller {

  /**
    * The ABC Parser main page.
    */
  def index = Action.async {
    Future(Ok(views.html.index()))
  }

}
