package com.jiahan.smartcamera

import android.app.Application
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jiahan.smartcamera.databinding.SearchListItemBinding

class SearchAdapter(private val userDataList: List<UserData>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var context: Context? = null
    private var searchListItemBinding: SearchListItemBinding? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        context = parent.context
        searchListItemBinding = SearchListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SearchViewHolder(searchListItemBinding!!)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        setContentData(holder as SearchViewHolder, position)
    }

    override fun getItemCount(): Int {
        return userDataList.size
    }

    override fun getItemViewType(position: Int): Int {
        return position
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    fun setContentData(holder: SearchViewHolder, position: Int) {
        holder.bind(userDataList[position], context!!.applicationContext as Application)
    }

}