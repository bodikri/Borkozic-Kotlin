package com.borkozic.track

import android.app.Activity
import android.app.Dialog
import android.app.ProgressDialog
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import androidx.preference.PreferenceManager
import android.text.Editable
import android.text.InputFilter
import android.text.Spanned
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.borkozic.Borkozic
import com.borkozic.BaseApplication
import com.borkozic.R
import com.borkozic.data.Track
import com.borkozic.location.ILocationService
import com.borkozic.ui.ColorButton
import com.borkozic.util.FileUtils
import com.borkozic.util.GpxFiles
import com.borkozic.util.KmlFiles
import com.borkozic.util.OziExplorerFiles
import com.googlecode.android.widgets.DateSlider.SliderContainer
import java.io.File
import java.util.Calendar
import java.util.List

open class TrackExportDialog : DialogFragment(), TextWatcher {
    private var nameText: EditText? = null
    private var formatSpinner: Spinner? = null
    private var skip: CheckBox? = null
    private var color: ColorButton? = null
    private var fromSliderContainer: SliderContainer? = null
    private var tillSliderContainer: SliderContainer? = null
    private var saveButton: Button? = null

    private var validName = false
    private var validDates = false
    private var locationService: ILocationService? = null

    companion object {
        @JvmStatic
        fun newInstance(locationService: ILocationService): TrackExportDialog {
            val dialog = TrackExportDialog()
            dialog.setLocationService(locationService)
            return dialog
        }
    }

    fun setLocationService(locationService: ILocationService) {
        this.locationService = locationService
        retainInstance = true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireActivity())

        val view = inflater.inflate(R.layout.dlg_exporttrack, container)

        nameText = view.findViewById<View>(R.id.name_text) as EditText
        nameText!!.filters = arrayOf<InputFilter>(filter)
        nameText!!.addTextChangedListener(this)
        formatSpinner = view.findViewById<View>(R.id.format_spinner) as Spinner

        skip = view.findViewById<View>(R.id.skip_check) as CheckBox
        color = view.findViewById<View>(R.id.color_button) as ColorButton
        color!!.setColor(
            prefs.getInt(
                getString(R.string.pref_tracking_currentcolor),
                resources.getColor(R.color.currenttrack)
            ), Color.RED
        )

        val startTime = Calendar.getInstance()
        startTime.timeInMillis = locationService!!.getTrackStartTime()
        val endTime = Calendar.getInstance()
        endTime.timeInMillis = locationService!!.getTrackEndTime()

        fromSliderContainer = view.findViewById<View>(R.id.fromSliderContainer) as SliderContainer
        fromSliderContainer!!.setMinuteInterval(1)
        fromSliderContainer!!.setTime(endTime)
        fromSliderContainer!!.setMinTime(startTime)
        fromSliderContainer!!.setMaxTime(endTime)
        fromSliderContainer!!.setMinuteInterval(60)
        fromSliderContainer!!.setOnTimeChangeListener(onFromTimeChangeListener)
        tillSliderContainer = view.findViewById<View>(R.id.tillSliderContainer) as SliderContainer
        tillSliderContainer!!.setMinuteInterval(1)
        tillSliderContainer!!.setTime(endTime)
        tillSliderContainer!!.setMinTime(startTime)
        tillSliderContainer!!.setMaxTime(endTime)
        tillSliderContainer!!.setMinuteInterval(60)
        tillSliderContainer!!.setOnTimeChangeListener(onTillTimeChangeListener)

        val dialog = dialog

        val cancelButton = view.findViewById<View>(R.id.cancel_button) as Button
        cancelButton.setOnClickListener(object : OnClickListener {
            override fun onClick(v: View) {
                dialog!!.cancel()
            }
        })
        saveButton = view.findViewById<View>(R.id.save_button) as Button
        saveButton!!.setOnClickListener(saveOnClickListener)

        validName = false
        validDates = true
        updateSaveButton()

