package dev.hunterlab.app.ui.playground

import dev.hunterlab.core.link.TransmissionAck
import dev.hunterlab.core.playground.*
import dev.hunterlab.protocol.DayPeriod

/** A tela recebe dados e intenções; nenhuma porta de pacotes ou rádio é exposta. */
interface PlaygroundActions {
    fun select(id: String)
    fun category(value: PlaygroundCategory)
    fun evidence(value: VisualEvidence?)
    fun slot(value: IconSlot)
    fun period(value: DayPeriod)
    fun duration(value: Long)
    fun arm(ack: TransmissionAck)
    fun scan()
    fun connect()
    fun requestPin()
    fun submitPin(value: String)
    fun confirmPin()
    fun rejectPin()
    fun show()
    fun stopPhysical()
    fun leave()
    fun addStage()
    fun removeStage(index: Int)
    fun moveStage(index: Int, offset: Int)
    fun stageDuration(index: Int, ms: Long)
    fun preset(name: String)
    fun play()
    fun pause()
    fun restart()
    fun stopPreview(reason: ExperimentEnd = ExperimentEnd.USER_STOP)
    fun observe(index: Int, value: VisualObservation)
    fun clock(index: Int, value: ClockRecovery)
    fun clearHistory()
    fun report(): String
}
