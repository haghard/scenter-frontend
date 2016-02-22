package modules

import java.io.File
import com.google.inject.AbstractModule

class CoreModule extends AbstractModule with play.api.libs.concurrent.AkkaGuiceSupport {

  override def configure() = {
    val polygonGeojson = new File("./conf/nyc-borough-boundaries-polygon.geojson")
    if(!polygonGeojson.exists())
      throw new Exception(s"${polygonGeojson.getAbsolutePath} can't be found")

    bind(classOf[File]).toInstance(polygonGeojson)
  }
}