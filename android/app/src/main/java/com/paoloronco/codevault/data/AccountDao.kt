package com.paoloronco.codevault.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY isFavorite DESC, title ASC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("""
        SELECT * FROM accounts
        WHERE title LIKE '%' || :q || '%'
        ORDER BY isFavorite DESC, title ASC
    """)
    fun searchAccounts(q: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavorites(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts ORDER BY title ASC")
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
}
