package com.jiahan.smartcamera

import android.app.Application
import android.content.Intent
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.schedulers.Schedulers

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private var userDatabase: UserDatabase = UserDatabase.getInstance(application)!!
    private var userLiveData: LiveData<List<UserData>>
    private lateinit var userEachLiveData: Flowable<UserData>
//    private var fb : FirebaseService = FirebaseService()

    companion object {
//        @JvmStatic
//        @BindingAdapter("onLongClick")
//        fun setOnLongClick(view: View, userData: UserData) {
//            view.setOnLongClickListener {
////            view.setBackgroundColor(view.getResources().getColor(R.color.colorAccent));
//                userDatabase.userDAO().updateUserName(userData.id, userData.username + "n")
//                false
//            }
//        }
    }
    init {
        userLiveData = userDatabase.userDAO().getAllUser()
    }

    fun getUserLiveData(): LiveData<List<UserData>> {
        return userLiveData
    }

    fun getUserEachLiveData(a: String): Flowable<UserData> {
        return userDatabase.userDAO().getUserBasedOnID(a)
    }

    fun searchUserFlowable(a: String): Flowable<List<UserData>> {
        return userDatabase.userDAO().searchUser(a)
    }

    fun setOnClick(view: View, userData: UserData) {
        val intent = Intent(view.context, ImageActivity::class.java)
        intent.putExtra("imageid", userData.id)
        intent.putExtra("imagetext", userData.imagetext)
        view.context.startActivity(intent)
    }
    fun setOnClickDelete(view: View, userData: UserData) {
        AlertDialog.Builder(view.context)
            .setTitle("Remove selected photo?")
            .setPositiveButton("Confirm") { dialogInterface, i ->
                dialogInterface.dismiss()

                Completable.ambArray(userDatabase!!.userDAO().deleteImage(userData.id))
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .subscribe {
                    }

            }
            .setNegativeButton("Cancel") { dialogInterface, i -> dialogInterface.dismiss() }
            .setCancelable(false)
            .show()
    }

    // two way data binding on SearchView
//    @Bindable
//    public MutableLiveData<String> searchViewText = new MutableLiveData<>();
//    @BindingAdapter("searchViewQuery")
//    public static void setSearchViewText(SearchView searchView, MutableLiveData<String> text){
//        searchView.setQuery(text.getValue(), false);
//    }

//    fun updateUserInfo(){
//        fb.updateUserInfo(userDatabase)
//    }
//    fun updateMessageAndRecent(){
//        fb.updateMessageAndRecent(messageDatabase, userDatabase)
//    }


}