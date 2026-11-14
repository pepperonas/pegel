package io.celox.pegel

import android.app.Application

class PegelApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

val android.content.Context.container: AppContainer get() = (applicationContext as PegelApp).container
