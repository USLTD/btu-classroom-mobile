package com.example

import android.app.Application
import com.example.app.AppContainer

class BtuCmApplication : Application() {
  val container: AppContainer by lazy { AppContainer(this) }
}
