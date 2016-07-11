package modules

import com.google.inject.AbstractModule
import services._

/**
  * Module to configure dependency injection bindings.
  */
class BindingsModule extends AbstractModule {
  override def configure(): Unit = {

    bind(classOf[AbcFileProcessor]).to(classOf[AbcTuneService])
    bind(classOf[AbcTuneProcessor]).to(classOf[AbcTuneSequenceService])

    // Configure the AbcFileService as an eager singleton as it loads all persisted ABC files at startup.
    bind(classOf[AbcFileService]).asEagerSingleton()
  }

}
