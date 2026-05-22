package com.borkozic.waypoint

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.borkozic.BaseApplication
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.data.Waypoint
import com.borkozic.util.Geo
import com.borkozic.util.StringFormatter
import java.io.File

class WaypointInfo : DialogFragment(), View.OnClickListener {
    private var waypoint: Waypoint? = null
    private var icon: Drawable? = null
    private var waypointActionsCallback: OnWaypointActionListener? = null

    init {
        retainInstance = true
    }

    fun setWaypoint(waypoint: Waypoint) {
        this.waypoint = waypoint
        icon = null
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_LEFT_ICON)
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.act_waypoint_info, container)
        view.findViewById<ImageButton>(R.id.navigate_button).setOnClickListener(this)
        view.findViewById<ImageButton>(R.id.edit_button).setOnClickListener(this)
        view.findViewById<ImageButton>(R.id.share_button).setOnClickListener(this)
        view.findViewById<ImageButton>(R.id.remove_button).setOnClickListener(this)
        return view
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val act = if (context is Activity) context else null
        try {
            waypointActionsCallback = act as OnWaypointActionListener
        } catch (e: ClassCastException) {
            throw ClassCastException((act?.toString() ?: context.toString()) + " must implement OnWaypointActionListener")
        }
    }

    override fun onStart() {
        super.onStart()
        val args = arguments
        if (args != null) {
            val lat = args.getDouble("lat")
            val lon = args.getDouble("lon")
            val elev = args.getDouble("elev")
            updateWaypointInfo(lat, lon, elev)
        }
    }

    override fun onDestroyView() {
        if (dialog != null && retainInstance) {
            dialog!!.setDismissMessage(null)
        }
        super.onDestroyView()
    }

    override fun onClick(v: View) {
        val wpt = waypoint ?: return
        when (v.id) {
            R.id.navigate_button -> waypointActionsCallback?.onWaypointNavigate(wpt)
            R.id.edit_button -> waypointActionsCallback?.onWaypointEdit(wpt)
            R.id.share_button -> waypointActionsCallback?.onWaypointShare(wpt)
            R.id.remove_button -> waypointActionsCallback?.onWaypointRemove(wpt)
        }
        dismiss()
    }

    @SuppressLint("NewApi")
    private fun updateWaypointInfo(lat: Double, lon: Double, elev: Double) {
        val application = BaseApplication.getApplication<Borkozic>()
        val activity = getActivity()
        val dialog = getDialog()
        val view = getView() ?: return
        val wpt = waypoint ?: return
        val app = application ?: return

        if (wpt.drawImage) {
            val options = BitmapFactory.Options()
            options.inScaled = false
            val b = BitmapFactory.decodeFile(app.iconPath + File.separator + wpt.image, options)
            if (b != null) {
                b.setDensity(Bitmap.DENSITY_NONE)
                icon = BitmapDrawable(resources, b)
            }
        }

        val description = view.findViewById<WebView>(R.id.description)

        if ("" == wpt.description) {
            description.visibility = View.GONE
        } else {
            var descriptionHtml: String
            try {
                val tv = TypedValue()
                val theme = activity!!.theme
                theme.resolveAttribute(android.R.attr.textColorSecondary, tv, true)
                val ctx = context ?: return
                val secondaryColor = ContextCompat.getColor(ctx, tv.resourceId)

                val cssFmt = "html,body{margin:0;background:transparent} *{color:#%06X}"
                val css = String.format(cssFmt, (secondaryColor and 0x00FFFFFF))
                descriptionHtml = StringBuilder().append(css).append(wpt.description ?: "").toString()
                description.setWebViewClient(object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        view.setBackgroundColor(Color.TRANSPARENT)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                            view.setLayerType(WebView.LAYER_TYPE_SOFTWARE, null)
                    }
                })
                description.setBackgroundColor(Color.TRANSPARENT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                    description.setLayerType(WebView.LAYER_TYPE_SOFTWARE, null)
            } catch (e: Resources.NotFoundException) {
                description.setBackgroundColor(Color.LTGRAY)
                descriptionHtml = wpt.description
            }

            val settings: WebSettings = description.settings
            settings.defaultTextEncodingName = "utf-8"
            settings.allowFileAccess = true
            val baseUrl = Uri.fromFile(File(app.dataPath!!))
            description.loadDataWithBaseURL(
                baseUrl.toString() + "/",
                descriptionHtml,
                "text/html",
                "utf-8",
                null
            )
        }

        val coords = StringFormatter.coordinates(
            app.coordinateFormat, " ",
            wpt.latitude, wpt.longitude
        )
        view.findViewById<TextView>(R.id.coordinates).text = coords

        val alt = wpt.altitude
        if (alt != Integer.MIN_VALUE.toDouble()) {
            val altitudeTxt = StringFormatter.elevationC(alt)
            view.findViewById<TextView>(R.id.altitude).text = altitudeTxt
        }

        val dist = Geo.distance(lat, lon, wpt.latitude, wpt.longitude)
        var bearing = Geo.bearing(lat, lon, wpt.latitude, wpt.longitude)
        bearing = app.fixDeclination(bearing)
        val distanceTxt = StringFormatter.distanceH(dist) + " " + StringFormatter.bearingH(bearing)
        view.findViewById<TextView>(R.id.distance).text = distanceTxt

        if (wpt.date != null) {
            val dateText = DateFormat.getDateFormat(activity).format(wpt.date) + " " +
                    DateFormat.getTimeFormat(activity).format(wpt.date)
            view.findViewById<TextView>(R.id.date).text = dateText
        } else {
            view.findViewById<TextView>(R.id.date).visibility = View.GONE
        }

        if (icon != null) {
            dialog?.setFeatureDrawable(Window.FEATURE_LEFT_ICON, icon)
        } else {
            dialog?.setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, android.R.drawable.ic_dialog_map)
        }
        dialog?.setTitle(wpt.name)
    }
}
