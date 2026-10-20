package app.honmani.reader

import android.app.Application

class HonmaniApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
