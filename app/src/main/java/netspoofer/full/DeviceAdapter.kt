// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package netspoofer.full

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import netspoofer.full.domain.model.Device

class DeviceAdapter(
    private var devices: List<Device>,
    private val onBlockToggled: (Device, Boolean) -> Unit,
    private val onMitmToggled: (Device, Boolean) -> Unit,
    private val onSniffClicked: (Device) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvIp: TextView = view.findViewById(R.id.tvDeviceIp)
        val tvMac: TextView = view.findViewById(R.id.tvDeviceMac)
        val tvVendor: TextView = view.findViewById(R.id.tvDeviceVendor)
        val switchBlock: SwitchMaterial = view.findViewById(R.id.switchBlock)
        val switchMitm: SwitchMaterial = view.findViewById(R.id.switchMitm)
        val btnSniff: Button = view.findViewById(R.id.btnSniff)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]
        holder.tvIp.text = device.ipAddress
        holder.tvMac.text = device.macAddress
        holder.tvVendor.text = device.vendor ?: holder.itemView.context.getString(R.string.vendor_unknown)
        
        holder.switchBlock.setOnCheckedChangeListener(null)
        holder.switchBlock.isChecked = device.isBlocked
        holder.switchBlock.setOnCheckedChangeListener { _, isChecked ->
            onBlockToggled(device, isChecked)
        }

        holder.switchMitm.setOnCheckedChangeListener(null)
        // For simplicity, we don't store MITM state in Device model yet
        holder.switchMitm.setOnCheckedChangeListener { _, isChecked ->
            onMitmToggled(device, isChecked)
        }

        holder.btnSniff.setOnClickListener {
            onSniffClicked(device)
        }
    }

    override fun getItemCount() = devices.size

    fun updateDevices(newDevices: List<Device>) {
        devices = newDevices
        notifyDataSetChanged()
    }
}
