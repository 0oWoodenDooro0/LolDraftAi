package com.loldraft.client.compose

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.loldraft.client.compose.ui.DraftApp
import com.loldraft.client.compose.ui.analytics.DataAnalyticsApp
import com.loldraft.client.compose.viewmodel.DraftClientViewModel

fun main() =
    application {
        val viewModel = remember { DraftClientViewModel() }
        val isAnalyticsOpen by viewModel.isAnalyticsWindowOpen.collectAsState()

        Window(
            onCloseRequest = ::exitApplication,
            title = "LoL Draft AI - BP Intelligence Client",
            state = WindowState(width = 1440.dp, height = 900.dp),
        ) {
            DraftApp(viewModel)
        }

        if (isAnalyticsOpen) {
            Window(
                onCloseRequest = viewModel::closeAnalyticsWindow,
                title = "LoL Esports Analytics & Roster Matrix - 賽事數據與戰隊英雄池中心",
                state = WindowState(width = 1360.dp, height = 860.dp),
            ) {
                DataAnalyticsApp(viewModel.analyticsViewModel)
            }
        }
    }
