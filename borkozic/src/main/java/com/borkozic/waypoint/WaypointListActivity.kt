package com.borkozic.waypoint
import com.borkozic.navigation.BaseNavigationService
import com.borkozic.BaseApplication

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.data.Waypoint
import com.borkozic.navigation.NavigationService
import com.borkozic.util.StringFormatter

class WaypointListActivity : AppCompatActivity(), OnWaypointActionListener {

    private lateinit var application: Borkozic

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        application = BaseApplication.getApplication<Borkozic>()!!

        setContentView(R.layout.act_fragment)

        if (savedInstanceState == null) {
            val fragment = Fragment.instantiate(this, WaypointList::class.java.name)
            val fragmentTransaction = supportFragmentManager.beginTransaction()
            fragmentTransaction.add(android.R.id.content, fragment, "WaypointList")
            fragmentTransaction.commit()
        }
    }

    override fun onWaypointView(waypoint: Waypoint) {
        application.ensureVisible(waypoint)
        finish()
    }

    override fun onWaypointNavigate(waypoint: Waypoint) {
        val intent = Intent(application, NavigationService::class.java).setAction(BaseNavigationService.NAVIGATE_MAPOBJECT)
        intent.putExtra(BaseNavigationService.EXTRA_NAME, waypoint.name)
        intent.putExtra(BaseNavigationService.EXTRA_LATITUDE, waypoint.latitude)
        intent.putExtra(BaseNavigationService.EXTRA_LONGITUDE, waypoint.longitude)
        intent.putExtra(BaseNavigationService.EXTRA_PROXIMITY, waypoint.proximity)
        application.startService(intent)
        finish()
    }

    override fun onWaypointEdit(waypoint: Waypoint) {
        val index = application.getWaypointIndex(waypoint)
        startActivity(Intent(application, WaypointProperties::class.java).putExtra("INDEX", index))
    }

    override fun onWaypointShare(waypoint: Waypoint) {
        val i = Intent(android.content.Intent.ACTION_SEND)
        i.type = "text/plain"
        i.putExtra(Intent.EXTRA_SUBJECT, R.string.currentloc)
        val coords = StringFormatter.coordinates(application.coordinateFormat, " ", waypoint.latitude, waypoint.longitude)
        i.putExtra(Intent.EXTRA_TEXT, waypoint.name + " @ " + coords)
        startActivity(Intent.createChooser(i, getString(R.string.menu_share)))
    }

    override fun onWaypointRemove(waypoint: Waypoint) {
        application.removeWaypoint(waypoint)
    }
}
