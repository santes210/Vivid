package com.vivid.app.presentation.stories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

data class Story(
    val id: String,
    val username: String,
    val avatarUrl: String,
    val hasUnseenStory: Boolean = true
)

@Composable
fun StoriesRow(
    stories: List<Story>,
    onStoryClick: (Story) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(stories) { story ->
            StoryItem(story = story, onClick = { onStoryClick(story) })
        }
    }
}

@Composable
fun StoryItem(story: Story, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Surface(
            shape = CircleShape,
            modifier = Modifier.size(68.dp),
            color = if (story.hasUnseenStory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ) {
            AsyncImage(
                model = story.avatarUrl,
                contentDescription = story.username,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .padding(3.dp),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            story.username,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

// Demo stories for Feed
val demoStories = listOf(
    Story("1", "Tú", "https://picsum.photos/id/1011/64/64", false),
    Story("2", "ana_vivid", "https://picsum.photos/id/1009/64/64"),
    Story("3", "carlos_vivid", "https://picsum.photos/id/1012/64/64"),
    Story("4", "lucia_vivid", "https://picsum.photos/id/1006/64/64"),
    Story("5", "diego_vivid", "https://picsum.photos/id/160/64/64")
)