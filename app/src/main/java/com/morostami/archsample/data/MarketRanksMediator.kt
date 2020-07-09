/*
 * *
 *  * Created by Moslem Rostami on 6/17/20 12:29 PM
 *  * Copyright (c) 2020 . All rights reserved.
 *  * Last modified 6/17/20 12:29 PM
 *
 */

package com.morostami.archsample.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.morostami.archsample.data.api.CoinGeckoService
import com.morostami.archsample.data.local.CryptoLocalDataSource
import com.morostami.archsample.domain.model.CoinsRemoteKeys
import com.morostami.archsample.domain.model.RankedCoin
import com.morostami.archsample.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import java.io.InvalidObjectException
import javax.inject.Inject


const val COINGECKO_STARTING_PAGE_INDEX = 1
const val PAGE_SIZE = 50

@OptIn(ExperimentalPagingApi::class)
class MarketRanksMediator @Inject constructor(
    private val cryptoLocalDataSource: CryptoLocalDataSource,
    private val coinGeckoService: CoinGeckoService
) : RemoteMediator<Int, RankedCoin>() {

    override suspend fun initialize(): InitializeAction = InitializeAction.LAUNCH_INITIAL_REFRESH

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, RankedCoin>
    ): MediatorResult {
        val page = when (loadType) {
            LoadType.REFRESH -> {
                val remoteKeys = getRemoteKeyClosestToCurrentPosition(state)
                remoteKeys?.nextKey?.minus(1) ?: COINGECKO_STARTING_PAGE_INDEX
            }
            LoadType.PREPEND -> {
                val remoteKeys: CoinsRemoteKeys = getRemoteKeyForFirstItem(state)
                    ?: // The LoadType is PREPEND so some data was loaded before,
                    // so we should have been able to get remote keys
                    // If the remoteKeys are null, then we're an invalid state and we have a bug
                    throw InvalidObjectException("Remote key and the prevKey should not be null")
                // If the previous key is null, then we can't request more data
                val prevKey = remoteKeys.prevKey ?: return MediatorResult.Success(
                    endOfPaginationReached = true
                )
                remoteKeys.prevKey
            }
            LoadType.APPEND -> {
                val remoteKeys = getRemoteKeyForLastItem(state)
                if (remoteKeys?.nextKey == null) {
                    throw InvalidObjectException("Remote key should not be null for $loadType")
                }
                remoteKeys.nextKey
            }

        }

        try {
            if (!NetworkUtils.hasNetworkConnection()) {
                Timber.e("No Network Connection!")
                return MediatorResult.Success(true)
            }
            val rankedCoins = GlobalScope.async(Dispatchers.IO) {
                coinGeckoService.getPagedMarketRanks(
                    "usd",
                    page,
                    state.config.pageSize
                )
            }.await()

            val endOfPaginationReached = rankedCoins.isNullOrEmpty()
            Timber.e("PagingMediator End-Of-Pagination = $endOfPaginationReached")

            if (loadType == LoadType.REFRESH && !rankedCoins.isNullOrEmpty()) {
                cryptoLocalDataSource.clearCoinsRemoteKeys()
                cryptoLocalDataSource.deleteAllRankedCoins()
            }
            val prevKey = if (page == COINGECKO_STARTING_PAGE_INDEX) null else page - 1
            val nextKey = if (endOfPaginationReached) null else page + 1
            val keys = rankedCoins.map {
                CoinsRemoteKeys(coin_Id = it.id, prevKey = prevKey, nextKey = nextKey)
            }
            keys.apply {
                cryptoLocalDataSource.insertAllCoinsRemoteKeys(keys)
            }
            rankedCoins.apply {
                cryptoLocalDataSource.insertRankedCoins(rankedCoins)
            }

            return MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)
        } catch (exception: IOException) {
            return MediatorResult.Error(exception)
        } catch (exception: HttpException) {
            return MediatorResult.Error(exception)
        } catch (exception: Exception) {
            return MediatorResult.Error(exception)
        }
    }

    private suspend fun getRemoteKeyForLastItem(state: PagingState<Int, RankedCoin>): CoinsRemoteKeys? {
        // Get the last page that was retrieved, that contained items.
        // From that last page, get the last item
        return state.pages.lastOrNull { it.data.isNotEmpty() }?.data?.lastOrNull()
            ?.let { repo ->
                // Get the remote keys of the last item retrieved
                cryptoLocalDataSource.getRemoteKeysCoinId(repo.id)
            }
    }

    private suspend fun getRemoteKeyForFirstItem(state: PagingState<Int, RankedCoin>): CoinsRemoteKeys? {
        // Get the first page that was retrieved, that contained items.
        // From that first page, get the first item
        return state.pages.firstOrNull { it.data.isNotEmpty() }?.data?.firstOrNull()
            ?.let { repo ->
                // Get the remote keys of the first items retrieved
                cryptoLocalDataSource.getRemoteKeysCoinId(repo.id)
            }
    }

    private suspend fun getRemoteKeyClosestToCurrentPosition(
        state: PagingState<Int, RankedCoin>
    ): CoinsRemoteKeys? {
        // The paging library is trying to load data after the anchor position
        // Get the item closest to the anchor position
        return state.anchorPosition?.let { position ->
            state.closestItemToPosition(position)?.id?.let { repoId ->
                cryptoLocalDataSource.getRemoteKeysCoinId(repoId)
            }
        }
    }
}