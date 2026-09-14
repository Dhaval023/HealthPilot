package com.example.myfitnessapp.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myfitnessapp.databinding.ItemDeviceBinding
import com.example.myfitnessapp.models.BleDevice

class DeviceAdapter(private val onDeviceClick: (String) -> Unit) :
    ListAdapter<BleDevice, DeviceAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = getItem(position)
        holder.bind(device)
    }

    inner class DeviceViewHolder(private val binding: ItemDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(device: BleDevice) {
            binding.tvDeviceName.text = (device.name ?: "Unknown Device").uppercase()
            binding.tvDeviceAddress.text = device.address
            binding.tvStatus.text = if (device.rssi > -70) "NEARBY" else "TAP TO CONNECT"
            binding.btnPair.setOnClickListener { onDeviceClick(device.address) }
            binding.root.setOnClickListener { onDeviceClick(device.address) }
        }
    }

    class DeviceDiffCallback : DiffUtil.ItemCallback<BleDevice>() {
        override fun areItemsTheSame(oldItem: BleDevice, newItem: BleDevice): Boolean {
            return oldItem.address == newItem.address
        }

        override fun areContentsTheSame(oldItem: BleDevice, newItem: BleDevice): Boolean {
            return oldItem == newItem
        }
    }
}
