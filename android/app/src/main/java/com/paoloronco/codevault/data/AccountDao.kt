package com.paoloronco.codevault.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts WHERE isDeleted = 0 ORDER BY isFavorite DESC, title ASC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE isFavorite = 1 AND isDeleted = 0 ORDER BY title ASC")
    fun getFavorites(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getTrashAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE isDeleted = 0 ORDER BY title ASC")
    suspend fun getAllAccountsOnce(): List<AccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Delete
    suspend fun deleteAccount(account: AccountEntity)

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()

    @Query("DELETE FROM accounts WHERE isDeleted = 1")
    suspend fun emptyTrash()

    @Query("DELETE FROM accounts WHERE isDeleted = 1 AND deletedAt IS NOT NULL AND deletedAt <= :threshold")
    suspend fun deleteOlderThan(threshold: Long)
}
