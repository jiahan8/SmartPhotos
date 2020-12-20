package com.jiahan.smartcamera

import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.ArrayList

class SearchActivity : AppCompatActivity() {

    var searchView: SearchView? = null
    var recyclerView: RecyclerView? = null
    var searchAdapter: SearchAdapter? = null
    var layoutManager: RecyclerView.LayoutManager? = null
    var userDataList: MutableList<UserData>? = null
    var searchViewModel: SearchViewModel? = null
    private val mDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        searchView = findViewById(R.id.sv_search)
        recyclerView = findViewById(R.id.rv_search)

        userDataList = ArrayList()
        layoutManager = LinearLayoutManager(this)
        recyclerView!!.setLayoutManager(layoutManager)
        searchAdapter = SearchAdapter(userDataList!!)
        recyclerView!!.setAdapter(searchAdapter)

        val searchViewImageView = searchView!!.findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon)
//        searchViewImageView.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_search_dark))

//        FirebaseService.initFirebase();
//        userDatabase = UserDatabase.getInstance(applicationContext)

        // view model
        searchViewModel = ViewModelProvider(this).get(SearchViewModel::class.java)

        setupViewModel(searchViewModel!!)

//        searchViewModel!!.updateUserInfo()
//        searchViewModel!!.updateMessageAndRecent()

        searchView!!.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                if (newText == "") {
                    // default results
                    Log.e("updateui", newText + "_")
                    updateSearchedUI("")
                } else {
                    // search query
                    Log.e("updateui", newText)
                    updateSearchedUI(newText)
                }
                return true
            }
        })
//        searchViewModel.searchViewText.observe(this, new androidx.lifecycle.Observer<String>() {
//            @Override
//            public void onChanged(String s) {
//                if(s.equals("")){
//                    // default results
//                    Log.e("updateui", s + "_" );
//                    updateSearchedUI("");
//                }else{
//                    // search query
//                    Log.e("updateui", s );
//                    updateSearchedUI(s);
//                }
//            }
//        });

        val observable = Observable.fromIterable(userDataList)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
        observable.subscribe(object : Observer<UserData> {
            override fun onSubscribe(d: Disposable) {
                Log.e("rxsubscribe", "sub")
            }

            override fun onNext(userData: UserData) {
                Log.e("rxnext", userData.toString())
            }

            override fun onError(e: Throwable) {
                Log.e("rxerror", e.toString())
            }

            override fun onComplete() {
                Log.e("rxcomplete", "abc")
            }
        })

//        Maybe.just( userDataList );
//        Single.just( userDataList );
//
//        Flowable.fromIterable(userDataList);
//        Flowable.fromArray(userDataList);
//
//        Observable.fromCallable( () -> "what" );
//
//        Observable<String> obs = Observable.just("what");
//        Observable<String> obs2 = obs.map( s -> s.toUpperCase() );
        Thread(Runnable { Log.e("thread", Thread.currentThread().name) }).start()

//        mDisposable.add( searchView.getQuery() )
    }

    override fun onResume() {
        super.onResume()
        searchView!!.clearFocus()
    }

    fun setupViewModel(searchViewModel: SearchViewModel) {
        searchViewModel.getUserLiveData().observe(this, androidx.lifecycle.Observer { userData ->
//                if(userData != null && userData.size() != 0) {
            Log.e("userlist", userData.size.toString())
            updateUI(userData)
//                }
        })
//        searchViewModel.updateUserInfo()
//        searchViewModel.updateMessageAndRecent()

//        for(int i=0; i<searchViewModel.getUserIdList().size(); i++){
//            searchViewModel.getUserEachLiveData(searchViewModel.getUserIdList().get(i))
//                    .subscribeOn(Schedulers.io())
//                    .subscribe(new Subscriber<UserData>() {
//                        @Override
//                        public void onSubscribe(Subscription s) {
//                            Log.e("rxsubscribe", "sub" );
//                        }
//
//                        @Override
//                        public void onNext(UserData userData) {
//                            Log.e("rxnext", "sub" );
//                        }
//
//                        @Override
//                        public void onError(Throwable t) {
//                            Log.e("rxerror", "sub" );
//                        }
//
//                        @Override
//                        public void onComplete() {
//                            Log.e("rxcomplete", "sub" );
//                        }
//                    });
//        }

    }

    fun updateUI(temp: List<UserData>) {
//        userDataList.clear();
//        searchAdapter.notifyDataSetChanged();
//        UserData userData;
//        for(int i=0; i<temp.size() ; i++){
//            Log.e("updateuiui", String.valueOf(i) );
//            userData = temp.get(i);
//            userDataList.add(userData);
//            searchAdapter.notifyDataSetChanged();
//        }
//        searchAdapter.notifyItemRangeChanged(0, temp.size()-1);
        val diffResult = DiffUtil.calculateDiff(RecyclerViewDiffUtil(userDataList!!, temp))
        userDataList!!.clear()
        userDataList!!.addAll(temp)
        diffResult.dispatchUpdatesTo(searchAdapter!!)
    }

    fun updateSearchedUI(text: String) {
//        updateUI( userDatabase.userDAO().searchUser(text) );
//        Observable.fromCallable( () -> userDatabase.userDAO().searchUser(text) );
        mDisposable.add(searchViewModel!!.searchUserFlowable(text)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ userData ->
                Log.e("updateui_rx", userData.size.toString())
                updateUI(userData)
            }
            ) { throwable: Throwable? -> Log.e("TAG", "Unable to get username", throwable) })
    }

    override fun onDestroy() {
        super.onDestroy()
        mDisposable.clear()
    }

}