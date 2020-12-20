package com.jiahan.smartcamera

import android.app.Application
import androidx.recyclerview.widget.RecyclerView
import com.jiahan.smartcamera.databinding.SearchListItemBinding

class SearchViewHolder(private val searchListItemBinding: SearchListItemBinding) : RecyclerView.ViewHolder(searchListItemBinding.root) {
    fun bind(userData: UserData?, application: Application?) {
        searchListItemBinding.user = userData
        searchListItemBinding.viewmodel = SearchViewModel(application!!)
    }
}