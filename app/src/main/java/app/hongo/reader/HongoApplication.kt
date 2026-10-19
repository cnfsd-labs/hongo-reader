package app.hongo.reader

import android.app.Application

class HongoApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
