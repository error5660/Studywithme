package com.studywithme.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.studywithme.app.R
import com.studywithme.app.ui.components.Chip
import com.studywithme.app.ui.theme.AppText

/** The 기록 tab: the 10-minute timetable and the statistics, one tap apart. */
@Composable
fun LogScreen(padding: PaddingValues) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.log_title), style = AppText.title, modifier = Modifier.weight(1f))
            Chip(stringResource(R.string.log_tab_timetable), page == 0, { page = 0 })
            Chip(stringResource(R.string.log_tab_stats), page == 1, { page = 1 })
            Chip(stringResource(R.string.log_tab_report), page == 2, { page = 2 })
        }
        when (page) { 0 -> TimetableScreen(); 1 -> StatsScreen(); else -> ReportsScreen() }
    }
}
