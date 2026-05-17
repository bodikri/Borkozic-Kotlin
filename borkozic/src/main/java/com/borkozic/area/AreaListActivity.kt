package com.borkozic.area
import com.borkozic.BaseApplication

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.overlay.AreaOverlay

class AreaListActivity : AppCompatActivity(), OnAreaActionListener {

    companion object {
        const val RESULT_START_AREA = 1
        const val RESULT_LOAD_AREA = 2
        const val RESULT_AREA_DETAILS = 3
    }

    private lateinit var application: Borkozic

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        application = BaseApplication.getApplication<Borkozic>()!!

        setContentView(R.layout.act_fragment)

        if (savedInstanceState == null) {
            val fragment = Fragment.instantiate(this, AreaList::class.java.name)
            val fragmentTransaction: FragmentTransaction = supportFragmentManager.beginTransaction()
            fragmentTransaction.add(android.R.id.content, fragment, "AreaList")
            fragmentTransaction.commit()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            RESULT_START_AREA -> {
                if (resultCode == RESULT_OK) finish()
            }
            RESULT_LOAD_AREA -> {
                if (resultCode == RESULT_OK) {
                    val application: Borkozic = BaseApplication.getApplication<Borkozic>()!!
                    val indexes = data!!.extras!!.getIntArray("index")!!
                    for (index in indexes) {
                        val newArea = AreaOverlay(this, application.getArea(index)!!)
                        application.areaOverlays.add(newArea)
                    }
                }
            }
            RESULT_AREA_DETAILS -> {
                if (resultCode == RESULT_OK) finish()
            }
        }
    }

    override fun onAreaDetails(area: com.borkozic.data.Area) {
        startActivityForResult(
            Intent(this, AreaDetails::class.java).putExtra("index", application.getAreaIndex(area)),
            RESULT_AREA_DETAILS
        )
    }

    override fun onAreaNavigate(area: com.borkozic.data.Area) {
        startActivityForResult(
            Intent(this, AreaStart::class.java).putExtra("index", application.getAreaIndex(area)),
            RESULT_START_AREA
        )
    }

    override fun onAreaEdit(area: com.borkozic.data.Area) {
        startActivity(Intent(this, AreaProperties::class.java).putExtra("index", application.getAreaIndex(area)))
    }

    override fun onAreaEditPath(area: com.borkozic.data.Area) {
        area.show = true
        setResult(RESULT_OK, Intent().putExtra("index", application.getAreaIndex(area)))
        finish()
    }

    override fun onAreaSave(area: com.borkozic.data.Area) {
        startActivity(Intent(this, AreaSave::class.java).putExtra("index", application.getAreaIndex(area)))
    }
}
