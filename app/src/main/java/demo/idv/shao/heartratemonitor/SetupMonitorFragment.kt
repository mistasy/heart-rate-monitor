package demo.idv.shao.heartratemonitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.RxBleDevice
import com.polidea.rxandroidble2.exceptions.BleScanException
import com.polidea.rxandroidble2.exceptions.BleScanException.LOCATION_PERMISSION_MISSING
import com.polidea.rxandroidble2.scan.ScanFilter
import com.polidea.rxandroidble2.scan.ScanSettings
import com.polidea.rxandroidble2.scan.ScanSettings.CALLBACK_TYPE_FIRST_MATCH
import com.polidea.rxandroidble2.scan.ScanSettings.SCAN_MODE_LOW_LATENCY
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.screen_setup_monitor.*
import java.util.*

class SetupMonitorFragment: Fragment() {

    companion object {
        const val HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb"
        const val MY_PERMISSIONS_REQUEST_LOCATION = 829
        val UUID_HEART_RATE_MEASUREMENT = UUID.fromString(HEART_RATE_MEASUREMENT)
    }

    private lateinit var viewAdapter: MyAdapter
    private lateinit var viewManager: RecyclerView.LayoutManager

    private val rxBleClient: RxBleClient
        get() {
            val mainActivity = context as? MainActivity ?: throw IllegalStateException()
            return mainActivity.rxBleClient
        }

    private val disposible = CompositeDisposable()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.screen_setup_monitor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? MainActivity)?.apply {
            setSupportActionBar(toolbarInclude as Toolbar)
            val supportActionBar = supportActionBar ?: throw IllegalStateException()
            supportActionBar.setDisplayShowTitleEnabled(true)
            title = "Set up device"
        }

        viewManager = LinearLayoutManager(context)
        viewAdapter = MyAdapter(mutableListOf()) {
            (activity as? MainActivity)?.connectionObservable = it.establishConnection(false)

            findNavController().navigate(R.id.action_setupMonitorFragment_to_homeFragment)
        }

        recyclerView.apply {
            layoutManager = viewManager
            adapter = viewAdapter
        }
    }


    override fun onStart() {
        super.onStart()

        scan()
    }

    private fun scan() {
        val scanSettings = ScanSettings.Builder()
            .setScanMode(SCAN_MODE_LOW_LATENCY)
            .build()
        val scanFilter = ScanFilter.Builder().build()
        disposible.add(
            rxBleClient.scanBleDevices(scanSettings, scanFilter)
                .subscribe( {
                    viewAdapter.addDevice(it.bleDevice)
                }, { err ->
                    if (err is BleScanException) {
                        when (err.reason) {
                            LOCATION_PERMISSION_MISSING -> {
                                requireActivity().let { act ->
                                    requestPermissions(
                                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                                        MY_PERMISSIONS_REQUEST_LOCATION)
                                }
                            }
                            else -> throw IllegalStateException(err)
                        }
                    }
                    throw IllegalStateException(err)
                })
        )
    }

    override fun onStop() {
        super.onStop()
        disposible.dispose()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == MY_PERMISSIONS_REQUEST_LOCATION && grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
           scan()
        }
    }
}

class MyAdapter(
    private val myDataset: MutableList<RxBleDevice>,
    private val connectTo: (RxBleDevice) -> Unit) : RecyclerView.Adapter<MyAdapter.MyViewHolder>() {

    class MyViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(parent: ViewGroup,
                                    viewType: Int): MyAdapter.MyViewHolder {
        val textView = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false) as TextView
        return MyViewHolder(textView)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.textView.text = myDataset[position].name
        holder.textView.setOnClickListener {
            val device = myDataset[position]
            connectTo(device)
        }
    }

    override fun getItemCount() = myDataset.size

    fun addDevice(device: RxBleDevice) {
        if (myDataset.find { it.name == device.name } == null) {
            myDataset.add(device)
            notifyDataSetChanged()
        }
    }

    fun addDevices(map: List<RxBleDevice>) {
        map.forEach {
            addDevice(it)
        }
    }
}