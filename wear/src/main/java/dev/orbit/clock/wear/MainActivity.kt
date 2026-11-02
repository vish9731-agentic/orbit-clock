package dev.orbit.clock.wear

import dev.orbit.clock.core.ClockActivity
import dev.orbit.clock.core.WatchRingTransition

class MainActivity : ClockActivity() {
    override val watch = true
    override fun onResume() { super.onResume(); WatchRingTransition.attach(this) }
}
