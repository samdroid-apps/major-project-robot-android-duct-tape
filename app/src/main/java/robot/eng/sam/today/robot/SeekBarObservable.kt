package robot.eng.sam.today.robot

import android.util.Log
import android.widget.SeekBar
import io.reactivex.Observable

/**
 * Created by sam on 11/7/17.
 */

fun seekBarObservableFromView(
        seekBar: SeekBar,
        resetTo: Float? = null,
        min: Float = 0f,
        max: Float = 1f): Observable<Float> {
    val range = max - min

    if (resetTo != null) {
        setProgressInRange(seekBar, resetTo, min, max)
    }

    return Observable.create<Float> { emitter ->
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val frac = progress.toFloat() / seekBar.max.toFloat()
                emitter.onNext(min + range*frac)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                if (resetTo != null) {
                    setProgressInRange(seekBar, resetTo, min, max)
                    emitter.onNext(resetTo)
                }
            }
        })

        emitter.setCancellable {
            seekBar.setOnSeekBarChangeListener(null)
        }
    }
}

fun setProgressInRange(
        seekBar: SeekBar,
        value : Float,
        min: Float,
        max: Float) {
    val range = max - min
    val frac = (value - min) / range
    seekBar.progress = (seekBar.max.toFloat() * frac).toInt()
}
