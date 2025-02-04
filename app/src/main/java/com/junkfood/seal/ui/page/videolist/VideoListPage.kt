package com.junkfood.seal.ui.page.videolist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.ExperimentalLifecycleComposeApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.junkfood.seal.R
import com.junkfood.seal.database.DownloadedVideoInfo
import com.junkfood.seal.ui.common.LocalWindowWidthState
import com.junkfood.seal.ui.component.BackButton
import com.junkfood.seal.ui.component.ConfirmButton
import com.junkfood.seal.ui.component.DismissButton
import com.junkfood.seal.ui.component.FilterChip
import com.junkfood.seal.ui.component.LargeTopAppBar
import com.junkfood.seal.ui.component.MediaListItem
import com.junkfood.seal.ui.component.MultiChoiceItem
import com.junkfood.seal.util.DatabaseUtil
import com.junkfood.seal.util.FileUtil
import kotlinx.coroutines.launch
import java.io.File

private const val AUDIO_REGEX = "(\\.mp3)|(\\.aac)|(\\.opus)|(\\.m4a)"

fun DownloadedVideoInfo.filterByType(
    videoFilter: Boolean = false,
    audioFilter: Boolean = true
): Boolean {
//    Log.d(TAG, "filterByType: ${this.videoPath}")
    return if (!(videoFilter || audioFilter))
        true
    else if (audioFilter)
        this.videoPath.contains(Regex(AUDIO_REGEX))
    else !this.videoPath.contains(Regex(AUDIO_REGEX))
}

fun DownloadedVideoInfo.filterByExtractor(extractor: String?): Boolean {
    return extractor.isNullOrEmpty() || (this.extractor == extractor)
}

