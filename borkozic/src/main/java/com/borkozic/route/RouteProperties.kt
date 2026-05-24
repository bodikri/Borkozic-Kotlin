package com.borkozic.route

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.borkozic.BaseApplication
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.ui.BorkozicTheme

class RouteProperties : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val index = intent.extras!!.getInt("index")
        val application = BaseApplication.getApplication<Borkozic>()!!
        val route = application.getRoute(index)!!
        val defaultColor = resources.getColor(R.color.routeline)

        setContent {
            BorkozicTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    RoutePropertiesScreen(
                        route = route,
                        defaultColor = defaultColor,
                        onSave = {
                            setResult(RESULT_OK)
                            finish()
                        },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }
}
