package com.loldraft.client.compose

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.loldraft.client.compose.ui.DraftApp

fun main() =
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "LoL Draft AI - BP Intelligence Client",
            state = WindowState(width = 1440.dp, height = 900.dp),
        ) {
            DraftApp()
        }
    }