private const val TAG = "VideoListPage"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLifecycleComposeApi::class)
@Composable
fun VideoListPage(
    videoListViewModel: VideoListViewModel = hiltViewModel(), onBackPressed: () -> Unit
) {
    val viewState = videoListViewModel.stateFlow.collectAsStateWithLifecycle().value
    val videoListFlow = videoListViewModel.videoListFlow

    val videoList = videoListFlow.collectAsState(ArrayList())
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState(),
        canScroll = { true }
    )
    val scope = rememberCoroutineScope()

    var isSelectEnabled by remember { mutableStateOf(false) }
    var showRemoveMultipleItemsDialog by remember { mutableStateOf(false) }

    val filterSet = videoListViewModel.filterSetFlow.collectAsState(mutableSetOf()).value
    fun DownloadedVideoInfo.filterSort(viewState: VideoListViewModel.VideoListViewState): Boolean {
        return filterByType(
            videoFilter = viewState.videoFilter,
            audioFilter = viewState.audioFilter
        ) && filterByExtractor(
            filterSet.elementAtOrNull(viewState.activeFilterIndex)
        )
    }

    @Composable
    fun FilterChips(modifier: Modifier = Modifier) {
        Row(
            modifier
                .horizontalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            FilterChip(
                selected = viewState.audioFilter,
                onClick = { videoListViewModel.clickAudioFilter() },
                label = stringResource(id = R.string.audio),
            )

            FilterChip(
                selected = viewState.videoFilter,
                onClick = { videoListViewModel.clickVideoFilter() },
                label = stringResource(id = R.string.video),
            )
            if (filterSet.size > 1) {
                Row {
                    Divider(
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .height(24.dp)
                            .width(1f.dp)
                            .align(Alignment.CenterVertically),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    for (i in 0 until filterSet.size) {
                        FilterChip(
                            selected = viewState.activeFilterIndex == i,
                            onClick = { videoListViewModel.clickExtractorFilter(i) },
                            label = filterSet.elementAt(i)
                        )
                    }
                }
            }
        }
    }


    val selectedItemIds =
        remember(videoList.value, isSelectEnabled, viewState) { mutableStateListOf<Int>() }
    val selectedVideos = remember(selectedItemIds.size) {
        mutableStateOf(
            videoList.value.count { info ->
                selectedItemIds.contains(info.id) && info.filterByType(
                    videoFilter = true,
                    audioFilter = false
                )
            })
    }
    val selectedAudioFiles = remember(selectedItemIds.size) {
        mutableStateOf(
            videoList.value.count { info ->
                selectedItemIds.contains(info.id) && info.filterByType(
                    videoFilter = false,
                    audioFilter = true
                )
            })
    }
    val visibleItemCount = remember(
        videoList.value, viewState
    ) { mutableStateOf(videoList.value.count { it.filterSort(viewState) }) }

    BackHandler(isSelectEnabled) {
        isSelectEnabled = false
    }

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        text = stringResource(R.string.downloads_history)
                    )
                },
                navigationIcon = {
                    BackButton(Modifier.padding(horizontal = 8.dp)) {
                        onBackPressed()
                    }
                }, actions = {
                    IconToggleButton(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        onCheckedChange = { isSelectEnabled = !isSelectEnabled },
                        checked = isSelectEnabled
                    ) {
                        Icon(
                            Icons.Outlined.Checklist,
                            contentDescription = stringResource(R.string.multiselect_mode)
                        )
                    }
                }, scrollBehavior = scrollBehavior
            )
        }, bottomBar = {
            AnimatedVisibility(
                isSelectEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                BottomAppBar(
                    modifier = Modifier
                ) {
                    val selectAllText = stringResource(R.string.select_all)
                    Checkbox(
                        modifier = Modifier.semantics {
                            this.contentDescription = selectAllText
                        },
                        checked = selectedItemIds.size == visibleItemCount.value && selectedItemIds.isNotEmpty(),
                        onCheckedChange = {
                            if (selectedItemIds.size == visibleItemCount.value) {
                                selectedItemIds.clear()
                            } else {
                                for (item in videoList.value) {
                                    if (!selectedItemIds.contains(item.id)
                                        && item.filterSort(viewState)
                                    ) {
                                        selectedItemIds.add(item.id)
                                    }
                                }
                            }
                        },
                    )
                    Text(
                        stringResource(R.string.multiselect_item_count).format(
                            selectedVideos.value,
                            selectedAudioFiles.value
                        ),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { showRemoveMultipleItemsDialog = true },
                        enabled = selectedItemIds.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteSweep,
                            contentDescription = stringResource(id = R.string.remove)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val cellCount = when (LocalWindowWidthState.current) {
            WindowWidthSizeClass.Expanded -> 2
            else -> 1
        }
        val span: (LazyGridItemSpanScope) -> GridItemSpan = { GridItemSpan(cellCount) }
        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxHeight()
                .padding(innerPadding), columns = GridCells.Fixed(cellCount)
        ) {
            item(span = span) {
                FilterChips(Modifier.fillMaxWidth())
            }
            items(
                items = videoList.value.reversed().sortedBy { it.filterByType() },
                key = { info -> info.id }) {
                with(it) {
                    val file = File(videoPath)
                    val videoFileSize = file.length() / (1024f * 1024f)
                    AnimatedVisibility(
                        visible = it.filterSort(viewState),
                        exit = shrinkVertically() + fadeOut(),
                        enter = expandVertically() + fadeIn()
                    ) {
                        MediaListItem(
                            modifier = Modifier,
                            title = videoTitle,
                            author = videoAuthor,
                            thumbnailUrl = thumbnailUrl,
                            videoPath = videoPath,
                            isFileAvailable = file.exists(),
                            videoFileSize = videoFileSize,
                            videoUrl = videoUrl,
                            isSelectEnabled = { isSelectEnabled },
                            isSelected = { selectedItemIds.contains(id) },
                            onSelect = {
                                if (selectedItemIds.contains(id)) selectedItemIds.remove(id)
                                else selectedItemIds.add(id)
                            },
                            onClick = { FileUtil.openFileInURI(videoPath) }
                        ) { videoListViewModel.showDrawer(scope, it) }
                    }
                }
            }
        }

    }
    VideoDetailDrawer()
    if (showRemoveMultipleItemsDialog) {
        var deleteFile by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showRemoveMultipleItemsDialog = false },
            icon = { Icon(Icons.Outlined.DeleteSweep, null) },
            title = { Text(stringResource(R.string.delete_info)) }, text = {
                Column {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        text = stringResource(R.string.delete_multiple_items_msg).format(
                            selectedItemIds.size
                        )
                    )
                    MultiChoiceItem(
                        text = stringResource(R.string.delete_file),
                        checked = deleteFile
                    ) { deleteFile = !deleteFile }
                }
            }, confirmButton = {
                ConfirmButton {
                    scope.launch {
                        selectedItemIds.forEach { id ->
                            if (deleteFile) {
                                val info = DatabaseUtil.getInfoById(id)
                                File(info.videoPath).delete()
                            }
                            DatabaseUtil.deleteInfoById(id)
                        }
                    }
                    showRemoveMultipleItemsDialog = false
                    isSelectEnabled = false
                }
            }, dismissButton = {
                DismissButton {
                    showRemoveMultipleItemsDialog = false
                }
            }
        )
    }
}