        dialog!!.setTitle(R.string.exporttrack_name)
        dialog.setCanceledOnTouchOutside(false)
        return view
    }

    override fun onDestroyView() {
        if (dialog != null && retainInstance) dialog!!.setDismissMessage(null)
        super.onDestroyView()
    }

    private val saveOnClickListener = object : OnClickListener {
        override fun onClick(v: View) {
            val activity = activity!!
            val application = BaseApplication.getApplication<Borkozic>()!!

            val pd = ProgressDialog(activity)
            pd.isIndeterminate = true
            pd.setMessage(getString(R.string.msg_wait))
            pd.setCancelable(false)
            pd.show()

            Thread(object : Runnable {
                override fun run() {
                    val skipSingles = skip!!.isChecked

                    val name = nameText!!.text.toString()
                    val format = formatSpinner!!.getItemAtPosition(formatSpinner!!.selectedItemPosition).toString()
                    val filename = FileUtils.sanitizeFilename(name) + format

                    val startTime = fromSliderContainer!!.time
                    startTime[Calendar.HOUR_OF_DAY] = 0
                    startTime[Calendar.MINUTE] = 0
                    startTime[Calendar.SECOND] = 0
                    startTime[Calendar.MILLISECOND] = 0
                    val start = startTime.timeInMillis
                    val endTime = tillSliderContainer!!.time
                    endTime[Calendar.HOUR_OF_DAY] = 23
                    endTime[Calendar.MINUTE] = 59
                    endTime[Calendar.SECOND] = 59
                    endTime[Calendar.MILLISECOND] = 999
                    val end = endTime.timeInMillis

                    val track = locationService!!.getTrack(start, end)
                    val points = track.points

                    if (skipSingles) {
                        var pp = track.getLastPoint()!!
                        for (i in points.size - 2 downTo 0) {
                            val cp = points[i]
                            if (!pp.continous && !cp.continous) {
                                track.removePoint(i + 1)
                            }
                            pp = cp
                        }
                    }

                    if (track.points.size < 2) {
                        activity.runOnUiThread(object : Runnable {
                            override fun run() {
                                Toast.makeText(activity, R.string.msg_emptytracksegment, Toast.LENGTH_LONG).show()
                            }
                        })
                        pd.dismiss()
                        return
                    }

                    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
                    track.name = name
                    track.width = prefs.getInt(
                        getString(R.string.pref_tracking_linewidth),
                        resources.getInteger(R.integer.def_track_linewidth)
                    )
                    track.color = color!!.getColor()

                    try {
                        val dir = File(application.dataPath!!)
                        //android.util.Log.i("TrackExportDialog", dir.toString());
                        if (!dir.exists()) dir.mkdirs()
                        val file = File(dir, filename)
                        if (!file.exists()) {
                            file.createNewFile()
                        }
                        if (file.canWrite()) {
                            when (format) {
                                ".plt" -> OziExplorerFiles.saveTrackToFile(file, application.charset!!, track)
                                ".kml" -> KmlFiles.saveTrackToFile(file, track)
                                ".gpx" -> GpxFiles.saveTrackToFile(file, track)
                            }
                        }
                        dismiss()
                    } catch (e: Exception) {
                        Log.e("TrackExport", e.toString(), e)
                        activity.runOnUiThread(object : Runnable {
                            override fun run() {
                                Toast.makeText(activity, R.string.err_write, Toast.LENGTH_LONG).show()
                            }
                        })
                    }
                    pd.dismiss()
                }
            }).start()
        }
    }

    private fun updateSaveButton() {
        saveButton!!.isEnabled = validName && validDates
    }

    private val onFromTimeChangeListener = object : SliderContainer.OnTimeChangeListener {
        override fun onTimeChange(time: Calendar) {
            validDates = time.compareTo(tillSliderContainer!!.time) <= 0
            updateSaveButton()
        }
    }

    private val onTillTimeChangeListener = object : SliderContainer.OnTimeChangeListener {
        override fun onTimeChange(time: Calendar) {
            validDates = time.compareTo(fromSliderContainer!!.time) >= 0
            updateSaveButton()
        }
    }

    override fun afterTextChanged(s: Editable) {
        validName = s.length > 0 && "" != s.toString().trim { it <= ' ' }
        updateSaveButton()
    }

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

    internal var filter: InputFilter = object : InputFilter {
        override fun filter(
            source: CharSequence,
            start: Int,
            end: Int,
            dest: Spanned,
            dstart: Int,
            dend: Int
        ): CharSequence? {
            for (i in start until end) {
                val resultingTxt = source.subSequence(start, end).toString()
                if (resultingTxt.matches(".*[/\\\\:;|].*".toRegex())) {
                    return ""
                }
            }
            return null
        }
    }
}