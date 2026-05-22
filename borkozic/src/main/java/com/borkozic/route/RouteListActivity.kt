package com.borkozic.route

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.borkozic.BaseApplication
import com.borkozic.Borkozic
import com.borkozic.data.Route
import com.borkozic.overlay.RouteOverlay
import com.borkozic.ui.BorkozicTheme

class RouteListActivity : ComponentActivity(), OnRouteActionListener {

    companion object {
        const val RESULT_START_ROUTE = 1
        const val RESULT_LOAD_ROUTE = 2
        const val RESULT_ROUTE_DETAILS = 3
    }

    private lateinit var application: Borkozic

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        application = BaseApplication.getApplication<Borkozic>()!!

        val mode = intent.extras?.getInt("MODE") ?: RouteList.MODE_MANAGE

        setContent {
            var themeVersion by remember { mutableStateOf(0) }
            BorkozicTheme(listType = "route", themeVersion = themeVersion) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RouteListScreen(
                        mode = mode,
                        themeVersion = themeVersion,
                        onThemeChanged = { themeVersion++ },
                        onAction = { route, action ->
                            handleRouteAction(route, action)
                        },
                        onLoadRoute = {
                            startActivityForResult(
                                Intent(this@RouteListActivity, RouteFileList::class.java),
                                RESULT_LOAD_ROUTE
                            )
                        }
                    )
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            RESULT_START_ROUTE, RESULT_ROUTE_DETAILS -> {
                if (resultCode == RESULT_OK) finish()
            }
            RESULT_LOAD_ROUTE -> {
                if (resultCode == RESULT_OK) {
                    val indexes = data?.extras?.getIntArray("index") ?: return
                    for (index in indexes) {
                        val newRoute = RouteOverlay(this, application.getRoute(index)!!)
                        application.routeOverlays.add(newRoute)
                    }
                    setResult(RESULT_OK, Intent())
                    finish()
                }
            }
        }
    }

    private fun handleRouteAction(route: Route, action: RouteAction) {
        when (action) {
            is RouteAction.Details -> {
                startActivityForResult(
                    Intent(this, RouteDetails::class.java).putExtra("index", application.getRouteIndex(route)),
                    RESULT_ROUTE_DETAILS
                )
            }
            is RouteAction.NavigateRoute -> {
                startActivityForResult(
                    Intent(this, RouteStart::class.java).putExtra("index", application.getRouteIndex(route)),
                    RESULT_START_ROUTE
                )
            }
            is RouteAction.Properties -> {
                startActivity(Intent(this, RouteProperties::class.java).putExtra("index", application.getRouteIndex(route)))
            }
            is RouteAction.Edit -> {
                route.show = true
                setResult(RESULT_OK, Intent().putExtra("index", application.getRouteIndex(route)).putExtra("dir", 0))
                finish()
            }
            is RouteAction.Save -> {
                startActivity(Intent(this, RouteSave::class.java).putExtra("index", application.getRouteIndex(route)))
            }
            is RouteAction.Remove -> {
                application.removeRoute(route)
            }
        }
    }

    override fun onRouteDetails(route: Route) {
        startActivityForResult(
            Intent(this, RouteDetails::class.java).putExtra("index", application.getRouteIndex(route)),
            RESULT_ROUTE_DETAILS
        )
    }

    override fun onRouteNavigate(route: Route) {
        startActivityForResult(
            Intent(this, RouteStart::class.java).putExtra("index", application.getRouteIndex(route)),
            RESULT_START_ROUTE
        )
    }

    override fun onRouteEdit(route: Route) {
        startActivity(Intent(this, RouteProperties::class.java).putExtra("index", application.getRouteIndex(route)))
    }

    override fun onRouteEditPath(route: Route) {
        route.show = true
        setResult(RESULT_OK, Intent().putExtra("index", application.getRouteIndex(route)))
        finish()
    }

    override fun onRouteSave(route: Route) {
        startActivity(Intent(this, RouteSave::class.java).putExtra("index", application.getRouteIndex(route)))
    }
}
