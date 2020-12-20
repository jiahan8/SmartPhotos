//package com.jiahan.smartcamera;
//
//import android.util.Log
////import com.google.firebase.auth.FirebaseAuth
////import com.google.firebase.database.*
//import com.google.firebase.storage.FirebaseStorage
//import com.google.firebase.storage.StorageReference
////import com.jiahan.mysanity.ActivityChatInformation
////import com.jiahan.mysanity.database.MessageDatabase
////import com.jiahan.mysanity.database.UserDatabase
////import com.jiahan.mysanity.model.MessageData
////import com.jiahan.mysanity.model.MessageSendData
////import com.jiahan.mysanity.model.UserData
////import com.jiahan.mysanity.view.LocalDBAccessCoroutine
//import io.reactivex.Completable
//import io.reactivex.disposables.CompositeDisposable
//import io.reactivex.schedulers.Schedulers
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
////import kotlinx.coroutines.launch
//import java.util.ArrayList
//
//class FirebaseService {
//
//    var realname: String? = null
//    companion object{
//        var realnamehey: String? = null
//    }
////    var mFirebaseDatabase = FirebaseDatabase.getInstance()
//    var mFirebaseStorage = FirebaseStorage.getInstance()
//    var mFBMessageRef = mFirebaseDatabase.reference.child(ActivityChatInformation.main_messages)
//    var mFBUserRef = mFirebaseDatabase.reference.child(ActivityChatInformation.main_PlayUserInfo)
//    var mFBRealnameRef = mFirebaseDatabase.reference.child(ActivityChatInformation.main_PlayUserInfo).child(FirebaseAuth.getInstance().uid!!).child(ActivityChatInformation.main_Realname)
//    var mFBMessageListener: ChildEventListener? = null
//    var mFBUserListener: ChildEventListener? = null
//    var mFBRealnameListener: ValueEventListener? = null
////    var mStorageReference = mFirebaseStorage.reference.child(ActivityChatInformation.main_message_photos)
//    private val mDisposable = CompositeDisposable()
//
//    fun getStorageRef(): StorageReference{
//        return mFirebaseStorage.reference.child(ActivityChatInformation.main_message_photos)
//    }
//    fun sendMessage(messageSendData: MessageSendData?) {
//        mFBMessageRef.push().setValue(messageSendData)
//    }
//    fun updateUserInfo(userDatabase: UserDatabase?) {
//        mFBUserListener = object : ChildEventListener {
//            override fun onChildAdded(dataSnapshot: DataSnapshot, s: String?) {
//                val userData = UserData(dataSnapshot.key!!,
//                        dataSnapshot.child(ActivityChatInformation.main_username).value.toString(),
//                        dataSnapshot.child(ActivityChatInformation.main_Realname).value.toString(),
//                        dataSnapshot.child(ActivityChatInformation.main_image).value.toString()
////                        ,dataSnapshot.child("messageRecent").getValue() == null ? "" : dataSnapshot.child("messageRecent").getValue().toString(),
////                        Long.parseLong(dataSnapshot.child("timestamp").child("time").getValue().toString())
//                        , null, "8".toLong())
//                Log.e("addeduser", dataSnapshot.key!!)
//
////                LocalDBAccessCoroutine.runIt(userDatabase!!, userData)
//
////                    CoroutineScope(Dispatchers.IO).launch {
////                        userDatabase!!.userDAO().addUser(userData)
////                    }
//
//                mDisposable.add(Completable.ambArray(userDatabase!!.userDAO().addUser(userData))
//                        .subscribeOn(Schedulers.io())
//                        .observeOn(Schedulers.io())
//                        .subscribe {
////                                    Log.e("123", content);
//                        })
//
//            }
//            override fun onChildChanged(dataSnapshot: DataSnapshot, s: String?) {
//                Log.e("addeduserchanged", dataSnapshot.key!!)
//            }
//            override fun onChildRemoved(dataSnapshot: DataSnapshot) {}
//            override fun onChildMoved(dataSnapshot: DataSnapshot, s: String?) {}
//            override fun onCancelled(databaseError: DatabaseError) {
//                Log.e("addeduser", databaseError.message)
//            }
//        }
//        mFBUserRef.addChildEventListener(mFBUserListener!!)
//    }
//    fun updateAllUserInfoDummy(userDatabase: UserDatabase) {
////        UserData userData = new UserData(1, "dataSnapshot.child(ActivityChatInformation.main_username).getValue().toString()", dataSnapshot.child(ActivityChatInformation.main_Realname).getValue().toString(),
////                dataSnapshot.child(ActivityChatInformation.main_image).getValue().toString(), null, Long.parseLong("8"));
//        var tempint = 1
//        val temp: MutableList<UserData> = ArrayList()
//        while (tempint <= 30) {
//            ++tempint
//            temp.add(UserData(tempint.toString(), tempint.toString(), tempint.toString(), "", "hey football now?", 2.toLong()))
//        }
//        userDatabase.userDAO().addUser(temp)
//    }
//    fun updateMessageAndRecent(messageDatabase: MessageDatabase, userDatabase: UserDatabase) {
//        mFBMessageListener = object : ChildEventListener {
//            override fun onChildAdded(dataSnapshot: DataSnapshot, s: String?) {
//                Log.e("messageadded", dataSnapshot.key!!)
////                GenericTypeIndicator<List<MessageData>> t = new GenericTypeIndicator<List<MessageData>>() {};
////                MessageData messageData = dataSnapshot.getValue(MessageData.class);
//                val messageid = dataSnapshot.key
//                val messageData = MessageData(messageid!!, null,
//                        dataSnapshot.child(ActivityChatInformation.main_Realname ?: "").value.toString(), null, dataSnapshot.child(ActivityChatInformation.main_senderid).value.toString(),
//                        dataSnapshot.child(ActivityChatInformation.main_receiverid).value.toString(), dataSnapshot.child(ActivityChatInformation.main_followerid).value.toString(),
//                        if (dataSnapshot.child(ActivityChatInformation.main_image).value == null) "" else dataSnapshot.child(ActivityChatInformation.main_image).value.toString(),
//                        if (dataSnapshot.child(ActivityChatInformation.main_video).value == null) "" else dataSnapshot.child(ActivityChatInformation.main_video).value.toString(),
//                        if (dataSnapshot.child(ActivityChatInformation.main_text).value == null) "" else dataSnapshot.child(ActivityChatInformation.main_text).value.toString(),
//                        dataSnapshot.child(ActivityChatInformation.main_timestamp).child(ActivityChatInformation.main_Time).value.toString().toLong(),
//                        dataSnapshot.child(ActivityChatInformation.main_type).value.toString()
//                )
////                messageData.setId(messageid);
////                contentData.add(0, messageData);
////                adapter.notifyDataSetChanged();
////                LocalDBAccessCoroutine.Companion.runSendMessage(messageDatabase, messageData);
////                        .subscribeOn(Schedulers.io())
////                        .observeOn(AndroidSchedulers.mainThread())
////                        .subscribe( new Consumer<List<UserData>>() {
////                                        @Override
////                                        public void accept(List<UserData> userData) throws Exception {
////
////                                        }
////                                    })
////                ;
////                                throwable -> Log.e("TAG", "Unable to get username", throwable)));
//
////                UserData userData = new UserData(
////                        dataSnapshot.getKey(), dataSnapshot.child("username").getValue().toString(), dataSnapshot.child("realname").getValue().toString(), dataSnapshot.child("image").getValue().toString(),
////                        dataSnapshot.child("messageRecent").getValue() == null ? "" : dataSnapshot.child("messageRecent").getValue().toString(),
////                        Long.parseLong(dataSnapshot.child("timestamp").child("time").getValue().toString())
////                );
//
//                val content = (if (messageData.text == "") if (messageData.image == "") "video" else "image" else messageData.text)!!
//                if (messageData.senderid == FirebaseAuth.getInstance().uid) {
//
//                    mDisposable.add(Completable.concatArray(
//                            messageDatabase.messageDAO().addMessage(messageData),
//                            userDatabase.userDAO().updateUserMostRecentMessage(messageData.receiverid, content, messageData.timestamp))
//                            .subscribeOn(Schedulers.io())
//                            .observeOn(Schedulers.io())
//                            .subscribe {
////                                    Log.e("123", content);
//                            })
//                } else {
//                    mDisposable.add(Completable.concatArray(
//                            messageDatabase.messageDAO().addMessage(messageData),
//                            userDatabase.userDAO().updateUserMostRecentMessage(messageData.senderid, content, messageData.timestamp))
//                            .subscribeOn(Schedulers.io())
//                            .observeOn(Schedulers.io())
//                            .subscribe {
////                                    Log.e("789", content);
//                            })
//                }
//            }
//            override fun onChildChanged(dataSnapshot: DataSnapshot, s: String?) {
//                Log.e("messagechanged", dataSnapshot.key!!)
//            }
//            override fun onChildRemoved(dataSnapshot: DataSnapshot) {}
//            override fun onChildMoved(dataSnapshot: DataSnapshot, s: String?) {}
//            override fun onCancelled(databaseError: DatabaseError) {
//                Log.e("updatedmessage", databaseError.message)
//            }
//        }
//        mFBRealnameListener = object : ValueEventListener {
//            override fun onDataChange(dataSnapshot: DataSnapshot) {
//                Log.e("realnamechanged", dataSnapshot.key!!)
////                GenericTypeIndicator<List<MessageData>> t = new GenericTypeIndicator<List<MessageData>>() {};
////                MessageData messageData = dataSnapshot.getValue(MessageData.class);
////                contentData.add(0, messageData);
////                adapter.notifyDataSetChanged();
//                realnamehey = dataSnapshot.value.toString()
//            }
//            override fun onCancelled(databaseError: DatabaseError) {}
//        }
//        mFBMessageRef.addChildEventListener(mFBMessageListener!!)
//        mFBRealnameRef.addListenerForSingleValueEvent(mFBRealnameListener!!)
//    }
//
//
//    fun getRealnameBasedId(id: String): String {
//        val readusernamedatabase = FirebaseDatabase.getInstance()
//        val readusernamedatabaseReference = readusernamedatabase.getReference(ActivityChatInformation.main_PlayUserInfo).child(id) // use testing as the path
////        boolean validusername;
//        Log.e("realnameid", id)
//        readusernamedatabaseReference.addChildEventListener(object : ChildEventListener {
//            override fun onChildAdded(dataSnapshot: DataSnapshot, s: String?) {
////                UserInfoData userInfoData = dataSnapshot.getValue(UserInfoData.class);
////                realname = userInfoData.realname;
////                Log.e("realname", realname);
//            }
//            override fun onChildChanged(dataSnapshot: DataSnapshot, s: String?) {}
//            override fun onChildRemoved(dataSnapshot: DataSnapshot) {}
//            override fun onChildMoved(dataSnapshot: DataSnapshot, s: String?) {}
//            override fun onCancelled(databaseError: DatabaseError) {}
//        })
//        return id
//    }
//
//}