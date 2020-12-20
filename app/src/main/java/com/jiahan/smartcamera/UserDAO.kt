package com.jiahan.smartcamera

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.Completable
import io.reactivex.Flowable

@Dao
interface UserDAO {

    @Query("SELECT * from user")
    fun getAllUser(): LiveData<List<UserData>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addUser(userData: UserData): Completable


    @Query("SELECT * from user where imageid = :id")
    fun getUserBasedOnID(id: String): Flowable<UserData>

    @Query("select * from user where image_text like '%' || :searchQuery || '%'")
    fun searchUser(searchQuery: String): Flowable<List<UserData>>


    @Query("Delete from user where imageid = :id")
    fun deleteImage(id: String): Completable

    @Query("Delete from user")
    fun deleteTable(): Completable
}